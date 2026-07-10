package com.devapplab.model.field.response

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FieldPricingEstimateResponse(
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val fieldName: String,
    val fieldCapacity: Int,
    val maxPlayers: Int,
    val fieldCostInCents: Long,
    val organizerFeeInCents: Long,
    val currency: String,
    val constraints: FieldPricingConstraintsResponse,
    val operationalInsights: FieldPricingOperationalInsightsResponse,
    val recommendedOption: FieldPricingOptionResponse,
    val pricingOptions: List<FieldPricingOptionResponse>,
    val selectedOption: FieldPricingOptionResponse
)

@Serializable
data class FieldPricingCustomResponse(
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val fieldName: String,
    val fieldCapacity: Int,
    val maxPlayers: Int,
    val pricePerPlayerInCents: Long,
    val currency: String,
    val constraints: FieldPricingConstraintsResponse,
    val result: FieldPricingOptionResponse
)

@Serializable
data class FieldPricingConstraintsResponse(
    val minimumProfitInCents: Long,
    val maxPricePerPlayerInCents: Long,
    val priceStepInCents: Long,
    val stripePercentFeeBps: Int,
    val stripeFixedFeeCents: Long,
    val futmatchProfitBps: Int,
    val usesFieldOverrides: Boolean
)

@Serializable
data class FieldPricingOperationalInsightsResponse(
    val breakEvenPlayersRequired: Int,
    val recommendedMinimumPlayersToStart: Int
)

@Serializable
data class FieldPricingOptionResponse(
    val pricePerPlayerInCents: Long,
    val breakEvenPlayersRequired: Int,
    val minimumPlayersToStart: Int,
    val estimatedProfitAtMinimumPlayersInCents: Long,
    val estimatedProfitAtFullCapacityInCents: Long,
    val isViable: Boolean,
    val isRecommended: Boolean,
    val label: FieldPricingOptionLabel,
    val breakdownAtMinimumPlayersToStart: FieldPricingBreakdownResponse,
    val breakdownAtFullCapacity: FieldPricingBreakdownResponse
)

@Serializable
enum class FieldPricingOptionLabel {
    RECOMMENDED,
    SUGGESTED,
    CUSTOM
}

@Serializable
data class FieldPricingBreakdownResponse(
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
