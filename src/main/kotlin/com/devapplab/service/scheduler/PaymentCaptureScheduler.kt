package com.devapplab.service.scheduler

import com.devapplab.service.match.MatchService
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

object PaymentCaptureScheduler : KoinComponent {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val schedulerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val matchService: MatchService by inject()

    private val INTERVAL_MS = 15.minutes.inWholeMilliseconds

    fun start() {
        if (job?.isActive == true) return

        job = schedulerScope.launch {
            logger.info("🚀 PaymentCaptureScheduler started: Validating payments every 15 min")
            while (isActive) {
                try {
                    runCaptureProcess()
                } catch (e: Exception) {
                    logger.error("❌ Error in payment capture cycle", e)
                }
                delay(INTERVAL_MS)
            }
        }
    }

    private suspend fun runCaptureProcess() {
        logger.info("⏰ Running 6-hour window payment validation...")
        matchService.capturePendingPayments()
    }

    fun stop() {
        job?.cancel()
        logger.info("🛑 PaymentCaptureScheduler stopped")
    }
}