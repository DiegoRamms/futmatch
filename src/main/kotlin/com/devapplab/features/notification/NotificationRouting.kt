package com.devapplab.features.notification

import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.notificationRouting() {
    route("/notification") {
        get("/") {
            val controller = call.scope.get<NotificationController>()
            controller.getNotifications(call)
        }
        delete("/{notificationId}") {
            val controller = call.scope.get<NotificationController>()
            controller.deleteNotification(call)
        }
    }
}
