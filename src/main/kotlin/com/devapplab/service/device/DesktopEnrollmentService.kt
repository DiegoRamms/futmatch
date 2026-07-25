package com.devapplab.service.device

import com.devapplab.model.AppResult
import com.devapplab.model.device.ApproveDesktopEnrollmentRequest
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import java.util.Locale
import java.util.UUID

/** Application service for endpoint-facing enrollment operations. */
class DesktopEnrollmentService(private val securityService: DesktopDeviceSecurityService) {
    suspend fun approveFromMobile(
        request: ApproveDesktopEnrollmentRequest,
        adminId: UUID,
        locale: Locale
    ): AppResult<Unit> = runCatching {
        securityService.registerApprovedDevice(
            deviceId = request.deviceId,
            publicKey = request.publicKey,
            label = request.label,
            nonce = request.nonce,
            expiresAt = request.expiresAt,
            ownerUserId = adminId
        )
    }.fold(
        onSuccess = { created ->
            if (created) AppResult.Success(Unit, HttpStatusCode.Created)
            else locale.createError(status = HttpStatusCode.Conflict)
        },
        onFailure = { locale.createError() }
    )

    suspend fun revoke(deviceId: UUID, locale: Locale): AppResult<Unit> =
        if (securityService.revoke(deviceId)) AppResult.Success(Unit, HttpStatusCode.NoContent) else locale.createError(
            status = HttpStatusCode.NotFound
        )

}
