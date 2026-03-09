package com.devapplab.service.firebase

import com.devapplab.core.FirebaseAdminProvider
import com.devapplab.model.firestore.MatchPlayerList
import com.devapplab.utils.awaitNoGet
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
