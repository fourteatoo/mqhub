(ns fourteatoo.mqhub.mqtt
  (:require [fourteatoo.mqhub.log :as log]
            [clojurewerkz.machine-head.client :as mh]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]
            [fourteatoo.mqhub.conf :refer :all]
            [fourteatoo.mqhub.misc :refer :all]
            [mount.core :as mount]
            [diehard.core :as dh]))

(defn- wrap-handler [handler]
  (fn [topic metadata payload]
    (try
      (let [payload (String. payload "UTF-8")]
        (log/debug "received" topic ":" payload)
        (handler topic payload))
      (catch Exception e
        (log/error e "Error in topic listener.")
        #_(System/exit 2)))))

(def service)

(defn- restore-subscriptions [connection subscriptions]
  (doseq [[topic handler] subscriptions]
    (log/debug "re-subscribing" topic)
    (mh/subscribe connection topic (wrap-handler handler))))

(defn connect [& [connect-opts]]
  (let [subscriptions (atom {})
        conn (mh/connect (conf :mqtt :broker)
                         {;; :client-id (conf :mqtt :client-id)
                          :on-connection-lost (fn [cause]
                                                (log/debug "connection lost, cause:" cause))
                          :on-connect-complete (fn [connection _ url]
                                                 (log/debug "connect complete" connection)
                                                 (restore-subscriptions connection @subscriptions))
                          :opts (merge {:auto-reconnect true
                                        :connection-timeout 30
                                        :clean-session true}
                                       connect-opts)})]
    (mh/publish conn "hello" "mqhub connected")
    {:connection conn
     :subscriptions subscriptions}))

(defn disconnect [service]
  (try
    (mh/disconnect-and-close (:connection service))
    (catch Exception e
      (log/error e "cannot disconnect:" (pr-str service)))))

(mount/defstate service
  :start (connect)
  :stop (disconnect service))

(defn- sanitize-topic [topic]
  (->> (s/split topic #"/")
       (map (fn [part]
              (if (s/starts-with? part "$")
                "+"
                part)))
       (s/join "/")))

(defn subscribe [topic f]
  (mh/subscribe (:connection service) topic (wrap-handler f))
  (swap! (:subscriptions service) assoc topic f))

;; This is to allow us to receive the same messages we publish
(mount/defstate publish-service
  :start (connect)
  :stop (disconnect publish-service))

(defn publish [topic payload]
  (log/info "publishing" topic ":" (pr-str payload))
  (dh/with-retry {:policy retry-policy
                  :on-failure (fn [v e]
                                (log/error e "publish failed"))}
    (mh/publish (:connection publish-service) topic payload)))

;; parts may be omitted with nil elements
(defn parse-topic-parts [topic parts]
  (dissoc (->> (s/split topic #"/" (count parts))
               (zipmap parts))
          nil))

(defn parse-topic-from-template [topic template]
  (->> (s/split template #"/")
       (map (fn [s]
              (if (s/starts-with? s "$")
                (keyword (subs s 1))
                nil)))
       (parse-topic-parts topic)))

(defn parse-topic [topic parts-or-template]
  (if (vector? parts-or-template)
    (parse-topic-from-template topic parts-or-template)
    (parse-topic-parts topic parts-or-template)))

(defn- vec->path [v]
  (s/join "/" (map #(str (if (keyword? %) (name %) %)) v)))

(defn publish-delta
  "Compare two maps and publish the differences in topic."
  [topic old new]
  (->> (list-new-values old new)
       (map (fn [[path v]]
              [(vec->path path) v]))
       (run! (fn [[p v]]
               (publish (str topic "/" p) (str v))))))
