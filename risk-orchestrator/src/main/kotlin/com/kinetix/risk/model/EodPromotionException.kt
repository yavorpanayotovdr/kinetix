package com.kinetix.risk.model

import java.util.UUID

sealed class EodPromotionException(message: String) : RuntimeException(message) {
    class JobNotFound(jobId: UUID) : EodPromotionException("Job not found: $jobId")
    class JobNotCompleted(jobId: UUID) : EodPromotionException("Job $jobId is not completed")
    class AlreadyPromoted(jobId: UUID) : EodPromotionException("Job $jobId is already promoted to Official EOD")
    class NotPromoted(jobId: UUID) : EodPromotionException("Job $jobId is not currently promoted")
    class ConflictingOfficialEod(portfolioId: String, valuationDate: String) :
        EodPromotionException("An Official EOD already exists for portfolio $portfolioId on $valuationDate")
    class SelfPromotion(userId: String) :
        EodPromotionException("Separation of duties: user $userId cannot promote their own run")
}
