(ns fourteatoo.mqhub.sub
  (:require [fourteatoo.mqhub.meter :as meter]
            [fourteatoo.mqhub.geo :as geo]
            [fourteatoo.mqhub.macro :as macro]
            [taoensso.timbre :as log]
            [clojure.string :as s]
            [fourteatoo.mqhub.mqtt :as mqtt]
            [fourteatoo.mqhub.blink :as blink]
            [fourteatoo.mqhub.evo-home :as eh]
            [fourteatoo.mqhub.conf :refer :all]))


(defmulti subscribe-topic :type)

(def meters (atom {}))

(defmethod subscribe-topic :log
  [configuration]
  (mqtt/subscribe {(:topic configuration) 0}
                    (fn [topic payload]
                      (log/log (or (:level configuration)
                                   :info)
                        "MQTT topic:" topic " payload:" payload))))

(defmethod subscribe-topic :meter
  [configuration]
  (mqtt/subscribe {(:topic configuration) 0}
                  (meter/make-topic-listener meters configuration)))

(defmethod subscribe-topic :geo
  [configuration]
  (mqtt/subscribe {(:topic configuration) 0}
                  (geo/make-topic-listener configuration)))

(defmethod subscribe-topic :macro
  [configuration]
  (mqtt/subscribe {(:topic configuration) 0}
                  (macro/make-topic-listener configuration)))

(defmethod subscribe-topic :blink
  [configuration]
  (mqtt/subscribe {(:topic configuration) 0}
                  (blink/make-topic-listener configuration)))

(defmethod subscribe-topic :evo-home
  [configuration]
  (mqtt/subscribe {(:topic configuration) 0}
                  (eh/make-topic-listener configuration)))

(defn start-subscriptions [subscriptions]
  (log/info "Subscribing topics:" (s/join ", " (keys subscriptions)))
  (doseq [[topic configuration] subscriptions]
    (-> configuration
        (assoc :topic topic)
        subscribe-topic)))

