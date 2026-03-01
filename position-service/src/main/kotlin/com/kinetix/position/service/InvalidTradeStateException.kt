package com.kinetix.position.service

import com.kinetix.common.model.TradeStatus

class InvalidTradeStateException(
    val tradeId: String,
    val currentStatus: TradeStatus,
    val attemptedAction: String,
) : RuntimeException("Cannot $attemptedAction trade $tradeId in status $currentStatus")
