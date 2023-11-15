package net.wushilin.kafka.tools

class Common {
    companion object {
        fun <T> splitList(input:List<T>, count:Int):List<List<T>> {
            val result = mutableListOf<List<T>>()
            var next = mutableListOf<T>()
            for(ele in input) {
                next.add(ele)
                if(next.size >= count) {
                    result.add(next)
                    next = mutableListOf()
                }
            }
            if(next.isNotEmpty()) {
                result.add(next)
            }
            return result
        }
        fun <K, V> splitMap(input:Map<K, V>, count:Int):List<Map<K, V>> {
            val result = mutableListOf<Map<K, V>>()
            var next = mutableMapOf<K, V>()
            for((k, v) in input) {
                next[k] = v
                if(next.size >= count) {
                    result.add(next)
                    next = mutableMapOf()
                }
            }
            if(next.isNotEmpty()) {
                result.add(next)
            }
            return result
        }
    }
}