package com.devapplab.service.hashing

interface HashingService {
    fun hashPassword(password: String): String
    fun verifyPassword(password: String, hashedPassword: String): Boolean
}

