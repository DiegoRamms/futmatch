package com.devapplab.features.training

import com.devapplab.config.getIdentifier
import com.devapplab.config.getRole
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.training.TrainingRequest
import com.devapplab.service.training.TrainingEnrollmentService
import com.devapplab.service.training.TrainingService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive

class TrainingController(
    private val service: TrainingService,
    private val enrollments: TrainingEnrollmentService
) {
    suspend fun join(call: ApplicationCall) =
        call.respond(enrollments.join(call.trainingId(), call.viewer(), call.retrieveLocale()))

    suspend fun refreshPayment(call: ApplicationCall) =
        call.respond(enrollments.refreshPayment(call.trainingId(), call.viewer()))

    suspend fun leave(call: ApplicationCall) =
        call.respond(enrollments.leave(call.trainingId(), call.viewer(), call.retrieveLocale()))

    suspend fun list(call: ApplicationCall) = call.respond(service.list(call.viewer()))

    suspend fun owned(call: ApplicationCall) = call.respond(service.list(call.viewer(), owner = true))

    suspend fun booked(call: ApplicationCall) = call.respond(service.list(call.viewer(), booked = true))

    suspend fun detail(call: ApplicationCall) =
        call.respond(service.detail(call.trainingId(), call.viewer(), call.retrieveLocale()))

    suspend fun create(call: ApplicationCall) =
        call.respond(service.save(null, call.viewer(), call.getRole(), call.receive<TrainingRequest>(), call.retrieveLocale()))

    suspend fun edit(call: ApplicationCall) =
        call.respond(service.save(call.trainingId(), call.viewer(), call.getRole(), call.receive<TrainingRequest>(), call.retrieveLocale()))

    suspend fun publish(call: ApplicationCall) =
        call.respond(service.publish(call.trainingId(), call.viewer(), call.retrieveLocale()))

    suspend fun deleteDraft(call: ApplicationCall) =
        call.respond(service.deleteDraft(call.trainingId(), call.viewer(), call.retrieveLocale()))

    private fun ApplicationCall.viewer() = getIdentifier(ClaimType.USER_IDENTIFIER)

    private fun ApplicationCall.trainingId() =
        parameters["trainingId"]?.toUUIDOrNull() ?: throw BadRequestException("Invalid training ID")
}
