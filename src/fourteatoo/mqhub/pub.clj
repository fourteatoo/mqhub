(ns fourteatoo.mqhub.pub
  (:require [taoensso.timbre :as log]
            [clojure.string :as s]
            [fourteatoo.mqhub.mqtt :as mqtt]
            [fourteatoo.mqhub.blink :as blink]
            [fourteatoo.mqhub.evo-home :as eh]
            [fourteatoo.mqhub.conf :refer :all]))


(defmulti publish-topic :type)

(defmethod publish-topic :blink
  [configuration]
  (blink/start-blink-monitor (:topic configuration) configuration))

(defmethod publish-topic :evo-home
  [configuration]
  (eh/start-evo-home-monitor (:topic configuration) configuration))

(defn start-topic-publisher [publishers]
  (log/info "Starting publishing")
  (doseq [[topic configuration] publishers]
    (-> configuration
        (assoc :topic topic)
        publish-topic)))

