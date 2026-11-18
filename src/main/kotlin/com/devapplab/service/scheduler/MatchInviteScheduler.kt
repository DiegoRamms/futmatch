package com.devapplab.service.scheduler

import com.devapplab.model.MatchInviteConfig
import com.devapplab.service.match.MatchInviteService
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

object MatchInviteScheduler : KoinComponent {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val schedulerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val matchInviteService: MatchInviteService by inject()
    private val config: MatchInviteConfig by inject()

    fun start() {
        if (job?.isActive == true) return
        if (!config.enabled) {
            logger.info("MatchInviteScheduler disabled by config")
            return
        }

        job = schedulerScope.launch {
            logger.info(
                "MatchInviteScheduler started: milestones={}h every {} min",
                config.milestonesHours, config.intervalMinutes
            )
            while (isActive) {
                try {
                    matchInviteService.sendMatchInvites()
                } catch (e: Exception) {
                    logger.error("Error in match invite cycle", e)
                }
                delay(config.intervalMinutes.minutes)
            }
        }
    }

    fun stop() {
        job?.cancel()
        logger.info("MatchInviteScheduler stopped")
    }
}
