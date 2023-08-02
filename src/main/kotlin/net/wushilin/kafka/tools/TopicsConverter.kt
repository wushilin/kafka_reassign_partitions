package net.wushilin.kafka.tools

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

class TopicsConverter : CliktCommand() {
        companion object {
            val logger: Logger = LoggerFactory.getLogger(GetTopicList::class.java)
        }

        private val lookupFile:String by option(
            "-l",
            "--lookup",
            "--lookup-file",
            help = "Lookup partition number from this file"
        ).required()

        private val topicsFile: String by option(
            "-t",
            "--topics",
            "--topics-file",
            help = "Topics file in text format, one per line"
        ).required()

        private val outputFile:String by option(
            "-o",
            "--out",
            "--output-file",
            help = "Output file"
        ).required()
        override fun run() {
            val topics = mutableListOf<String>()
            java.io.File(topicsFile).inputStream().use {
                it.bufferedReader(StandardCharsets.UTF_8).lines().forEach {
                    line->
                    topics.add(line)
                }
            }

            val lookup = mutableMapOf<String, String>()
            java.io.File(lookupFile).useLines { it ->
                it.filter{ line -> line.trim().isNotEmpty() }.map {
                    line ->
                    val tokens = line.split(":")
                    tokens
                }.filter {
                    tokens ->
                    tokens.size == 2
                }.map {
                    Pair(it[0], it[1])
                }.forEach {
                    lookup[it.first] = it.second
                }
            }

            java.io.File(outputFile).outputStream().use {
                for(topic in topics) {
                    val partition = lookup[topic]!!
                    it.write("$topic:$partition\n".toByteArray(StandardCharsets.UTF_8))
                }
            }
        }
    }

fun main(args: Array<String>) = TopicsConverter().main(args)