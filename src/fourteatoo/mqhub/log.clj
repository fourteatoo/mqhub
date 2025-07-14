(ns fourteatoo.mqhub.log
  (:require
   [unilog.config :refer [start-logging!]]
   [clojure.tools.logging :as log]
   ;; [taoensso.timbre :as log]
   ;; #_[taoensso.timbre.tools.logging]
   [mount.core :as mount]
   [fourteatoo.mqhub.conf :refer [conf]]))


(defn setup-logging []
  (let [default-logging {:level "info" :console true}]
    (start-logging! (merge default-logging (conf :logging)))))

#_(defn setup-logging []
  ;; (taoensso.timbre.tools.logging/use-timbre)
  (let [default-logging {:level "info" :console true}]
    (log/merge-config! (conf :logging))))

(mount/defstate loggin-service
  :start (setup-logging))

(defmacro log [& args]
  `(log/log ~@args))

(defmacro debug [& args]
  `(log/trace ~@args))

(defmacro debug [& args]
  `(log/debug ~@args))

(defmacro info [& args]
  `(log/info ~@args))

(defmacro warn [& args]
  `(log/info ~@args))

(defmacro error [& args]
  `(log/error ~@args))

(defmacro fatal [& args]
  `(log/error ~@args))

