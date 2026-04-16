package com.devapplab.core

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FirebaseAdminProvider(private val config: ApplicationConfig) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
       initialize()
    }

    private fun initialize() {
        try {
            val firebaseConfigJson = config.propertyOrNull("firebase.config_json")?.getString()

            if (firebaseConfigJson.isNullOrBlank()) {
                logger.warn("⚠️ Firebase config not found. Firebase Admin SDK will NOT be initialized.")
                return
            }

            val options = FirebaseOptions.builder()
                .setCredentials(
                    GoogleCredentials.fromStream(ByteArrayInputStream(firebaseConfigJson.toByteArray()))
                )
                .build()

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
                logger.info("✅ Firebase Admin SDK initialized successfully")
            } else {
                logger.info("ℹ️ Firebase Admin SDK already initialized")
            }

            // Non-blocking warmup to avoid first-user Firestore cold-start latency.
            thread(start = true, isDaemon = true, name = "firebase-firestore-warmup") {
                warmupFirestore()
            }
        } catch (e: Exception) {
            logger.error("🔥 Failed to initialize Firebase Admin SDK", e)
        }
    }

    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    fun firestore(): Firestore = FirestoreClient.getFirestore()

    fun warmupFirestore() {
        val startAt = System.currentTimeMillis()
        try {
            // Trigger first Firestore network call so user-facing requests don't pay cold-start latency.
            firestore()
                .collection("_system")
                .document("warmup")
                .get()
                .get(10, TimeUnit.SECONDS)
            logger.info("🔥 Firestore warmup completed in {} ms", System.currentTimeMillis() - startAt)
        } catch (e: Exception) {
            logger.warn("⚠️ Firestore warmup failed after {} ms", System.currentTimeMillis() - startAt, e)
        }
    }
}
