package com.devapplab.service.firebase

import com.devapplab.core.FirebaseAdminProvider
import com.devapplab.model.firestore.MatchPlayerList
import com.devapplab.utils.awaitNoGet
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

class MatchPlayerRealtimeService(
    private val provider: FirebaseAdminProvider
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val COLLECTION_NAME = "match_players"
    }

    suspend fun updateMatchPlayers(matchId: String, playerList: MatchPlayerList): Boolean {
        return try {
            val db = provider.firestore()
            val docRef = db.collection(COLLECTION_NAME).document(matchId)
            docRef.set(playerList.toMap()).awaitNoGet()
            logger.info("✅ Firestore real-time player list updated for match $matchId")
            true
        } catch (e: Exception) {
            logger.error("🔥 Firestore real-time update failed for match $matchId", e)
            false
        }
    }

    /** Upgrades a pre-existing projection without replacing a roster written concurrently. */
    suspend fun backfillGuestProjection(matchId: String, playerList: () -> MatchPlayerList) {
        try {
            val db = provider.firestore()
            val docRef = db.collection(COLLECTION_NAME).document(matchId)
            db.runTransaction { transaction ->
                val current = transaction.get(docRef).get()
                if (!current.contains("whatsappGuests")) {
                    val projection = playerList().toMap()
                    if (current.exists()) {
                        transaction.update(docRef, projection.filterKeys { it.startsWith("whatsappGuest") })
                    } else {
                        transaction.set(docRef, projection)
                    }
                }
                Unit
            }.awaitNoGet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Could not backfill Firestore guests for match {}", matchId, e)
        }
    }

    suspend fun deleteMatchPlayers(matchId: String): Boolean {
        return try {
            val db = provider.firestore()
            val docRef = db.collection(COLLECTION_NAME).document(matchId)
            docRef.delete().awaitNoGet()
            logger.info("🗑️ Firestore real-time player list deleted for match $matchId")
            true
        } catch (e: Exception) {
            logger.error("🔥 Firestore real-time delete failed for match $matchId", e)
            false
        }
    }
}
