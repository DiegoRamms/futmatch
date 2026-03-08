package com.devapplab.service.device

import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.model.AppResult
import com.devapplab.model.auth.response.SimpleResponse
import com.devapplab.model.device.UpdateFcmTokenRequest
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import java.util.Locale

class DeviceService(private val deviceRepository: DeviceRepository) {

    suspend fun updateFcmToken(request: UpdateFcmTokenRequest, locale: Locale): AppResult<SimpleResponse> {
        val updated = deviceRepository.updateDeviceFcmToken(
            deviceId = request.deviceId,
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