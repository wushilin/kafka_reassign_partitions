package net.wushilin.kafka.tools

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class Partition(
    @JsonProperty("partition") val partition:String,
    @JsonProperty("size") val size:Long,
    @JsonProperty("offsetLag") val offsetLag: Long,
    @JsonProperty("isFuture")  val isFuture:Boolean) {
    constructor():this("", -1, -1, false)
}
