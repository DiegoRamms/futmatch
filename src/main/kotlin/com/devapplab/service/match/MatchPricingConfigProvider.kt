package com.devapplab.service.match

import com.devapplab.data.repository.config.MatchPricingConfigRepository
import com.devapplab.data.repository.config.MatchPricingConfigRepositoryImpl
import com.devapplab.model.MatchPricingConfig

class MatchPricingConfigProvider(
    private val repository: MatchPricingConfigRepository
) {
    suspend fun get(): MatchPricingConfig {
        return runCatching { repository.getConfig() }
            .getOrElse { MatchPricingConfigRepositoryImpl.defaultConfig() }
    }
}
