package com.devapplab.service.scheduler

import com.devapplab.service.clean.CleanupDataService
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days

object DataCleanupScheduler : KoinComponent {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val schedulerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val cleanupDataService: CleanupDataService by inject()

    private val INTERVAL_MS = 1.days.inWholeMilliseconds

    fun start() {
        if (job?.isActive == true) return

        job = schedulerScope.launch {
            logger.info("🧹 DataCleanupScheduler started: Cleaning up data every 24h")
            while (isActive) {
                try {
                    logger.info("🧹 Running data cleanup...")
                    cleanupDataService.cleanupData()
                    cleanupDataService.cleanupExpiredPendingRegistrations()
                    logger.info("✅ Data cleanup completed successfully")
                } catch (e: Exception) {
                    logger.error("❌ Error in data cleanup cycle", e)
                }
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        logger.info("🛑 DataCleanupScheduler stopped")
    }
}