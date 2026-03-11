(ns fourteatoo.mqhub.wol
  (:import (java.net DatagramPacket DatagramSocket InetAddress))
  (:require [fourteatoo.mqhub.conf :as c]
            [clojure.string :as s]
            [fourteatoo.mqhub.log :as log]))

(defn- mac->bytes [mac]
  (->> (s/split mac #":")
       (map #(Integer/parseInt % 16))
       (map unchecked-byte)
       byte-array))

(defn- make-magic-packet [mac]
  (let [mac-bytes (mac->bytes mac)
        packet (byte-array (* 6 17))]
    (dotimes [i 6]
      (aset-byte packet i (unchecked-byte 0xFF)))
    (dotimes [i 16]
      (System/arraycopy mac-bytes 0 packet (+ 6 (* i 6)) 6))
    packet))

(defn wake [mac]
  (log/debug "wake:" mac)
  (let [packet (make-magic-packet mac)
        address (InetAddress/getByName "255.255.255.255")]
    (with-open [socket (DatagramSocket.)]
      (.setBroadcast socket true)
      (.send socket (DatagramPacket. packet (alength packet) address 9)))))

(comment
  (byte (Integer/parseInt "88" 16))
  (byte 0x88)
  (Byte/parseByte "88" 16)
  (wake "78:5d:c8:4c:53:68"))
