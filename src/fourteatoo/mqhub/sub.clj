(ns fourteatoo.mqhub.sub
  (:require [fourteatoo.mqhub.meter :as meter]
            [fourteatoo.mqhub.geo :as geo]
            [fourteatoo.mqhub.macro :as macro]
            [fourteatoo.mqhub.log :as log]
            [clojure.string :as s]
            [fourteatoo.mqhub.mqtt :as mqtt]
            [fourteatoo.mqhub.blink :as blink]
            [fourteatoo.mqhub.evo-home :as eh]
            [fourteatoo.mqhub.conf :refer :all]))


(defmulti subscribe-topic :type)

(def meters (atom {}))

(defn wrap-condition [configuration f]
  (let [p (eval (:condition configuration))]
    (fn [topic data]
      (when (or (not p)
                (p topic data))
        (f topic data)))))

(defn- subscribe [configuration handler]
  (mqtt/subscribe {(:topic configuration) 0}
                  (wrap-condition configuration handler)))

(defmethod subscribe-topic :log
  [configuration]
  (subscribe configuration
             (fn [topic payload]
                    (log/log (or (:level configuration)
                                 :info)
                             "MQTT topic:" topic " payload:" payload))))

(defmethod subscribe-topic :meter
  [configuration]
  (subscribe configuration
             (meter/make-topic-listener meters configuration)))

(defmethod subscribe-topic :geo
  [configuration]
  (subscribe configuration
             (geo/make-topic-listener configuration)))

(defmethod subscribe-topic :macro
  [configuration]
  (subscribe configuration
             (macro/make-topic-listener configuration)))

(defmethod subscribe-topic :blink
  [configuration]
  (subscribe configuration
             (blink/make-topic-listener configuration)))

(defmethod subscribe-topic :evo-home
  [configuration]
  (subscribe configuration
             (eh/make-topic-listener configuration)))

(defn start-subscriptions [subscriptions]
  (log/info "Subscribing topics:" (s/join ", " (keys subscriptions)))
  (doseq [[topic configuration] subscriptions]
    (subscribe-topic (assoc configuration :topic topic))))
