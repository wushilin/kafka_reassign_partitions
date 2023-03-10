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
If your cluster has no RACK, you can use "@NONE" as rack, and it will be distributed amongst the brokers has no rack.

3. Prepare your `topics.txt`. 
This is optional. If no topics.txt is specified, all topics placement json will be generated.
You can get topics from your cluster by using a script shipped together in this package.

```bash
./get-topics.sh -c client.properties -t topics.txt
```

You may edit `topics.txt` after it is loaded if you want to exclude some topics from placement movements.

4. Generate your placement JSON
```bash
./generate-reassignment.sh -c client.properties -p placement.json -t topics.txt -o movement.json
```

A `movement.json` file based on your `placement.json` will be randomly generated. It may involves leader movements, topic data copying during execution.

You may want to inspect the `movement.json` before executing.

5. Execute your movement
```bash
kafka-reassign-partitions --execute --reassignment-json-file "movement.json" --bootstrap-server "simple-kafka-c2.jungle:9091" --command-config "client.properties"
```

6. Verify the status until it is fully completed
```
kafka-reassign-partitions --verify --reassignment-json-file "movement.json" --bootstrap-server "simple-kafka-c2.jungle:9091" --command-config "client.properties"
```

