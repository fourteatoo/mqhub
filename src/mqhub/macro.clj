(ns mqhub.macro
  (:require
   [mqhub.action :as act]
   [mqhub.mqtt :as mqtt]))

(defn make-topic-listener [configuration]
  (fn [topic data]
    (let [topic (mqtt/parse-topic topic [nil :name :rest])]
      (act/execute-actions (:commands configuration)
                           topic data))))
