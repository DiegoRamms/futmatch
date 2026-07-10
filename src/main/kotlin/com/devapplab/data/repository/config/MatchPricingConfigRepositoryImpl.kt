package com.devapplab.data.repository.config

import com.devapplab.config.dbQuery
import com.devapplab.data.database.config.MatchPricingConfigTable
import com.devapplab.model.MatchPricingConfig
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class MatchPricingConfigRepositoryImpl : MatchPricingConfigRepository {
    override suspend fun getConfig(): MatchPricingConfig = dbQuery {
        val row = MatchPricingConfigTable
            .selectAll()
            .where { MatchPricingConfigTable.id eq CONFIG_ROW_ID }
            .forUpdate()
            .singleOrNull()

        if (row != null) {
            row.toConfig()
        } else {
            val now = System.currentTimeMillis()
            val config = defaultConfig()
            MatchPricingConfigTable.insert {
                it[id] = CONFIG_ROW_ID
                it[futmatchProfitBps] = config.futmatchProfitBps
                it[minimumProfitInCents] = config.minimumProfitInCents
                it[maxPricePerPlayerInCents] = config.maxPricePerPlayerInCents
                it[stripePercentFeeBps] = config.stripePercentFeeBps
                it[stripeFixedFeeCents] = config.stripeFixedFeeCents
                it[priceRoundingStepCents] = config.priceRoundingStepCents
                it[pricingOptionsStepInCents] = config.pricingOptionsStepInCents
                it[updatedAt] = now
            }
            config
        }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toConfig(): MatchPricingConfig {
        return MatchPricingConfig(
            futmatchProfitBps = this[MatchPricingConfigTable.futmatchProfitBps],
            minimumProfitInCents = this[MatchPricingConfigTable.minimumProfitInCents],
            maxPricePerPlayerInCents = this[MatchPricingConfigTable.maxPricePerPlayerInCents],
            stripePercentFeeBps = this[MatchPricingConfigTable.stripePercentFeeBps],
            stripeFixedFeeCents = this[MatchPricingConfigTable.stripeFixedFeeCents],
            priceRoundingStepCents = this[MatchPricingConfigTable.priceRoundingStepCents],
            pricingOptionsStepInCents = this[MatchPricingConfigTable.pricingOptionsStepInCents]
        )
    }

    companion object {
        const val CONFIG_ROW_ID = 1

        fun defaultConfig(): MatchPricingConfig = MatchPricingConfig(
            futmatchProfitBps = 1500,
            minimumProfitInCents = 30_000L,
            maxPricePerPlayerInCents = 22_000L,
            stripePercentFeeBps = 360,
            stripeFixedFeeCents = 300L,
            priceRoundingStepCents = 100L,
            pricingOptionsStepInCents = 1_000L
        )
    }
}
