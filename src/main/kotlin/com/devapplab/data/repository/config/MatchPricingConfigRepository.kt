package com.devapplab.data.repository.config

import com.devapplab.model.MatchPricingConfig

interface MatchPricingConfigRepository {
    suspend fun getConfig(): MatchPricingConfig
}
