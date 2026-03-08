package com.devapplab.features.device

import com.devapplab.model.device.UpdateFcmTokenRequest
import com.devapplab.service.device.DeviceService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.util.Locale

class DeviceController(private val deviceService: DeviceService) {

    suspend fun updateFcmToken(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val request = call.receive<UpdateFcmTokenRequest>()
        val result = deviceService.updateFcmToken(request, locale)
        call.respond(result)
    }
}