package com.devapplab.model

data class MatchPricingConfig(
    val futmatchProfitBps: Int,
    val minimumProfitInCents: Long,
    val maxPricePerPlayerInCents: Long,
    val stripePercentFeeBps: Int,
    val stripeFixedFeeCents: Long,
    val priceRoundingStepCents: Long,
    val pricingOptionsStepInCents: Long
)
