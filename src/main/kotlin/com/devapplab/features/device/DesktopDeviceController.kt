package com.devapplab.features.device

import com.devapplab.model.device.ApproveDesktopEnrollmentRequest
import com.devapplab.model.device.DesktopEnrollmentStatusProof
import com.devapplab.service.device.DesktopDeviceSecurityService
import com.devapplab.service.device.DesktopEnrollmentService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.util.*

class DesktopDeviceController(
    private val enrollmentService: DesktopEnrollmentService,
    private val securityService: DesktopDeviceSecurityService
) {
    suspend fun approveFromMobile(call: ApplicationCall, adminId: UUID) {
        val request = call.receive<ApproveDesktopEnrollmentRequest>()
        call.respond(enrollmentService.approveFromMobile(request, adminId, call.retrieveLocale()))
    }

    suspend fun revoke(call: ApplicationCall, deviceId: UUID) {
        call.respond(enrollmentService.revoke(deviceId, call.retrieveLocale()))
    }

    suspend fun enrollmentStatus(call: ApplicationCall) {
        val proof = DesktopEnrollmentStatusProof(
            deviceId = call.parameters["deviceId"],
            timestamp = call.request.header("X-Desktop-Timestamp"),
            requestId = call.request.header("X-Desktop-Request-Id"),
            signature = call.request.header("X-Desktop-Signature"),
            method = call.request.httpMethod.value,
            path = call.request.path()
        )
        call.respond(securityService.enrollmentStatus(proof, call.retrieveLocale()))
    }
}
