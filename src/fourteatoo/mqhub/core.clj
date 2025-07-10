(ns fourteatoo.mqhub.core
  (:require [taoensso.timbre :as log]
            [fourteatoo.mqhub.conf :refer :all]
            [fourteatoo.mqhub.sub :as sub]
            [fourteatoo.mqhub.pub :as pub]
            [fourteatoo.mqhub.sched :as sched]
            [mount.core :as mount])
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

(defn -main [& args]
  (mount/start)
  (sub/start-subscriptions (conf :subscriptions))
  ;; (start-scheduler)
  (pub/start-topic-publisher (conf :publications))
  (println "Monitor started.  Type Ctrl-C to exit.")
  (while true
    (Thread/sleep 1000))
  ;; for keepsake
  (mount/stop))
