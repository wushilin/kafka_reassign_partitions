package net.wushilin.kafka.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.wushilin.kafka.tools.KafkaUtil.Companion.connectToKafka
import net.wushilin.kafka.tools.KafkaUtil.Companion.getClusterInfo
import net.wushilin.kafka.tools.KafkaUtil.Companion.getTopicList
import net.wushilin.kafka.tools.KafkaUtil.Companion.getTopicPartitions
import net.wushilin.props.EnvAwareProperties
import org.apache.kafka.clients.admin.AdminClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class GenerateKafkaPartitionReassignment : CliktCommand() {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(GenerateKafkaPartitionReassignment::class.java)
    }

    private val force by option("--force", "-f").flag("--no-force", "-nf", default = false)
    private val placementFile: String by option(
        "-p",
        "--placement",
        "--placement-json-file",
        help = "net.wushilin.kafka.tools.Placement constraint json file"
    ).required()
    private val topicsFile: String? by option(
        "-t",
        "--topics",
        "--topics-file",
        help = "Topics file in text format, one per line. If omitted, all topics will be applied"
    )
    private val clientFile: String by option(
        "-c",
        "--command-config",
        help = "Your kafka connectivity client properties"
    ).required()
    private val outputFile: String by option(
        "-o",
        "--output",
        help = "net.wushilin.kafka.tools.Output JSON file for kafka-reassign-partitions"
    ).required()

    private lateinit var adminClient: AdminClient
    override fun run() {
        logger.info("Arguments:")
        logger.info("    placement-file:            $placementFile")
        logger.info("    topics-file (none -> all): $topicsFile")
        logger.info("    kafka-client-file:         $clientFile")
        logger.info("    json-out-file:             $outputFile")
        if (java.io.File(outputFile).exists()) {
            if (!force) {
                logger.error("File $outputFile exists. Please delete it first or use -f option to force overwrite.")
                return
            } else {
                logger.warn("Going to overwrite $outputFile!")
            }
        }
        val props = EnvAwareProperties.fromPath(clientFile)
        logger.info("Connecting to kafka")
        adminClient = connectToKafka(clientFile)
        logger.info("Kafka connected")
        logger.info("Parsing placement file: $placementFile")
        val placement = Parser.parsePlacement(placementFile)
        logger.info("net.wushilin.kafka.tools.Placement file parsed: $placement")

        logger.info("Getting cluster info:")
        val cluster = getClusterInfo(adminClient)
        logger.info("net.wushilin.kafka.tools.Cluster info retrieved:")
        val topicsSet = getTopicList(adminClient)

        val topics = TreeSet<String>()
        if (topicsFile != null) {
            java.io.File(topicsFile).forEachLine { topic ->
                if (topic.trim().isNotEmpty()) {
                    topics.add(topic.trim())
                }
            }
        } else {
            topics.addAll(topicsSet)
        }

        logger.info("Count of topics: ${topics.size} topics")
        logger.info("Inspecting topics...")
        for (topic in topics) {
            if (!topicsSet.contains(topic)) {
                throw IllegalArgumentException("Topic $topic is not found on cluster.")
            }
        }
        logger.info("Looking good...")
        logger.info("Getting topic partitions...")
        val topicPartitions = getTopicPartitions(adminClient, topics)
        logger.info("All good!")
        val result = mutableListOf<OutputEntry>()
        for (topic in topics) {
            val partitions = topicPartitions[topic]!!
            for (partitionNumber in partitions) {
                logger.info("  Working on Topic $topic partition $partitionNumber")
                val replicas = mutableListOf<Int>()
                val observers = mutableListOf<Int>()
                for (spec in placement.replicaRules) {
                    val targetRack = spec.rack
                    val targetCount = spec.count
                    val exclusion = mutableListOf<Int>()
                    exclusion.addAll(replicas)
                    val selected = cluster.selectReplica(targetRack, targetCount, exclusion)
                    replicas.addAll(selected)
                }
                for (spec in placement.observerRules) {
                    val targetRack = spec.rack
                    val targetCount = spec.count
                    val exclusion = mutableListOf<Int>()
                    exclusion.addAll(replicas)
                    exclusion.addAll(observers)
                    val selected =
                        cluster.selectReplica(targetRack, targetCount, exclusion) // exclude replicas from selection
                    observers.addAll(selected)
                }
                replicas.addAll(observers) //observers are also replicas
                val replicasFinal = filterDup(replicas)
                val observersFinal = filterDup(observers)
                val difference = mutableSetOf<Int>()
                difference.addAll(replicasFinal)
                difference.removeAll(observersFinal.toSet())

                logger.info("  Calculated replicas: $replicasFinal observers: $observersFinal")
                if (difference.size == 0) {
                    throw IllegalArgumentException("$topic:$partitionNumber : Effectively all replicas are observers!")
                }
                replicasFinal.shuffle()
                observersFinal.shuffle()
                val newItem = OutputEntry(topic, partitionNumber, replicasFinal, observersFinal)
                result.add(newItem)
            }
        }
        val output = Output(result, 1)
        logger.info("About to write to file $outputFile")
        java.io.File(outputFile).outputStream().use {
            Parser.objectMapper.writerWithDefaultPrettyPrinter().writeValue(it, output)
        }
        logger.info("Written ${result.size} entries to $outputFile")
        val bootstrapServers = props.getProperty("bootstrap.servers")
        println("###############################################################################")
        println("Please considering doing the following:")
        println("    1. Inspect `$outputFile` and make sure it is accurate and makes sense!")
        println("    2. If desired, please apply the changes with kafka-reassign-partitions!")
        println("        a. If you use open source kafka, you can use `kafka-reassign-partitions.sh`")
        println("        b. If you use Confluent Platform, you can use `kafka-reassign-partitions`")
        println("    3. Run this command:")
        println("       $ kafka-reassign-partitions --execute --reassignment-json-file \"$outputFile\" --bootstrap-server \"$bootstrapServers\" --command-config \"$clientFile\"")
        println("    4. Run this command until it completes successfully:")
        println("       $ kafka-reassign-partitions --verify --reassignment-json-file \"$outputFile\" --bootstrap-server \"$bootstrapServers\" --command-config \"$clientFile\"")
        println("    5. Verify your placement manually using kafka-topics describe feature.")
        println("###############################################################################")
    }

    private fun filterDup(list: MutableList<Int>): MutableList<Int> {
        val set = mutableSetOf<Int>()
        val result = mutableListOf<Int>()
        for (i in list) {
            if (!set.contains(i)) {
                set.add(i)
                result.add(i)
            }
        }
        return result
    }
}

fun main(args: Array<String>) = GenerateKafkaPartitionReassignment().main(args)