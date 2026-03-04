package com.devapplab.config

import com.devapplab.service.scheduler.DataCleanupScheduler
import io.ktor.server.application.*

fun Application.scheduleCleanUp() {
    monitor.subscribe(ApplicationStarted) {
        DataCleanupScheduler.start()
    }
    monitor.subscribe(ApplicationStopping) {
        DataCleanupScheduler.stop()
    }
}