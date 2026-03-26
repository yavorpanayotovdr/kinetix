package com.kinetix.position.client

import com.kinetix.common.model.InstrumentId
import java.math.BigDecimal

interface InstrumentLiquidityClient {
    /** Returns the Average Daily Volume (in notional terms) for the given instrument, or null if unavailable. */
    suspend fun getAdv(instrumentId: InstrumentId): BigDecimal?
}
