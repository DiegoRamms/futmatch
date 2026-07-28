package com.devapplab

import com.devapplab.model.PiiCryptoConfig
import com.devapplab.service.pii.PiiCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import java.util.Base64

class PiiCryptoTest {
    private val crypto = PiiCrypto(
        PiiCryptoConfig(
            encryptionKeyBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            lookupPepperBase64 = "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
            keyVersion = "v1"
        )
    )

    @Test
    fun `encrypts the same value differently and decrypts both values`() {
        val firstCiphertext = crypto.encrypt("person@example.com")
        val secondCiphertext = crypto.encrypt("person@example.com")

        assertNotEquals(firstCiphertext, secondCiphertext)
        assertEquals("person@example.com", crypto.decrypt(firstCiphertext, "v1"))
        assertEquals("person@example.com", crypto.decrypt(secondCiphertext, "v1"))
    }

    @Test
    fun `email lookup is stable after normalization`() {
        assertEquals(
            crypto.emailLookup("person@example.com"),
            crypto.emailLookup(" Person@Example.COM ")
        )
    }

    @Test
    fun `phone lookup is stable after trimming`() {
        assertEquals(
            crypto.phoneLookup("+525512345678"),
            crypto.phoneLookup(" +525512345678 ")
        )
    }

    @Test
    fun `accepts standard Base64 configuration keys`() {
        val standardBase64Key = Base64.getEncoder().encodeToString(ByteArray(32) { 0xFB.toByte() })

        PiiCrypto(
            PiiCryptoConfig(
                encryptionKeyBase64 = standardBase64Key,
                lookupPepperBase64 = standardBase64Key,
                keyVersion = "v1"
            )
        )
    }
}
