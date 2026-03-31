(ns fourteatoo.mqhub.geo
  (:require [fourteatoo.mqhub.log :as log]
            [clojurewerkz.machine-head.client :as mh]
            [fourteatoo.mqhub.conf :refer :all]
            [fourteatoo.mqhub.mqtt :as mqtt]
            [fourteatoo.mqhub.action :as act]
            [cheshire.core :as json]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]))


(defn- haversine-distance
  "Calculates the distance in meters between two points (lat1, lon1)
  and (lat2, lon2)."
  [{lat1 :lat lon1 :lon} {lat2 :lat lon2 :lon}]
  (let [R 6371000 ; Earth radius in meters
        dlat (Math/toRadians (- lat2 lat1))
        dlon (Math/toRadians (- lon2 lon1))
        a (+ (* (Math/sin (/ dlat 2)) (Math/sin (/ dlat 2)))
             (* (Math/cos (Math/toRadians lat1))
                (Math/cos (Math/toRadians lat2))
                (Math/sin (/ dlon 2))
                (Math/sin (/ dlon 2))))
        c (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))]
    (* R c)))

(defmulti ^:private process-event
  (fn [_ctx _topic data _configuration]
    (:type data)))

;; Owntracks often fails to notify or even notice that it crossed a
;; geo fence.  We don't fully rely on its opinion and we calculate our
;; own zones.  The only thing we need is the coordinates from
;; Owntracks, of which the user can force the delivery in the app.

#_
(defmethod process-event "transition"
  [ctx topic data configuration]
  (log/debug "process-event transition:" (pr-str ctx) (pr-str data))
  (when-let [events (get (:areas configuration) (:desc data))]
    ((get events (keyword (:event data))) ctx topic data)))

(def transition-threshold 20)

(defn- transition [position zone currently-inside?]
  (let [distance (haversine-distance position zone)]
    (if currently-inside?
      (when (> distance (+ transition-threshold (:radius zone)))
        :leave)
      (when (< distance (:radius zone))
        :enter))))

(defn- new-context [& [initial-state]]
  (assoc initial-state ::context true))

(defn- context? [thing]
  (and (map? thing)
       (::context thing)))

(defn- a-context [& args]
  (->> args
       (drop-while (complement context?))
       first))

(defn present-in? [region ctx]
  (contains? (:presences ctx) region))

(defn- list-transitions [data areas presences]
  (map (fn [[name area-config]]
         [name (transition data area-config (contains? presences name))])
       areas))

(defn- apply-presence-transitions [current-presences transitions]
  (reduce (fn [regions [name transition]]
            (case transition
              :enter (conj regions name)
              :leave (disj regions name)
              regions))
          (or current-presences #{})
          transitions))

(defn- execute-transitions [ctx topic data areas transitions]
  (reduce (fn [ctx [area-name transition]]
            (if transition
              (a-context ((get-in areas [area-name transition])
                          ctx topic data)
                         ctx)
              ctx))
          ctx transitions))

(defmethod process-event "location"
  [ctx topic data configuration]
  (log/debug "process-event location:" (pr-str ctx) (pr-str data))
  (let [transitions (list-transitions data (:areas configuration) (:presences ctx))
        ctx (update ctx :presences apply-presence-transitions transitions)]
    (execute-transitions ctx topic data (:areas configuration) transitions)))

(defmethod process-event :default
  [ctx topic data configuration]
  (log/debug "ignored event" {:ctx ctx :topic topic :data data}))

(defn- actions->fn [actions]
  (act/make-code-fn '[ctx topic data] actions))

(defn- normalize-configuration [configuration]
  (update configuration :areas
          update-vals (fn [area-config]
                        (-> area-config
                            (update :enter actions->fn)
                            (update :leave actions->fn)))))

(defn make-topic-listener [configuration]
  (let [ctx (atom (new-context))
        configuration (normalize-configuration configuration)]
    (fn [topic data]
      (let [data (json/parse-string data csk/->kebab-case-keyword)
            topic (mqtt/parse-topic topic (:topic configuration))]
        (reset! ctx
                (process-event @ctx topic data configuration))))))
