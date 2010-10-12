#!/bin/sh

APP_NAME=killdeer
VERSION=0.5
HEAP_OPTS="-Xmx1024m -Xms1024m -XX:NewSize=512m"
GC_OPTS="-XX:+PrintGCTimeStamps -XX:+UseConcMarkSweepGC -XX:+UseParNewGC"
DEBUG_OPTS="-XX:ErrorFile=/var/log/$APP_NAME/java_error%p.log"
#YK_OPTS="-agentpath:/Applications/YourKit_Java_Profiler_9.0.2.app/bin/mac/libyjpagent.jnilib -jar"
#YK_OPTS="-verbose:class"
JAVA_OPTS="-server $YK_OPTS $GC_OPTS $HEAP_OPTS $DEBUG_OPTS"


java $JAVA_OPTS -jar $APP_NAME-$VERSION.jar $@
