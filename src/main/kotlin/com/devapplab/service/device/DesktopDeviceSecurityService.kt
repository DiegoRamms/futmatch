package com.devapplab.service.device

import com.devapplab.data.repository.device.DesktopDeviceRepository
import com.devapplab.data.repository.device.DesktopEnrollmentRecord
import com.devapplab.data.repository.device.DesktopEnrollmentRepository
import com.devapplab.model.AppResult
import com.devapplab.model.device.CreateDesktopEnrollmentRequest
import com.devapplab.model.device.CreateDesktopEnrollmentResponse
import com.devapplab.model.device.DesktopEnrollmentCreationProof
import com.devapplab.model.device.DesktopEnrollmentStatusProof
import com.devapplab.model.device.DesktopEnrollmentStatus
import com.devapplab.model.device.DesktopEnrollmentStatusResponse
import com.devapplab.model.device.PendingDesktopEnrollment
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
import org.slf4j.LoggerFactory

class DesktopDeviceSecurityService(
    private val repository: DesktopDeviceRepository,
    private val enrollmentRepository: DesktopEnrollmentRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun createEnrollment(
        request: CreateDesktopEnrollmentRequest,
        proof: DesktopEnrollmentCreationProof,
        locale: Locale
    ): AppResult<CreateDesktopEnrollmentResponse> = runCatching {
        val normalizedLabel = request.label.trim()
        require(normalizedLabel.isNotBlank() && normalizedLabel.length <= 120) { "Invalid device label" }
        val publicKey = parsePublicKey(request.publicKey)
        val deviceId = UUID.fromString(request.deviceId)
        val timestamp = proof.timestamp?.toLongOrNull() ?: error("Invalid desktop timestamp")
        val requestId = proof.requestId?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
            ?: error("Invalid desktop request id")
        require(kotlin.math.abs(System.currentTimeMillis() - timestamp) <= MAX_CLOCK_SKEW_MS) { "Expired desktop proof" }
        val signature = proof.signature ?: error("Missing desktop signature")
        val canonical = "${proof.method}\n${proof.path}\n$timestamp\n$requestId\n${request.deviceId}\n${request.publicKey}\n$normalizedLabel".toByteArray()
        require(verifySignature(publicKey, signature, canonical)) { "Invalid desktop proof" }
        val now = System.currentTimeMillis()
        val enrollment = PendingDesktopEnrollment(
            id = UUID.randomUUID(),
            deviceId = deviceId,
            publicKey = request.publicKey,
            label = normalizedLabel,
            nonce = UUID.randomUUID(),
            expiresAt = now + ENROLLMENT_TTL_MS,
            createdAt = now
        )
        require(enrollmentRepository.createPending(enrollment)) { "Desktop enrollment already exists" }
        CreateDesktopEnrollmentResponse(
            enrollmentId = enrollment.id.toString(),
            deviceId = enrollment.deviceId.toString(),
            nonce = enrollment.nonce.toString(),
            expiresAt = enrollment.expiresAt
        )
    }.fold(
        onSuccess = { AppResult.Success(it, HttpStatusCode.Created) },
        onFailure = { error ->
            logger.warn("Desktop enrollment creation rejected: reason={}", error.message)
            locale.createError(status = HttpStatusCode.Conflict)
        }
    )

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
        val enrollment = repository.getEnrollment(id) ?: enrollmentRepository.findByDeviceId(id, System.currentTimeMillis())
            ?.let { DesktopEnrollmentRecord(it.publicKey, DesktopEnrollmentStatus.PENDING) }
            ?: return null
        val status = enrollment.status
        val canonical = "${proof.method}\n${proof.path}\n$at\n$nonce\n$EMPTY_BODY_SHA256".toByteArray()
        return status.takeIf {
            verifySignature(parsePublicKey(enrollment.publicKey), encodedSignature, canonical)
        }
    }

    suspend fun revoke(deviceId: UUID): Boolean = repository.revokeDevice(deviceId, System.currentTimeMillis())

    suspend fun isDesktopDevice(deviceId: UUID): Boolean = repository.isDesktopDevice(deviceId)

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
        runCatching { parsePublicKey(publicKey) }.getOrNull()?.let { key ->
            verifySignature(key, encodedSignature, canonical)
        } ?: false

    private fun verifySignature(publicKey: ECPublicKey, encodedSignature: String, canonical: ByteArray): Boolean =
        runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(canonical)
                verify(p256SignatureForJava(Base64.getDecoder().decode(encodedSignature)))
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

    /**
     * Windows/TPM bridges commonly expose P-256 signatures as raw `r || s` (64 bytes),
     * while macOS Security.framework returns the X9.62 ASN.1/DER form. Java's provider
     * expects DER, so normalize raw signatures and retain a valid DER signature as-is.
     */
    private fun p256SignatureForJava(signature: ByteArray): ByteArray =
        if (signature.size == P256_RAW_SIGNATURE_SIZE) rawP256SignatureToDer(signature) else signature

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
        const val P256_RAW_SIGNATURE_SIZE = 64
        const val EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
