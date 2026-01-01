package com.devapplab.di

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.koin.dsl.module

fun appModule(application: Application) = module {
    single<Application> { application }
    single<ApplicationConfig> { application.environment.config }
}