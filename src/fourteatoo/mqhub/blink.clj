(ns fourteatoo.mqhub.blink
  "The Blink Camera module, implementing the monitoring of the Blink
  system and the actions applicable to it."
  (:require
   [fourteatoo.clj-blink.api :as blink]
   [fourteatoo.mqhub.conf :refer :all]
   [fourteatoo.mqhub.misc :refer :all]
   [fourteatoo.mqhub.mqtt :as mqtt]
   [fourteatoo.mqhub.action :as act]
   [mount.core :as mount]
   [fourteatoo.mqhub.log :as log]
   [diehard.core :as dh]))


(mount/defstate blink-client
  :start (blink/authenticate-client (conf :blink :user)
                                    (conf :blink :password)
                                    (conf :blink :unique-id)))

(defn- restruct-networks-status [status]
  (update status :networks (partial index-by :id)))

(defn- get-networks [cli]
  (dh/with-retry {:policy retry-policy
                  :on-failure (fn [v e]
                                (log/error e "get-networks failed"))}
    (blink/get-networks cli)))

(defn start-blink-monitor [topic configuration]
  (daemon
    (loop [previous-state nil]
      (let [nets (:networks (restruct-networks-status (get-networks blink-client)))
            state (if (:networks configuration)
                    (select-keys nets (:networks configuration))
                    nets)]
        (mqtt/publish-delta topic previous-state state)
        (Thread/sleep (* 1000 (:freq configuration)))
        (recur state)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti ^:private process-event
  (fn [topic _ _]
    (:type topic)))

(defn- as-boolean [s]
  (if (string? s)
    (read-string s)
    (boolean s)))

(defmethod process-event "network"
  [topic data configuration]
  (when (= (:variable topic) "armed")
    (let [new-state (if (as-boolean data) :armed :disarmed)
          actions (get configuration new-state)]
      (log/debug "blink system" new-state)
      (act/execute-actions actions topic data))))

(defmethod process-event "camera"
  [topic data _configuration]
  ;; TODO -wcp04/07/25
  (log/warn "camera messages not supported yet" (str topic)))

(defn make-topic-listener [configuration]
  (fn [topic data]
    ;; topic = blink/network/1256 data = arm/disarm
    (let [topic (mqtt/parse-topic topic [nil :type :id :variable])]
      (process-event topic data configuration))))

(defn- set-location-mode [loc mode]
  (blink/set-system-state blink-client loc mode))

(defmethod act/execute-action :blink
  [action _ _]
  (cond (:location action)
        (set-location-mode (:location action) (:mode action))
        :else
        (throw
         (ex-info "malformed action; must specify :location"
                  {:action action}))))
