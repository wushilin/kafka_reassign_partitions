package net.wushilin.kafka.tools

data class Placement(val replicaRules:List<ReplicaSpec>, val observerRules:List<ReplicaSpec>)