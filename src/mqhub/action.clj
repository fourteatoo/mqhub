(ns mqhub.action
  (:require [taoensso.timbre :as log]
            [postal.core :as post]
            [clj-evohome.api2 :as eh]
            [mqhub.conf :refer :all]
            [mqhub.mqtt :as mqtt]
            [cheshire.core :as json]))


(defmulti execute-action (fn [action topic data] (:type action)))

(defmethod execute-action nil
  [action _ _]
  (comment "do nothing"))

(defmethod execute-action :default
  [action topic data]
  (throw (ex-info "don't know how to execute action"
                  {:action action :topic topic :data data})))

(defmethod execute-action :mail
  [action topic _]
  ;; allow the configuration file to override the host, the from, the
  ;; body and the subject
  (post/send-message (merge {:host "localhost"}
                            (conf :smtp :server)
                            (:server action))
                     (merge {:from "mqhub@localhost"
                             :subject (str "Notification from mqhub")
                             :body (str topic " triggered a notification for you.")}
                            (conf :smtp :message)
                            (:message action))))

(comment
  (execute-action {:type :mail
                   :message {:subject "Washing Machine"
                             :body "The washing is ready to hang!"}}
                  "topico"
                  "data"))

(defmethod execute-action :publish
  [action _ _]
  (mqtt/publish (:topic action)
                (if (string? (:payload action))
                  (:payload action)
                  (json/generate-string (:payload action)))))

(def scheduled (atom {}))

(def execute-actions)

(defn- schedule-actions [delay actions topic data]
  (swap! scheduled
         (fn [m]
           (update m topic
                   (fn [old]
                     (when old
                       (future-cancel old))
                     (future
                       (Thread/sleep (* delay 1000))
                       (execute-actions actions topic data)))))))

(defmethod execute-action :delayed
  [action topic data]
  (schedule-actions (:delay action) (:actions action) topic data))

(def eh (atom nil))

(defn unique [key seq]
  (->> seq
       (reduce (fn [m e]
                 (let [k (key e)]
                   (if (contains? m k)
                     m
                     (assoc m k e))))
               {})
       vals))

(defmethod execute-action :evo-home
  [action _ _]
  (swap! eh (fn [c]
              (or c
                  (eh/connect (conf :evo-home :user) (conf :evo-home :password)))))
  (eh/set-mode @eh (:location action) (:mode action)))

(defn execute-actions [actions topic data]
  ;; We want to avoid executing clashing actions.  For that we use
  ;; the :name tag.  Only one action, among those with the same name,
  ;; is executed.  Actions without a name are always executed. That
  ;; is, if no name is specified, we presume all actions are disjoint
  ;; and, thus, can be executed.
  (let [assoc-name (fn [a]
                     (if (get a :name)
                       a
                       (assoc a :name (gensym))))
        exec (fn [action]
               (log/info "topic" topic "triggers action" (pr-str action))
               (try
                 (execute-action action topic data)
                 (catch Exception e
                   (log/error e "error setting system mode"
                              {:action action :topic topic :data data}))))]
    (->> actions
         (map assoc-name)
         (unique :name)
         (run! exec))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment
  (reset! eh (eh/connect (conf :evo-home :user) (conf :evo-home :password)))
  (def acc-info (eh/user-account-info @eh))
  (def insts (eh/installations-by-user @eh (:user-id acc-info)))
  (def inst1 (eh/installation-by-location @eh (get-in (first insts) [:location-info :location-id])))
  (def system (-> inst1
                  :gateways
                  first
                  :temperature-control-systems
                  first))
  (eh/select-zones @eh "Home sweet home" "Bedroom")
  (eh/select-locations @eh "Home sweet home")
  (eh/get-system-status @eh (:system-id system))
  (set-system-mode c (:system-id system) :dayoff)
  (def zone (first (:zones system)))
  (def sched (get-zone-schedule c (:zone-id zone)))
  (set-zone-schedule c (:zone-id zone) sched)
  (set-zone-temperature c (:zone-id zone) 17.5)
  (cancel-zone-override c (:zone-id zone))
  (eh/get-location-status @eh (get-in inst1 [:location-info :location-id])))
