(ns fourteatoo.mqhub.report
  (:require
   [cheshire.core :as json]
   [fourteatoo.clj-evohome.api :as eh]
   [fourteatoo.mqhub.conf :refer :all]
   [fourteatoo.mqhub.mqtt :as mqtt]
   [postal.core :as post]
   [taoensso.timbre :as log]))

(defn report-on [& args])
