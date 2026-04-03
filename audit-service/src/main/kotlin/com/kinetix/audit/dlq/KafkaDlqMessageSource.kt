package com.kinetix.audit.dlq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

/**
 * Reads all current messages from a DLQ topic once and returns them in offset order.
 *
 * Uses a fresh consumer group on each call (timestamped) so it always starts from
 * the beginning of the topic without interfering with other consumers.
 */
class KafkaDlqMessageSource(
    private val bootstrapServers: String,
    private val dlqTopic: String,
) {
    private val logger = LoggerFactory.getLogger(KafkaDlqMessageSource::class.java)

    suspend operator fun invoke(): List<DlqMessage> = withContext(Dispatchers.IO) {
        val groupId = "audit-dlq-replay-${System.currentTimeMillis()}"
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }

        KafkaConsumer<String, String>(props).use { consumer ->
            val partitions = consumer.partitionsFor(dlqTopic)
                .map { TopicPartition(dlqTopic, it.partition()) }

            consumer.assign(partitions)
            consumer.seekToBeginning(partitions)

            val endOffsets = consumer.endOffsets(partitions)
            val hasMessages = endOffsets.values.any { it > 0 }

            if (!hasMessages) {
                logger.info("DLQ topic {} is empty, nothing to replay", dlqTopic)
                return@withContext emptyList()
            }

            val messages = mutableListOf<DlqMessage>()
            var allDone = false

            while (!allDone) {
                val records = consumer.poll(Duration.ofSeconds(2))
                for (record in records) {
                    messages.add(DlqMessage(key = record.key() ?: "", value = record.value()))
                }

                // Stop when we've read up to the end offsets that were captured before we started.
                allDone = partitions.all { tp ->
                    val endOffset = endOffsets[tp] ?: 0L
                    consumer.position(tp) >= endOffset
                }
            }

            logger.info("Read {} messages from DLQ topic {}", messages.size, dlqTopic)
            messages
        }
    }
}
