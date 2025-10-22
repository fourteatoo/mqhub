(ns fourteatoo.mqhub.misc
  (:require [clojure.data :as data]
            [diehard.core :as dh]
            [fourteatoo.mqhub.log :as log]
            [clojure.string :as s]))

(defn index-by [k coll]
  (->> coll
       (map (juxt k identity))
       (into {})))

(defn map->paths [m]
  (mapcat (fn [[k v]]
            (if (map? v)
              (map (fn [[k' v']]
                     [(cons k k') v'])
                   (map->paths v))
              [[(list k) v]]))
          m))

(defn list-new-values [old-map new-map]
  (let [[_ new-values _] (data/diff old-map new-map)]
    (map->paths new-values)))

(defmacro ignore-errors [& forms]
  `(try ~@forms
        (catch Exception _# nil)))

(def exit? (promise))

(defn arm-exit-hooks []
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (log/info "Shutting down")
                               (deliver exit? true)))))

(defmacro daemon [& body]
  `(future
     (try
       (do ~@body)
       (catch Exception e#
         (log/fatal e# "Exception in daemon")
         (deliver exit? e#)))))

(dh/defretrypolicy retry-policy
  {:max-retries 10
   :backoff-ms [(* 3 1000) (* 60 1000)]})

(defn expand-home-dir [s]
  (s/replace-first s #"^~/" (str (System/getProperty "user.home") "/")))
