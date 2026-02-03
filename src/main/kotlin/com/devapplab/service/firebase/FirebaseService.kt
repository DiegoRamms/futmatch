package com.devapplab.service.firebase

import com.devapplab.utils.awaitNoGet
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.config.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

class FirebaseService(config: ApplicationConfig) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        try {
            val firebaseConfigJson = config.propertyOrNull("firebase.config_json")?.getString()

            if (!firebaseConfigJson.isNullOrBlank()) {
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(ByteArrayInputStream(firebaseConfigJson.toByteArray())))
                    .build()

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options)
                    logger.info("✅ Firebase Admin SDK initialized successfully")
                }
            } else {
                logger.warn("⚠️ Firebase config not found. Custom tokens will not be generated.")
            }
        } catch (e: Exception) {
            logger.error("🔥 Failed to initialize Firebase Admin SDK", e)
        }
    }

    fun createCustomToken(userId: String, claims: Map<String, Any> = emptyMap()): String? {
        return try {
            FirebaseAuth.getInstance().createCustomToken(userId, claims)
        } catch (e: Exception) {
            logger.error("🔥 Error generating Firebase custom token for user $userId", e)
            null
        }
    }

    suspend fun signalMatchUpdate(matchId: String) {
        try {
            val db: Firestore = FirestoreClient.getFirestore()
            val docRef = db.collection("match-updates").document(matchId)
            val data = mapOf("lastUpdated" to System.currentTimeMillis())
            docRef.set(data).awaitNoGet()



            logger.info("✅ Successfully signaled update for match: $matchId")
        } catch (e: Exception) {
            logger.error("🔥 Error signaling match update for $matchId", e)
        }
    }
}

