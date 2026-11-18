package com.devapplab.config

import com.devapplab.service.scheduler.MatchInviteScheduler
import io.ktor.server.application.*

fun Application.configureMatchInviteJob() {
    monitor.subscribe(ApplicationStarted) {
        MatchInviteScheduler.start()
    }
    monitor.subscribe(ApplicationStopping) {
        MatchInviteScheduler.stop()
    }
}
