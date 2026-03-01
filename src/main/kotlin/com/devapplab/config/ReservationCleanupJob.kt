package com.devapplab.config

import com.devapplab.service.scheduler.ReservationCleanupScheduler
import io.ktor.server.application.*

fun Application.configureReservationCleanupJob() {
    monitor.subscribe(ApplicationStarted) {
        ReservationCleanupScheduler.start()
    }
    monitor.subscribe(ApplicationStopping) {
        ReservationCleanupScheduler.stop()
    }
}
