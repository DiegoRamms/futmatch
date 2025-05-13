package com.devapplab.di

import com.devapplab.service.UserService
import com.devapplab.service.auth.AuthService
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.auth.auth_token.JWTService
import com.devapplab.service.auth.auth_token.RefreshTokenServiceImp
import com.devapplab.service.auth.mfa.MfaCodeService
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.service.field.FieldService
import com.devapplab.service.hashing.HashingService
import com.devapplab.service.hashing.HashingServiceImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import service.auth.DeviceService
import service.email.EmailService
import service.email.EmailServiceImpl

val serviceModule = module {
    singleOf(::UserService)
    singleOf(::AuthService)
    singleOf(::HashingServiceImpl) { bind<HashingService>() }
    singleOf(::JWTService) { bind<AuthTokenService>() }
    singleOf(::RefreshTokenServiceImp) { bind<RefreshTokenService>() }
    singleOf(::DeviceService)
    singleOf(::MfaCodeService)
    singleOf(::EmailServiceImpl) { bind<EmailService>() }
    singleOf(::FieldService)
}
