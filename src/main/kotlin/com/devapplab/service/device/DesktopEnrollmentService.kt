package com.devapplab.service.device

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.device.DesktopEnrollmentRepository
import com.devapplab.model.AppResult
import com.devapplab.model.device.ApproveDesktopEnrollmentRequest
import com.devapplab.model.device.DesktopEnrollmentDetailsRequest
import com.devapplab.model.device.DesktopEnrollmentDetailsResponse
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import java.util.Locale
import java.util.UUID

/** Application service for endpoint-facing enrollment operations. */
class DesktopEnrollmentService(
    private val dbExecutor: DbExecutor,
    private val enrollmentRepository: DesktopEnrollmentRepository,
    private val securityService: DesktopDeviceSecurityService
) {
    suspend fun detailsForMobile(
        request: DesktopEnrollmentDetailsRequest,
        locale: Locale
    ): AppResult<DesktopEnrollmentDetailsResponse> = runCatching {
        val details = enrollmentRepository.findDetails(
            enrollmentId = UUID.fromString(request.enrollmentId),
            nonce = UUID.fromString(request.nonce),
            now = System.currentTimeMillis()
        ) ?: return@runCatching null
        DesktopEnrollmentDetailsResponse(details.deviceInfo, details.appVersion, details.osVersion)
    }.fold(
        onSuccess = { details ->
            details?.let { AppResult.Success(it) } ?: locale.createError(status = HttpStatusCode.NotFound)
        },
        onFailure = { locale.createError(status = HttpStatusCode.NotFound) }
    )

    suspend fun approveFromMobile(
        request: ApproveDesktopEnrollmentRequest,
        adminId: UUID,
        locale: Locale
    ): AppResult<Unit> = runCatching {
        val enrollmentId = UUID.fromString(request.enrollmentId)
        val nonce = UUID.fromString(request.nonce)
        dbExecutor.tx {
            enrollmentRepository.consumeAndApprove(enrollmentId, nonce, adminId, adminId, System.currentTimeMillis())
        }
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
