(ns fourteatoo.mqhub.meter
  (:require [taoensso.timbre :as log]
            [cprop.core :as cprop]
            [cheshire.core :as json]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]
            [fourteatoo.mqhub.conf :refer :all]
            [fourteatoo.mqhub.mqtt :as mqtt]
            [fourteatoo.mqhub.action :as act]))

(defn- parse-time [s]
  (java.time.LocalDateTime/parse s))

(defn- parse-data [data]
  (-> (json/parse-string data csk/->kebab-case-keyword)
      (update :time parse-time)
      (update-in [:energy :total-start-time] parse-time)))

(defn- make-telemetry-printer
  [config]
  (fn [topic payload]
    (prn topic (parse-data payload))))

(defn- assoc-device [devices topic k v]
  (swap! devices update topic assoc k v))

(defn- update-device [devices topic k f & args]
  (apply swap! devices update topic update k f args))

(def ^:private default-avg-samples 5)

(defn- cma
  "Calculate the Cumulative Moving Average of `x`."
  [samples old-avg new-value]
  (let [old-avg (or old-avg new-value)]
    (float                    ; avoid accumulating huge ratios
     (+ old-avg
        (/ (- new-value old-avg)
           (inc (or samples default-avg-samples)))))))

(defn make-topic-listener
  [devices config]
  (fn [topic payload]
    (let [{:keys [telemetry threshold hysteresis trigger actions]} config
          data (parse-data payload)
          state (get-in @devices [topic :state] :off)
          avg-telemetry (cma (get config :avg-samples) (get-in @devices [topic :avg]) (get-in data telemetry))
          on-threshold (* threshold (+ 1 hysteresis))
          off-threshold (* threshold (- 1 hysteresis))
          current-state (cond (< avg-telemetry off-threshold) :off
                              (> avg-telemetry on-threshold) :on
                              :else state)]
      (assoc-device devices topic :avg avg-telemetry)
      (log/debug "average telemetry for" topic ":"
                 avg-telemetry (str "(" current-state ")"))
      (when (not= state current-state)
        (assoc-device devices topic :state current-state)
        (log/debug "switching state of" topic ":" state "->" current-state)
        (when (or (and (= current-state :on) (= trigger :off-to-on))
                  (and (= current-state :off) (= trigger :on-to-off)))
          (act/execute-actions actions topic data))))))

(defn start-monitor [make-listener]
  (log/info "Monitoring:" (s/join ", " (keys (conf :devices))))
  (doseq [[topic configuration] (conf :devices)]
    (mqtt/subscribe {topic 0} (make-listener configuration))))
