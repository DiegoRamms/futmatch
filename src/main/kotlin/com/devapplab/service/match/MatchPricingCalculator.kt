package com.devapplab.service.match

import com.devapplab.model.field.response.FieldPricingOptionLabel
import kotlin.math.max

object MatchPricingCalculator {
    fun buildPricingEstimate(
        policy: MatchPricingPolicy,
        inputs: MatchPricingInputs,
        deadlineNanos: Long? = null
    ): MatchPricingEstimate {
        validateInputs(policy, inputs)

        val recommendedPrice = recommendedPricePerPlayer(policy, inputs, deadlineNanos)
        val recommendedOption = calculateOption(
            policy = policy,
            inputs = inputs,
            pricePerPlayerInCents = recommendedPrice,
            label = FieldPricingOptionLabel.RECOMMENDED,
            isRecommended = true,
            deadlineNanos = deadlineNanos
        )

        val pricingOptions = buildPricingOptions(
            policy = policy,
            inputs = inputs,
            recommendedPrice = recommendedPrice,
            recommendedOption = recommendedOption,
            deadlineNanos = deadlineNanos
        )

        return MatchPricingEstimate(
            recommendedOption = recommendedOption,
            pricingOptions = pricingOptions,
            operationalInsights = MatchPricingOperationalInsights(
                breakEvenPlayersRequired = recommendedOption.breakEvenPlayersRequired,
                recommendedMinimumPlayersToStart = recommendedOption.minimumPlayersToStart
            ),
            selectedOption = recommendedOption
        )
    }

    fun calculateCustomPricing(
        policy: MatchPricingPolicy,
        inputs: MatchPricingInputs,
        pricePerPlayerInCents: Long
    ): MatchPricingOption {
        validateInputs(policy, inputs)
        require(pricePerPlayerInCents > 0) { "pricePerPlayerInCents must be greater than 0" }

        return calculateOption(
            policy = policy,
            inputs = inputs,
            pricePerPlayerInCents = pricePerPlayerInCents,
            label = FieldPricingOptionLabel.CUSTOM,
            isRecommended = false
        )
    }

    fun targetProfitInCents(policy: MatchPricingPolicy, fieldCostInCents: Long): Long {
        val percentTarget = ceilDiv(fieldCostInCents * policy.futmatchProfitBps.toLong(), BASIS_POINTS)
        return max(policy.minimumProfitInCents, percentTarget)
    }

    private fun calculateOption(
        policy: MatchPricingPolicy,
        inputs: MatchPricingInputs,
        pricePerPlayerInCents: Long,
        label: FieldPricingOptionLabel,
        isRecommended: Boolean,
        deadlineNanos: Long? = null
    ): MatchPricingOption {
        checkDeadline(deadlineNanos)
        val breakEvenPlayersRequired = minimumPlayersRequired(
            policy = policy,
            inputs = inputs,
            pricePerPlayerInCents = pricePerPlayerInCents,
            targetMode = TargetMode.BREAK_EVEN,
            deadlineNanos = deadlineNanos
        )

        val minimumPlayersToStart = minimumPlayersRequired(
            policy = policy,
            inputs = inputs,
            pricePerPlayerInCents = pricePerPlayerInCents,
            targetMode = TargetMode.MINIMUM_PROFIT,
            deadlineNanos = deadlineNanos
        )

        val playersForBreakdown = minimumPlayersToStart ?: inputs.maxPlayers
        val breakdown = calculateBreakdown(
            policy = policy,
            fieldCostInCents = inputs.fieldCostInCents,
            organizerFeeInCents = inputs.organizerFeeInCents,
            players = playersForBreakdown,
            pricePerPlayerInCents = pricePerPlayerInCents
        )
        val fullCapacityBreakdown = calculateBreakdown(
            policy = policy,
            fieldCostInCents = inputs.fieldCostInCents,
            organizerFeeInCents = inputs.organizerFeeInCents,
            players = inputs.maxPlayers,
            pricePerPlayerInCents = pricePerPlayerInCents
        )

        return MatchPricingOption(
            pricePerPlayerInCents = pricePerPlayerInCents,
            breakEvenPlayersRequired = breakEvenPlayersRequired ?: inputs.maxPlayers,
            minimumPlayersToStart = minimumPlayersToStart ?: inputs.maxPlayers,
            estimatedProfitAtMinimumPlayersInCents = breakdown.estimatedProfitInCents,
            estimatedProfitAtFullCapacityInCents = fullCapacityBreakdown.estimatedProfitInCents,
            isViable = minimumPlayersToStart != null && pricePerPlayerInCents <= policy.maxPricePerPlayerInCents,
            isRecommended = isRecommended,
            label = label,
            breakdownAtMinimumPlayersToStart = breakdown,
            breakdownAtFullCapacity = fullCapacityBreakdown
        )
    }

    fun minimumPlayersRequired(
        policy: MatchPricingPolicy,
        inputs: MatchPricingInputs,
        pricePerPlayerInCents: Long,
        targetMode: TargetMode,
        deadlineNanos: Long? = null
    ): Int? {
        if (inputs.maxPlayers <= 0 || pricePerPlayerInCents <= 0) return null

        return (1..inputs.maxPlayers).firstOrNull { players ->
            checkDeadline(deadlineNanos)
            isProfitable(
                policy = policy,
                fieldCostInCents = inputs.fieldCostInCents,
                organizerFeeInCents = inputs.organizerFeeInCents,
                players = players,
                pricePerPlayerInCents = pricePerPlayerInCents,
                targetMode = targetMode
            )
        }
    }

    fun isProfitable(
        policy: MatchPricingPolicy,
        fieldCostInCents: Long,
        organizerFeeInCents: Long,
        players: Int,
        pricePerPlayerInCents: Long,
        targetMode: TargetMode
    ): Boolean {
        if (fieldCostInCents <= 0 || players <= 0 || pricePerPlayerInCents <= 0) return false

        val breakdown = calculateBreakdown(
            policy = policy,
            fieldCostInCents = fieldCostInCents,
            organizerFeeInCents = organizerFeeInCents,
            players = players,
            pricePerPlayerInCents = pricePerPlayerInCents
        )
        return breakdown.netRevenueInCents >= targetNetInCents(policy, fieldCostInCents, organizerFeeInCents, targetMode)
    }

    fun calculateBreakdown(
        policy: MatchPricingPolicy,
        fieldCostInCents: Long,
        organizerFeeInCents: Long,
        players: Int,
        pricePerPlayerInCents: Long
    ): MatchPricingBreakdown {
        val grossRevenueInCents = pricePerPlayerInCents * players
        val stripeFixedFeeInCents = policy.stripeFixedFeeCents * players
        val stripePercentFeeInCents = ceilDiv(grossRevenueInCents * policy.stripePercentFeeBps.toLong(), BASIS_POINTS)
        val totalStripeFeesInCents = stripeFixedFeeInCents + stripePercentFeeInCents
        val netRevenueInCents = grossRevenueInCents - totalStripeFeesInCents
        val targetProfitInCents = targetProfitInCents(policy, fieldCostInCents)

        return MatchPricingBreakdown(
            players = players,
            grossRevenueInCents = grossRevenueInCents,
            stripeFixedFeeInCents = stripeFixedFeeInCents,
            stripePercentFeeInCents = stripePercentFeeInCents,
            totalStripeFeesInCents = totalStripeFeesInCents,
            netRevenueInCents = netRevenueInCents,
            fieldCostInCents = fieldCostInCents,
            organizerFeeInCents = organizerFeeInCents,
            targetProfitInCents = targetProfitInCents,
            estimatedProfitInCents = netRevenueInCents - fieldCostInCents - organizerFeeInCents
        )
    }

    private fun recommendedPricePerPlayer(
        policy: MatchPricingPolicy,
        inputs: MatchPricingInputs,
        deadlineNanos: Long?
    ): Long {
        val step = policy.priceRoundingStepCents
        val maximumPriceToSearch = max(
            policy.maxPricePerPlayerInCents,
            inputs.fieldCostInCents + inputs.organizerFeeInCents + targetProfitInCents(policy, inputs.fieldCostInCents)
        )
        var lowStep = 1L
        var highStep = ceilDiv(maximumPriceToSearch, step)

        while (lowStep < highStep) {
            checkDeadline(deadlineNanos)
            val midStep = lowStep + (highStep - lowStep) / 2
            val midPrice = midStep * step
            if (isProfitable(
                    policy = policy,
                    fieldCostInCents = inputs.fieldCostInCents,
                    organizerFeeInCents = inputs.organizerFeeInCents,
                    players = inputs.maxPlayers,
                    pricePerPlayerInCents = midPrice,
                    targetMode = TargetMode.MINIMUM_PROFIT
                )
            ) {
                highStep = midStep
            } else {
                lowStep = midStep + 1
            }
        }

        return lowStep * step
    }

    private fun buildPricingOptions(
        policy: MatchPricingPolicy,
        inputs: MatchPricingInputs,
        recommendedPrice: Long,
        recommendedOption: MatchPricingOption,
        deadlineNanos: Long?
    ): List<MatchPricingOption> {
        val minimumSuggestedPrice = roundUpToStep(
            max(recommendedPrice - (2 * policy.pricingOptionsStepInCents), policy.pricingOptionsStepInCents),
            policy.pricingOptionsStepInCents
        )

        val optionPrices = linkedSetOf<Long>()

        var price = minimumSuggestedPrice
        while (price <= policy.maxPricePerPlayerInCents) {
            checkDeadline(deadlineNanos)
            optionPrices += price
            if (price > Long.MAX_VALUE - policy.pricingOptionsStepInCents) break
            price += policy.pricingOptionsStepInCents
        }

        if (policy.maxPricePerPlayerInCents !in optionPrices) {
            optionPrices += policy.maxPricePerPlayerInCents
        }

        if (recommendedPrice <= policy.maxPricePerPlayerInCents && recommendedPrice !in optionPrices) {
            optionPrices += recommendedPrice
        }

        return optionPrices
            .toList()
            .sorted()
            .map { price ->
                checkDeadline(deadlineNanos)
                calculateOption(
                    policy = policy,
                    inputs = inputs,
                    pricePerPlayerInCents = price,
                    label = if (price == recommendedPrice) {
                        FieldPricingOptionLabel.RECOMMENDED
                    } else {
                        FieldPricingOptionLabel.SUGGESTED
                    },
                    isRecommended = price == recommendedPrice,
                    deadlineNanos = deadlineNanos
                )
            }
            .ifEmpty { listOf(recommendedOption) }
    }

    private fun targetNetInCents(
        policy: MatchPricingPolicy,
        fieldCostInCents: Long,
        organizerFeeInCents: Long,
        targetMode: TargetMode
    ): Long {
        val baseCost = fieldCostInCents + organizerFeeInCents
        return when (targetMode) {
            TargetMode.BREAK_EVEN -> baseCost
            TargetMode.MINIMUM_PROFIT -> baseCost + targetProfitInCents(policy, fieldCostInCents)
        }
    }

    private fun validateInputs(policy: MatchPricingPolicy, inputs: MatchPricingInputs) {
        require(inputs.fieldCostInCents > 0) { "fieldCostInCents must be greater than 0" }
        require(inputs.organizerFeeInCents >= 0) { "organizerFeeInCents must be greater than or equal to 0" }
        require(inputs.fieldCapacity > 0) { "fieldCapacity must be greater than 0" }
        require(inputs.maxPlayers in 1..inputs.fieldCapacity) { "maxPlayers must be between 1 and fieldCapacity" }
        require(policy.priceRoundingStepCents > 0) { "priceRoundingStepCents must be greater than 0" }
        require(policy.pricingOptionsStepInCents > 0) { "pricingOptionsStepInCents must be greater than 0" }
        require(policy.maxPricePerPlayerInCents >= policy.priceRoundingStepCents) {
            "maxPricePerPlayerInCents must be greater than or equal to priceRoundingStepCents"
        }
    }

    private fun roundUpToStep(value: Long, step: Long): Long = ceilDiv(value, step) * step

    private fun ceilDiv(value: Long, divisor: Long): Long = (value + divisor - 1) / divisor

    private fun checkDeadline(deadlineNanos: Long?) {
        if (deadlineNanos != null && System.nanoTime() > deadlineNanos) {
            throw PricingCalculationTimeoutException()
        }
    }

    private const val BASIS_POINTS = 10_000L
}

class PricingCalculationTimeoutException : RuntimeException("Pricing calculation exceeded its time limit")

data class MatchPricingPolicy(
    val futmatchProfitBps: Int,
    val minimumProfitInCents: Long,
    val maxPricePerPlayerInCents: Long,
    val stripePercentFeeBps: Int,
    val stripeFixedFeeCents: Long,
    val priceRoundingStepCents: Long,
    val pricingOptionsStepInCents: Long,
    val usesFieldOverrides: Boolean
)

data class MatchPricingInputs(
    val fieldCostInCents: Long,
    val organizerFeeInCents: Long,
    val fieldCapacity: Int,
    val maxPlayers: Int
)

data class MatchPricingEstimate(
    val recommendedOption: MatchPricingOption,
    val pricingOptions: List<MatchPricingOption>,
    val operationalInsights: MatchPricingOperationalInsights,
    val selectedOption: MatchPricingOption
)

data class MatchPricingOperationalInsights(
    val breakEvenPlayersRequired: Int,
    val recommendedMinimumPlayersToStart: Int
)

data class MatchPricingOption(
    val pricePerPlayerInCents: Long,
    val breakEvenPlayersRequired: Int,
    val minimumPlayersToStart: Int,
    val estimatedProfitAtMinimumPlayersInCents: Long,
    val estimatedProfitAtFullCapacityInCents: Long,
    val isViable: Boolean,
    val isRecommended: Boolean,
    val label: FieldPricingOptionLabel,
    val breakdownAtMinimumPlayersToStart: MatchPricingBreakdown,
    val breakdownAtFullCapacity: MatchPricingBreakdown
)

data class MatchPricingBreakdown(
    val players: Int,
    val grossRevenueInCents: Long,
    val stripeFixedFeeInCents: Long,
    val stripePercentFeeInCents: Long,
    val totalStripeFeesInCents: Long,
    val netRevenueInCents: Long,
    val fieldCostInCents: Long,
    val organizerFeeInCents: Long,
    val targetProfitInCents: Long,
    val estimatedProfitInCents: Long
)

enum class TargetMode {
    BREAK_EVEN,
    MINIMUM_PROFIT
}
