package com.kinetix.risk.service

import com.kinetix.risk.model.ChartBucketRow
import com.kinetix.risk.model.JobPhaseName
import com.kinetix.risk.model.RunLabel
import com.kinetix.risk.model.ValuationJob
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface ValuationJobRecorder {
    suspend fun save(job: ValuationJob)
    suspend fun update(job: ValuationJob)
    suspend fun updateCurrentPhase(jobId: UUID, phase: JobPhaseName)
    suspend fun findByBookId(
        portfolioId: String,
        limit: Int = 50,
        offset: Int = 0,
        from: Instant? = null,
        to: Instant? = null,
        valuationDate: LocalDate? = null,
        runLabel: RunLabel? = null,
    ): List<ValuationJob>
    suspend fun countByBookId(
        portfolioId: String,
        from: Instant? = null,
        to: Instant? = null,
        valuationDate: LocalDate? = null,
        runLabel: RunLabel? = null,
    ): Long
    suspend fun findByJobId(jobId: UUID): ValuationJob?
    suspend fun findDistinctBookIds(): List<String>
    suspend fun findLatestCompletedByDate(portfolioId: String, valuationDate: LocalDate): ValuationJob?
    suspend fun findLatestCompleted(portfolioId: String): ValuationJob?
    suspend fun findLatestCompletedBeforeDate(portfolioId: String, beforeDate: LocalDate): ValuationJob?
    suspend fun findOfficialEodByDate(portfolioId: String, valuationDate: LocalDate): ValuationJob?
    suspend fun findOfficialEodRange(portfolioId: String, from: LocalDate, to: LocalDate): List<ValuationJob>
    suspend fun promoteToOfficialEod(jobId: UUID, promotedBy: String, promotedAt: Instant): ValuationJob
    suspend fun demoteOfficialEod(jobId: UUID): ValuationJob
    suspend fun supersedeOfficialEod(jobId: UUID): ValuationJob
    suspend fun findChartData(portfolioId: String, from: Instant, to: Instant, bucketInterval: String): List<ChartBucketRow>
    suspend fun resetOrphanedRunningJobs(): Int
}
