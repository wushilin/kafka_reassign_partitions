package net.wushilin.kafka.tools

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class OutputEntry(val topic:String, val partition:Int, val replicas:List<Int>, val observers:List<Int>) {
    @JsonProperty("log_dirs")
    var logDirs = mutableListOf<String>()
    init {
        for(i in replicas) {
            logDirs.add("any")
        }
    }
}