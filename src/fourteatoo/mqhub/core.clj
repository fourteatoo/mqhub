(ns fourteatoo.mqhub.core
  (:require
   [fourteatoo.mqhub.log :as log]
   [fourteatoo.mqhub.sub :as sub]
   [fourteatoo.mqhub.pub :as pub]
   [fourteatoo.mqhub.sched :as sched]
   [mount.core :as mount]
   [fourteatoo.mqhub.conf :as c :refer [conf]]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.java.io :as io])
  (:gen-class))


(defn start-scheduler []
  (log/info "Starting scheduler")
  (doseq [sched (conf :schedule)]
    (sched/schedule-actions (:when sched)
                            (:actions sched))))

(comment
  (mount.core/start)
  (sub/start-subscriptions (conf :subscriptions))
  (pub/start-topic-publisher (conf :publications)))

(def ^:private cli-options
  ;; An option with an argument
  [["-c" "--config FILE" "Confirguration file"
    :parse-fn #(io/file %)
    :validate [#(.exists %) "Configuration file does not exist"]]
   ["-v" "--verbose" "Increase logging verbosity"
    :default 0
    :update-fn inc]
   ["-h" "--help"]])

(defn- usage [summary errors]
  (println "usage: mqhub [options ...]")
  (doseq [e errors]
    (println e))
  (println summary)
  (System/exit -1))

(defn- parse-cli [args]
  (let [{:keys [options arguments summary errors]} (parse-opts args cli-options)]
    (when (or errors
              (seq arguments))
      (usage summary errors))
    options))

(def exit? (promise))

(defn- arm-exit-hooks []
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (log/info "Shutting down")
                               (deliver exit? true)))))

(defn -main [& args]
  (let [options (parse-cli args)]
    (binding [c/options options]
      (mount/start)
      (sub/start-subscriptions (conf :subscriptions))
      (start-scheduler)
      (pub/start-topic-publisher (conf :publications))
      (println "MQhub started.  Type Ctrl-C to exit.")
      (deref exit?)
      (println "Exiting...")
      (mount/stop))))

