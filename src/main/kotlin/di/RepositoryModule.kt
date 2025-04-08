package com.devapplab.di

import com.devapplab.data.repository.*
import data.repository.DeviceRepositoryImpl
import data.repository.MfaCodeRepositoryImpl
import data.repository.RefreshTokenRepositoryImp
import data.repository.UserRepositoryImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val repositoryModule = module {
    singleOf(::UserRepositoryImpl) { bind<UserRepository>() }
    singleOf(::RefreshTokenRepositoryImp) { bind<RefreshTokenRepository>() }
    singleOf(::DeviceRepositoryImpl) { bind<DeviceRepository>() }
    singleOf(::MfaCodeRepositoryImpl) { bind<MfaCodeRepository>() }
    singleOf(::AuthRepositoryImpl) { bind<AuthRepository>() }
}