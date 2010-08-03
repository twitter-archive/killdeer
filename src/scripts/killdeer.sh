#!/bin/sh

APP_NAME=killdeer
VERSION=0.5
HEAP_OPTS="-Xmx1024m -Xms1024m -XX:NewSize=512m"
GC_OPTS="-verbosegc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+UseConcMarkSweepGC -XX:+UseParNewGC"
DEBUG_OPTS="-XX:ErrorFile=/var/log/$APP_NAME/java_error%p.log"
JAVA_OPTS="-server $GC_OPTS $HEAP_OPTS $DEBUG_OPTS"

java $JAVA_OPTS -jar $APP_NAME-$VERSION.jar $@
