package com.kinetix.risk.client

import com.kinetix.proto.risk.CurvePoint
import com.kinetix.proto.risk.KeyRateDurationRequest
import com.kinetix.proto.risk.RiskCalculationServiceGrpcKt.RiskCalculationServiceCoroutineStub
import com.kinetix.risk.model.InstrumentKrdResult
import com.kinetix.risk.model.KrdBucket
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import com.kinetix.proto.risk.Curve as ProtoCurve

data class KrdCalculationInput(
    val instrumentId: String,
    val faceValue: BigDecimal,
    val couponRate: BigDecimal,
    val couponFrequency: Int,
    val maturityYears: BigDecimal,
    val yieldCurveTenors: List<Pair<Int, BigDecimal>>,  // (days, rate)
)

interface GrpcKrdClient {
    suspend fun calculateKrd(input: KrdCalculationInput): InstrumentKrdResult
}

class GrpcRiskEngineKrdClient(
    private val stub: RiskCalculationServiceCoroutineStub,
    private val deadlineMs: Long = 60_000,
) : GrpcKrdClient {

    override suspend fun calculateKrd(input: KrdCalculationInput): InstrumentKrdResult {
        val curvePoints = input.yieldCurveTenors.map { (days, rate) ->
            CurvePoint.newBuilder()
                .setTenor(days.toString())
                .setValue(rate.toDouble())
                .build()
        }
        val curve = ProtoCurve.newBuilder().addAllPoints(curvePoints).build()

        val request = KeyRateDurationRequest.newBuilder()
            .setInstrumentId(input.instrumentId)
            .setFaceValue(input.faceValue.toPlainString())
            .setCouponRate(input.couponRate.toPlainString())
            .setCouponFrequency(input.couponFrequency)
            .setMaturityYears(input.maturityYears.toPlainString())
            .setYieldCurve(curve)
            .build()

        val response = stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .calculateKeyRateDurations(request)

        val buckets = response.krdBucketsList.map { bucket ->
            KrdBucket(
                tenorLabel = bucket.tenorLabel,
                tenorDays = bucket.tenorDays,
                dv01 = BigDecimal(bucket.dv01),
            )
        }

        return InstrumentKrdResult(
            instrumentId = response.instrumentId,
            krdBuckets = buckets,
            totalDv01 = BigDecimal(response.totalDv01),
        )
    }
}
