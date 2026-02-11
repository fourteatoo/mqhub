(ns fourteatoo.mqhub.upnp
  (:import
   (java.net DatagramSocket DatagramPacket InetAddress SocketTimeoutException)
   (java.nio.charset StandardCharsets))
  (:require [fourteatoo.mqhub.misc :refer :all]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]
            [clj-http.client :as http]
            [clojure.xml :as xml]
            [fourteatoo.mqhub.mqtt :as mqtt]
            [fourteatoo.mqhub.log :as log]
            [fourteatoo.mqhub.pub :as pub]))

(def ssdp-address "239.255.255.250")
(def ssdp-port 1900)

(defn ssdp-request [search-target]
  (str "M-SEARCH * HTTP/1.1\r\n"
       "HOST: " ssdp-address ":" ssdp-port "\r\n"
       "MAN: \"ssdp:discover\"\r\n"
       "MX: 2\r\n"
       "ST: " search-target "\r\n\r\n"))

(def ssdp-search-target-lg-webos
  "urn:lge-com:service:webos-second-screen:1")

(def ssdp-search-target-all "ssdp:all")

(def ssdp-search-target-roots "upnp:rootdevice")

(def default-search-target ssdp-search-target-all)


(defn- make-datagram-packet
  ([bytes]
   (let [bytes (cond (int? bytes) (byte-array bytes)
                     (string? bytes) (.getBytes bytes StandardCharsets/UTF_8)
                     :else bytes)]
     (DatagramPacket. bytes (alength bytes))))
  ([bytes address port]
   (let [bytes (if (string? bytes)
                 (.getBytes bytes StandardCharsets/UTF_8)
                 bytes)]
     (DatagramPacket. bytes (alength bytes)
                      (if (string? address)
                        (InetAddress/getByName address)
                        address)
                      port))))

(defn- receive-datagram-packet [socket]
  (let [packet (make-datagram-packet 2048)]
    (.receive socket packet)
    {:source (.getHostAddress (.getAddress packet))
     :data (String. (.getData packet) 0 (.getLength packet) StandardCharsets/UTF_8)}))

(comment
  (discover))

(defn- parse-ssdp-reply [data]
  (->> (s/split-lines data)
       (drop 1)
       (map (fn [l]
              (let [[k v] (s/split l #": *" 2)]
                [(csk/->kebab-case-keyword k)
                 v])))
       (into {})))

(defn- now [] (System/currentTimeMillis))

(def default-timeout 3000)

(defn discover
  "Discover UPnP devices via SSDP."
  [& {:keys [timeout-ms search-target]
      :or {timeout-ms default-timeout
           search-target default-search-target}}]
  (with-open [socket (DatagramSocket.)]
    (.setSoTimeout socket 500)
    (let [req (ssdp-request search-target)
          packet (make-datagram-packet req ssdp-address ssdp-port)
          end (+ (now) timeout-ms)]
      (.send socket packet)
      (->> (repeatedly (fn []
                         (if (>= (now) end)
                           ::eof
                           (try
                             (receive-datagram-packet socket)
                             (catch SocketTimeoutException _
                               nil)))))
           (remove nil?)
           (take-while (partial not= ::eof))
           (index-by :source)
           vals
           (map #(assoc (parse-ssdp-reply (:data %)) :source (:source %)))))))

(defn fetch-xml-description [ssdp-discovery]
  (assoc ssdp-discovery :xml-description
         (xml/parse (:location ssdp-discovery))))

(defn start-upnp-monitor [topic configuration]
  (daemon
   (loop []
     (try
       (run! (fn [discovery]
               (mqtt/publish (str topic "/" (:source discovery)) discovery))
             (discover))
       (catch Exception e
         (log/error e "error refreshing UPnP discoveries")))
     (Thread/sleep (* (:freq configuration 13) 1000))
     (recur))))

(defmethod pub/start-monitor :upnp
  [configuration]
  (start-upnp-monitor (:topic configuration) configuration))
