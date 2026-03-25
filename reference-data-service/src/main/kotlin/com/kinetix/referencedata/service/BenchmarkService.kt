package com.kinetix.referencedata.service

import com.kinetix.referencedata.model.Benchmark
import com.kinetix.referencedata.model.BenchmarkConstituent
import com.kinetix.referencedata.model.BenchmarkDailyReturn
import com.kinetix.referencedata.persistence.BenchmarkRepository
import java.time.LocalDate

class BenchmarkService(
    private val benchmarkRepository: BenchmarkRepository,
) {
    suspend fun create(benchmark: Benchmark) {
        benchmarkRepository.save(benchmark)
    }

    suspend fun findAll(): List<Benchmark> =
        benchmarkRepository.findAll()

    suspend fun findById(benchmarkId: String): Benchmark? =
        benchmarkRepository.findById(benchmarkId)

    suspend fun replaceConstituents(benchmarkId: String, constituents: List<BenchmarkConstituent>) {
        benchmarkRepository.replaceConstituents(benchmarkId, constituents)
    }

    suspend fun findConstituents(benchmarkId: String, asOfDate: LocalDate): List<BenchmarkConstituent> =
        benchmarkRepository.findConstituents(benchmarkId, asOfDate)

    suspend fun recordReturn(benchmarkReturn: BenchmarkDailyReturn) {
        benchmarkRepository.saveReturn(benchmarkReturn)
    }

    suspend fun findReturns(benchmarkId: String, from: LocalDate, to: LocalDate): List<BenchmarkDailyReturn> =
        benchmarkRepository.findReturns(benchmarkId, from, to)
}
