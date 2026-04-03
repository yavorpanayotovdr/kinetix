package com.kinetix.risk.seed

import com.kinetix.risk.model.RunStatus
import com.kinetix.risk.model.TriggerType
import com.kinetix.risk.model.ValuationJob
import com.kinetix.risk.service.ValuationJobRecorder
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.sin

class DevDataSeeder(
    private val jobRecorder: ValuationJobRecorder,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = jobRecorder.findByTriggeredBy("SEED")
        if (existing.isNotEmpty()) {
            log.info("VaR timeline seed data already present ({} rows), skipping", existing.size)
            return
        }

        val jobs = buildSeedJobs()
        log.info("Seeding {} VaR timeline entries across {} books", jobs.size, BOOK_VAR_PROFILES.size)

        for (job in jobs) {
            jobRecorder.save(job)
        }

        log.info("VaR timeline seeding complete")
    }

    companion object {
        private data class VaRProfile(
            val bookId: String,
            val baseVaR: Double,
            val volatilityPct: Double,
            val baseES: Double,
        )

        private val BOOK_VAR_PROFILES = listOf(
            VaRProfile("equity-growth", 2_450_000.0, 0.08, 2_980_000.0),
            VaRProfile("tech-momentum", 2_680_000.0, 0.10, 3_260_000.0),
            VaRProfile("emerging-markets", 1_950_000.0, 0.12, 2_370_000.0),
            VaRProfile("fixed-income", 930_000.0, 0.04, 1_134_000.0),
            VaRProfile("multi-asset", 3_720_000.0, 0.07, 4_524_000.0),
            VaRProfile("macro-hedge", 2_220_000.0, 0.09, 2_700_000.0),
            VaRProfile("balanced-income", 1_160_000.0, 0.05, 1_408_000.0),
            VaRProfile("derivatives-book", 3_375_000.0, 0.11, 4_110_000.0),
        )

        private const val HISTORY_DAYS = 30
        private const val INTRADAY_HOURS = 8

        private fun deterministicJitter(bookIndex: Int, dayIndex: Int, hourIndex: Int): Double {
            val seed = bookIndex * 1000.0 + dayIndex * 10.0 + hourIndex
            return sin(seed * 0.7) * 0.5 + sin(seed * 1.3) * 0.3 + sin(seed * 2.1) * 0.2
        }

        fun buildSeedJobs(): List<ValuationJob> {
            val now = Instant.now()
            val today = LocalDate.now(ZoneOffset.UTC)
            val jobs = mutableListOf<ValuationJob>()

            BOOK_VAR_PROFILES.forEachIndexed { bookIdx, profile ->
                // Daily entries for the past 30 days (one per day at 09:30 UTC)
                for (dayOffset in HISTORY_DAYS downTo 2) {
                    val date = today.minusDays(dayOffset.toLong())
                    val startedAt = date.atTime(9, 30).toInstant(ZoneOffset.UTC)
                    val jitter = deterministicJitter(bookIdx, dayOffset, 0)
                    val varValue = profile.baseVaR * (1.0 + jitter * profile.volatilityPct)
                    val esValue = profile.baseES * (1.0 + jitter * profile.volatilityPct)

                    jobs += ValuationJob(
                        jobId = UUID.nameUUIDFromBytes("seed-var-${profile.bookId}-d$dayOffset".toByteArray()),
                        bookId = profile.bookId,
                        triggerType = TriggerType.SCHEDULED,
                        status = RunStatus.COMPLETED,
                        startedAt = startedAt,
                        valuationDate = date,
                        completedAt = startedAt.plusMillis(1500),
                        durationMs = 1500,
                        calculationType = "PARAMETRIC",
                        confidenceLevel = "CL_95",
                        varValue = varValue,
                        expectedShortfall = esValue,
                        triggeredBy = "SEED",
                    )
                }

                // Intraday entries for today (hourly from market open)
                for (hour in 0 until INTRADAY_HOURS) {
                    val startedAt = now.minus((INTRADAY_HOURS - hour).toLong(), ChronoUnit.HOURS)
                    val jitter = deterministicJitter(bookIdx, 0, hour)
                    val varValue = profile.baseVaR * (1.0 + jitter * profile.volatilityPct)
                    val esValue = profile.baseES * (1.0 + jitter * profile.volatilityPct)

                    jobs += ValuationJob(
                        jobId = UUID.nameUUIDFromBytes("seed-var-${profile.bookId}-h$hour".toByteArray()),
                        bookId = profile.bookId,
                        triggerType = TriggerType.SCHEDULED,
                        status = RunStatus.COMPLETED,
                        startedAt = startedAt,
                        valuationDate = today,
                        completedAt = startedAt.plusMillis(1800),
                        durationMs = 1800,
                        calculationType = "PARAMETRIC",
                        confidenceLevel = "CL_95",
                        varValue = varValue,
                        expectedShortfall = esValue,
                        triggeredBy = "SEED",
                    )
                }
            }

            return jobs
        }
    }
}
