# mqhub

An IoT device monitor based on MQTT, written in Clojure.  Upon the
occurrence of a specific event on a given MQTT topic, one or more
actions are triggered.

## Motivation

Although this project may remind of stuff like [Home
Assistant](https://home-assistant.io), at least in the scope, it
doesn't get anywhere close to the sophistication, user friendliness,
and capabilities Home Assistance offers.

mqhub was born from a personal need to scratch an itch: have a simple,
lightweight sensors monitor that requires little in terms of
resources, has almost no dependencies (just an MQTT broker), and can
run on any OS a JVM can run on.  While having some fun in Clojure.

So, if you need a complete home automation solution, by all means do
not hesitate, head to [Home Assistant](https://home-assistant.io) and
start downloading.  If on the other hand, you enjoy hacking in
Clojure, then, perhaps, you may want to read on.

Incidentally two non-MQTT resource monitors are provided.  Although
they would need their own separate application, for the time being,
they are included in mqhub.  Those are the EVO Home (Honeywell
thermostats) and the Blink Camera (Amazon).  Those monitor your
heating and your surveillance cameras.  On state change they feed
events on MQTT topics.  You can, of course, subscribe to those topics
and associate actions to their events.


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
                                          :actions [{:type :mail
			                             :message {:subject "Washing Machine"
                                                               :body "The washing is ready to hang!"}}]}
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


### Subscriptions

mqhub subscribes to the list of topics you specify in the
`:subscriptions` map.  Each subscription performs one or more actions.
Most subscriptions expect a specific topic or data morphology.

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
 :actions [{:type :mail
            :message {:subject "Washing Machine"
                      :body "The washing is ready to hang!"}}]}
```

The data is expected to be in JSON format and contain a map with at
least the element specified in the `:telemetry` configuration.  In the
example above, it is expected to have at least a map of the form
`{:energy {:power 300}}`.  The topic name is irrelevant.


The `:geo` subescription is suitable for geo-fencing with applications
like [Owntracks](https://owntracks.org).

```clojure
{:type :geo
 :areas {"home" {:enter [{:type :evo-home
                          :name :home-heating
                          :location ["Home"]
                          :mode :auto}
                         {:type :blink
                          :name :cameras
                          :mode :disarmed}]
                 :leave [{:type :evo-home
                          :name :home-heating
                          :location ["Home"]
                          :mode :away}
                         {:type :blink
                          :name :cameras
                          :mode :armed}]}
         "my-town" {:leave [{:type :evo-home
                             :name :home-heating
                             :location ["Home"]
                             :mode :off}]
                    :enter [{:type :evo-home
                             :name :home-heating
                             :location ["Home"]
                             :mode :away}]}}}
```

The data is supposed to be in JSON format and contain a map with at
least a `:type` (either `transition` or `location`).  If it is a
`transition`, `:desc` and `:event` are also expected.  If it is a
`location`, `:inregions` is expected too.

The `:blink` subscription is suitable for reacting to the same
messages generated by this application.  See the monitoring part.

```clojure
{:type :blink
 :armed [{:type :evo-home
          :location ["Home"]
          :mode :away}]
 :disarmed [{:type :evo-home
             :location ["Home"]
             :mode :auto}]}
```

The `:macro` subscription just invokes a number of actions and does
not expect any type of information from the topic or the payload (and
so should the actions invoked).  The `:macro` subscriptions are meant
to react on MQTT messages coming from some user interface.  Topics
like `macro/all-off` or `macro/leaving-home` are typical candidates
for the `:macro` subscription.

```clojure
{:type :macro
 :actions [{:type :publish
            :topic "zigbee2mqtt/ceiling_bulb/set"
            :payload {:state :off}}
           {:type :publish
            :topic "zigbee2mqtt/door_panel_light_01/set"
            :payload {:state :off}}
           {:type :publish
            :topic "zigbee2mqtt/door_panel_light_02/set"
            :payload {:state :off}}
           {:type :publish
            :topic "zigbee2mqtt/socket/set"
            :payload {:state :off}}
           {:type :publish
            :topic "zigbee2mqtt/desk_lamp/set"
            :payload {:state :off}}
           {:type :publish
            :topic "shellies/shellyswitch25-34945477B8F2/roller/0/command"
            :payload "close"}]}
```



### Actions

Each subscription is linked to one or more actions.  Currently, there
is just a handful of actions implemented, but there is no reason you
couldn't add your own.

The `:mail` action lets you send an email upon receipt of am MQTT
message.  The configuration is simple:

```clojure
{:type :mail
 :message {:subject "your subject"
           :body "Some text"}}
```

The `:publish` action lets you send an MQTT message to the same
broker:

```clojure
{:type :publish
 :topic "zigbee2mqtt/ceiling_bulb/set"
 :payload {:state :on}}
```

The `:evo-home` action lets you perform a change of state of an EVO
Home location:

```clojure
{:type :evo-home
 :location ["Home"]
 :mode :away}
```

or just a system:

```clojure
{:type :evo-home
 :system "1544623"
 :mode :day-off}
```

or zone:

```clojure
{:type :evo-home
 :zone "98764"
 :temperature 15}
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

    $ java -cp target/mqhub-<version_number>-standalone.jar mqhub.core



## Options

So far, the configuration file should be all you need.


### Bugs

Likely.


## License

Copyright © 2020-2025 Walter C. Pelissero

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
