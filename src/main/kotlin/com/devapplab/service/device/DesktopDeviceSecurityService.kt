package com.devapplab.service.device

import com.devapplab.data.repository.device.DesktopDeviceRepository
import com.devapplab.model.AppResult
import com.devapplab.model.device.DesktopEnrollmentStatusProof
import com.devapplab.model.device.DesktopEnrollmentStatus
import com.devapplab.model.device.DesktopEnrollmentStatusResponse
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import java.util.UUID
import java.util.Locale

class DesktopDeviceSecurityService(private val repository: DesktopDeviceRepository) {
    suspend fun registerApprovedDevice(
        deviceId: String,
        publicKey: String,
        label: String,
        nonce: String,
        expiresAt: Long,
        ownerUserId: UUID
    ): Boolean {
        val normalizedLabel = label.trim()
        require(normalizedLabel.isNotBlank() && normalizedLabel.length <= 120) { "Invalid device label" }
        parsePublicKey(publicKey)
        val id = UUID.fromString(deviceId)
        UUID.fromString(nonce)
        val now = System.currentTimeMillis()
        require(expiresAt > now && expiresAt <= now + ENROLLMENT_TTL_MS) { "Invalid enrollment expiration" }
        return repository.registerApprovedDevice(id, publicKey, normalizedLabel, ownerUserId, ownerUserId, now)
    }

    /** This is used before the desktop has a JWT; the approved device key authenticates the polling client. */
    suspend fun enrollmentStatus(
        proof: DesktopEnrollmentStatusProof,
        locale: Locale
    ): AppResult<DesktopEnrollmentStatusResponse> {
        val status = verifiedEnrollmentStatus(proof)
        return status?.let { AppResult.Success(DesktopEnrollmentStatusResponse(it)) }
            ?: locale.createError(status = HttpStatusCode.Forbidden)
    }

    private suspend fun verifiedEnrollmentStatus(proof: DesktopEnrollmentStatusProof): DesktopEnrollmentStatus? {
        val id = runCatching { UUID.fromString(proof.deviceId) }.getOrNull() ?: return null
        val at = proof.timestamp?.toLongOrNull() ?: return null
        if (kotlin.math.abs(System.currentTimeMillis() - at) > MAX_CLOCK_SKEW_MS) return null
        val nonce = proof.requestId?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: return null
        val encodedSignature = proof.signature ?: return null
        val enrollment = repository.getEnrollment(id) ?: return null
        val status = enrollment.status
        if (status != DesktopEnrollmentStatus.ACTIVE) return null
        val canonical = "${proof.method}\n${proof.path}\n$at\n$nonce\n$EMPTY_BODY_SHA256".toByteArray()
        return status.takeIf {
            verifySignature(enrollment.publicKey, encodedSignature, canonical)
        }
    }

    suspend fun revoke(deviceId: UUID): Boolean = repository.revokeDevice(deviceId, System.currentTimeMillis())

    suspend fun verify(
        deviceId: String?,
        timestamp: String?,
        requestId: String?,
        signature: String?,
        method: String,
        path: String,
        bodyHash: String?
    ): UUID? {
        val id = runCatching { UUID.fromString(deviceId) }.getOrNull() ?: return null
        val at = timestamp?.toLongOrNull() ?: return null
        val now = System.currentTimeMillis()
        if (kotlin.math.abs(now - at) > MAX_CLOCK_SKEW_MS) return null
        val nonce = requestId?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: return null
        val encodedSignature = signature ?: return null
        val hash = bodyHash ?: return null
        val key = repository.getActivePublicKey(id) ?: return null
        val canonical = "$method\n$path\n$at\n$nonce\n$hash".toByteArray()
        val valid = verifySignature(key, encodedSignature, canonical)
        if (!valid) return null
        return id.takeIf { repository.claimRequestNonce(id, UUID.fromString(nonce), now + NONCE_TTL_MS, now) }
    }

    private fun verifySignature(publicKey: String, encodedSignature: String, canonical: ByteArray): Boolean =
        runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(parsePublicKey(publicKey))
                update(canonical)
                verify(rawP256SignatureToDer(Base64.getDecoder().decode(encodedSignature)))
            }
        }.getOrDefault(false)

    private fun parsePublicKey(base64: String): ECPublicKey {
        val bytes = Base64.getDecoder().decode(base64)
        require(bytes.size == 65 && bytes[0] == 0x04.toByte()) { "Expected an uncompressed P-256 public key" }
        val parameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
        val spec = parameters.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        val point = ECPoint(BigInteger(1, bytes.copyOfRange(1, 33)), BigInteger(1, bytes.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, spec)) as ECPublicKey
    }

    private fun rawP256SignatureToDer(raw: ByteArray): ByteArray {
        require(raw.size == 64) { "Expected a 64-byte P-256 signature" }
        fun integer(bytes: ByteArray): ByteArray {
            val trimmed = bytes.dropWhile { it == 0.toByte() }.toByteArray()
            val unsigned = if (trimmed.isEmpty()) byteArrayOf(0) else trimmed
            val value = if (unsigned[0].toInt() and 0x80 != 0) byteArrayOf(0) + unsigned else unsigned
            return byteArrayOf(0x02, value.size.toByte()) + value
        }
        val r = integer(raw.copyOfRange(0, 32)); val s = integer(raw.copyOfRange(32, 64)); val sequence = r + s
        return byteArrayOf(0x30, sequence.size.toByte()) + sequence
    }

    private companion object {
        const val MAX_CLOCK_SKEW_MS = 300_000L
        const val NONCE_TTL_MS = 300_000L
        const val ENROLLMENT_TTL_MS = 600_000L
        const val EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
