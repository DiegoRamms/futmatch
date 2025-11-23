package com.devapplab.config

import com.devapplab.service.clean.CleanupDataService
import io.ktor.server.application.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.exposedLogger
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.days

fun Application.scheduleCleanUp(){
     monitor.subscribe(ApplicationStarted) {
         val cleanupDataService  by inject<CleanupDataService>()
         exposedLogger.info("Cleanup Data Service started 🧹")
        launch {
            while (isActive){
                delay(1.days)
                cleanupDataService.cleanupData()
                exposedLogger.info("Cleanup Data Service ran successfully. ✅")
            }
        }
    }
}