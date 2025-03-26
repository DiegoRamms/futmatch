package com.devapplab.features.auth

import com.devapplab.config.getJWTConfig
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.authRouting() {
    route("/auth") {
        post("/signUp") {
            val authController = call.scope.get<AuthController>()
            val jwtConfig = application.getJWTConfig()
            authController.signUp(call, jwtConfig)
        }
        post("/refresh") {
            val authController = call.scope.get<AuthController>()
            val jwtConfig = application.getJWTConfig()
            authController.refreshJWT(call, jwtConfig)
        }
    }
}