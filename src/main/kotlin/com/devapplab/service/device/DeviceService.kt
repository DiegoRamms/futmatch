package com.devapplab.service.device

import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.model.AppResult
import com.devapplab.model.auth.response.SimpleResponse
import com.devapplab.model.device.UpdateFcmTokenRequest
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.HttpStatusCode
import java.util.UUID
import java.util.Locale

class DeviceService(private val deviceRepository: DeviceRepository) {

    suspend fun updateFcmToken(
        request: UpdateFcmTokenRequest,
        locale: Locale,
        userId: UUID,
        deviceId: UUID
    ): AppResult<SimpleResponse> {
        val isValidDeviceForUser = deviceRepository.isValidDeviceIdForUser(deviceId, userId)
        if (!isValidDeviceForUser) {
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
            AppResult.Success(
                SimpleResponse(
                    success = true,
                    message = locale.getString(StringResourcesKey.DEVICE_UPDATE_SUCCESS_MESSAGE)
                )
            )
        } else {
            locale.createError(
                titleKey = StringResourcesKey.DEVICE_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.DEVICE_UPDATE_FAILED_DESCRIPTION
            )
        }
    }
}
