package net.wushilin.kafka.tools

data class Broker(val broker:Int, val logDirs:List<LogDir>){
    constructor():this(-1, listOf())
}
