package com.kinetix.risk.service

import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PriceServiceClient
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class PriceBasedFxRateProvider(
    private val priceServiceClient: PriceServiceClient,
) : FxRateProvider {

    private val logger = LoggerFactory.getLogger(PriceBasedFxRateProvider::class.java)
    private val mc = MathContext(20, RoundingMode.HALF_UP)

    override suspend fun getRate(fromCurrency: String, toCurrency: String): BigDecimal? {
        if (fromCurrency == toCurrency) return BigDecimal.ONE

        return try {
            val directPair = "$fromCurrency$toCurrency"
            when (val direct = priceServiceClient.getLatestPrice(InstrumentId(directPair))) {
                is ClientResponse.Success -> direct.value.price.amount

                is ClientResponse.NotFound -> {
                    logger.debug("FX pair {} not found, trying inverse", directPair)
                    val inversePair = "$toCurrency$fromCurrency"
                    when (val inverse = priceServiceClient.getLatestPrice(InstrumentId(inversePair))) {
                        is ClientResponse.Success -> {
                            val inverseRate = inverse.value.price.amount
                            BigDecimal.ONE.divide(inverseRate, mc)
                        }
                        else -> {
                            logger.warn("FX rate unavailable for {}/{} in either direction", fromCurrency, toCurrency)
                            null
                        }
                    }
                }

                else -> {
                    logger.warn("Error fetching FX rate for {}/{}: {}", fromCurrency, toCurrency, direct)
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("Exception fetching FX rate for {}/{}: {}", fromCurrency, toCurrency, e.message)
            null
        }
    }
}
