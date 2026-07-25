package com.devapplab.features.device

import com.devapplab.model.device.CreateDesktopEnrollmentRequest
import com.devapplab.service.device.DesktopEnrollmentService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.util.*

class DesktopDeviceController(private val enrollmentService: DesktopEnrollmentService) {
    suspend fun createEnrollment(call: ApplicationCall, adminId: UUID) {
        val request = call.receive<CreateDesktopEnrollmentRequest>()
        call.respond(enrollmentService.create(request, adminId, call.retrieveLocale()))
    }

    suspend fun pendingEnrollments(call: ApplicationCall) = call.respond(enrollmentService.pending(call.retrieveLocale()))

    suspend fun approve(call: ApplicationCall, adminId: UUID, deviceId: UUID) {
        call.respond(enrollmentService.approve(deviceId, adminId, call.retrieveLocale()))
    }

    suspend fun reject(call: ApplicationCall, adminId: UUID, deviceId: UUID) {
        call.respond(enrollmentService.reject(deviceId, adminId, call.retrieveLocale()))
    }

    suspend fun revoke(call: ApplicationCall, deviceId: UUID) {
        call.respond(enrollmentService.revoke(deviceId, call.retrieveLocale()))
    }

    suspend fun enrollmentStatus(call: ApplicationCall, status: com.devapplab.model.device.DesktopEnrollmentStatus?) {
        call.respond(enrollmentService.status(status, call.retrieveLocale()))
    }
}
