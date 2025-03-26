package com.devapplab.di

import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.UserRepository
import data.repository.RefreshTokenRepositoryImp
import data.repository.UserRepositoryImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val repositoryModule = module {
    singleOf(::UserRepositoryImpl) { bind<UserRepository>() }
    singleOf(::RefreshTokenRepositoryImp) { bind<RefreshTokenRepository>() }
}