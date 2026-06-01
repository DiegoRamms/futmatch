package com.devapplab.data.repository.match

interface PublicMatchesVersionRepository {
    suspend fun getVersion(region: String): Long
    suspend fun incrementVersion(region: String): Long
}

