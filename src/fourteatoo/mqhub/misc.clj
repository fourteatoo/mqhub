(ns fourteatoo.mqhub.misc
  (:require [clojure.data :as data]))

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

