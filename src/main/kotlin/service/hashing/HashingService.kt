package com.devapplab.service.hashing

interface HashingService {
    suspend fun hash(password: String): String
    suspend fun verify(password: String, hashedPassword: String): Boolean
    fun hashOpaqueToken(token: String): String
}

