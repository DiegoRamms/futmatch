package com.devapplab.di

import com.devapplab.data.database.user.UserDao
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val daoModule = module {
    singleOf(::UserDao)
}