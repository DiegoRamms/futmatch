package com.devapplab.service.firebase

import com.devapplab.core.FirebaseAdminProvider
import com.devapplab.utils.awaitNoGet
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.SetOptions
import org.slf4j.LoggerFactory
import java.util.*

class MatchSignalsService(
    private val provider: FirebaseAdminProvider
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Upsert:
     * - always updates lastUpdated
     * - only sets expireAt when provided (create/cancel)
     */
    suspend fun signalMatchUpdateUpsert(matchId: String, expireAtMillis: Long? = null): Boolean {
        return try {
            val db = provider.firestore()
            val docRef = db.collection("match-updates").document(matchId)

            val data = mutableMapOf<String, Any>(
                "lastUpdated" to FieldValue.serverTimestamp()
            )
            if (expireAtMillis != null) {
                data["expireAt"] = Date(expireAtMillis)
            }

            docRef.set(data, SetOptions.merge()).awaitNoGet()
            true
        } catch (e: Exception) {
            logger.error("🔥 Firestore signal failed for match $matchId", e)
            false
        }
    }
}