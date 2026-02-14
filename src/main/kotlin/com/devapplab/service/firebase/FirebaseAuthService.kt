package com.devapplab.service.firebase

import com.devapplab.core.FirebaseAdminProvider
import org.slf4j.LoggerFactory

class FirebaseAuthService(
    private val provider: FirebaseAdminProvider
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun createCustomToken(userId: String, claims: Map<String, Any> = emptyMap()): String? {
        return try {
            provider.auth().createCustomToken(userId, claims)
        } catch (e: Exception) {
            logger.error("🔥 Error generating Firebase custom token for user $userId", e)
            null
        }
    }
}