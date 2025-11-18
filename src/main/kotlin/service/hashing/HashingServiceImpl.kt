package com.devapplab.service.hashing

import at.favre.lib.crypto.bcrypt.BCrypt
import org.slf4j.LoggerFactory

class HashingServiceImpl : HashingService {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override suspend fun hash(password: String): String {
        val start = System.currentTimeMillis()
        val hashed = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        logger.info("🔐 Hashing password took ${System.currentTimeMillis() - start} ms")
        return hashed
    }

    override suspend fun verify(password: String, hashedPassword: String): Boolean {
        val start = System.currentTimeMillis()
        val result = BCrypt.verifyer().verify(password.toCharArray(), hashedPassword)
        logger.info("🔐 Verifying password took ${System.currentTimeMillis() - start} ms")
        return result.verified
    }
}