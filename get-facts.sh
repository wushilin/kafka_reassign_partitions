#!/bin/sh

kafka-topics --bootstrap-server simple-kafka1.jungle:9092 --list > topics.txt

kafka-topics --bootstrap-server simple-kafka1.jungle:9092 --describe > describe.txt

kafka-log-dirs --describe --bootstrap-server simple-kafka1.jungle:9092 | tail +3 > storage.json
