package com.devapplab.service.hashing

import at.favre.lib.crypto.bcrypt.BCrypt

class HashingServiceImpl : HashingService {
    override suspend fun hash(password: String): String {
        val start = System.currentTimeMillis()
        val hashed = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        println("🔐 Hashing password took ${System.currentTimeMillis() - start} ms")
        return hashed
    }

    override suspend fun verify(password: String, hashedPassword: String): Boolean {
        val start = System.currentTimeMillis()
        val result = BCrypt.verifyer().verify(password.toCharArray(), hashedPassword)
        println("🔐 Verifying password took ${System.currentTimeMillis() - start} ms")
        return result.verified
    }
}