package com.devapplab.di

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.database.executor.ExposedDbExecutor
import com.devapplab.features.match.MatchUpdateBus
import com.devapplab.core.FirebaseAdminProvider
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.core.module.dsl.bind

fun appModule(application: Application) = module {
    single<Application> { application }
    single<ApplicationConfig> { application.environment.config }
    singleOf(::ExposedDbExecutor) { bind<DbExecutor>() }
    singleOf(::MatchUpdateBus)
    singleOf(::FirebaseAdminProvider)
}