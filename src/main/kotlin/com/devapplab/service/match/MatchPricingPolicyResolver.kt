package com.devapplab.service.match

import com.devapplab.model.MatchPricingConfig
import com.devapplab.model.field.Field

object MatchPricingPolicyResolver {
    fun resolve(config: MatchPricingConfig, field: Field): MatchPricingPolicy {
        val minimumProfitOverrideInCents = field.minimumProfitOverrideInCents
        val maxPricePerPlayerOverrideInCents = field.maxPricePerPlayerOverrideInCents

        return MatchPricingPolicy(
            futmatchProfitBps = config.futmatchProfitBps,
            minimumProfitInCents = minimumProfitOverrideInCents ?: config.minimumProfitInCents,
            maxPricePerPlayerInCents = maxPricePerPlayerOverrideInCents ?: config.maxPricePerPlayerInCents,
            stripePercentFeeBps = config.stripePercentFeeBps,
            stripeFixedFeeCents = config.stripeFixedFeeCents,
            priceRoundingStepCents = config.priceRoundingStepCents,
            usesFieldOverrides = minimumProfitOverrideInCents != null || maxPricePerPlayerOverrideInCents != null
        )
    }
}
