package com.devapplab.di

import com.devapplab.data.repository.*
import com.devapplab.data.repository.login_attempt.LoginAttemptRepository
import com.devapplab.data.repository.login_attempt.LoginAttemptRepositoryImpl
import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.match.MatchRepositoryImp
import com.devapplab.data.repository.password_reset.PasswordResetTokenRepository
import com.devapplab.data.repository.password_reset.PasswordResetTokenRepositoryImpl
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepository
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepositoryImpl
import data.repository.MfaCodeRepositoryImpl
import data.repository.RefreshTokenRepositoryImp
import data.repository.UserRepositoryImpl
import data.repository.auth.AuthRepository
import data.repository.device.DeviceRepository
import data.repository.device.DeviceRepositoryImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val repositoryModule = module {
    singleOf(::UserRepositoryImpl) { bind<UserRepository>() }
    singleOf(::RefreshTokenRepositoryImp) { bind<RefreshTokenRepository>() }
    singleOf(::DeviceRepositoryImpl) { bind<DeviceRepository>() }
    singleOf(::MfaCodeRepositoryImpl) { bind<MfaCodeRepository>() }
    singleOf(::AuthRepositoryImpl) { bind<AuthRepository>() }
    singleOf(::FieldRepositoryImp) { bind<FieldRepository>() }
    singleOf(::MatchRepositoryImp) { bind<MatchRepository>() }
    singleOf(::PasswordResetTokenRepositoryImpl) { bind<PasswordResetTokenRepository>() }
    singleOf(::LoginAttemptRepositoryImpl) { bind<LoginAttemptRepository>() }
    singleOf(::PendingRegistrationRepositoryImpl) { bind<PendingRegistrationRepository>() }
}