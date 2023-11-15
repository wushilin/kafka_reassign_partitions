package net.wushilin.kafka.tools

import FreeSpace
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.wushilin.kafka.tools.*
import net.wushilin.props.EnvAwareProperties
import java.nio.charset.StandardCharsets

class SimpleAdjustRF : CliktCommand() {
    private val describeFile: String by option(
        "-d",
        "--describe-file",
        help = "Existing topic describe result"
    ).required()

    private val freeSpace: String by option(
        "-f",
        "--free-space-file",
        help = "Free space for broker in brokerId,freeSpaceInGiB format"
    ).required()

    private val topicStorageFile:String by option(
        "-s",
        "--storage-file",
        help = "Topic storage JSON file exported from kafka"
    ).required()

    private val topicsFile:String by option(
        "-t",
        "--topics-file",
        help = "Specify the topics to cover"
    ).required()

    private val replicaCountS:String by option(
        "-r",
        "--replica-count",
        help = "Replica count target"
    ).required()

    private val minFreeS:String by option(
        "--min-free-gb",
        help = "Minimum free space required"
    ).default("30")
    private val outputFile:String by option(
        "-o",
        "--output-file",
        help = "output file json"
    ).required()

    private val limitS:String by option(
        "-l",
        "--limit-per-file",
        help = "Limit number of partition movements per file"
    ).default("300")
    override fun run() {
        val freeSpaceOriginal = FreeSpace.parse(freeSpace)
        val freeSpace = FreeSpace.parse(freeSpace)
        println("Before adjustment free space: $freeSpace")
        val topicStorage = DataSet.parse(topicStorageFile)
        val existingPlacement = DescribeResult.parse(describeFile)
        val topics = readAsList(topicsFile)
        val targetReplicas = replicaCountS.toInt()
        val minFree = (minFreeS.toDouble() * 1073741824).toLong()
        val adjustments = mutableMapOf<Pair<String, Int>, List<Int>>()
        for(topic in topics) {
            val partitionCount = findPartitionCountFor(existingPlacement, topic)
            for(partition in 0 until partitionCount) {
                val key = Pair(topic, partition)
                val partitionSize = findStorageFor(topicStorage, topic, partition)
                val replicas = mutableListOf<Int>()
                val replicasN = findReplicasFor(existingPlacement, topic, partition)
                replicas.addAll(replicasN)
                if(replicas.size == targetReplicas) {
                    println("$topic:$partition replica size is already ${replicas.size}")
                    continue
                }
                while(replicas.size > targetReplicas) {
                    val toRemove = freeSpace.shrinkReplica(topic, partition, replicas, partitionSize)
                    replicas.remove(toRemove)
                    println("Removing $toRemove from $topic:$partition (size $partitionSize) $replicas")
                }
                while(replicas.size < targetReplicas) {
                    val toAdd = freeSpace.increaseReplica(topic, partition, replicas, partitionSize, minFree)
                    replicas.add(toAdd)
                    println("Adding $toAdd to $topic:$partition (size $partitionSize) $replicas")
                }
                adjustments[key] = replicas
            }
        }

        println("After adjustment free space: $freeSpace")
        for((brokerId, free) in freeSpace.data) {
            val oldFree = freeSpaceOriginal.data[brokerId]!!
            val diff = free - oldFree
            val expression = if(diff >= 0) {
                "+$diff"
            } else {
                "$diff"
            }
            println("Change: Broker $brokerId free space $expression bytes")
        }
        val adjustmentSplit = Common.splitMap(adjustments, limitS.toInt())
        for((idx, next) in adjustmentSplit.withIndex()) {
            val result = mutableListOf<OutputEntry>()
            for((key, replicas) in next) {
                val (topic, partition) = key;
                val entry = OutputEntry(topic, partition, replicas, listOf())
                result.add(entry)
            }
            val output = Output(result, 1)
            var out = "$outputFile.${idx + 1}"
            if(adjustmentSplit.size == 1) {
                out = outputFile
            }
            GenerateKafkaPartitionReassignment.logger.info("About to write to file $out")
            java.io.File(out).outputStream().use {
                Parser.objectMapper.writerWithDefaultPrettyPrinter().writeValue(it, output)
            }
            GenerateKafkaPartitionReassignment.logger.info("Written ${result.size} entries to $out")

        }
        var bootstrapServers = "<bootstrap-server>"

        var clientFilePrompt = "<client-file>"

        println("###############################################################################")
        println("Please considering doing the following:")
        println("    1. Inspect `$outputFile` and make sure it is accurate and makes sense!")
        println("    2. If desired, please apply the changes with kafka-reassign-partitions!")
        println("        a. If you use open source kafka, you can use `kafka-reassign-partitions.sh`")
        println("        b. If you use Confluent Platform, you can use `kafka-reassign-partitions`")
        println("    3. Run this command:")
        println("       $ kafka-reassign-partitions --execute --throttle 50000000 --reassignment-json-file \"$outputFile\" --bootstrap-server \"$bootstrapServers\" --command-config \"$clientFilePrompt\"")
        println("    4. Run this command until it completes successfully:")
        println("       $ kafka-reassign-partitions --verify --reassignment-json-file \"$outputFile\" --bootstrap-server \"$bootstrapServers\" --command-config \"$clientFilePrompt\"")
        println("    5. Verify your placement manually using kafka-topics describe feature.")
        println("###############################################################################")
    }

    fun findReplicasFor(existingPlacement: DescribeResult, topic:String, partition:Int):List<Int> {
        val result = existingPlacement.data.get(Pair(topic, partition))!!
        return result
    }
    fun findStorageFor(topicStorage:DataSet, topic:String, partition:Int):Long {
        var maxFound = -1L
        for(broker in topicStorage.brokers) {
            for(logdir in broker.logDirs) {
                for(tp in logdir.partitions) {
                    if(tp.partition == "$topic-$partition") {
                        if(tp.size > maxFound) {
                            maxFound = tp.size
                        }
                    }
                }
            }
        }
        return maxFound
    }

    fun findPartitionCountFor(existingPlacement: DescribeResult, topic:String):Int {
        var maxFound = -1
        for(i in 0..99999999) {
            val key = Pair(topic, i)
            val placement = existingPlacement.data.get(key)
            if(placement == null) {
                return maxFound + 1
            } else {
                maxFound = i
            }
        }
        return maxFound
    }
    fun readAsList(input:String):List<String> {
        return java.io.File(input).readLines(StandardCharsets.UTF_8)
    }
}


fun main(args: Array<String>) = SimpleAdjustRF().main(args)