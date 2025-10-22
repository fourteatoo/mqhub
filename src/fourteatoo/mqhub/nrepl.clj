(ns fourteatoo.mqhub.nrepl
  (:require
   [fourteatoo.mqhub.conf :as c :refer [conf]]
   [fourteatoo.mqhub.misc :as m :refer [expand-home-dir]]
   [nrepl.server :as repl]
   [mount.core :as mount]))


(mount/defstate nrepl-server
  :start (when (conf :nrepl-socket)
           (repl/start-server :socket (expand-home-dir (conf :nrepl-socket))))
  :stop (when nrepl-server
          (repl/stop-server nrepl-server)))
