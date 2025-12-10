package utils

import java.security.SecureRandom
import kotlin.time.Duration.Companion.seconds

object MfaUtils {
    fun generateCode(): String {
        val random = SecureRandom()
        val code = random.nextInt(1_000_000)
        return String.format("%06d", code)
    }

    fun calculateExpiration(seconds: Long = 60): Long {
        return System.currentTimeMillis() + seconds.seconds.inWholeMilliseconds
    }
}