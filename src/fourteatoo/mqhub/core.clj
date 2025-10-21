(ns fourteatoo.mqhub.core
  (:require
   [fourteatoo.mqhub.log :as log]
   [fourteatoo.mqhub.sub :as sub]
   [fourteatoo.mqhub.pub :as pub]
   [fourteatoo.mqhub.sched :as sched]
   fourteatoo.mqhub.nrepl
   [mount.core :as mount]
   [fourteatoo.mqhub.conf :as c :refer [conf]]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.java.io :as io]
   [fourteatoo.mqhub.misc :as misc]
   [clj-http.client :as http]
   [clojure.edn :as edn]
   [fourteatoo.mqhub.blink :as blink])
  (:gen-class))


(defn start-scheduler []
  (log/info "Starting scheduler")
  (doseq [sched (conf :schedule)]
    (sched/schedule-actions (:when sched) sched)))

(comment
  (mount.core/start)
  (sub/start-subscriptions (conf :subscriptions))
  (pub/start-topic-publisher (conf :publications)))

(def ^:private cli-options
  ;; An option with an argument
  [["-c" "--config FILE" "Confirguration file"
    :parse-fn #(io/file %)
    :validate [#(.exists %) "Configuration file does not exist"]]
   [nil "--authorize-blink" "Perform one-time authorization with Blink servers (and save the auth tokens)"]
   ["-v" "--verbose" "Increase logging verbosity"
    :default 0
    :update-fn inc]
   ["-h" "--help" "Show program usage"]])

(defn- usage [summary errors]
  (println "usage: mqhub [options ...]")
  (doseq [e errors]
    (println e))
  (when summary
    (println summary))
  (System/exit -1))

(defn- parse-cli [args]
  (let [{:keys [arguments summary errors] :as result} (parse-opts args cli-options)]
    (when (or errors
              (seq arguments))
      (usage summary errors))
    result))

(defn- start-daemon [options]
  (try
    (binding [c/options options]
      (http/with-connection-pool {}
        (misc/arm-exit-hooks)
        (mount/start)
        (sub/start-subscriptions (conf :subscriptions))
        (start-scheduler)
        (pub/start-topic-publisher (conf :publications))
        (println "MQhub started.  Type Ctrl-C to exit.")
        (deref misc/exit?)
        (println "Exiting...")
        (mount/stop)))
    (catch Exception e
      (log/fatal e "failed to initialize")
      (println "Fatal error; see log.")
      (System/exit -1))))

(defn- register-blink-client []
  (http/with-connection-pool {}
    (misc/arm-exit-hooks)
    (mount/start #'fourteatoo.mqhub.conf/config)
    (blink/register-client)
    (mount/stop)))


(defn -main [& args]
  (let [{:keys [options summary]} (parse-cli args)]
    (cond (:help options)
          (usage summary nil)

          (:authorize-blink options)
          (register-blink-client)
          
          :else
          (start-daemon options))))

