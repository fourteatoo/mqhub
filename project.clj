(defproject mqhub "0.1.0-SNAPSHOT"
  :description "A simple(r) Home IoT Hub based on MQTT written in Clojure"
  :url "http://github.com/fourtytoo/mqhub"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/core.memoize "1.1.266"]
                 [org.clojure/tools.logging "1.3.0"]
                 [com.taoensso/timbre "6.6.1"]
                 [clojure.java-time "1.4.3"]
                 [cprop "0.1.20"]
                 [camel-snake-kebab "0.4.3"]
                 [clojurewerkz/machine_head "1.0.0"]
                 [clojurewerkz/quartzite "2.2.0"]
                 [cheshire "5.13.0"]
                 [com.draines/postal "2.0.5"]
                 [fourtytoo/clj-evohome "0.1.0-SNAPSHOT"]]
  :main ^:skip-aot mqhub.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[autodoc/lein-autodoc "1.1.1"]]
                   :resource-paths ["dev-resources" "resources"]}})
