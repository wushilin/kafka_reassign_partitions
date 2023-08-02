package net.wushilin.kafka.tools

import net.wushilin.props.EnvAwareProperties
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.DescribeTopicsOptions
import org.apache.kafka.clients.admin.KafkaAdminClient
import org.apache.kafka.clients.admin.ListTopicsOptions
import java.util.*

class KafkaUtil {
    companion object {
        fun connectToKafka(clientFile:String): AdminClient {
            val prop = EnvAwareProperties.fromPath(clientFile)
            return KafkaAdminClient.create(prop)
        }

        fun getTopicList(adminClient:AdminClient): TreeMap<String, Int> {
            val option = ListTopicsOptions()
            option.listInternal(true)
            option.timeoutMs(60000)
            val result = adminClient.listTopics(option).names().get()
            val largerResult = adminClient.describeTopics(result).allTopicNames().get()
            val resultMap = TreeMap<String, Int>()
            for((topicName, meta) in largerResult) {
                val partitions = meta.partitions().size
                resultMap[topicName] = partitions
            }
            return resultMap
        }


        fun getClusterInfo(adminClient:AdminClient): Cluster {
            val nodes = adminClient.describeCluster().nodes().get()
            val nodeMap = mutableMapOf<String, MutableList<Int>>()
            for(nextNode in nodes) {
                var rack = nextNode.rack()
                if(rack == null) {
                    rack = "@NONE"
                }
                val brokerId = nextNode.id()
                if(nodeMap[rack] == null) {
                    val newList = mutableListOf<Int>()
                    newList.add(brokerId)
                    nodeMap[rack] = newList
                } else {
                    nodeMap[rack]?.add(brokerId)
                }
            }
            return Cluster(nodeMap)
        }

        fun getTopicPartitions(adminClient:AdminClient, topics:Set<String>):Map<String, List<Int>> {
            val options = DescribeTopicsOptions()
            options.timeoutMs(60000)
            options.includeAuthorizedOperations(false)
            val topicMeta = adminClient.describeTopics(topics, options).allTopicNames().get()
            val result = mutableMapOf<String, List<Int>>()
            for(entry in topicMeta) {
                val topicName = entry.key
                val topicMetaValues = entry.value
                val data = topicMetaValues.partitions().map {
                        i -> i.partition()
                }
                result[topicName] = data
            }
            return result
        }
    }
}