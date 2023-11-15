import java.io.*
data class FreeSpace(val data:MutableMap<Int, Long>) {
    companion object {
        fun parse(file:String):FreeSpace {
            val result = mutableMapOf<Int, Long>()
            File(file).useLines {
                for(line in it) {
                    var line = line.trim()
                    if(line.startsWith("#")) {
                        continue
                    }
                    if(line == "") {
                        continue
                    }

                    val tokens = line.split(",")
                    if(tokens.size != 2) {
                        throw IllegalArgumentException("Invalid line ${line}")
                    }

                    var brokerId = tokens[0].toInt()
                    var sizeInGib = tokens[1].toDouble()
                    var sizeInBytes = (sizeInGib * 1073741824).toLong()
                    result[brokerId] = sizeInBytes
                }
            }
            return FreeSpace(result)
        }
    }

    fun shrinkReplica(topic:String, partition:Int, existingIsr:List<Int>, size:Long):Int {
        var minFree = Long.MAX_VALUE
        var minFreeBroker = -1
        for((broker, free) in data) {
            if(!existingIsr.contains(broker)) {
                continue
            }

            if(free < minFree) {
                minFree = free
                minFreeBroker = broker
            }
        }
        var newFree = minFree + size
        data[minFreeBroker] = newFree
        return minFreeBroker
    }
    fun increaseReplica(topic:String, partition:Int, existingIsr:List<Int>, size:Long):Int {
        var maxFree = -1L
        var maxFreeBroker = -1
        for((broker, free) in data) {
            if(existingIsr.contains(broker)) {
                // avoid selecting on existing ISR
                continue
            }
            if(free > maxFree) {
                maxFree = free
                maxFreeBroker = broker
            }
        }

        var newFree = maxFree - size
        if(newFree <= 300*1073741824) {
            println("Not possible!")
            throw IllegalArgumentException("No broker is free to take $topic:$partition (size $size bytes)")
        }
        data[maxFreeBroker] = newFree
        return maxFreeBroker
    }
}