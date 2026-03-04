package com.devapplab.features.cron

import com.devapplab.config.RateLimitType
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.cronRouting() {
    val controller by inject<CronController>()

    route("/cron") {
        rateLimit(RateLimitName(RateLimitType.CRON_JOB.value)) {
            get("/ping") {
                controller.ping(call)
            }
        }
    }
}
