# kafka_reassign_partitions
Increase your topic replication factor, move partitions around, generate observers, and more!

It supports the following key features:

- Support increasing replicas by placement constraint, even when you have no placement constraints applied on topic!
- Support replica placement based on placement constraint, even with Open Source Kafka.
- Support reshuffle of replicas so leaders are more balanced
- Support adding observers (for Confluent Platform)
- Gives clear instruction on how to execute the movement
- In rare cases, you may want to reduce replicas - yes it also supports
- Support `@NONE` macro for default RACK placement, in case your cluster has no RACK defined!!!

It only generates the `movement.json` for you. To execute, you will still use well supported
`kafka-reassign-partitions` tool, no risk involved if you checked before executing!

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

NOTE: `@NONE` is not any rack, it is for brokers that has no RACK at all.

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

By default, the program read the rack info from clusters. However, if you want to limit 
placement for certain rack to certain brokers, override with `-r rack:1,2,3`. The option may repeat for multiple racks.

Example: 

If you want to override this:
```
For RACK1, only place in broker 1,2,3
for RACK2, only place in broker 7,8,9
for @NONE, only place in 14,17,33 (@NONE means no rack)
```

You can override with:
```bash
-r "RACK1:1,2,3" -r "RACK2:7,8,9" -r "@NONE:14,17,33"
```

This command will generate `movement.json` to reassign your partition. The rules will be as random as possible, but adhere to the `placement.json` rules.

After this, a `movement.json` file based on your `placement.json` will be randomly generated. If you execute it (see later), it may involves 
partition leadership movements, topic data copying during execution, or increasing or replication factor (or decreasin if you misconfigured it)

Sample `movement.json`:
```json
{
  "partitions" : [ {
    "topic" : "__consumer_offsets",
    "partition" : 0,
    "replicas" : [ 202, 101, 201, 301, 302, 102 ],
    "observers" : [ 301, 302, 102 ],
    "log_dirs" : [ "any", "any", "any", "any", "any", "any" ]
  }, {
    "topic" : "__consumer_offsets",
    "partition" : 1,
    "replicas" : [ 301, 202, 101, 102, 201, 302 ],
    "observers" : [ 102, 201, 302 ],
    "log_dirs" : [ "any", "any", "any", "any", "any", "any" ]
  } ],
  "version" : 1
}
```
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

# Simpler version when you just want to adjust RF

Use this if you do need to increase/decrease RF but you don't want to:
1. Reshuffle the leaders
2. Retain current placement as much as you can
3. Optimize the broker with free space selection

## Usage:
```bash
java -cp build/libs/kafka_reassign_partitions-1.0-RELEASE.jar \
    net.wushilin.kafka.tools.SimpleAdjustRFKt \
    --min-free-gb 4.7 --limit-per-file 300 \
    -d describe.txt -f freespace.txt -s storage.json \
    -t topics.txt  --replica-count 3 -o out.json
```

Explanation:
This program runs simple RF adjustment generation and:
* limit output adjustment to 300 adjustment per file
* Use existing topic `describe.txt`, which is output of your `kafka-topics --describe`. This to retain current replicas
* Consult `freespace.txt` for current broker free space
* Require after adjustment each broker should have 4.7GB free
* Consult `storage.json` for partition's storage utilization. The `storage.json` is the output of `kafka-log-dirs` command, after removing the non-json part (first 2 lines typically)
* Ensure after each partition adjustment, the broker still has at least 4.7GiB worth free space. If this can't be fufilled, the program will throw exception and stop.
* Write output to `out.json`. Note that if the entries is more than `300` (the limit), the actual file might be `out.json.1`, `out.json.2` and so on.
* Generate only for topics in `topics.txt`
* Target replica count is 3. 
  * If more than 3, it would remove replica on the most occupied broker
  * If less than 3, it would add replica on the least occupied broker
* Write output to `out.json` (multiple files might be written)

The way to use the `out.json` is same as the first method.
## Enjoy!
