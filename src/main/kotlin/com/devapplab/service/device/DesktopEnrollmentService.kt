package com.devapplab.service.device

import com.devapplab.model.AppResult
import com.devapplab.model.device.CreateDesktopEnrollmentRequest
import com.devapplab.model.device.DesktopEnrollmentRequestResponse
import com.devapplab.model.device.DesktopEnrollmentStatus
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import java.util.Locale
import java.util.UUID

/** Application service for endpoint-facing enrollment operations. */
class DesktopEnrollmentService(private val securityService: DesktopDeviceSecurityService) {
    suspend fun create(request: CreateDesktopEnrollmentRequest, adminId: UUID, locale: Locale): AppResult<Unit> = runCatching {
        securityService.createEnrollmentRequest(request.deviceId, request.publicKey, request.label, request.ownerUserId, request.nonce, request.expiresAt, adminId)
        AppResult.Success(Unit, HttpStatusCode.Accepted)
    }.getOrElse { locale.createError() }

    suspend fun pending(locale: Locale): AppResult<List<DesktopEnrollmentRequestResponse>> = runCatching {
        AppResult.Success(securityService.pendingEnrollments())
    }.getOrElse { locale.createError() }

    suspend fun approve(deviceId: UUID, adminId: UUID, locale: Locale): AppResult<Unit> =
        if (securityService.approve(deviceId, adminId)) AppResult.Success(Unit, HttpStatusCode.NoContent) else locale.createError(status = HttpStatusCode.NotFound)

    suspend fun reject(deviceId: UUID, adminId: UUID, locale: Locale): AppResult<Unit> =
        if (securityService.reject(deviceId, adminId)) AppResult.Success(Unit, HttpStatusCode.NoContent) else locale.createError(status = HttpStatusCode.NotFound)

    suspend fun revoke(deviceId: UUID, locale: Locale): AppResult<Unit> =
        if (securityService.revoke(deviceId)) AppResult.Success(Unit, HttpStatusCode.NoContent) else locale.createError(status = HttpStatusCode.NotFound)

    fun status(status: DesktopEnrollmentStatus?, locale: Locale): AppResult<com.devapplab.model.device.DesktopEnrollmentStatusResponse> =
        status?.let { AppResult.Success(com.devapplab.model.device.DesktopEnrollmentStatusResponse(it)) }
            ?: locale.createError(status = HttpStatusCode.Forbidden)
}
