package net.wushilin.kafka.tools

data class LogDir(val logDir:String, val error:String, val partitions: List<Partition>){
    constructor():this("", "", listOf())
}
