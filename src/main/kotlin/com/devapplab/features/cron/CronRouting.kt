package com.devapplab.features.cron

import com.devapplab.config.RateLimitType
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.cronRouting() {
    route("/cron") {
        rateLimit(RateLimitName(RateLimitType.CRON_JOB.value)) {
            get("/ping") {
                val controller = call.scope.get<CronController>()
                controller.ping(call)
            }
        }
    }
}
