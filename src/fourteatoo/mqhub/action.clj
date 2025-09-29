(ns fourteatoo.mqhub.action
  (:require
   [cheshire.core :as json]
   [fourteatoo.mqhub.conf :refer :all]
   [fourteatoo.mqhub.mqtt :as mqtt]
   [postal.core :as post]
   [fourteatoo.mqhub.log :as log]
   [mount.core :as mount]))

(defmulti execute-action (fn [action topic data] (:type action)))

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

(defmethod execute-action :log
  [action _ _]
  (log/log (or (:level action) :info) (:message action)))

(defn execute-actions [actions topic data]
  (let [exec (fn [action]
               (log/info "topic" topic "triggers action" (pr-str action))
               (try
                 (execute-action action topic data)
                 (catch Exception e
                   (log/error e "error executing action"
                              {:action action :topic topic :data data}))))]
    (run! exec actions)))

#_(defn- unique [key seq]
    (->> seq
         (reduce (fn [m e]
                   (let [k (key e)]
                     (if (contains? m k)
                       m
                       (assoc m k e))))
                 {})
         vals))

#_(defn execute-actions [actions topic data]
    (let [assoc-name (fn [a]
                       (update a :name #(or % (gensym))))
          exec (fn [action]
                 (log/info "topic" topic "triggers action" (pr-str action))
                 (try
                   (execute-action action topic data)
                   (catch Exception e
                     (log/error e "error executing action"
                                {:action action :topic topic :data data}))))]
      ;; We want to avoid executing clashing actions.  For that we use
      ;; the :name tag.  Only one action, among those with the same
      ;; name, is executed.  Actions without a name are always
      ;; executed. That is, if no name is specified, we presume all
      ;; actions are disjoint and, thus, can be executed.
      (->> actions
           (map assoc-name)
           (unique :name)
           (run! exec))))
