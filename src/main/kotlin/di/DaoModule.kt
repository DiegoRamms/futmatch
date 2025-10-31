package com.devapplab.di

import com.devapplab.data.database.device.DeviceDao
import com.devapplab.data.database.field.FieldDao
import com.devapplab.data.database.field.FieldImageDao
import com.devapplab.data.database.match.MatchDao
import com.devapplab.data.database.match.MatchWithFieldDao
import com.devapplab.data.database.refresh_token.RefreshTokenDao
import com.devapplab.data.database.user.UserDao
import data.database.mfa.MfaCodeDao
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
}