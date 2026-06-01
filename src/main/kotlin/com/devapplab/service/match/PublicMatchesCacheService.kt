package com.devapplab.service.match

import com.devapplab.model.match.response.MatchSummaryResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PublicMatchesCacheService {

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
        val cached = mutex.withLock {
            val regionCache = cacheByRegion.getOrPut(region) { RegionCache() }
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
            regionCache.matches = rebuiltMatches
            Snapshot(regionCache.version, rebuiltMatches)
        }
    }

    suspend fun invalidate(region: String): Long {
        return mutex.withLock {
            val regionCache = cacheByRegion.getOrPut(region) { RegionCache() }
            regionCache.version += 1
            regionCache.matches = null
            regionCache.version
        }
    }
}
