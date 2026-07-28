package com.devapplab.service.pii

import com.devapplab.model.PiiCryptoConfig
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PiiCrypto(config: PiiCryptoConfig) {
    private val encryptionKeys = buildMap {
        put(config.keyVersion, decodeRequired(config.encryptionKeyBase64, "PII_ENCRYPTION_KEY"))
        config.previousEncryptionKeys.forEach { (version, key) ->
            require(version.isNotBlank()) { "PII previous encryption key version must not be blank." }
            require(version != config.keyVersion) { "PII previous encryption key version duplicates the current version." }
            put(version, decodeRequired(key, "PII previous encryption key '$version'"))
        }
    }
    private val lookupPepper = decodeRequired(config.lookupPepperBase64, "PII_LOOKUP_PEPPER")
    val keyVersion = config.keyVersion

    init {
        require(encryptionKeys.values.all { it.size == AES_KEY_BYTES }) { "Every PII encryption key must decode to 32 bytes." }
        require(lookupPepper.size >= MINIMUM_PEPPER_BYTES) { "PII_LOOKUP_PEPPER must decode to at least 32 bytes." }
        require(keyVersion.isNotBlank()) { "PII_KEY_VERSION must not be blank." }
    }

    fun encrypt(value: String): String {
        val nonce = ByteArray(GCM_NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(currentEncryptionKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return base64Encoder.encodeToString(nonce + encrypted)
    }

    fun decrypt(ciphertext: String, keyVersion: String): String {
        val payload = try {
            base64Decoder.decode(ciphertext)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Invalid PII ciphertext encoding.", error)
        }
        require(payload.size > GCM_NONCE_BYTES + GCM_TAG_BYTES) { "Invalid PII ciphertext." }
        val nonce = payload.copyOfRange(0, GCM_NONCE_BYTES)
        val encrypted = payload.copyOfRange(GCM_NONCE_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = encryptionKeys[keyVersion] ?: throw IllegalStateException("No PII encryption key is configured for version '$keyVersion'.")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8)
    }

    fun emailLookup(email: String): String = lookup("email", normalizeEmail(email))

    fun phoneLookup(phone: String): String = lookup("phone", normalizePhone(phone))

    fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    fun normalizePhone(phone: String): String = phone.trim()

    private fun lookup(type: String, normalizedValue: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(lookupPepper, "HmacSHA256"))
        return base64Encoder.encodeToString(mac.doFinal("$type:$normalizedValue".toByteArray(StandardCharsets.UTF_8)))
    }

    private fun decodeRequired(value: String, name: String): ByteArray = try {
        base64Decoder.decode(value)
    } catch (error: IllegalArgumentException) {
        throw IllegalStateException("$name must be Base64-encoded.", error)
    }

    private val currentEncryptionKey: ByteArray
        get() = requireNotNull(encryptionKeys[keyVersion])

    private companion object {
        const val AES_KEY_BYTES = 32
        const val MINIMUM_PEPPER_BYTES = 32
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        val secureRandom = SecureRandom()
        val base64Encoder = Base64.getUrlEncoder().withoutPadding()
        val base64Decoder = Base64.getUrlDecoder()
    }
}
