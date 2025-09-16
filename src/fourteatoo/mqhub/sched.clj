(ns fourteatoo.mqhub.sched
  (:require [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.jobs :as qj]
            [clojurewerkz.quartzite.conversion :as qc]
            [clojurewerkz.quartzite.triggers :as qt]
            [clojurewerkz.quartzite.schedule.simple :as qss]
            [clojurewerkz.quartzite.schedule.cron :as qsc]
            [clojurewerkz.quartzite.schedule.daily-interval :as qsdti]
            [clojurewerkz.quartzite.schedule.calendar-interval :as qsci]
            [clojurewerkz.quartzite.schedule.simple :as qss]
            [camel-snake-kebab.core :as csk]
            [fourteatoo.mqhub.action :as act]
            [fourteatoo.mqhub.misc :refer :all]
            [fourteatoo.mqhub.log :as log]
            [java-time.api :as jt]
            [mount.core :as mount])
  (:import [org.quartz Job DateBuilder$IntervalUnit Trigger$TriggerState JobDataMap JobExecutionContext]
           [java.util Calendar Date]
           [java.time LocalDateTime Instant ZoneOffset]
           [clojure.lang IPersistentMap RT]))


(mount/defstate scheduler
  :start (qs/start (qs/initialize))
  :stop (qs/shutdown scheduler))

(defn ensure-class [name]
  (if (class? name)
    name
    (RT/classForName name)))

(defn invoke-static-method [class method & args]
  (clojure.lang.Reflector/invokeStaticMethod class method (into-array Object args)))

(defn enum->keyword [enum]
  (csk/->kebab-case-keyword (.name enum)))

(defn enum->map [klass]
  (->> (invoke-static-method klass "values")
       seq
       (map (juxt enum->keyword
                  identity))
       (into {})))

(defn enum-keywords [klass]
  (keys (enum->map klass)))

;; We assume a kebab case syntax for enum values.  See also
;; `clj-grpc.core/from-grpc`
(defn keyword->enum [enum-type keyword]
  (let [name (csk/->kebab-case-string keyword)]
    (or (->> (invoke-static-method enum-type "values")
             (filter (fn [ev]
                       (= name (csk/->kebab-case-string (.name ev)))))
             first)
        (throw (ex-info "unknown keyword for enum type" {:type enum-type :keyword keyword
                                                         :legal-values (enum-keywords enum-type)})))))

(defn int->enum [enum-type x]
  (invoke-static-method enum-type "forNumber" x))

(defn as-enum [enum-type x]
  (cond (keyword? x) (keyword->enum enum-type x)
        (number? x) (int->enum enum-type x)
        :else x))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn as-interval-unit [u]
  (as-enum DateBuilder$IntervalUnit u))

(defn interval-unit->keyword [u]
  (enum->keyword u))

(defn string->date [s]
  (Date/from (or (ignore-errors (Instant/parse s))
                 (ignore-errors
                  (.toInstant
                   (.atZone (LocalDateTime/parse s)
                            (ZoneOffset/systemDefault))))
                 (throw (ex-info "malformed date/time string" {:string s})))))

#_((juxt type identity)(string->date "2021-02-16T14:31"))

(extend-protocol clojurewerkz.quartzite.conversion/DateConversion
  String
  (to-date [input]
    (string->date input))

  java.time.LocalDateTime
  (to-date [input]
    (jt/java-date input)))

(defn tkey [& [name group]]
  (cond (and name group) (qt/key name group)
        name (qt/key name)
        :else (qt/key)))

(defn jkey [& [name group]]
  (cond (and name group) (qj/key name group)
        name (qj/key name)
        :else (qj/key)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti trigger-map->schedule :type)

(defmethod trigger-map->schedule :cron
  [m]
  (qsc/cron-schedule (:expression m)))

(defmethod trigger-map->schedule :calendar
  [m]
  (let [{:keys [unit interval]} m]
    (qsci/schedule
     (.withInterval interval (as-interval-unit unit)))))

(def weekday->int
  {:sunday Calendar/SUNDAY
   :monday Calendar/MONDAY
   :tuesday Calendar/TUESDAY
   :wednsday Calendar/WEDNESDAY
   :thursday Calendar/THURSDAY
   :friday Calendar/FRIDAY
   :saturday Calendar/SATURDAY})

(def int->weekday (zipmap (vals weekday->int) (keys weekday->int)))

(defmethod trigger-map->schedule :daily-time
  [m]
  (let [{:keys [unit interval repeat week-days start-daily end-daily]} m]
    (qsdti/schedule
     (.withInterval interval (as-interval-unit unit))
     (cond->
         repeat (qsdti/with-repeat-count repeat)
         (empty? week-days) (qsdti/every-day)
         week-days (qsdti/days-of-the-week (set (map weekday->int week-days)))
         start-daily (qsdti/starting-daily-at (qsdti/time-of-day start-daily))
         end-daily (qsdti/ending-daily-at (qsdti/time-of-day end-daily))))))

(defmethod trigger-map->schedule :simple
  [m]
  (let [{:keys [ms seconds minutes hours days repeat]} m]
    (qss/schedule
     (cond->
         ms (qss/with-interval-in-milliseconds ms)
         seconds (qss/with-interval-in-seconds seconds)
         minutes (qss/with-interval-in-minutes minutes)
         hours (qss/with-interval-in-hours hours)
         days (qss/with-interval-in-days days)
         (= repeat :forever) (qss/repeat-forever)
         (integer? repeat) (qss/with-repeat-count repeat)))))

(defmethod trigger-map->schedule :default
  [m]
  (throw
   (ex-info "unsupported trigger type"
            {:type (:type m) :conf m})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 


(defn make-trigger [schedule-conf job]
  (let [{:keys [name group start end description priority]} schedule-conf]
    (qt/build
     (qt/with-identity (tkey name group))
     (qt/with-schedule (trigger-map->schedule schedule-conf))
     (cond->
         priority (qt/with-priority priority)
         description (qt/with-description description)
         start (qt/start-at start)
         end (qt/end-at end)
         (nil? start) (qt/start-now)
         job (qt/for-job job)))))

(defn make-job [type & {:keys [name group description data durable]}]
  (qj/build
   (qj/of-type (ensure-class type))
   (qj/with-identity (jkey name group))
   (cond->
       data (qj/using-job-data data)
       description (qj/with-description description)
       durable (qj/store-durably))))

(defn add-job [job]
  (log/debug "add-job" (pr-str job))
  (let [{:keys [triggers type name group description data]} job
        job (make-job type :name name :group group
                      :data data
                      :description description :durable true)]
    (qs/add-job scheduler job)
    (doseq [t triggers]
      (qs/add-trigger scheduler (make-trigger t job)))
    [(.getGroup job)
     (.getName job)]))

(defmacro defjob [name [data] & body]
  `(qj/defjob ~name [ctx#]
     (let [~data (qc/from-job-data ctx#)]
       ~@body)))

(defjob MqhubJob [actions]
  (log/info "Executing actions:" (pr-str actions))
  (act/execute-actions actions))

(defn schedule-actions [whence actions]
  (add-job {:triggers [{:type :cron
                        :expression whence}]
            :type MqhubJob
            :data actions}))

(comment
  (defjob FooJob [actions]
    (log/info "Executing actions:" (pr-str actions)))

  (extend-protocol clojurewerkz.quartzite.conversion/DateConversion
    java.time.LocalDateTime
    (to-date [input]
      (jt/to-sql-timestamp input)))

  (let [[group name] (add-job {:triggers [{:type :simple
                                           :start (jt/plus (jt/local-date-time) (jt/seconds 10))
                                           :seconds 10}]
                               :type FooJob
                               :data {:data [1 2 3]}})]
    (qs/delete-job scheduler (qc/to-job-key name))))
