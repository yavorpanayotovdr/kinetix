package com.kinetix.regulatory.historical

interface HistoricalScenarioRepository {
    suspend fun savePeriod(period: HistoricalScenarioPeriod)
    suspend fun saveReturns(returns: List<HistoricalScenarioReturn>)
    suspend fun findAllPeriods(): List<HistoricalScenarioPeriod>
    suspend fun findPeriodById(periodId: String): HistoricalScenarioPeriod?
    suspend fun findReturns(periodId: String, instrumentIds: List<String>): List<HistoricalScenarioReturn>
}
