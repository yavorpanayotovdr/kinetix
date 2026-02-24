package com.kinetix.referencedata.service

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.referencedata.cache.ReferenceDataCache
import com.kinetix.referencedata.kafka.ReferenceDataPublisher
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository

class ReferenceDataIngestionService(
    private val dividendYieldRepository: DividendYieldRepository,
    private val creditSpreadRepository: CreditSpreadRepository,
    private val cache: ReferenceDataCache,
    private val publisher: ReferenceDataPublisher,
) {
    suspend fun ingest(dividendYield: DividendYield) {
        dividendYieldRepository.save(dividendYield)
        cache.putDividendYield(dividendYield)
        publisher.publishDividendYield(dividendYield)
    }

    suspend fun ingest(creditSpread: CreditSpread) {
        creditSpreadRepository.save(creditSpread)
        cache.putCreditSpread(creditSpread)
        publisher.publishCreditSpread(creditSpread)
    }
}
