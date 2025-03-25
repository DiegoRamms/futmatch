package com.devapplab.config

import com.devapplab.di.controllerModule
import com.devapplab.di.daoModule
import com.devapplab.di.repositoryModule
import com.devapplab.di.serviceModule
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(
            daoModule,
            repositoryModule,
            serviceModule,
            controllerModule,
            stringModule
        )
    }
}


val stringModule = module {
    single { "Test" }
}