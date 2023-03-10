#!/bin/sh

CP=`find . -name 'kafka_reassign_partition*.jar'`
echo Classpath: $CP
java -cp $CP net.wushilin.kafka.tools.GetTopicListKt $*
