(ns mqhub.mqtt
  (:require [taoensso.timbre :as log]
            [clojurewerkz.machine-head.client :as mh]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]
            [mqhub.conf :refer :all]))


(defonce subscriptions (atom {}))

(defn- wrappe-handler [handler]
  (fn [topic metadata payload]
    (try
      (let [payload (String. payload "UTF-8")]
        (log/debug "received" topic ":" payload)
        (handler topic payload))
      (catch Exception e
        (log/error e "Error in topic listener.")
        #_(System/exit 2)))))

(def connection)

(defn- restore-subscriptions []
  (run! (fn [[topic handler]]
          (log/debug "re-subscribing" topic)
          (mh/subscribe @connection topic (wrappe-handler handler)))
        @subscriptions))

(defn connect [& [connect-opts]]
  (let [conn (mh/connect (conf :mqtt :broker)
                         {:client-id (conf :mqtt :client-id)
                          :on-connection-lost (fn [cause]
                                                (log/debug "connection lost, cause:" cause))
                          :on-connect-complete (fn [connection _ url]
                                                 (log/debug "connect complete" connection)
                                                 (restore-subscriptions))
                          :opts (merge {:auto-reconnect true
                                        :connection-timeout 30
                                        :clean-session true}
                                       connect-opts)})]
    (mh/publish conn "hello" "mqhub connected")
    conn))

(def connection (delay (connect)))

(defn subscribe [topic f]
  (mh/subscribe @connection topic (wrappe-handler f))
  (swap! subscriptions assoc topic f))

(comment
  (subscribe {"#" 0} (fn [topic payload] (prn topic payload))))

(defn publish [topic payload]
  (mh/publish @connection topic payload))

;; parts may be omitted with nil elements
(defn parse-topic [topic parts]
  (dissoc (->> (s/split topic #"/" (count parts))
               (zipmap parts))
          nil))
