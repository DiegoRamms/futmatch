package com.devapplab.features.device

import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.deviceRouting() {
    route("/device") {
        put("/fcm-token") {
            val deviceController = call.scope.get<DeviceController>()
            deviceController.updateFcmToken(call)
        }
    }
}