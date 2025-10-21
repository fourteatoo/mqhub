(ns fourteatoo.mqhub.blink
  "The Blink Camera module, implementing the monitoring of the Blink
  system and the actions applicable to it."
  (:require
   [fourteatoo.clj-blink.api :as blink]
   [fourteatoo.mqhub.conf :as c :refer [conf]]
   [fourteatoo.mqhub.misc :refer :all]
   [fourteatoo.mqhub.mqtt :as mqtt]
   [fourteatoo.mqhub.action :as act]
   [mount.core :as mount]
   [fourteatoo.mqhub.log :as log]
   [diehard.core :as dh]
   [clojure.edn :as edn]))


(defn- save-refresh-token [token]
  (c/save-state-file (assoc (c/read-state-file)
                            :blink-refresh-token token)))

(defn- save-client-state [client]
  (when client
    (save-refresh-token (blink/refresh-token client))))

(defn- read-refresh-token []
  (:blink-refresh-token (c/read-state-file)))

(defn- restore-client []
  (when (conf :blink)
    (let [refresh-token (read-refresh-token)]
      (if refresh-token
        (blink/authenticate-client (conf :blink :user)
                                   (conf :blink :password)
                                   refresh-token)
        (throw (ex-info "No refresh token.  Must authorize the app first." {:state-file (c/state-file)}))))))

(mount/defstate blink-client
  :start (restore-client)
  :stop (save-client-state blink-client))

(defn register-client []
  (save-refresh-token (blink/refresh-token (blink/register-client (conf :blink :user) (conf :blink :password)))))

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
      (recur
       (try (let [nets (:networks (restruct-networks-status (get-networks blink-client)))
                  state (if (:networks configuration)
                          (select-keys nets (:networks configuration))
                          nets)]
              (mqtt/publish-delta topic previous-state state)
              (Thread/sleep (* 1000 (:freq configuration)))
              state)
            (catch Exception e
              (log/error e "error refreshing Blink state")
              previous-state))))))

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
