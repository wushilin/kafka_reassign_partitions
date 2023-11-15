package net.wushilin.kafka.tools


data class DescribeResult(val data:Map<Pair<String, Int>, List<Int>>) {
    companion object {
        fun parse(input:String):DescribeResult {
            val data = mutableMapOf<Pair<String, Int>, List<Int>>()
            val pattern = Regex("^\\s+Topic:\\s*(\\S+)\\s*Partition:\\s*(\\d+).*Replicas:\\s*([1-9,]+)\\s+.*$")
            java.io.File(input).useLines { lines ->
                for(line in lines) {
                    if(!line.matches(pattern)) {
                        continue
                    }
                    var matcher = pattern.matchEntire(line)!!
                    var tokens = matcher.groupValues
                    val topic = tokens[1]
                    val partition = tokens[2].toInt()
                    val replicas = tokens[3].split(",").map{ it.trim() }.map{ it.toInt() }
                    data[Pair(topic, partition)] = replicas
                }
            }
            return DescribeResult(data)
        }
    }
}