package com.devapplab.features.auth

import com.devapplab.config.RateLimitType
import com.devapplab.config.getJWTConfig
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.authRouting() {
    route("/auth") {
        post("/signUp") {
            val authController = call.scope.get<AuthController>()
            authController.signUp(call)
        }

        rateLimit (configuration = RateLimitName(RateLimitType.REFRESH_TOKEN.value)){
            post("/refresh") {
                val authController = call.scope.get<AuthController>()
                val jwtConfig = application.getJWTConfig()
                authController.refreshJWT(call, jwtConfig)
            }
        }

        post("/signIn") {
            val authController = call.scope.get<AuthController>()
            val jwtConfig = application.getJWTConfig()
            authController.signIn(call, jwtConfig)
        }

        rateLimit (configuration = RateLimitName(RateLimitType.MFA_SEND.value)){
            post("/mfa/send"){
                val authController = call.scope.get<AuthController>()
                authController.sendMfaCode(call)
            }
        }

        rateLimit (configuration = RateLimitName(RateLimitType.MFA_VERIFY.value)){
            post("/mfa/verify"){
                val authController = call.scope.get<AuthController>()
                val jwtConfig = application.getJWTConfig()
                authController.verifyMfaCode(call, jwtConfig)
            }
        }

        post("/signOut"){
            val authController = call.scope.get<AuthController>()
            authController.signOut(call)
        }
    }
}