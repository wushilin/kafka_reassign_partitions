package net.wushilin.kafka.tools

import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.IllegalArgumentException

class Parser {
    companion object {
        val objectMapper = ObjectMapper()

        fun parsePlacement(file:String): Placement {
            java.io.File(file).inputStream().use {
                val mapObject = objectMapper.readValue(it, Map::class.java)
                val replicaListSpecs = mutableListOf<ReplicaSpec>()
                val observerListSpecs = mutableListOf<ReplicaSpec>()
                var versionStr = mapObject["version"]?:throw IllegalArgumentException("`version` in placement file can't be null")
                var versionInt = versionStr as Int
                if (versionInt != 2) {
                    throw IllegalArgumentException("Unsupported version: `version` in placement file must be 2")
                }

                val replicasObj = mapObject["replicas"]?:throw IllegalArgumentException("`replicas` in placement file can't be null")
                @Suppress("UNCHECKED_CAST")
                val replicas = replicasObj as? List<Map<String, Any>>?:throw IllegalArgumentException("Invalid definition for replicas")
                for(next in replicas) {
                    val countObj = next["count"]
                        ?: throw IllegalArgumentException("`count` must present in each placement constraints")
                    val countInt = countObj as Int
                    if(countInt < 0) {
                        throw IllegalArgumentException("`count` can't be negative: $countInt")
                    }
                    val rackObject = next["constraints"]?:throw IllegalArgumentException("`constraints` must present in each placement constraints")
                    @Suppress("UNCHECKED_CAST")
                    val rackMap = rackObject as? Map<String, String>?:throw IllegalArgumentException("Invalid definition for replicas")
                    val rack = rackMap["rack"]?:throw IllegalArgumentException("`constraints->rack` must present in each placement constraints")
                    val spec = ReplicaSpec(countInt, rack)
                    replicaListSpecs.add(spec)
                }

                val observerObj = mapObject["observers"] // observers are optional
                if(observerObj != null) {
                    @Suppress("UNCHECKED_CAST")
                    val observers = observerObj as? List<Map<String, Any>>?:throw IllegalArgumentException("Invalid definition for observers")
                    for (next in observers) {
                        val countObj = next["count"]
                            ?: throw IllegalArgumentException("`count` must present in each placement constraints")
                        val countInt = countObj as Int
                        if (countInt < 0) {
                            throw IllegalArgumentException("`count` can't be negative: $countInt")
                        }
                        val rackObject = next["constraints"]
                            ?: throw IllegalArgumentException("`constraints` must present in each placement constraints")
                        @Suppress("UNCHECKED_CAST")
                        val rackMap = rackObject as Map<String, String>
                        val rack = rackMap["rack"]
                            ?: throw IllegalArgumentException("`constraints->rack` must present in each placement constraints")
                        val spec = ReplicaSpec(countInt, rack)
                        observerListSpecs.add(spec)
                    }
                }
                return Placement(replicaListSpecs, observerListSpecs)
            }
        }
    }

}