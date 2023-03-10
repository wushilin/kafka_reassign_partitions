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

The package will be created at `build/*.tar.gz` location.

Alternatively you can download a binary in release page. It is the same as if you created package yourself.

# Running

## Prepare your `client.properties`

This is a genearal kafka `client.properties` used for your admin client.
The user used in this client.properties should have enough permission to reassign partitions.

If you have SASL (e.g. kerberos), please configure it here.

## Prepare your `placement.json`

See the `placement.json` in `examples` folder.

For more info, refer to `https://docs.confluent.io/platform/current/multi-dc-deployments/multi-region.html`
If your cluster has no RACK, you can use `@NONE` as rack in `placement.json`, and it will be distributed amongst the brokers has no rack.

## Prepare your `topics.txt`
This is the list of topics to propose for movement.

This is optional. If no `topics.txt` is specified, all topics' placement json will be generated.

You can get topics from your cluster by using a script shipped together in this package.

The command is:
```bash
./get-topics.sh -c client.properties -t topics.txt
```

This command will dump all topics into `topics.txt`.

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
## Generate your placement JSON
```bash
./generate-reassignment.sh -c client.properties -p placement.json -t topics.txt -o movement.json
```

This command will generate `movement.json` to reassign your partition. The rules will be as random as possible, but adhere to the `placement.json` rules.

After this, a `movement.json` file based on your `placement.json` will be randomly generated. If you execute it (see later), it may involves 
partition leadership movements, topic data copying during execution, or increasing or replication factor (or decreasin if you misconfigured it)

You may want to inspect the `movement.json` before executing.

If you decreased `replicas`, you may also want to check if your topics `min.insync.replicas` is set appropriately!

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

The command also gives you the actual command to run. Please consider running after verifing if the movement makes sense!

Example output at the last few lines:
```
###############################################################################
Please considering doing the following:
    1. Inspect `movement1.json` and make sure it is accurate and makes sense!
    2. If desired, please apply the changes with kafka-reassign-partitions!
        a. If you use open source kafka, you can use `kafka-reassign-partitions.sh`
        b. If you use Confluent Platform, you can use `kafka-reassign-partitions`
    3. Run this command:
       $ kafka-reassign-partitions --execute --reassignment-json-file "movement1.json" \
              --bootstrap-server "simple-kafka-c2.jungle:9091" \
              --command-config "client.properties"
    4. Run this command until it completes successfully:
       $ kafka-reassign-partitions --verify --reassignment-json-file "movement1.json" \
              --bootstrap-server "simple-kafka-c2.jungle:9091" \
              --command-config "client.properties"
    5. Verify your placement manually using kafka-topics describe feature.
###############################################################################
```
## Execute your movement using `kafka-reassign-partitions` (or `kafka-reassign-partitions.sh`)
```bash
kafka-reassign-partitions --execute --reassignment-json-file "movement.json" \
  --bootstrap-server "simple-kafka-c2.jungle:9091" --command-config "client.properties"
```

This will submit the movement request to server. This may take a while to complete.

6. Verify the status until it is fully completed
```
kafka-reassign-partitions --verify --reassignment-json-file "movement.json" \
  --bootstrap-server "simple-kafka-c2.jungle:9091" --command-config "client.properties"
```

Upon completion, the internal throttles will be removed automatically

## Enjoy!
