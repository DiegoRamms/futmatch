package com.devapplab.service.hashing

import at.favre.lib.crypto.bcrypt.BCrypt

class HashingServiceImpl : HashingService {
    override fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    override fun verifyPassword(password: String, hashedPassword: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hashedPassword)
        return result.verified
    }
}