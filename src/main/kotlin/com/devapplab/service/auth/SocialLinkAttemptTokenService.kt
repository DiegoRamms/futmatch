package com.devapplab.service.auth

import com.devapplab.service.hashing.HashingService
import java.security.SecureRandom
import java.util.Base64

class SocialLinkAttemptTokenService(private val hashingService: HashingService) {
    private val random = SecureRandom()
    fun generate(): Pair<String, String> {
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return token to hashingService.hashOpaqueToken(token)
    }
    fun hash(token: String) = hashingService.hashOpaqueToken(token)
}
