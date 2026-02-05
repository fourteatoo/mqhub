(ns fourteatoo.mqhub.geo
  (:require [fourteatoo.mqhub.log :as log]
            [clojurewerkz.machine-head.client :as mh]
            [fourteatoo.mqhub.conf :refer :all]
            [fourteatoo.mqhub.mqtt :as mqtt]
            [fourteatoo.mqhub.action :as act]
            [cheshire.core :as json]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]))


(defn- execute-actions [actions topic data])

(defmulti ^:private process-event
  (fn [_ctx _topic data _configuration]
    (:type data)))

(defmethod process-event "transition"
  [ctx topic data configuration]
  (when-let [events (get (:areas configuration) (:desc data))]
    ((get events (keyword (:event data))) ctx topic data)
    #_(act/execute-actions (get events (keyword (:event data)))
                         topic data)))

(defmethod process-event "location"
  [ctx topic data configuration]
  (let [regions (set (:inregions data))]
    (log/debug "process-event location regions=" (pr-str regions))
    (log/debug "process-event location areas=" (pr-str (:areas configuration)))
    (->> (:areas configuration)
         (map (fn [[name events]]
                ((if (regions name) :enter :leave) events)))
         (remove nil?)
         (run! (fn [f]
                 (log/debug "process-event f=" (pr-str f))
                 (f ctx topic data))))))

(defmethod process-event :default
  [ctx topic data configuration]
  (log/debug "ignored event" {:ctx ctx :topic topic :data data}))

(defn- actions->fn [actions]
  (if (and (vector? actions)
           (map? (first actions)))
    (fn [_ctx topic data]
      (act/execute-actions actions topic data))
    (act/make-code-fn '[ctx topic data] actions)))

(defn- normalize-configuration [configuration]
  (update configuration :areas update-vals #(update-vals % actions->fn)))

(comment
  (normalize-configuration {:foo 1
                            :bar 2
                            :areas {"home" {:enter '(do data)
                                            :leave '(do topic)}
                                    "office" {:enter [{:type :evo-home}]
                                              :leave [{:type :evo-home}]}}}))

(defn make-topic-listener [configuration]
  (let [ctx (atom {})
        configuration (normalize-configuration configuration)]
    (log/debug "geo/make-topic-listener configuration=" (pr-str configuration))
    (fn [topic data]
      (let [data (json/parse-string data csk/->kebab-case-keyword)
            topic (mqtt/parse-topic topic (:topic configuration))]
        (process-event ctx topic data configuration)))))

(comment
  (mqtt/parse-topic "owntracks/walter/phone" "owntracks/walter/$device")
  (mqtt/parse-topic "owntracks/walter/phone" "owntracks/walter/+"))
