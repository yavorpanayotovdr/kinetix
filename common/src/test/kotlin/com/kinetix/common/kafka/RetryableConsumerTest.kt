package com.kinetix.common.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.concurrent.CompletableFuture

class RetryableConsumerTest : FunSpec({

    test("should succeed on first attempt without retry") {
        val retryable = RetryableConsumer(topic = "test.topic", maxRetries = 3)

        var callCount = 0
        val result = retryable.process("key-1", "value-1") {
            callCount++
            "success"
        }

        result shouldBe "success"
        callCount shouldBe 1
    }

    test("should retry up to maxRetries times on failure") {
        val retryable = RetryableConsumer(topic = "test.topic", maxRetries = 3, baseDelayMs = 1)

        var callCount = 0
        shouldThrow<RuntimeException> {
            retryable.process("key-1", "value-1") {
                callCount++
                throw RuntimeException("always fails")
            }
        }

        callCount shouldBe 4 // 1 initial + 3 retries
    }

    test("should succeed after transient failures within retry limit") {
        val retryable = RetryableConsumer(topic = "test.topic", maxRetries = 3, baseDelayMs = 1)

        var callCount = 0
        val result = retryable.process("key-1", "value-1") {
            callCount++
            if (callCount < 3) throw RuntimeException("transient failure")
            "recovered"
        }

        result shouldBe "recovered"
        callCount shouldBe 3
    }

    test("should send to DLQ after max retries exhausted") {
        val dlqProducer = mockk<KafkaProducer<String, String>>()
        val recordSlot = slot<ProducerRecord<String, String>>()
        every { dlqProducer.send(capture(recordSlot)) } returns CompletableFuture.completedFuture(null)

        val retryable = RetryableConsumer(
            topic = "orders.topic",
            maxRetries = 2,
            baseDelayMs = 1,
            dlqProducer = dlqProducer,
        )

        shouldThrow<RuntimeException> {
            retryable.process("order-key", "order-value") {
                throw RuntimeException("permanent failure")
            }
        }

        verify(exactly = 1) { dlqProducer.send(any()) }
        recordSlot.captured.topic() shouldBe "orders.topic.dlq"
        recordSlot.captured.key() shouldBe "order-key"
        recordSlot.captured.value() shouldBe "order-value"
    }

    test("should use exponential backoff between retries") {
        val retryable = RetryableConsumer(topic = "test.topic", maxRetries = 3, baseDelayMs = 50)

        val timestamps = mutableListOf<Long>()
        shouldThrow<RuntimeException> {
            retryable.process("key-1", "value-1") {
                timestamps.add(System.currentTimeMillis())
                throw RuntimeException("fail")
            }
        }

        timestamps.size shouldBe 4 // 1 initial + 3 retries

        val delay1 = timestamps[1] - timestamps[0] // should be ~50ms (baseDelay * 2^0)
        val delay2 = timestamps[2] - timestamps[1] // should be ~100ms (baseDelay * 2^1)
        val delay3 = timestamps[3] - timestamps[2] // should be ~200ms (baseDelay * 2^2)

        delay1 shouldBeGreaterThanOrEqual 40
        delay2 shouldBeGreaterThanOrEqual 80
        delay3 shouldBeGreaterThanOrEqual 160

        // Sanity upper bounds to avoid flakiness
        delay1 shouldBeLessThan 500
        delay2 shouldBeLessThan 500
        delay3 shouldBeLessThan 500
    }

    test("should construct DLQ topic name as original topic plus .dlq suffix") {
        val dlqProducer = mockk<KafkaProducer<String, String>>()
        val recordSlot = slot<ProducerRecord<String, String>>()
        every { dlqProducer.send(capture(recordSlot)) } returns CompletableFuture.completedFuture(null)

        val retryable = RetryableConsumer(
            topic = "price.updates",
            maxRetries = 1,
            baseDelayMs = 1,
            dlqProducer = dlqProducer,
        )

        shouldThrow<RuntimeException> {
            retryable.process("key", "value") {
                throw RuntimeException("fail")
            }
        }

        recordSlot.captured.topic() shouldBe "price.updates.dlq"
    }

    test("should not send to DLQ when dlqProducer is null") {
        val retryable = RetryableConsumer(
            topic = "test.topic",
            maxRetries = 1,
            baseDelayMs = 1,
            dlqProducer = null,
        )

        shouldThrow<RuntimeException> {
            retryable.process("key", "value") {
                throw RuntimeException("fail")
            }
        }
        // No DLQ producer, so no exception from trying to send -- just the original exception propagated
    }
})
