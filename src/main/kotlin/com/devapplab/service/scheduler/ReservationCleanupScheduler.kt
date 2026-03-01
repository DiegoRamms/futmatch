package com.devapplab.service.scheduler

import com.devapplab.service.match.MatchService
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

object ReservationCleanupScheduler : KoinComponent {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val schedulerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val matchService: MatchService by inject()
    
    private val INTERVAL_MS = 2.minutes.inWholeMilliseconds

    fun start() {
        if (job?.isActive == true) return

        job = schedulerScope.launch {
            logger.info("🧹 ReservationCleanupScheduler started: Cleaning expired reservations every 2 min")
            while (isActive) {
                try {
                    matchService.processExpiredReservations()
                } catch (e: Exception) {
                    logger.error("❌ Error in reservation cleanup cycle", e)
                }
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        logger.info("🛑 ReservationCleanupScheduler stopped")
    }
}