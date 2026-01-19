package com.devapplab.config

import com.devapplab.di.*
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(
            appModule(this@configureKoin),
            daoModule,
            repositoryModule,
            serviceModule,
            controllerModule,
            stringModule,
            httpClientModule,
            configModule
        )
    }
}


val stringModule = module {
    single { "Test" }
}