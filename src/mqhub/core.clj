(ns mqhub.core
  (:require [taoensso.timbre :as log]
            [clojure.string :as s]
            [mqhub.conf :refer :all]
            [mqhub.meter :as meter]
            [mqhub.geo :as geo]
            [mqhub.mqtt :as mqtt]
            [mqhub.macro :as macro]
            [mqhub.sched :as sched])
  (:gen-class))


(defmulti monitor :type)

(def meters (atom {}))

(defmethod monitor :meter
  [configuration]
  (mqtt/subscribe {(:topic configuration) 0}
                  (meter/make-topic-listener meters configuration)))

(defmethod monitor :geo
  [configuration]
  (mqtt/subscribe {(:topic configuration) 0}
                  (geo/make-topic-listener configuration)))

(defmethod monitor :macro
  [configuration]
  (mqtt/subscribe {(:topic configuration) 0}
                  (macro/make-topic-listener configuration)))

(defn start-monitor []
  (log/info "Monitoring:" (s/join ", " (keys (conf :topics))))
  (doseq [[topic configuration] (conf :topics)]
    (-> configuration
        (assoc :topic topic)
        monitor)))

(defn -main [& args]
  (start-monitor)
  (println "Monitor started.  Type Ctrl-C to exit.")
  (while true
    (Thread/sleep 1000)))
