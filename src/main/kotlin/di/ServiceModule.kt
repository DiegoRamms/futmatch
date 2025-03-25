package com.devapplab.di

import com.devapplab.service.UserService
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.auth.auth_token.JWTService
import com.devapplab.service.hashing.HashingService
import com.devapplab.service.hashing.HashingServiceImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val serviceModule = module {
    singleOf(::UserService)
    singleOf(::HashingServiceImpl) { bind<HashingService>() }
    singleOf(::JWTService) { bind<AuthTokenService>() }
}
