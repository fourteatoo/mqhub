(ns fourteatoo.mqhub.pub
  (:require [fourteatoo.mqhub.log :as log]
            [fourteatoo.mqhub.conf :refer :all]))


(defmulti start-monitor :type)

(defmethod start-monitor :default
  [configuration]
  (log/error "unknown monitor" {:configuration configuration}))

(defn start-topic-publisher [publishers]
  (log/info "Starting publishing")
  (doseq [[topic configuration] publishers]
    (-> configuration
        (assoc :topic topic)
        start-monitor)))

