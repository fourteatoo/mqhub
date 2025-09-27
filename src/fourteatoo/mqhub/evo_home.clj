(ns fourteatoo.mqhub.evo-home
  "The EVO Home module, implementing the monitoring of the Honeywell
  system and the actions applicable to it."
  (:require [fourteatoo.clj-evohome.core :as eh]
            [fourteatoo.clj-evohome.api :as eha]
            [fourteatoo.mqhub.conf :refer :all]
            [fourteatoo.mqhub.mqtt :as mqtt]
            [fourteatoo.mqhub.misc :refer :all]
            [fourteatoo.mqhub.action :as act]
            [camel-snake-kebab.core :as csk]
            [clojure.data :as data]
            [mount.core :as mount]
            [fourteatoo.mqhub.log :as log]
            [diehard.core :as dh]))


(mount/defstate evo-client
  :start (eh/authenticate-client (conf :evo-home :user) (conf :evo-home :password)))

(defn set-zone-temperature [zone temp & {:keys [until]}]
  (eh/set-zone-temperature evo-client zone temp))

(defn cancel-zone-override [zone]
  (eh/cancel-zone-override evo-client zone))

(defn set-system-mode [system mode]
  (eha/set-system-mode evo-client system mode))

(defn set-location-mode [location mode]
  (eh/set-location-mode evo-client location mode))

(defn- index-zones [zones]
  (index-by :zone-id zones))

(defn- restruct-systems-status [status]
  (->> status
       (map (fn [system]
              (update system :zones index-zones)))
       (index-by :system-id)))

(defn- get-location-systems-status [cli loc]
  (dh/with-retry {:policy retry-policy
                  :on-failed-attempt (fn [v e]
                                       (log/warn e "get-location-systems-status failed attempt"))
                  :on-failure (fn [v e]
                                (log/error e "get-location-systems-status failed"))}
    (doall (eh/get-location-systems-status cli loc))))

(defn start-evo-home-monitor [topic configuration]
  (daemon
    (loop [previous-state nil]
      (recur
       (try
         (let [state (restruct-systems-status (get-location-systems-status evo-client (:location configuration)))]
           (mqtt/publish-delta topic previous-state state)
           (Thread/sleep (* (:freq configuration) 1000))
           state)
         (catch Exception e
           (log/error e "error refreshing EVO Home state")
           previous-state))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti ^:private process-event
  (fn [topic _ _]
    (:type topic)))

(defmethod process-event "system"
  [topic data configuration]
  (set-system-mode (:id topic) data))

(defmethod process-event "zone"
  [topic data configuration]
  (if (= "cancel" data)
    (cancel-zone-override (:id topic))
    (set-zone-temperature (:id topic) data)))

(defmethod process-event :default
  [topic data _]
  (throw
   (ex-info "unknown event type; supported are :system or :zone"
            {:topic topic :data data})))

(defn make-topic-listener [configuration]
  (fn [topic data]
    ;; topic = evo-home/system/XXXXX data = auto/day-off/away/off/...
    ;; topic = evo-home/zone/XXXXX data = temperature or "cancel"
    (let [topic (mqtt/parse-topic topic [nil :type :id])]
      (process-event topic data configuration))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- action-subtype [action]
  (some #{:location :system :zone} (keys action)))

(defmulti ^:private exec-state-change action-subtype)

(defmethod exec-state-change :location
  [action]
  (set-location-mode (:location action) (:mode action)))

(defmethod exec-state-change :system
  [action]
  (set-system-mode (:system action) (:mode action)))

(defmethod exec-state-change :zone
  [action]
  (if (= (:temperature action) :cancel)
    (cancel-zone-override (:zone action))
    (set-zone-temperature (:zone action) (:temperature action))))

(defmethod exec-state-change :default
  [action]
  (throw
   (ex-info "malformed action; must specify :location, :system or :zone"
            {:action action})))

(defmethod act/execute-action :evo-home
  [action _ _]
  (exec-state-change action))
