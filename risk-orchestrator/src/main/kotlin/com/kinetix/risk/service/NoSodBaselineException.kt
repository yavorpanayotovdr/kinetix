package com.kinetix.risk.service

class NoSodBaselineException(portfolioId: String) :
    RuntimeException("No SOD baseline exists for portfolio $portfolioId on today's date")
