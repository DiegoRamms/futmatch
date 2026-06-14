package com.devapplab.service.device

import com.devapplab.config.dbQuery
import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.model.AppResult
import com.devapplab.model.auth.response.SimpleResponse
import com.devapplab.model.device.UpdateFcmTokenRequest
import com.devapplab.observability.AppRequestContext
import com.devapplab.observability.appRejected
import com.devapplab.observability.appSuccess
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.Locale

class DeviceService(private val deviceRepository: DeviceRepository) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun updateFcmToken(
        request: UpdateFcmTokenRequest,
        locale: Locale,
        userId: UUID,
        deviceId: UUID,
        context: AppRequestContext
    ): AppResult<SimpleResponse> {
        val isValidDeviceForUser = dbQuery {
            deviceRepository.isValidDeviceIdForUser(deviceId, userId)
        }
        if (!isValidDeviceForUser) {
            logger.appRejected(
                event = "device.fcm_token.update_failed",
                context = context,
                reason = "device_not_owned",
                userId = userId,
                deviceId = deviceId,
                statusCode = HttpStatusCode.Forbidden.value
            )
            return locale.createError(
                titleKey = StringResourcesKey.AUTH_DEVICE_ID_INVALID,
                descriptionKey = StringResourcesKey.AUTH_DEVICE_ID_INVALID,
                status = HttpStatusCode.Forbidden
            )
        }

        val updated = deviceRepository.updateDeviceFcmToken(
            deviceId = deviceId,
            platform = request.platform,
            fcmToken = request.fcmToken,
            deviceInfo = request.deviceInfo,
            appVersion = request.appVersion,
            osVersion = request.osVersion
        )

        return if (updated) {
            logger.appSuccess(
                event = "device.fcm_token.updated",
                context = context,
                userId = userId,
                deviceId = deviceId,
                statusCode = HttpStatusCode.OK.value,
                extra = mapOf("platform" to request.platform)
            )
            AppResult.Success(
                SimpleResponse(
                    success = true,
                    message = locale.getString(StringResourcesKey.DEVICE_UPDATE_SUCCESS_MESSAGE)
                )
            )
        } else {
            logger.appRejected(
                event = "device.fcm_token.update_failed",
                context = context,
                reason = "device_update_failed",
                userId = userId,
                deviceId = deviceId,
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("platform" to request.platform)
            )
            locale.createError(
                titleKey = StringResourcesKey.DEVICE_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.DEVICE_UPDATE_FAILED_DESCRIPTION
            )
        }
    }
}
