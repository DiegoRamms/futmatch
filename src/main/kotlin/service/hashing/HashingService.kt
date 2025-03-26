package com.devapplab.service.hashing

interface HashingService {
    fun hash(password: String): String
    fun verify(password: String, hashedPassword: String): Boolean
}

