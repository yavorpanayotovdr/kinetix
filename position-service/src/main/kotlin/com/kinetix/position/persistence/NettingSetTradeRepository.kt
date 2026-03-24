package com.kinetix.position.persistence

interface NettingSetTradeRepository {
    /**
     * Returns a map of tradeId -> nettingSetId for the given trade IDs.
     * Trades not assigned to any netting set will be absent from the result.
     */
    suspend fun findNettingSetsByTradeIds(tradeIds: List<String>): Map<String, String>

    /**
     * Assigns a trade to a netting set. Idempotent — re-assigning to the same set is a no-op.
     */
    suspend fun assign(tradeId: String, nettingSetId: String)
}
