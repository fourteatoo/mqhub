(ns fourteatoo.mqhub.macro
  (:require
   [fourteatoo.mqhub.action :as act]
   [fourteatoo.mqhub.mqtt :as mqtt]))

(defn make-topic-listener [configuration]
  (fn [topic data]
    (let [topic (mqtt/parse-topic topic (:topic configuration))]
      (act/execute-actions (:actions configuration)
                           topic data))))
