java -cp build/libs/kafka_reassign_partitions-1.0-RELEASE.jar net.wushilin.kafka.tools.SimpleAdjustRFKt --limit-per-file 300 -d describe.txt -f freespace.txt -s storage.json -t step2.txt  --replica-count 3 -o out.json
