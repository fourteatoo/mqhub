(ns fourteatoo.mqhub.webos
  (:require [cheshire.core :as json]
            [clojure.core.async :as a]
            [fourteatoo.mqhub.log :as log]
            [hato.websocket :as ws]
            [hato.client :as http]))


(defmacro with-open-socket [[sock val] & body]
  `(let [~sock ~val]
     (try
       ~@body
       (finally (ws/close! ~sock)))))

(defn- ws-send [socket payload]
  (log/debug ">>" payload)
  (ws/send! socket (if (string? payload)
                     payload
                     (json/generate-string payload))))

(def ^:private default-permissions
  ["LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER"])

(defn- make-registration-body [& {:keys [id client-key permissions]
                                 :or {id "register_0"}}]
  (let [payload {:forcePairing false
                 :manifest {:manifestVersion 1
                            :appVersion "1.1"
                            :signed {:created "20140509"
                                     :appId "com.lge.test"
                                     :vendorId "com.lge"
                                     :localizedAppNames {"" "LG Remote"}}
                            :permissions (or permissions default-permissions)}}]
    {:type "register"
     :id id
     :payload (merge payload
                     (if client-key
                       {:client-key client-key}
                       {:pairingType "PROMPT"}))}))

(defn- chan-poll [c timeout-ms]
  (let [[v from] (a/alts!! [c (a/timeout timeout-ms)])]
    (if (= from c)
      v
      ::timeout)))

(defn- wait-for-answer [c timeout found?]
  (->> (repeatedly #(chan-poll c timeout))
       (take-while (fn [v]
                     (log/spy v)
                     (not (or (= v ::timeout)
                              (found? v)))))
       last))

(defn- ws-open [uri on-message]
  (log/debug "ws-open" uri)
  (let [socket (ws/websocket uri
                             {:on-message (fn [_ws data last?]
                                            (log/debug "<<" data)
                                            (on-message (if last?
                                                          ::eof
                                                          (json/decode (str data) true))))})]
    @socket))

(defn- call-with-ws-chans [uri f & [in out]]
  (let [in (or in (a/chan))
        out (or out (a/chan))]
    (try
      (with-open-socket [sock (ws-open uri #(a/>!! in %))]
        (a/go-loop []
          (let [payload-to-send (a/<!! out)]
            (when payload-to-send
              (ws-send sock payload-to-send)
              (recur))))
        (f in out))
      (finally
        (a/close! in)
        (a/close! out)))))

(defn pair-tv [address]
  (log/debug "pair-tv" (pr-str address))
  (call-with-ws-chans (str "ws://" address ":3000")
                      (fn [in out]
                        (a/>!! out (make-registration-body))
                        (let [r (wait-for-answer in (* 15 1000) #(= (:type %) "registered"))
                              client-key (get-in r [:payload :client-key])]
                          client-key))))

(defn turn-off-tv [address client-key]
  (log/debug "turn-off-tv" address)
  (call-with-ws-chans (str "ws://" address ":3000")
                      (fn [in out]
                        (a/>!! out (make-registration-body :client-key client-key))
                        (wait-for-answer in (* 5 1000) #(= (:type %) "registered"))
                        (a/>!! out {:type "request"
                                    :uri "ssap://system/turnOff"})
                        (wait-for-answer in (* 5 1000) #(= (:type %) "response")))))

(comment
  (discover)
  (def k (pair-tv "10.0.0.212"))
  (turn-off-tv "10.0.0.212" "fb7bdb8265982cdfd7b60b98467b04fa")
  (turn-off-tv "10.0.0.212" k))
