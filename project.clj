(defproject io.github.fourteatoo/mqhub "0.2.0-SNAPSHOT"
  :description "A simple(r) Home IoT Hub based on MQTT written in Clojure"
  :url "http://github.com/fourteatoo/mqhub"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [org.clojure/core.memoize "1.1.266"]
                 [cheshire "6.0.0"]
                 [org.clojure/tools.logging "1.3.0"]
                 [spootnik/unilog "0.7.32"]
                 ;; [com.taoensso/timbre "6.7.1"]
                 ;; [com.taoensso/timbre-slf4j "6.7.0"]
                 [org.clojure/tools.cli "1.1.230"]
                 [clojure.java-time "1.4.3"]
                 [cprop "0.1.20"]
                 [camel-snake-kebab "0.4.3"]
                 [clojurewerkz/machine_head "1.0.0"]
                 [clojurewerkz/quartzite "2.2.0"]
                 [com.draines/postal "2.0.5"]
                 [io.github.fourteatoo/clj-evohome "1.1.1-SNAPSHOT"]
                 [io.github.fourteatoo/clj-blink "0.1.2-SNAPSHOT"]
                 [mount "0.1.23"]]
  :main ^:skip-aot fourteatoo.mqhub.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[lein-codox "0.10.8"]
                             [lein-cloverage "1.2.4"]]
                   :resource-paths ["dev-resources" "resources"]}}
  :repl-options {:init-ns fourteatoo.mqhub.core}
  :lein-release {:deploy-via :clojars})
