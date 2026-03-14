package com.kinetix.risk.schedule

import com.kinetix.risk.persistence.BlobRetentionRepository
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalTime

class ScheduledBlobRetentionJobTest : FunSpec({

    val blobRetentionRepository = mockk<BlobRetentionRepository>()

    beforeEach {
        clearMocks(blobRetentionRepository)
    }

    test("deletes orphaned blobs when run time has passed") {
        coEvery { blobRetentionRepository.deleteOrphanedBlobs(90) } returns 42

        val job = ScheduledBlobRetentionJob(
            blobRetentionRepository = blobRetentionRepository,
            retentionDays = 90,
            runAtTime = LocalTime.of(3, 0),
            nowProvider = { LocalTime.of(3, 30) },
        )

        job.tick()

        coVerify(exactly = 1) { blobRetentionRepository.deleteOrphanedBlobs(90) }
    }

    test("skips cleanup before the scheduled run time") {
        val job = ScheduledBlobRetentionJob(
            blobRetentionRepository = blobRetentionRepository,
            retentionDays = 90,
            runAtTime = LocalTime.of(3, 0),
            nowProvider = { LocalTime.of(2, 59) },
        )

        job.tick()

        coVerify(exactly = 0) { blobRetentionRepository.deleteOrphanedBlobs(any()) }
    }

    test("uses configured retentionDays when calling repository") {
        coEvery { blobRetentionRepository.deleteOrphanedBlobs(30) } returns 5

        val job = ScheduledBlobRetentionJob(
            blobRetentionRepository = blobRetentionRepository,
            retentionDays = 30,
            runAtTime = LocalTime.of(3, 0),
            nowProvider = { LocalTime.of(3, 0) },
        )

        job.tick()

        coVerify(exactly = 1) { blobRetentionRepository.deleteOrphanedBlobs(30) }
    }

    test("handles repository failure without propagating the exception") {
        coEvery { blobRetentionRepository.deleteOrphanedBlobs(any()) } throws RuntimeException("DB connection lost")

        val job = ScheduledBlobRetentionJob(
            blobRetentionRepository = blobRetentionRepository,
            retentionDays = 90,
            runAtTime = LocalTime.of(3, 0),
            nowProvider = { LocalTime.of(4, 0) },
        )

        job.tick()

        coVerify(exactly = 1) { blobRetentionRepository.deleteOrphanedBlobs(90) }
    }

    test("runs cleanup when current time is exactly at the scheduled run time") {
        coEvery { blobRetentionRepository.deleteOrphanedBlobs(90) } returns 0

        val job = ScheduledBlobRetentionJob(
            blobRetentionRepository = blobRetentionRepository,
            retentionDays = 90,
            runAtTime = LocalTime.of(3, 0),
            nowProvider = { LocalTime.of(3, 0) },
        )

        job.tick()

        coVerify(exactly = 1) { blobRetentionRepository.deleteOrphanedBlobs(90) }
    }
})
