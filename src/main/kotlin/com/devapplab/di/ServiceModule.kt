package com.devapplab.di

import com.devapplab.service.UserService
import com.devapplab.service.auth.AuthenticatedResponseGenerator
import com.devapplab.service.auth.AuthTokenManagementService
import com.devapplab.service.auth.PasswordResetService
import com.devapplab.service.auth.RegistrationService
import com.devapplab.service.auth.SignInService
import com.devapplab.service.auth.auth_token.JWTService
import com.devapplab.service.auth.auth_token.RefreshTokenServiceImp
import com.devapplab.service.auth.mfa.MfaCodeService
import com.devapplab.service.clean.CleanupDataService
import com.devapplab.service.email.RealEmailServiceTestImp
import com.devapplab.service.field.FieldService
import com.devapplab.service.firebase.FirebaseService
import com.devapplab.service.hashing.HashingServiceImpl
import com.devapplab.service.image.ImageServiceImp
import com.devapplab.service.location.LocationService
import com.devapplab.service.match.MatchService
import com.devapplab.service.password_reset.PasswordResetTokenServiceImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val serviceModule = module {

    singleOf(::UserService)
    singleOf(::RegistrationService)
    singleOf(::AuthenticatedResponseGenerator)
    singleOf(::SignInService)
    singleOf(::PasswordResetService)
    singleOf(::AuthTokenManagementService)
    singleOf(::HashingServiceImpl) { bind<com.devapplab.service.hashing.HashingService>() }
    singleOf(::JWTService) { bind<com.devapplab.service.auth.auth_token.AuthTokenService>() }
    singleOf(::RefreshTokenServiceImp) { bind<com.devapplab.service.auth.refresh_token.RefreshTokenService>() }
    singleOf(::MfaCodeService)
    //singleOf(::EmailServiceImpl) { bind<EmailService>() }
    singleOf(::RealEmailServiceTestImp) { bind<com.devapplab.service.email.EmailService>() }
    singleOf(::FieldService)
    singleOf(::MatchService)
    singleOf(::LocationService)
    singleOf(::ImageServiceImp) { bind<com.devapplab.service.image.ImageService>() }
    singleOf(::CleanupDataService)
    singleOf(::PasswordResetTokenServiceImpl) { bind<com.devapplab.service.password_reset.PasswordResetTokenService>()  }
    singleOf(::FirebaseService)
}
