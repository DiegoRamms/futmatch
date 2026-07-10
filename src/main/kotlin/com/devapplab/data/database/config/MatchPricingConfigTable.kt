package com.devapplab.data.database.config

import org.jetbrains.exposed.v1.core.Table

object MatchPricingConfigTable : Table("match_pricing_config") {
    val id = integer("id")
    val futmatchProfitBps = integer("futmatch_profit_bps")
    val minimumProfitInCents = long("minimum_profit_in_cents").default(30_000L)
    val maxPricePerPlayerInCents = long("max_price_per_player_in_cents").default(22_000L)
    val stripePercentFeeBps = integer("stripe_percent_fee_bps")
    val stripeFixedFeeCents = long("stripe_fixed_fee_cents")
    val priceRoundingStepCents = long("price_rounding_step_cents")
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}
