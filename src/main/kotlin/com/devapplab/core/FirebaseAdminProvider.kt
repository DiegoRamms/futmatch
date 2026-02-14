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
        } catch (e: Exception) {
            logger.error("🔥 Failed to initialize Firebase Admin SDK", e)
        }
    }

    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    fun firestore(): Firestore = FirestoreClient.getFirestore()
}