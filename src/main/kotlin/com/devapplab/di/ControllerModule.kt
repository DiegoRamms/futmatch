package com.devapplab.di

import com.devapplab.features.auth.AuthController
import com.devapplab.features.field.FieldController
import com.devapplab.features.location.LocationController
import com.devapplab.features.match.MatchController
import com.devapplab.features.user.UserController
import org.koin.core.module.dsl.scopedOf
import org.koin.dsl.module
import org.koin.module.requestScope

val controllerModule = module {
    requestScope {
        scopedOf(::AuthController)
        scopedOf(::UserController)
        scopedOf(::FieldController)
        scopedOf(::MatchController)
        scopedOf(::LocationController)
    }
}
