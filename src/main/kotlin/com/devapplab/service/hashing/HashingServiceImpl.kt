package com.devapplab.service.hashing

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.*

class HashingServiceImpl : HashingService {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val bcryptHash = BCrypt.withDefaults()
    private val bcryptVerifier = BCrypt.verifyer()

    override suspend fun hash(password: String): String {
        val start = System.currentTimeMillis()
        val hashed = withContext(Dispatchers.Default) {
            bcryptHash.hashToString(12, password.toCharArray())
        }
        logger.info("🔐 Hashing password took ${System.currentTimeMillis() - start} ms")
        return hashed
    }

    override suspend fun verify(password: String, hashedPassword: String): Boolean {
        val start = System.currentTimeMillis()
        val result = withContext(Dispatchers.Default) {
            bcryptVerifier.verify(password.toCharArray(), hashedPassword)
        }
        logger.info("🔐 Verifying password took ${System.currentTimeMillis() - start} ms")
        return result.verified
    }

    override fun hashOpaqueToken(token: String): String {
        val start = System.currentTimeMillis()
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        val hashed = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        logger.debug("🔐 Hashing opaque token (SHA-256) took ${System.currentTimeMillis() - start} ms")
        return hashed
    }
}