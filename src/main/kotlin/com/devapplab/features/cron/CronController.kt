package com.devapplab.features.cron

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

class CronController {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun ping(call: ApplicationCall) {
        logger.info("Cron job ping received: success")
        call.respond(HttpStatusCode.OK, "success")
    }
}
