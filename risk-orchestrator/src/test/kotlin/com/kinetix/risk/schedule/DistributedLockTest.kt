package com.kinetix.risk.schedule

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.*
import kotlinx.coroutines.runBlocking

class DistributedLockTest : FunSpec({

    val commands = mockk<RedisCommands<String, String>>()
    val connection = mockk<StatefulRedisConnection<String, String>>()

    beforeEach {
        clearMocks(commands, connection)
        every { connection.sync() } returns commands
    }

    test("executes action when lock is acquired") {
        every { commands.set(any(), any(), any<SetArgs>()) } returns "OK"
        every { commands.del(any<String>()) } returns 1

        val lock = RedisDistributedLock(connection)
        var executed = false
        runBlocking {
            lock.withLock("test-lock", ttlSeconds = 90) {
                executed = true
            }
        }

        executed shouldBe true
        verify { commands.set("lock:test-lock", any(), any<SetArgs>()) }
        verify { commands.del("lock:test-lock") }
    }

    test("skips action when lock is not acquired") {
        every { commands.set(any(), any(), any<SetArgs>()) } returns null

        val lock = RedisDistributedLock(connection)
        var executed = false
        runBlocking {
            lock.withLock("test-lock", ttlSeconds = 90) {
                executed = true
            }
        }

        executed shouldBe false
        verify(exactly = 0) { commands.del(any<String>()) }
    }

    test("releases lock even when action throws") {
        every { commands.set(any(), any(), any<SetArgs>()) } returns "OK"
        every { commands.del(any<String>()) } returns 1

        val lock = RedisDistributedLock(connection)
        runBlocking {
            try {
                lock.withLock("test-lock", ttlSeconds = 90) {
                    throw RuntimeException("boom")
                }
            } catch (_: RuntimeException) { }
        }

        verify { commands.del("lock:test-lock") }
    }

    test("NoOpDistributedLock always executes action") {
        val lock = NoOpDistributedLock()
        var executed = false
        runBlocking {
            lock.withLock("any-key", ttlSeconds = 90) {
                executed = true
            }
        }
        executed shouldBe true
    }
})
