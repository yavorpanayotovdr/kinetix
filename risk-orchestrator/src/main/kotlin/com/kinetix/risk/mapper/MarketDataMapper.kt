package com.kinetix.risk.mapper

import com.google.protobuf.Timestamp
import com.kinetix.proto.risk.MarketDataType
import com.kinetix.risk.model.CurveMarketData
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.MatrixMarketData
import com.kinetix.risk.model.ScalarMarketData
import com.kinetix.risk.model.TimeSeriesMarketData
import com.kinetix.proto.risk.Curve as ProtoCurve
import com.kinetix.proto.risk.CurvePoint as ProtoCurvePoint
import com.kinetix.proto.risk.MarketDataValue as ProtoMarketDataValue
import com.kinetix.proto.risk.Matrix as ProtoMatrix
import com.kinetix.proto.risk.TimeSeries as ProtoTimeSeries
import com.kinetix.proto.risk.TimeSeriesPoint as ProtoTimeSeriesPoint

private val DATA_TYPE_MAP = mapOf(
    "SPOT_PRICE" to MarketDataType.SPOT_PRICE,
    "HISTORICAL_PRICES" to MarketDataType.HISTORICAL_PRICES,
    "VOLATILITY_SURFACE" to MarketDataType.VOLATILITY_SURFACE,
    "YIELD_CURVE" to MarketDataType.YIELD_CURVE,
    "RISK_FREE_RATE" to MarketDataType.RISK_FREE_RATE,
    "DIVIDEND_YIELD" to MarketDataType.DIVIDEND_YIELD,
    "CREDIT_SPREAD" to MarketDataType.CREDIT_SPREAD,
    "FORWARD_CURVE" to MarketDataType.FORWARD_CURVE,
    "CORRELATION_MATRIX" to MarketDataType.CORRELATION_MATRIX,
)

fun MarketDataValue.toProto(): ProtoMarketDataValue {
    val builder = ProtoMarketDataValue.newBuilder()
        .setDataType(DATA_TYPE_MAP[dataType] ?: MarketDataType.MARKET_DATA_TYPE_UNSPECIFIED)
        .setInstrumentId(instrumentId)
        .setAssetClass(assetClass)

    when (this) {
        is ScalarMarketData -> builder.setScalar(value)
        is TimeSeriesMarketData -> builder.setTimeSeries(
            ProtoTimeSeries.newBuilder().addAllPoints(
                points.map { pt ->
                    ProtoTimeSeriesPoint.newBuilder()
                        .setTimestamp(Timestamp.newBuilder().setSeconds(pt.timestamp.epochSecond))
                        .setValue(pt.value)
                        .build()
                }
            )
        )
        is CurveMarketData -> builder.setCurve(
            ProtoCurve.newBuilder().addAllPoints(
                points.map { pt ->
                    ProtoCurvePoint.newBuilder()
                        .setTenor(pt.tenor)
                        .setValue(pt.value)
                        .build()
                }
            )
        )
        is MatrixMarketData -> builder.setMatrix(
            ProtoMatrix.newBuilder()
                .setRows(rows.size)
                .setCols(columns.size)
                .addAllValues(values)
                .addAllLabels(rows)
        )
    }

    return builder.build()
}
