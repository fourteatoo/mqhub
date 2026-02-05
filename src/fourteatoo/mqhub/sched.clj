(ns fourteatoo.mqhub.sched
  (:require [chime.core :refer [chime-at]]
            [camel-snake-kebab.core :as csk]
            [fourteatoo.mqhub.action :as act]
            [fourteatoo.mqhub.misc :refer :all]
            [fourteatoo.mqhub.log :as log]
            [java-time.api :as jt]
            [mount.core :as mount]
            [fourteatoo.mqhub.conf :as c :refer [conf]])
  (:import [java.util Calendar Date]
           [java.time LocalDateTime Instant ZoneOffset]
           (com.cronutils.model CronType)
           (com.cronutils.model.definition CronDefinitionBuilder)
           (com.cronutils.parser CronParser)
           (com.cronutils.model.time ExecutionTime)))


(def cron-parser (CronParser. (CronDefinitionBuilder/instanceDefinitionFor CronType/UNIX)))

(defn parse-cron-expression [s]
  (.parse cron-parser s))

(defn execution-time-for-cron [cron]
  (ExecutionTime/forCron cron))

(defn next-execution [exec-time epoch]
  (.get (.nextExecution exec-time epoch)))

(comment
  (let [et (execution-time-for-cron (parse-cron-expression "0 * * * *"))]
    (next-execution et (next-execution et
                                       (jt/zoned-date-time))))
  (jt/local-date-time))

(defn cron->instants [cron-expr]
  (let [cron (parse-cron-expression cron-expr)
        exec-time (ExecutionTime/forCron cron)]
    (iterate (fn [t]
               (next-execution exec-time t))
             (jt/zoned-date-time))))

(comment
  (take 10 (map str (cron->instants "* * * * *")))
  (take 10 (map str (cron->instants "0 9 * * Mon")))
  (take 10 (map str (cron->instants "0 12 * * Thu")))
  (take 10 (map str (cron->instants "0 9 * * Fri")))
  @(chime-at (cron->instants "* * * * *")
             #(prn "DING!!" %)))

;; (iterate #(jt/plus % (jt/days 14)) start-zoned-datetime)

(defn make-cron-job [configuration]
  (let [f (act/make-code-fn '[ctx] (:exec configuration))
        ctx (atom {})]
    (fn [instant]
      (let [new-ctx (f @ctx)]
        (reset! ctx new-ctx)))))


(defn map->period [schedule]
  (cond (:days schedule)
        (jt/days (:days schedule))
        (:weeks schedule)
        (jt/days (* 7 (:weeks schedule)))
        (:weeks schedule)
        (jt/weeks (:weeks schedule))
        (:months schedule)
        (jt/months (:months schedule))
        (:years schedule)
        (jt/years (:years schedule))))

(defn first-instant [start period]
  (let [now (jt/zoned-date-time)
        period-in-minutes (jt/as period :minutes)
        diff-minutes (jt/time-between start now :minutes)]
    (jt/plus start (jt/minutes (* period-in-minutes (Math/ceil (/ diff-minutes period-in-minutes)))))))

(comment
  (jt/zoned-date-time "2025-09-04T09:00+01")
  (first-instant (jt/zoned-date-time "2026-01-20T13:53+01")
                 (map->period {:weeks 2})))

(defn map->instants [schedule]
  (let [start (jt/zoned-date-time (:start schedule))
        period (map->period schedule)]
    (iterate #(jt/plus % period)
             start)))

(defn make-actions-runner [actions]
  (fn [_]
    (act/execute-actions actions "<<SCHEDULER>>" {})))

(defn schedule-actions [whence config]
  (let [f (cond (:exec config)
                (make-cron-job (:exec config))
                (:actions config)
                (make-actions-runner (:actions config)))
        instants (cond (string? whence)
                       (cron->instants whence)
                       (map? whence)
                       (map->instants whence))]
    (chime-at instants f)))

(defn start-all-schedulers [schedules]
  (->> schedules
       (map (fn [cfg]
              (schedule-actions (:when cfg) cfg)))
       doall))

(mount/defstate schedulers
  :start (start-all-schedulers (conf :schedules))
  :stop (run! #(.close %) schedulers))

