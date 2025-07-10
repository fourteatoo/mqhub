(ns fourteatoo.mqhub.conf
  (:require [clojure.java.io :as io]
            [cprop.core :as cprop]
            [mount.core :as mount]))

(defn- home-conf []
  (io/file (System/getProperty "user.home") ".mqhub"))

(defn- load-configuration []
  (let [c (home-conf)]
    (cprop/load-config :file (when (.exists c)
                               c))))

(mount/defstate config
  :start (load-configuration))

(defn conf [& path]
  (get-in config path))

