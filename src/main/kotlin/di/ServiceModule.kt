package com.devapplab.di

import com.devapplab.service.UserService
import service.auth.AuthService
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.auth.auth_token.JWTService
import com.devapplab.service.auth.auth_token.RefreshTokenServiceImp
import com.devapplab.service.auth.mfa.MfaCodeService
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.service.clean.CleanupDataService
import com.devapplab.service.field.FieldService
import com.devapplab.service.hashing.HashingService
import com.devapplab.service.hashing.HashingServiceImpl
import com.devapplab.service.image.ImageServiceImp
import com.devapplab.service.match.MatchService
import com.devapplab.service.password_reset.PasswordResetTokenService
import com.devapplab.service.password_reset.PasswordResetTokenServiceImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import service.email.EmailService
import service.email.RealEmailServiceTestImp
import service.image.ImageService

val serviceModule = module {

    singleOf(::UserService)
    singleOf(::AuthService)
    singleOf(::HashingServiceImpl) { bind<HashingService>() }
    singleOf(::JWTService) { bind<AuthTokenService>() }
    singleOf(::RefreshTokenServiceImp) { bind<RefreshTokenService>() }
    singleOf(::MfaCodeService)
    //singleOf(::EmailServiceImpl) { bind<EmailService>() }
    singleOf(::RealEmailServiceTestImp) { bind<EmailService>() }
    singleOf(::FieldService)
    singleOf(::MatchService)
    singleOf(::ImageServiceImp) { bind<ImageService>() }
    singleOf(::CleanupDataService)
    singleOf(::PasswordResetTokenServiceImpl) { bind<PasswordResetTokenService>()  }
}
