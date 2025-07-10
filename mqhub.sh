#!/bin/sh

class=fourteatoo.mqhub.core
jar=$0.jar

exec java -cp $jar $class "$@"
