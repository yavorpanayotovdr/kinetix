package com.kinetix.risk.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.model.*
import com.kinetix.risk.service.ValuationJobRecorder
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ExposedValuationJobRecorder(private val db: Database? = null) : ValuationJobRecorder {

    override suspend fun save(job: ValuationJob): Unit = newSuspendedTransaction(db = db) {
        ValuationJobsTable.insert {
            it[jobId] = job.jobId
            it[portfolioId] = job.portfolioId
            it[triggerType] = job.triggerType.name
            it[status] = job.status.name
            it[startedAt] = OffsetDateTime.ofInstant(job.startedAt, ZoneOffset.UTC)
            it[completedAt] = job.completedAt?.let { ts -> OffsetDateTime.ofInstant(ts, ZoneOffset.UTC) }
            it[durationMs] = job.durationMs
            it[calculationType] = job.calculationType
            it[confidenceLevel] = job.confidenceLevel
            it[varValue] = job.varValue
            it[expectedShortfall] = job.expectedShortfall
            it[pvValue] = job.pvValue
            it[delta] = job.delta
            it[gamma] = job.gamma
            it[vega] = job.vega
            it[theta] = job.theta
            it[rho] = job.rho
            it[positionRisk] = job.positionRiskSnapshot.takeIf { s -> s.isNotEmpty() }?.map { pr -> pr.toJson() }
            it[componentBreakdown] = job.componentBreakdownSnapshot.takeIf { s -> s.isNotEmpty() }?.map { cb -> cb.toJson() }
            it[computedOutputs] = job.computedOutputsSnapshot.takeIf { s -> s.isNotEmpty() }?.map { o -> o.name }
            it[assetClassGreeks] = job.assetClassGreeksSnapshot.takeIf { s -> s.isNotEmpty() }?.map { g -> g.toJson() }
            it[steps] = job.steps.map { step -> step.toJson() }
            it[error] = job.error
        }
    }

    override suspend fun update(job: ValuationJob): Unit = newSuspendedTransaction(db = db) {
        ValuationJobsTable.update({ ValuationJobsTable.jobId eq job.jobId }) {
            it[status] = job.status.name
            it[completedAt] = job.completedAt?.let { ts -> OffsetDateTime.ofInstant(ts, ZoneOffset.UTC) }
            it[durationMs] = job.durationMs
            it[calculationType] = job.calculationType
            it[confidenceLevel] = job.confidenceLevel
            it[varValue] = job.varValue
            it[expectedShortfall] = job.expectedShortfall
            it[pvValue] = job.pvValue
            it[delta] = job.delta
            it[gamma] = job.gamma
            it[vega] = job.vega
            it[theta] = job.theta
            it[rho] = job.rho
            it[positionRisk] = job.positionRiskSnapshot.takeIf { s -> s.isNotEmpty() }?.map { pr -> pr.toJson() }
            it[componentBreakdown] = job.componentBreakdownSnapshot.takeIf { s -> s.isNotEmpty() }?.map { cb -> cb.toJson() }
            it[computedOutputs] = job.computedOutputsSnapshot.takeIf { s -> s.isNotEmpty() }?.map { o -> o.name }
            it[assetClassGreeks] = job.assetClassGreeksSnapshot.takeIf { s -> s.isNotEmpty() }?.map { g -> g.toJson() }
            it[steps] = job.steps.map { step -> step.toJson() }
            it[error] = job.error
        }
    }

    override suspend fun findByPortfolioId(
        portfolioId: String,
        limit: Int,
        offset: Int,
        from: Instant?,
        to: Instant?,
    ): List<ValuationJob> = newSuspendedTransaction(db = db) {
        ValuationJobsTable
            .selectAll()
            .where {
                var condition = ValuationJobsTable.portfolioId eq portfolioId
                if (from != null) {
                    condition = condition and (ValuationJobsTable.startedAt greaterEq OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                }
                if (to != null) {
                    condition = condition and (ValuationJobsTable.startedAt lessEq OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                }
                condition
            }
            .orderBy(ValuationJobsTable.startedAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toValuationJob() }
    }

    override suspend fun countByPortfolioId(
        portfolioId: String,
        from: Instant?,
        to: Instant?,
    ): Long = newSuspendedTransaction(db = db) {
        ValuationJobsTable
            .selectAll()
            .where {
                var condition = ValuationJobsTable.portfolioId eq portfolioId
                if (from != null) {
                    condition = condition and (ValuationJobsTable.startedAt greaterEq OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                }
                if (to != null) {
                    condition = condition and (ValuationJobsTable.startedAt lessEq OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                }
                condition
            }
            .count()
    }

    override suspend fun findByJobId(jobId: UUID): ValuationJob? = newSuspendedTransaction(db = db) {
        ValuationJobsTable
            .selectAll()
            .where { ValuationJobsTable.jobId eq jobId }
            .firstOrNull()
            ?.toValuationJob()
    }

    override suspend fun findDistinctPortfolioIds(): List<String> = newSuspendedTransaction(db = db) {
        ValuationJobsTable
            .select(ValuationJobsTable.portfolioId)
            .withDistinct()
            .map { it[ValuationJobsTable.portfolioId] }
    }

    override suspend fun findLatestCompleted(portfolioId: String): ValuationJob? = newSuspendedTransaction(db = db) {
        ValuationJobsTable
            .selectAll()
            .where {
                (ValuationJobsTable.portfolioId eq portfolioId) and
                    (ValuationJobsTable.status eq "COMPLETED")
            }
            .orderBy(ValuationJobsTable.startedAt, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toValuationJob()
    }

    private fun JobStep.toJson(): JobStepJson = JobStepJson(
        name = name.name,
        status = status.name,
        startedAt = startedAt.toString(),
        completedAt = completedAt?.toString(),
        durationMs = durationMs,
        details = details.mapValues { (_, v) -> v?.toString() ?: "" },
        error = error,
    )

    private fun parseStepName(raw: String): JobStepName = when (raw) {
        "CALCULATE_VAR" -> JobStepName.VALUATION
        else -> JobStepName.valueOf(raw)
    }

    private fun JobStepJson.toDomain(): JobStep = JobStep(
        name = parseStepName(name),
        status = RunStatus.valueOf(status),
        startedAt = Instant.parse(startedAt),
        completedAt = completedAt?.let { Instant.parse(it) },
        durationMs = durationMs,
        details = details,
        error = error,
    )

    private fun PositionRisk.toJson(): PositionRiskJson = PositionRiskJson(
        instrumentId = instrumentId.value,
        assetClass = assetClass.name,
        marketValue = marketValue.toPlainString(),
        delta = delta,
        gamma = gamma,
        vega = vega,
        varContribution = varContribution.toPlainString(),
        esContribution = esContribution.toPlainString(),
        percentageOfTotal = percentageOfTotal.toPlainString(),
    )

    private fun PositionRiskJson.toDomain(): PositionRisk = PositionRisk(
        instrumentId = InstrumentId(instrumentId),
        assetClass = AssetClass.valueOf(assetClass),
        marketValue = BigDecimal(marketValue),
        delta = delta,
        gamma = gamma,
        vega = vega,
        varContribution = BigDecimal(varContribution),
        esContribution = BigDecimal(esContribution),
        percentageOfTotal = BigDecimal(percentageOfTotal),
    )

    private fun ComponentBreakdown.toJson(): ComponentBreakdownJson = ComponentBreakdownJson(
        assetClass = assetClass.name,
        varContribution = varContribution,
        percentageOfTotal = percentageOfTotal,
    )

    private fun ComponentBreakdownJson.toDomain(): ComponentBreakdown = ComponentBreakdown(
        assetClass = AssetClass.valueOf(assetClass),
        varContribution = varContribution,
        percentageOfTotal = percentageOfTotal,
    )

    private fun GreekValues.toJson(): AssetClassGreeksJson = AssetClassGreeksJson(
        assetClass = assetClass.name,
        delta = delta,
        gamma = gamma,
        vega = vega,
    )

    private fun AssetClassGreeksJson.toDomain(): GreekValues = GreekValues(
        assetClass = AssetClass.valueOf(assetClass),
        delta = delta,
        gamma = gamma,
        vega = vega,
    )

    private fun ResultRow.toValuationJob(): ValuationJob = ValuationJob(
        jobId = this[ValuationJobsTable.jobId],
        portfolioId = this[ValuationJobsTable.portfolioId],
        triggerType = TriggerType.valueOf(this[ValuationJobsTable.triggerType]),
        status = RunStatus.valueOf(this[ValuationJobsTable.status]),
        startedAt = this[ValuationJobsTable.startedAt].toInstant(),
        completedAt = this[ValuationJobsTable.completedAt]?.toInstant(),
        durationMs = this[ValuationJobsTable.durationMs],
        calculationType = this[ValuationJobsTable.calculationType],
        confidenceLevel = this[ValuationJobsTable.confidenceLevel],
        varValue = this[ValuationJobsTable.varValue],
        expectedShortfall = this[ValuationJobsTable.expectedShortfall],
        pvValue = this[ValuationJobsTable.pvValue],
        delta = this[ValuationJobsTable.delta],
        gamma = this[ValuationJobsTable.gamma],
        vega = this[ValuationJobsTable.vega],
        theta = this[ValuationJobsTable.theta],
        rho = this[ValuationJobsTable.rho],
        positionRiskSnapshot = this[ValuationJobsTable.positionRisk]?.map { it.toDomain() } ?: emptyList(),
        componentBreakdownSnapshot = this[ValuationJobsTable.componentBreakdown]?.map { it.toDomain() } ?: emptyList(),
        computedOutputsSnapshot = this[ValuationJobsTable.computedOutputs]?.map { ValuationOutput.valueOf(it) }?.toSet() ?: emptySet(),
        assetClassGreeksSnapshot = this[ValuationJobsTable.assetClassGreeks]?.map { it.toDomain() } ?: emptyList(),
        steps = this[ValuationJobsTable.steps].map { it.toDomain() },
        error = this[ValuationJobsTable.error],
    )
}
