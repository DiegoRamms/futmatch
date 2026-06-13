package com.devapplab.features.device

import com.devapplab.config.getIdentifier
import com.devapplab.config.getOptionalIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.device.UpdateFcmTokenRequest
import com.devapplab.service.device.DeviceService
import com.devapplab.utils.InvalidTokenException
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*
import org.slf4j.LoggerFactory
import java.util.Locale

class DeviceController(private val deviceService: DeviceService) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun updateFcmToken(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<UpdateFcmTokenRequest>()
        val deviceIdFromJwt = call.getOptionalIdentifier(ClaimType.DEVICE_IDENTIFIER)
        val resolvedDeviceId = if (deviceIdFromJwt != null) {
            deviceIdFromJwt
        } else {
            // TODO: Remove legacy deviceId fallback once old access JWTs without device_identifier are no longer active.
            val legacyDeviceId = request.deviceId
                ?: throw InvalidTokenException("Missing device identifier for device update")
            logger.warn(
                "Deprecated legacy device/fcm-token payload used because access JWT has no device_identifier claim. userId={} deviceId={}",
                userId,
                legacyDeviceId
            )
            legacyDeviceId
        }
        val result = deviceService.updateFcmToken(request, locale, userId, resolvedDeviceId)
        call.respond(result)
    }
}
