package utils

import java.security.SecureRandom

object MfaUtils {
    fun generateCode(): String {
        val random = SecureRandom()
        val code = random.nextInt(1_000_000)
        return String.format("%06d", code)
    }

    fun calculateExpiration(minutes: Long = 5): Long {
        return System.currentTimeMillis() + minutes * 60 * 1000
    }
}