package com.devapplab.di

import com.devapplab.data.database.device.DeviceDao
import com.devapplab.data.database.field.FieldDao
import com.devapplab.data.database.field.FieldImageDao
import com.devapplab.data.database.login_attempt.LoginAttemptDAO

import com.devapplab.data.database.match.MatchDao
import com.devapplab.data.database.match.MatchWithFieldDao
import com.devapplab.data.database.password_reset.PasswordResetTokenDao
import com.devapplab.data.database.password_reset.PasswordResetTokenDaoImpl
import com.devapplab.data.database.pending_registrations.PendingRegistrationDao
import com.devapplab.data.database.pending_registrations.PendingRegistrationDaoImpl
import com.devapplab.data.database.refresh_token.RefreshTokenDao
import com.devapplab.data.database.user.UserDao
import data.database.mfa.MfaCodeDao
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val daoModule = module {
    singleOf(::UserDao)
    singleOf(::RefreshTokenDao)
    singleOf(::DeviceDao)
    singleOf(::MfaCodeDao)
    singleOf(::FieldDao)
    singleOf(::FieldImageDao)
    singleOf(::MatchDao)
    singleOf(::MatchWithFieldDao)
    singleOf(::PasswordResetTokenDaoImpl) { bind<PasswordResetTokenDao>() }
    singleOf(::LoginAttemptDAO)
    singleOf(::PendingRegistrationDaoImpl) { bind<PendingRegistrationDao>() }
}