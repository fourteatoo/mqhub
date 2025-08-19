(ns fourteatoo.mqhub.log
  (:require
   [unilog.config :refer [start-logging!]]
   [clojure.tools.logging :as log]
   ;; [taoensso.timbre :as log]
   ;; #_[taoensso.timbre.tools.logging]
   [mount.core :as mount]
   [fourteatoo.mqhub.conf :refer [conf]]))


(comment
  (mount/running-states)
  (deref fourteatoo.mqhub.conf/config)
  (mount/start #'fourteatoo.mqhub.conf/config)
  (mount/stop #'fourteatoo.mqhub.conf/config))

(defn setup-logging [& [config]]
  (let [default-config {:level "info" :console true}]
    (start-logging! (merge default-config config))))

#_(defn setup-logging []
  ;; (taoensso.timbre.tools.logging/use-timbre)
  (let [default-logging {:level "info" :console true}]
    (log/merge-config! (conf :logging))))

(mount/defstate logging-service
  :start (setup-logging (conf :logging)))

(defmacro log [& args]
  `(log/log ~@args))

(defmacro trace [& args]
  `(log/trace ~@args))

(defmacro debug [& args]
  `(log/debug ~@args))

(defmacro info [& args]
  `(log/info ~@args))

(defmacro warn [& args]
  `(log/warn ~@args))

(defmacro error [& args]
  `(log/error ~@args))

(defmacro fatal [& args]
  `(log/fatal ~@args))

