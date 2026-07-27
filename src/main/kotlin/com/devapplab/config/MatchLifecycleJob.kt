package com.devapplab.config

import com.devapplab.service.scheduler.MatchLifecycleScheduler
import io.ktor.server.application.*

fun Application.configureMatchLifecycleJob() {
    monitor.subscribe(ApplicationStarted) {
        MatchLifecycleScheduler.start()
    }
    monitor.subscribe(ApplicationStopping) {
        MatchLifecycleScheduler.stop()
    }
}
