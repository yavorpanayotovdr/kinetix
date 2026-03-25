package com.kinetix.referencedata.persistence

import com.kinetix.referencedata.model.Benchmark
import com.kinetix.referencedata.model.BenchmarkConstituent
import com.kinetix.referencedata.model.BenchmarkDailyReturn
import java.time.LocalDate

interface BenchmarkRepository {
    suspend fun save(benchmark: Benchmark)
    suspend fun findById(benchmarkId: String): Benchmark?
    suspend fun findAll(): List<Benchmark>
    suspend fun replaceConstituents(benchmarkId: String, constituents: List<BenchmarkConstituent>)
    suspend fun findConstituents(benchmarkId: String, asOfDate: LocalDate): List<BenchmarkConstituent>
    suspend fun saveReturn(benchmarkReturn: BenchmarkDailyReturn)
    suspend fun findReturns(benchmarkId: String, from: LocalDate, to: LocalDate): List<BenchmarkDailyReturn>
}
