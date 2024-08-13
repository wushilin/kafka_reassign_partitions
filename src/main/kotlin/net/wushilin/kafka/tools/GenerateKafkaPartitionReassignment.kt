package net.wushilin.kafka.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
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

    private val rackMapList: List<String> by option(
        "-r",
        "--rack-map",
        help = "Map rack to broker IDs with override in `-r dc1:1,2,3 -r @NONE:4,5,6 -r dc2:7,8,9` format"
    ).multiple()

    private val useServerRack by option("--use-server-rack")
        .flag("--ignore-server-rack", default = true)


    private val topicsFile: String by option(
        "-t",
        "--topics",
        "--topics-file",
        help = "Topics file in text format in topicName:partition `my-topic:12` format"
    ).required()

    private val limitS:String by option (
        "-l",
        "--limit-per-file",
        help="Limit number of entries per file"
    ).default("9999999")

    private val clientFile: String by option(
        "-c",
        "--command-config",
        help = "Your kafka connectivity client properties"
    ).default("")
    private val outputFile: String by option(
        "-o",
        "--output",
        help = "net.wushilin.kafka.tools.Output JSON file for kafka-reassign-partitions"
    ).required()

    private lateinit var adminClient: AdminClient

    private fun getRackMap():Map<String, List<Int>> {
        val result = mutableMapOf<String, List<Int>>()
        for(i in rackMapList) {
            val tokens = i.split(":").map { it.trim()}
            if(tokens.size != 2) {
                throw IllegalArgumentException("Invalid rack map `$i`")
            }
            var rack = tokens[0]
            if(rack == "") {
                rack = "@NONE"
            }
            val brokersStr = tokens[1]
            val tokensInner = brokersStr.split(",")
            val tokensInt = tokensInner.map {
                it.trim()
            }.filter {
                it.isNotEmpty()
            }.map {
                it.toInt()
            }.toSet().toList()
            result[rack] = tokensInt
        }
        return result
    }
    override fun run() {
        logger.info("Arguments:")
        logger.info("    placement-file:            $placementFile")
        logger.info("    topics-file:               $topicsFile")
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
        logger.info("Connecting to kafka")
        logger.info("Kafka connected")
        logger.info("Parsing placement file: $placementFile")
        val placement = Parser.parsePlacement(placementFile)
        val rackOverride = getRackMap();
        logger.info("net.wushilin.kafka.tools.Placement file parsed: $placement")
        logger.info("Rack override: $rackOverride")
        logger.info("Use Server Rack: $useServerRack")
        logger.info("Getting cluster info:")
        var cluster:Cluster? = null;
        if(useServerRack) {
            adminClient = connectToKafka(clientFile)
            cluster = getClusterInfo(adminClient)
            logger.info("net.wushilin.kafka.tools.Cluster info retrieved:")
        } else {
            cluster = Cluster(rackOverride)
        }
        val topics = TreeMap<String, Int>()
        if (topicsFile != null) {
            java.io.File(topicsFile).forEachLine { topic ->
                if (topic.trim().isNotEmpty()) {
                    val tokens = topic.split(":")
                    if(tokens.size != 2) {
                        throw IllegalArgumentException("Unknown token `$topic`")
                    }
                    val topicName = tokens[0].trim()
                    val partition = tokens[1].toInt()
                    topics[topicName] = partition
                }
            }
        }
        val result = mutableListOf<OutputEntry>()
        for ((topic, partition) in topics) {
            for (partitionNumber in 0 until partition) {
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
                replicas.shuffle()
                observers.shuffle()
                val replicasFinal = filterDup(replicas)
                val observersFinal = filterDup(observers)
                replicasFinal.addAll(observers) //observers are also replicas
                val difference = mutableSetOf<Int>()
                difference.addAll(replicasFinal)
                difference.removeAll(observersFinal.toSet())

                logger.info("  Calculated replicas: $replicasFinal observers: $observersFinal")
                if (difference.size == 0) {
                    throw IllegalArgumentException("$topic:$partitionNumber : Effectively all replicas are observers!")
                }
                val newItem = OutputEntry(topic, partitionNumber, replicasFinal, observersFinal)
                result.add(newItem)
            }
        }
        val outputs = Common.splitList(result, limitS.toInt())
        for((idx, outputSmall) in outputs.withIndex()) {
            val output = Output(outputSmall, 1)
            var outfile = if(outputs.size == 1) {
                outputFile
            } else {
                "$outputFile.${idx + 1}"
            }
            logger.info("About to write to file $outfile")
            java.io.File(outfile).outputStream().use {
                Parser.objectMapper.writerWithDefaultPrettyPrinter().writeValue(it, output)
            }
            logger.info("Written ${result.size} entries to $outfile")
        }
        var bootstrapServers = "<bootstrap-server>"

        var clientFilePrompt = "<client-file>"
        if(clientFile.isNotEmpty()) {
            val props = EnvAwareProperties.fromPath(clientFile)
            bootstrapServers = props.getProperty("bootstrap.servers")
            clientFilePrompt = clientFile
        }
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
