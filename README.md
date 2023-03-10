# kafka_reassign_partitions
Increase your topic replication factor, move partitions around, generate observers, and more!

# Pre-requisite
- Java 1.8 (JRE or JDK)

# Building

Compiling:

```bash
./gradlew clean jar
```

Packaging:

```bash
./create_package
```

Alternatively you can download a binary in release page. It is the same as if you created package yourself.

# Running

1. Prepare your `client.properties`. This is a genearal kafka client.properties used for your admin client.
The user used in this client.properties should have enough permission to reassign partitions.

2. Prepare your `placement.json`. See the placement.json in `examples` folder.
For more info, refer to `https://docs.confluent.io/platform/current/multi-dc-deployments/multi-region.html`
If your cluster has no RACK, you can use `@NONE` as rack in `placement.json`, and it will be distributed amongst the brokers has no rack.

3. Prepare your `topics.txt`. 
This is optional. If no `topics.txt` is specified, all topics placement json will be generated.
You can get topics from your cluster by using a script shipped together in this package.

```bash
./get-topics.sh -c client.properties -t topics.txt
```

You may edit `topics.txt` after it is loaded if you want to exclude some topics from placement movements.

To see all options for `get-topics.sh`, run:
```bash
./get-topics.sh --help
```
Output:
```
Classpath: ./kafka_reassign_partitions-1.0-RELEASE.jar
Usage: get-topic-list [OPTIONS]

Options:
  -f, --force / -nf, --no-force
  -t, --topics, --topics-file TEXT
                                   Topics file in text format, one per line
  -c, --command-config TEXT        Your kafka connectivity client properties
  -h, --help                       Show this message and exit
```
4. Generate your placement JSON
```bash
./generate-reassignment.sh -c client.properties -p placement.json -t topics.txt -o movement.json
```

A `movement.json` file based on your `placement.json` will be randomly generated. It may involves leader movements, topic data copying during execution.

You may want to inspect the `movement.json` before executing.

To see all options for `generate-reassignment.sh`, run:
```bash
./generate-reassignment.sh --help
```
Output:
```
Classpath: ./kafka_reassign_partitions-1.0-RELEASE.jar
Usage: generate-kafka-partition-reassignment [OPTIONS]

Options:
  -f, --force / -nf, --no-force
  -p, --placement, --placement-json-file TEXT
                                   net.wushilin.kafka.tools.Placement
                                   constraint json file
  -t, --topics, --topics-file TEXT
                                   Topics file in text format, one per line.
                                   If omitted, all topics will be applied
  -c, --command-config TEXT        Your kafka connectivity client properties
  -o, --output TEXT                net.wushilin.kafka.tools.Output JSON file
                                   for kafka-reassign-partitions
  -h, --help                       Show this message and exit
```
5. Execute your movement using `kafka-reassign-partitions` (or `kafka-reassign-partitions.sh`)
```bash
kafka-reassign-partitions --execute --reassignment-json-file "movement.json" \
  --bootstrap-server "simple-kafka-c2.jungle:9091" --command-config "client.properties"
```

6. Verify the status until it is fully completed
```
kafka-reassign-partitions --verify --reassignment-json-file "movement.json" \
  --bootstrap-server "simple-kafka-c2.jungle:9091" --command-config "client.properties"
```

