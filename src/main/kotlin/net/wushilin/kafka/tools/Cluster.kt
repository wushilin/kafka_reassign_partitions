package net.wushilin.kafka.tools

import java.lang.IllegalArgumentException
import java.util.*

data class Cluster(val racks:Map<String, List<Int>>) {
    var random:Random = Random();
    fun selectReplica(rack:String, count:Int, exclusions:List<Int> = mutableListOf<Int>()):List<Int> {
        val excludeSet = exclusions.toSet();
        if(count < 0) {
            throw IllegalArgumentException("Asked for negative replica count: $count")
        }
        val result = mutableListOf<Int>()
        if(count == 0) {
            return result
        }

        val options = mutableListOf<Int>()
        val optionsRaw =
                racks[rack] ?: throw IllegalArgumentException("Required $count replicas in rack $rack but none found!")
        options.addAll(optionsRaw)
        options.removeAll(excludeSet)

        result.addAll(options)
        if(result.size < count) {
            throw IllegalArgumentException("Required $count replicas in rack $rack but only ${result.size} found")
        }
        while(result.size > count) {
            val removeIndex = random.nextInt(result.size)
            result.removeAt(removeIndex)
        }
        result.shuffle()
        return result
    }
}