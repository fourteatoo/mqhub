(ns fourteatoo.mqhub.geo
  (:require [taoensso.timbre :as log]
            [clojurewerkz.machine-head.client :as mh]
            [fourteatoo.mqhub.conf :refer :all]
            [fourteatoo.mqhub.mqtt :as mqtt]
            [fourteatoo.mqhub.action :as act]
            [cheshire.core :as json]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]))


(defmulti ^:private process-event (fn [_ data _] (:type data)))

(defmethod process-event "transition"
  [topic data configuration]
  (when-let [events (get (:areas configuration) (:desc data))]
    (act/execute-actions (get events (keyword (:event data)))
                         topic data)))

(defmethod process-event "location"
  [topic data configuration]
  (let [regions (set (:inregions data))]
    (-> (mapcat (fn [[name events]]
                  ((if (regions name) :enter :leave) events))
                (:areas configuration))
        (act/execute-actions topic data))))

(defmethod process-event :default
  [topic data configuration]
  (throw (ex-info "don't know how to process event" {:topic topic :data data})))

(defn make-topic-listener [configuration]
  (fn [topic data]
    (let [data (json/parse-string data csk/->kebab-case-keyword)
          topic (mqtt/parse-topic topic [:app :user :device :rest])]
      (process-event topic data configuration))))

#_ ((make-topic-listener {}) "topic" {:data 1})
