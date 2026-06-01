package com.devapplab.service.match

import com.devapplab.data.repository.match.PublicMatchesVersionRepository
import com.devapplab.model.match.response.MatchSummaryResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PublicMatchesCacheService(
    private val publicMatchesVersionRepository: PublicMatchesVersionRepository
) {

    data class Snapshot(
        val version: Long,
        val matches: List<MatchSummaryResponse>
    )

    private data class RegionCache(
        var version: Long = 1,
        var matches: List<MatchSummaryResponse>? = null
    )

    private val mutex = Mutex()
    private val cacheByRegion = mutableMapOf<String, RegionCache>()

    suspend fun getOrBuild(region: String, builder: suspend () -> List<MatchSummaryResponse>): Snapshot {
        val persistentVersion = publicMatchesVersionRepository.getVersion(region)
        val cached = mutex.withLock {
            val regionCache = cacheByRegion.getOrPut(region) { RegionCache() }
            if (regionCache.version != persistentVersion) {
                regionCache.version = persistentVersion
                regionCache.matches = null
            }
            val matches = regionCache.matches
            if (matches != null) {
                return@withLock Snapshot(regionCache.version, matches)
            }
            null
        }
        if (cached != null) return cached

        val rebuiltMatches = builder()
        return mutex.withLock {
            val regionCache = cacheByRegion.getOrPut(region) { RegionCache() }
            regionCache.version = persistentVersion
            regionCache.matches = rebuiltMatches
            Snapshot(regionCache.version, rebuiltMatches)
        }
    }

    suspend fun invalidate(region: String): Long {
        val newVersion = publicMatchesVersionRepository.incrementVersion(region)
        return mutex.withLock {
            val regionCache = cacheByRegion.getOrPut(region) { RegionCache() }
            regionCache.version = newVersion
            regionCache.matches = null
            regionCache.version
        }
    }
}
