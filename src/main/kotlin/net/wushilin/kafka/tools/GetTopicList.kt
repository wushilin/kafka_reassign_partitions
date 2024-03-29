package net.wushilin.kafka.tools

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.wushilin.kafka.tools.KafkaUtil.Companion.connectToKafka
import net.wushilin.kafka.tools.KafkaUtil.Companion.getTopicList
import org.apache.kafka.clients.admin.AdminClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.*

class GetTopicList : CliktCommand() {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(GetTopicList::class.java)
    }
    private val force by option("--force", "-f").flag("--no-force", "-nf", default = false)
    private val topicsFile: String by option(
        "-t",
        "--topics",
        "--topics-file",
        help = "Topics file in text format, one per line. Each line is topic:partition_count format (e.g.`my-topic:12`)"
    ).required()
    private val clientFile: String by option(
        "-c",
        "--command-config",
        help = "Your kafka connectivity client properties"
    ).required()

    lateinit var adminClient: AdminClient
    override fun run() {
        logger.info("Using client properties from [$clientFile] writing output to [$topicsFile]")
        logger.info("Connecting to kafka...")
        if(java.io.File(topicsFile).exists()) {
            if(!force) {
                logger.error("File $topicsFile exists. Please delete it first or use -f option to force overwrite.")
                return
            } else {
                logger.warn("Going to overwrite $topicsFile!")
            }
        }
        adminClient = connectToKafka(clientFile)
        logger.info("Kafka connected.");
        val topicsMapOrdered = getTopicList(adminClient)
        logger.info("Found ${topicsMapOrdered.size} topics")
        java.io.File(topicsFile).outputStream().use {
            for ((topic, partitions) in topicsMapOrdered) {
                logger.info("Written topic: $topic:$partitions")
                it.write("$topic:$partitions\n".toByteArray(StandardCharsets.UTF_8))
            }
        }
        logger.info("Written ${topicsMapOrdered.size} entries to $topicsFile")
    }
}

fun main(args: Array<String>) = GetTopicList().main(args)