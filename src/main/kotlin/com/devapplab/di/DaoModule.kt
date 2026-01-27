package com.devapplab.di

import com.devapplab.data.database.password_reset.PasswordResetTokenDao
import com.devapplab.data.database.password_reset.PasswordResetTokenDaoImpl
import com.devapplab.data.database.refresh_token.RefreshTokenDao
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val daoModule = module {
    singleOf(::RefreshTokenDao)
    singleOf(::PasswordResetTokenDaoImpl) { bind<PasswordResetTokenDao>() }
}