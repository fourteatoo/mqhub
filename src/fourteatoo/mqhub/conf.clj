(ns fourteatoo.mqhub.conf
  (:require [clojure.java.io :as io]
            [cprop.core :as cprop]
            [mount.core :as mount]))


(def ^:dynamic options
  "Command line options map as returned by
  `clojure.tools.cli/parse-opts`.  This should be dynamically bound in
  the main function just after parsing the command line."
  nil)

(defn opt [o]
  (get options o))

(defn- home-conf []
  (io/file (System/getProperty "user.home") ".mqhub"))

(defn- load-configuration
  [& [file]]
  (cprop/load-config :file (or file (home-conf))))

(mount/defstate config
  :start (load-configuration (opt :config)))

(defn conf [& path]
  (get-in config path))

