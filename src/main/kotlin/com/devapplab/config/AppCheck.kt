package com.devapplab.config

import com.devapplab.model.AppCheckConfig
import com.devapplab.service.appcheck.AppCheckVerificationResult
import com.devapplab.service.appcheck.FirebaseAppCheckService
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.Route

private const val FIREBASE_APP_CHECK_HEADER = "X-Firebase-AppCheck"
private val appCheckScopedSelector = object : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }
}

fun Route.appCheck(
    appCheckService: FirebaseAppCheckService,
    appCheckConfig: AppCheckConfig,
    build: Route.() -> Unit
) {
    val scopedRoute = createChild(appCheckScopedSelector)
    scopedRoute.requireAppCheck(appCheckService, appCheckConfig)
    scopedRoute.build()
}

fun Route.requireAppCheck(
    appCheckService: FirebaseAppCheckService,
    appCheckConfig: AppCheckConfig
) {
    install(createRouteScopedPlugin("FirebaseAppCheckPlugin") {
        onCall { call ->
            when (val result = appCheckService.verify(call.request.header(FIREBASE_APP_CHECK_HEADER))) {
                AppCheckVerificationResult.Disabled -> Unit
                is AppCheckVerificationResult.Valid -> {
                    call.application.environment.log.debug(
                        "Firebase App Check verified for path={}, appId={}",
                        call.request.path(),
                        result.appId
                    )
                }

                AppCheckVerificationResult.Missing -> {
                    call.application.environment.log.warn(
                        "Firebase App Check token missing for path={}, enforce={}",
                        call.request.path(),
                        appCheckConfig.enforce
                    )
                    if (appCheckConfig.enforce) {
                        throw InvalidAppCheckException("missing_app_check_token")
                    }
                }

                is AppCheckVerificationResult.Invalid -> {
                    call.application.environment.log.warn(
                        "Firebase App Check token invalid for path={}, reason={}, enforce={}",
                        call.request.path(),
                        result.reason,
                        appCheckConfig.enforce
                    )
                    if (appCheckConfig.enforce) {
                        throw InvalidAppCheckException(result.reason)
                    }
                }
            }
        }
    })
}

class InvalidAppCheckException(message: String) : RuntimeException(message)
