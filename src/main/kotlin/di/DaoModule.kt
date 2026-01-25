package di

import com.devapplab.data.database.device.DeviceDAO
import com.devapplab.data.database.field.FieldDao
import com.devapplab.data.database.field.FieldImageDao
import com.devapplab.data.database.login_attempt.LoginAttemptDAO

import com.devapplab.data.database.match.MatchDao
import data.database.match.MatchWithFieldDao
import com.devapplab.data.database.password_reset.PasswordResetTokenDao
import com.devapplab.data.database.password_reset.PasswordResetTokenDaoImpl
import com.devapplab.data.database.pending_registrations.PendingRegistrationDao
import com.devapplab.data.database.pending_registrations.PendingRegistrationDaoImpl
import com.devapplab.data.database.refresh_token.RefreshTokenDao
import data.database.user.UserDAO
import data.database.location.LocationDao
import data.database.mfa.MfaCodeDao
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val daoModule = module {
    singleOf(::UserDAO)
    singleOf(::RefreshTokenDao)
    singleOf(::DeviceDAO)
    singleOf(::MfaCodeDao)
    singleOf(::FieldDao)
    singleOf(::FieldImageDao)
    singleOf(::MatchDao)
    singleOf(::MatchWithFieldDao)
    singleOf(::PasswordResetTokenDaoImpl) { bind<PasswordResetTokenDao>() }
    singleOf(::LoginAttemptDAO)
    singleOf(::PendingRegistrationDaoImpl) { bind<PendingRegistrationDao>() }
    singleOf(::LocationDao)
}