package com.devapplab.service.scheduler

import com.devapplab.service.match.MatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

object MatchLifecycleScheduler : KoinComponent {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val schedulerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val matchService: MatchService by inject()

    private val intervalMs = 15.minutes.inWholeMilliseconds

    fun start() {
        if (job?.isActive == true) return

        job = schedulerScope.launch {
            logger.info("MatchLifecycleScheduler started: Synchronizing match statuses every 15 min")
            while (isActive) {
                try {
                    matchService.synchronizeMatchStatuses()
                } catch (e: Exception) {
                    logger.error("Error in match lifecycle synchronization cycle", e)
                }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        logger.info("MatchLifecycleScheduler stopped")
    }
}
