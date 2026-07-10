package com.devapplab

import com.devapplab.model.MatchPricingConfig
import com.devapplab.service.match.MatchPricingCalculator
import com.devapplab.service.match.MatchPricingInputs
import com.devapplab.service.match.MatchPricingPolicy
import com.devapplab.service.match.TargetMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MatchPricingCalculatorTest {
    private val config = MatchPricingConfig(
        futmatchProfitBps = 1500,
        minimumProfitInCents = 30_000L,
        maxPricePerPlayerInCents = 22_000L,
        stripePercentFeeBps = 360,
        stripeFixedFeeCents = 300L,
        priceRoundingStepCents = 100L
    )
    private val policy = MatchPricingPolicy(
        futmatchProfitBps = config.futmatchProfitBps,
        minimumProfitInCents = config.minimumProfitInCents,
        maxPricePerPlayerInCents = config.maxPricePerPlayerInCents,
        stripePercentFeeBps = config.stripePercentFeeBps,
        stripeFixedFeeCents = config.stripeFixedFeeCents,
        priceRoundingStepCents = config.priceRoundingStepCents,
        usesFieldOverrides = false
    )

    @Test
    fun `estimate includes stripe fees and minimum profit target`() {
        val estimate = MatchPricingCalculator.buildPricingEstimate(
            policy = policy,
            inputs = MatchPricingInputs(
                fieldCostInCents = 100_000,
                organizerFeeInCents = 20_000,
                fieldCapacity = 14,
                maxPlayers = 10
            )
        )

        assertEquals(30_000, estimate.recommendedOption.breakdownAtMinimumPlayersToStart.targetProfitInCents)
        assertEquals(300L, config.stripeFixedFeeCents)
        assertTrue(estimate.recommendedOption.breakdownAtMinimumPlayersToStart.totalStripeFeesInCents > 3_000)
        assertTrue(estimate.recommendedOption.breakdownAtMinimumPlayersToStart.netRevenueInCents >= 130_000)
    }

    @Test
    fun `recommended price is rounded up to full peso`() {
        val estimate = MatchPricingCalculator.buildPricingEstimate(
            policy = policy,
            inputs = MatchPricingInputs(
                fieldCostInCents = 100_000,
                organizerFeeInCents = 20_000,
                fieldCapacity = 14,
                maxPlayers = 10
            )
        )

        assertEquals(0, estimate.recommendedOption.pricePerPlayerInCents % 100)
    }

    @Test
    fun `minimum profitable players is calculated from provided price`() {
        val minimumPlayers = MatchPricingCalculator.minimumPlayersRequired(
            policy = policy,
            inputs = MatchPricingInputs(
                fieldCostInCents = 100_000,
                organizerFeeInCents = 20_000,
                fieldCapacity = 14,
                maxPlayers = 14
            ),
            pricePerPlayerInCents = 15_000,
            targetMode = TargetMode.BREAK_EVEN
        )

        assertNotNull(minimumPlayers)
        assertTrue(minimumPlayers >= 1)
        assertTrue(
            MatchPricingCalculator.isProfitable(
                policy = policy,
                fieldCostInCents = 100_000,
                organizerFeeInCents = 20_000,
                players = minimumPlayers,
                pricePerPlayerInCents = 15_000,
                targetMode = TargetMode.BREAK_EVEN
            )
        )
    }

    @Test
    fun `price that cannot cover target within max players returns no minimum`() {
        val minimumPlayers = MatchPricingCalculator.minimumPlayersRequired(
            policy = policy,
            inputs = MatchPricingInputs(
                fieldCostInCents = 100_000,
                organizerFeeInCents = 20_000,
                fieldCapacity = 14,
                maxPlayers = 14
            ),
            pricePerPlayerInCents = 1_000,
            targetMode = TargetMode.MINIMUM_PROFIT
        )

        assertEquals(null, minimumPlayers)
    }
}
