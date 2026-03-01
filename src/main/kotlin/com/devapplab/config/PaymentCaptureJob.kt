package com.devapplab.config

import com.devapplab.service.scheduler.PaymentCaptureScheduler
import io.ktor.server.application.*

fun Application.configurePaymentCaptureJob() {
    monitor.subscribe(ApplicationStarted) {
        PaymentCaptureScheduler.start()
    }
    monitor.subscribe(ApplicationStopping) {
        PaymentCaptureScheduler.stop()
    }
}
