[![CircleCI](https://dl.circleci.com/status-badge/img/gh/fourteatoo/mqhub/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/fourteatoo/mqhub/tree/main)
[![Coverage Status](https://coveralls.io/repos/github/fourteatoo/mqhub/badge.svg)](https://coveralls.io/github/fourteatoo/mqhub)


# MQHUB

An IoT device monitor and scheduler based on MQTT, written in Clojure.
Upon the occurrence of a specific event on a given MQTT topic, one or
more actions are triggered.

## Motivation

Although this project may remind of stuff like [Home
Assistant](https://home-assistant.io), at least in the scope, it
doesn't get anywhere close to the sophistication, user friendliness,
and capabilities Home Assistant offers.

MQHUB was born to scratch an itch: have a simple, lightweight sensors
monitor and scheduler that requires little from your hardware, has
almost no dependencies (just an MQTT broker), and can run on any OS a
JVM can run on.  While having some fun in Clojure.

## Installation

To compile

    $ lein uberjar

To install the jar and the shell script:

	$ cp target/mqhub<version_number>-standalone.jar ~/bin/mqhub.jar
	$ cp mqhub.sh ~/bin/mqhub
	$ chmod u+x ~/bin/mqhub


## Configuration

Before running the program, you need to write your own configuration
in `~/.mqhub`.  Something along these lines:

``` clojure
;; -*- Clojure -*-

{:mqtt {:broker "tcp://mybroker:1883"}
 :smtp {:server {:host "smtp.gmail.com"
                 :user "myself@gmail.com"
                 ;; application password
                 :pass "secret"
                 :ssl true}
        :message {:from "mqhub@localhost"
                  :to "myself@mydomain.me"}}
 :subscriptions {"tele/home/ss01/SENSOR" {:telemetry [:energy :power]
                                          :threshold 20 ; Watts
                                          :hysteresis 0.3
                                          :avg-samples 10
                                          :trigger :on-to-off
                                          :exec (ntfy-send "The washing is ready to hang!")}
                 "tele/home/ss02/SENSOR" {
				          ;; configuration of another device
				          }
                 ;; etc...
                 }}
```

Aside from credentials and various configurations required to connect
to your MQTT broker and external services, there are two main maps you
need to be aware of: the `:subscriptions` and the `:publications`.
The `:subscriptions` associate MQTT topics to actions, while the
`:publications` are for those external events that the monitors should
notify the MQTT broker of.  That is, the EVO Home and the Blink system
can be consumers and sources of MQTT messages.

The two non-MQTT resource monitors provided would need their own
separate application.  For the time being, they are included in MQHUB.


### Subscriptions

MQHUB subscribes to the topics you specify in the `:subscriptions`
map.  Each subscription performs actions in the form of Clojure code.
Some subscription types expect a specific topic or data morphology.

The syntax for the topic is the usual; the one supported by your MQTT
broker.  Examples:

```
owntracks/john/phone
owntracks/+/phone
zigbee2mqtt/my_switch/#
```

The first matches only events on a specific topic; in this case, those
triggered by John's mobile phone.  The second matches events on any
user's phone, including John's, provided all users have a device named
"phone".  The third topic matches any event (or subtopic) from
"my_switch" device.

In addition to those, MQHUB supports named portions, like:

```
zigbee2mqtt/$device/$action
```

This is the equivalent of `zigbee2mqtt/+/+`, with the difference that
the last two portions of the topic are extracted and provided to the
app in a map.  For example an event on the topic
`zigbee2mqtt/lamp/set`, the topic is converted to

```clojure
{:device "lamp"
 :action "set"}
```

The map is then bound in your code to `topic`.

Currently the supported subscription types are the following.

The `:meter` subscription is suitable for IoT devices, like those wall
sockets running [Tasmota firmware](https://tasmota.github.io).

```clojure
{:type :meter
 :telemetry [:energy :power]
 :threshold 20 ; Watts
 :hysteresis 0.3
 :avg-samples 10
 :trigger :on-to-off
 :exec (ntfy-send "The washing is ready to hang!")}
```

The data is expected to be JSON and contain a map with at least the
element specified in the `:telemetry` configuration.  In the example
above, it is expected to have at least a map of the form `{:energy
{:power 300}}`.

The `:geo` subescription is suitable for geo-fencing with applications
like [Owntracks](https://owntracks.org).

```clojure
{:type :geo
 :areas {"home" {:enter (evo/set-location-mode ["Home"] :day-off)
                 :leave (evo/set-location-mode ["Home"] :auto)}
         "my-town" {:enter (evo/set-location-mode ["Home"] :auto)
                    :leave (evo/set-location-mode ["Home"] :away)}}}
```

The data is supposed to be in JSON format and contain a map with at
least a `:type` (either `transition` or `location`).  If it is a
`transition`, `:desc` and `:event` are also expected.  If it is a
`location`, `:inregions` is expected too.

The `:blink` subscription is suitable for reacting to the same
messages generated by its monitor.  See the monitoring part.

```clojure
{:type :blink
 :armed (evo/set-location-mode ["Home"] :away)
 :disarmed (evo/set-location-mode ["Home"] :auto)}
```

The `:exec` subscription just invokes some clojure code.  The `:exec`
subscriptions are meant to react on MQTT messages coming from a user
interface.  Topics like `macro/all-off` or `macro/leaving-home` are
typical candidates for the `:exec` subscription.

```clojure
{:type :exec
 :code (do
         (mqtt-publish "zigbee2mqtt/ceiling_bulb/set" {:state :on})
         (mqtt-publish "zigbee2mqtt/door_panel_light_01/set" {:state :on})
         (mqtt-publish "zigbee2mqtt/door_panel_light_02/set" {:state :on})
         (mqtt-publish "zigbee2mqtt/socket_01/set" {:state :on})
         (mqtt-publish "zigbee2mqtt/desk_lamp/set" {:state :on})
         (mqtt-publish "shellies/shellyswitch25-34945477B8F2/roller/0/command" "open"))}
```


### Actions

All subscription types allow you to associate actions.  Actions are
nothing but Clojure code with some caveats:

  + Some MQHUB-specific primitives are visible to the code
  + In the lexical scope, `ctx`, `topic` and `data` are bound. They
    are respectively: a local context, the event topic and the event
    data.

The context `ctx` is initially an empty map and it is overriden by the
return value of your code.  That is, if you don't care about
the context, it doesn't matter what your code returns.  If you,
instead intend to leverage this mechanism you need to update the
context as your return code:

```clojure
(do
  (ntfy-send (str "x=" (:x ctx)))
  (update ctx :x (fnil inc 0)))
```

The `topic` is by default the MQTT path such as
`"zigbee2mqtt/desk_lamp/set"`, but if your subscription specifies
variables, then the topic is presented to your code as a map.  For
example, a subscription to `"zigbee2mqtt/$device/set"` can use the
`$device` part:

```clojure
(ntfy-send (str "got zigbee event from device " (:device topic))
```

Below is a list of some primitives already implemented for you.

The `mail-send` action lets you send an email upon receipt of an MQTT
message:

```clojure
(mail-send {:to "me@home.lan" :subject "something interesting" :body "etc..."})
```

The `ntfy-send` action lets you send a push notification via ntfy.sh,
upon receipt of an MQTT message:

```clojure
(ntfy-send "my topic" "The washing is ready to hang!")
```

You can omit the `topic` provided you specify it in the ntfy
configuration.  Example:

```clojure
:ntfy {:url "https://ntfy.sh"   ; this is the default
       :topic "your topic name" ; if not specified in the actions
       }
```

The topic in the `:ntfy` configuration serves as default if none is
specified in the action.  Please note that the :ntfy configuration
portion is entirely optional; the URL is by default the one above.

The `mqtt-publish` action lets you send an MQTT message to the same
broker:

```clojure
(mqtt-publish "shellies/shellyswitch25-34945477B8F2/roller/0/command" "open")
```

The data can be send as JSON if a map is passed instead:
```clojure
(mqtt-publish "zigbee2mqtt/socket_01/set" {:state :on})
```

The `evo/set-zone-temperature` action lets you override the
temperature of a zone:

```clojure
(evo/set-zone-temperature ["Home" "kids"] 20)
```

or cancel the override

```clojure
(evo/cancel-zone-override ["Home" "kids"])
```

You can change the mode of a location:

```clojure
(evo/set-location-mode ["Home"] :day-off)
(evo/set-location-mode ["Office"] :auto)
(evo/set-location-mode ["Chalet"] :away)
```

Additional logging can be simply done with

```clojure
(log "this goes in the same log file")
```

Delays can be implemented with

```clojure
(sleep 5) ; in seconds
```

Of course any other Clojure function or macro can be used as well:

```clojure
(when (= (:status data) "on")
  (ntfy-send "turned on!"))
```

### Logging

With the default configuration the logging is simply done to the
console (stdout).  If you need anything else, you may want to specify
your logging configuration in the `:logging` map entry.

Example:

``` clojure
{:mqtt { ... }
 :smtp { ... }
 :subscriptions { ... }
 :logging {:level :info
           :console false
           :appenders [{:appender :rolling-file
                        :rolling-policy {:type :fixed-window
                                         :max-index 5}
                        :triggering-policy {:type :size-based
                                            ;; 5MB
                                            :max-size 5242880}
                        :file "/home/wcp/mqhub.log"
                        :encoder :pattern}]
           :overrides {"fourteatoo.mqhub.mqtt" :debug
                       "fourteatoo.mqhub.action" :debug}}}
```

See https://github.com/pyr/unilog for further details.


## Usage

If you have installed the mqhub shell script as indicated above, you
can simply:

	$ mqhub
	
which is the equivalent to

    $ java -cp mqhub-<version_number>-standalone.jar mqhub.core



## Options

So far, the configuration file should be all you need.


### Bugs

An HTTP error 429 from EVO Home, upon start of MQHUB, may mean you
have stumbled across a rate limitation.  Such errors are triggered by,
for instance, too many restarts, or another concurrent Honeywell app.
Just wait a while and try to start mqhub once again.

As of February 2026, the Blink camera integration doesn't work.  It
stopped working with Blink's latest rewrite of the authentication
workflow.  No time to fix that; Blink doesn't seem to be interested in
third party, open source, applications anyway.


## License

Copyright © 2020-2026 Walter C. Pelissero

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
