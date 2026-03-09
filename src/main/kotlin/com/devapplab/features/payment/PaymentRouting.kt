package com.devapplab.features.payment

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.paymentsRouting() {
    route("payment") {

        // CustomerSheet init
        post("customer-sheet/init") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val controller = call.scope.get<PaymentController>()
            controller.initCustomerSheet(call)
        }

        post("setup-intent") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val controller = call.scope.get<PaymentController>()
            controller.createSetupIntent(call)
        }

        get("methods") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val controller = call.scope.get<PaymentController>()
            controller.listMethods(call)
        }

        delete("methods/{paymentMethodId}") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val controller = call.scope.get<PaymentController>()
            controller.detachMethod(call)
        }

        get("status/{matchId}") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val controller = call.scope.get<PaymentController>()
            controller.recoverPaymentStatus(call)
        }
    }
}
