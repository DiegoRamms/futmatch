package com.devapplab.features.device

import com.devapplab.model.device.ApproveDesktopEnrollmentRequest
import com.devapplab.model.device.CreateDesktopEnrollmentRequest
import com.devapplab.model.device.DesktopEnrollmentCreationProof
import com.devapplab.model.device.DesktopEnrollmentDetailsRequest
import com.devapplab.model.device.DesktopEnrollmentMetadata
import com.devapplab.observability.requestContext
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
    suspend fun createEnrollment(call: ApplicationCall) {
        val request = call.receive<CreateDesktopEnrollmentRequest>()
        val proof = DesktopEnrollmentCreationProof(
            timestamp = call.request.header("X-Desktop-Timestamp"),
            requestId = call.request.header("X-Desktop-Request-Id"),
            signature = call.request.header("X-Desktop-Signature"),
            method = call.request.httpMethod.value,
            path = call.request.path()
        )
        val context = call.requestContext()
        val metadata = DesktopEnrollmentMetadata(
            deviceInfo = call.request.header("User-Agent"),
            appVersion = context.appVersion,
            osVersion = context.osVersion
        )
        call.respond(securityService.createEnrollment(request, proof, metadata, call.retrieveLocale()))
    }

    suspend fun approveFromMobile(call: ApplicationCall, adminId: UUID) {
        val request = call.receive<ApproveDesktopEnrollmentRequest>()
        call.respond(enrollmentService.approveFromMobile(request, adminId, call.retrieveLocale()))
    }

    suspend fun enrollmentDetails(call: ApplicationCall) {
        val request = call.receive<DesktopEnrollmentDetailsRequest>()
        call.respond(enrollmentService.detailsForMobile(request, call.retrieveLocale()))
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
