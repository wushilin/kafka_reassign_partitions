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

class GetTopicList : CliktCommand() {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(GetTopicList::class.java)
    }
    private val force by option("--force", "-f").flag("--no-force", "-nf", default = false)
    private val topicsFile: String by option(
        "-t",
        "--topics",
        "--topics-file",
        help = "Topics file in text format, one per line"
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
        val topicsSet = getTopicList(adminClient)
        logger.info("Found ${topicsSet.size} topics")
        java.io.File(topicsFile).outputStream().use {
            for (i in topicsSet) {
                logger.info("Written topic: $i")
                it.write("$i\n".toByteArray(StandardCharsets.UTF_8))
            }
        }
        logger.info("Written ${topicsSet.size} entries to $topicsFile")
    }
}

fun main(args: Array<String>) = GetTopicList().main(args)