package net.wushilin.kafka.tools

import com.fasterxml.jackson.databind.ObjectMapper

data class DataSet(val version:Int, val brokers:List<Broker>) {
    constructor():this(-1, listOf())
    companion object {
        fun parse(file:String):DataSet {
            val om = ObjectMapper()
            java.io.File(file).inputStream().use {
                val dataset = om.readValue(it, DataSet::class.java)
                return dataset
            }
        }
    }
}
