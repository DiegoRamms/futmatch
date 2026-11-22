package com.devapplab.features.training

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.trainingRouting() {
    route("trainings") {
        get { call.scope.get<TrainingController>().list(call) }
        get("booked") { call.scope.get<TrainingController>().booked(call) }
        get("{trainingId}") { call.scope.get<TrainingController>().detail(call) }
        post("{trainingId}/join") { call.scope.get<TrainingController>().join(call) }
        post("{trainingId}/payment-status") { call.scope.get<TrainingController>().refreshPayment(call) }
        delete("{trainingId}/join") { call.scope.get<TrainingController>().leave(call) }
        route("admin") {
            get {
                call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
                call.scope.get<TrainingController>().owned(call)
            }
            post {
                call.requireRole(UserRole.ADMIN)
                call.scope.get<TrainingController>().create(call)
            }
            put("{trainingId}") {
                call.requireRole(UserRole.ADMIN)
                call.scope.get<TrainingController>().edit(call)
            }
            post("{trainingId}/publish") {
                call.requireRole(UserRole.ADMIN)
                call.scope.get<TrainingController>().publish(call)
            }
            delete("{trainingId}") {
                call.requireRole(UserRole.ADMIN)
                call.scope.get<TrainingController>().deleteDraft(call)
            }
        }
    }
}
