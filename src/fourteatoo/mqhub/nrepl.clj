(ns fourteatoo.mqhub.nrepl
  (:require
   [fourteatoo.mqhub.conf :as c :refer [conf]]
   [nrepl.server :as repl]
   [mount.core :as mount]))


(mount/defstate nrepl-server
  :start (when (conf :nrepl-socket)
           (repl/start-server :socket (conf :nrepl-socket)))
  :stop (when nrepl-server
          (repl/stop-server nrepl-server)))
