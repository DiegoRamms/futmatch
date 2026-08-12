package com.devapplab.features.admin

import com.devapplab.service.admin.DashboardService
import com.devapplab.utils.respond
import io.ktor.server.application.ApplicationCall

class AdminDashboardController(private val service: DashboardService) {
    suspend fun getSummary(call: ApplicationCall) {
        call.respond(service.getSummary())
    }
}
