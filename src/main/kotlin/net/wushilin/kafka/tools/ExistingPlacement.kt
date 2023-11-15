package net.wushilin.kafka.tools

import java.io.File
data class ExistingPlacement(val data:MutableMap<Pair<String, Int>, List<Int>>) {
    companion object {
        fun parse(input:String):ExistingPlacement {
            val data = mutableMapOf<Pair<String, Int>, List<Int>>()
            File(input).useLines {
                for(line in it) {
                    var line = line.trim()
                    if(line.startsWith("#")) {
                        continue
                    }
                    if(line == "") {
                        continue
                    }

                    var tokens = line.split(":")
                    if(tokens.size != 3) {
                        throw IllegalArgumentException("Token size not 3")
                    }
                    var topic = tokens[0]
                    var partition = tokens[1].toInt()
                    var replicaString = tokens[2]
                    var key = Pair(topic, partition)
                    var replicaList = parseReplicas(replicaString)
                    data[key] = replicaList
                }
            }
            return ExistingPlacement(data)
        }

        private fun parseReplicas(what:String):List<Int> {
            var result = mutableListOf<Int>()
            var tokens = what.split(",")
            for(next in tokens) {
                var next = next.trim()
                if(next == "") {
                    continue
                }
                result.add(next.toInt())
            }
            return result
        }
    }
}