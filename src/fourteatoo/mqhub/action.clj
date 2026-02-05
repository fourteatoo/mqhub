(ns fourteatoo.mqhub.action
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.edn :as edn]
   [clojure.string :as s]
   [fourteatoo.mqhub.conf :refer :all]
   [fourteatoo.mqhub.log :as log]
   [fourteatoo.mqhub.mqtt :as mqtt]
   [java-time.api :as jt]
   [postal.core :as post]))

(defmulti execute-action (fn [action topic data] (:type action)))

(defmethod execute-action :default
  [action topic data]
  (throw (ex-info "don't know how to execute action"
                  {:action action :topic topic :data data})))

(defn- mail-send
  "`message` is a map and should at the very least contain a :to and
  a :body.  Other components such as :subject, :from, etc can have
  defaults from the :smtp configuration."
  [message]
  (post/send-message (merge {:host "localhost"}
                            (conf :smtp :server))
                     (merge {:from "mqhub@localhost"
                             :subject "Notification from mqhub"}
                            (conf :smtp :message)
                            message)))

(defmethod execute-action :mail
  [action topic _]
  (mail-send (:message action)))

(def default-ntfy-url "https://ntfy.sh")

(defn ntfy-send
  ([message]
   (ntfy-send (conf :ntfy :topic) message))
  ([topic message]
  (http/post (str (or (conf :ntfy :url)
                      default-ntfy-url)
                  "/" topic)
             {:body message})))

(defmethod execute-action :ntfy
  [action _ _]
  (ntfy-send (or (:topic action)
                 (conf :ntfy :topic))
             (:message action)))

(defn mqtt-publish [topic message]
  (mqtt/publish topic
                (cond (string? message) message
                      (keyword? message) (name message)
                      (map? message) (json/generate-string message)
                      :else (str message))))

(defmethod execute-action :publish
  [action _ _]
  (mqtt-publish (:topic action) (:payload action)))

(def scheduled (atom {}))

(def execute-actions)

(defn- ensure-ns [ns]
  (require ns)
  (find-ns ns))

(let [exenv-ns (delay (ensure-ns 'fourteatoo.mqhub.exenv))]
  (defn make-code-fn [args code]
    (log/debug "make-code-fn" (pr-str code))
    (binding [*ns* @exenv-ns]
      (log/debug "*ns*=" *ns*)          ; -wcp04/02/26
      (let [code (concat `(fn ~args)
                         (list code))]
        (eval code)))))

(defn sleep [secs]
  (Thread/sleep (* secs 1000)))

(defn delay-call
  ([delay f]
   ;; use the function as its own id
   (delay-call delay f f))
  ([delay id f]
   (swap! scheduled
          (fn [m]
            (update m id
                    (fn [old]
                      (when old
                        (future-cancel old))
                      (future
                        (sleep delay)
                        (f))))))))

(defn- schedule-actions [delay actions topic data]
  ;; use the topic as function id
  (delay-call delay topic #(execute-actions actions topic data)))

(defmethod execute-action :delayed
  [action topic data]
  (cond (:actions action)
        (schedule-actions (:delay action) (:actions action) topic data)
        (:code action)
        (let [f (make-code-fn '[ctx topic data] (:code action))]
          (delay-call (:delay action)
                      #(f topic data)))
        :else
        (throw (ex-info "delay action should specify either :actions or :code"
                        {:action action :topic topic}))))

(defn log
  ([message]
   (log :info message))
  ([level message]
   (log/log level message)))

(defmethod execute-action :log
  [action _ _]
  (log (or (:level action) :info) (:message action)))

(defn execute-actions [actions topic data]
  (let [exec (fn [action]
               (log/info "topic" topic "triggers action" (pr-str action))
               (try
                 (execute-action action topic data)
                 (catch Exception e
                   (log/error e "error executing action"
                              {:action action :topic topic :data data}))))]
    (run! exec actions)))

(defn make-topic-listener [configuration]
  (let [f (make-code-fn '[ctx topic data] (:code configuration))
        ctx (atom {})]
    (fn [topic data]
      (let [topic (s/split topic #"/")
            data (case (:data-format configuration)
                   :json (json/parse-string data csk/->kebab-case-keyword)
                   :edn (edn/read-string data)
                   :keyword (csk/->kebab-case-keyword data)
                   data)
            new-ctx (log/spy (f @ctx topic data))]
        (reset! ctx new-ctx)))))

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
