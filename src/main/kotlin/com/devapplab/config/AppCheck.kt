package com.devapplab.config

import com.devapplab.model.AppCheckConfig
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.device.DevicePlatform
import com.devapplab.service.appcheck.AppCheckVerificationResult
import com.devapplab.service.appcheck.FirebaseAppCheckService
import com.devapplab.service.device.DesktopDeviceSecurityService
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.Route
import io.ktor.util.AttributeKey
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

private const val FIREBASE_APP_CHECK_HEADER = "X-Firebase-AppCheck"
val DesktopVerifiedDeviceIdKey = AttributeKey<UUID>("desktop-verified-device-id")
private val appCheckScopedSelector = object : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }
}

fun Route.appCheck(
    appCheckService: FirebaseAppCheckService,
    appCheckConfig: AppCheckConfig,
    desktopDeviceSecurityService: DesktopDeviceSecurityService,
    build: Route.() -> Unit
) {
    val scopedRoute = createChild(appCheckScopedSelector)
    scopedRoute.requireAppCheck(appCheckService, appCheckConfig, desktopDeviceSecurityService)
    scopedRoute.build()
}

fun Route.requireAppCheck(
    appCheckService: FirebaseAppCheckService,
    appCheckConfig: AppCheckConfig,
    desktopDeviceSecurityService: DesktopDeviceSecurityService
) {
    install(createRouteScopedPlugin("FirebaseAppCheckPlugin") {
        onCall { call ->
            val appCheckToken = call.request.header(FIREBASE_APP_CHECK_HEADER)
            val desktopHeaderPresent = call.request.header("X-Desktop-Device-Id") != null
            val jwtDeviceId = call.getOptionalIdentifier(ClaimType.DEVICE_IDENTIFIER)
            val jwtDevicePlatform = call.getOptionalDevicePlatform()
            // JWTs issued before device_platform existed need a short-lived compatibility lookup.
            // Newly issued tokens take this branch without touching the database.
            val requiresDesktopSignature = jwtDevicePlatform == DevicePlatform.DESKTOP ||
                (jwtDevicePlatform == null && jwtDeviceId != null && desktopDeviceSecurityService.isDesktopDevice(jwtDeviceId))
            val bodyHash = if (desktopHeaderPresent) call.desktopBodyHash() else null
            val desktopDeviceId = desktopDeviceSecurityService.verify(
                call.request.header("X-Desktop-Device-Id"), call.request.header("X-Desktop-Timestamp"),
                call.request.header("X-Desktop-Request-Id"), call.request.header("X-Desktop-Signature"),
                call.request.httpMethod.value, call.request.path(), bodyHash
            )
            val requestId = call.request.header("X-Request-Id")
            if (requiresDesktopSignature && desktopDeviceId == null) {
                if (desktopHeaderPresent) throw InvalidAppCheckException("desktop_reenrollment_required")
                throw InvalidAppCheckException("desktop_signature_required")
            }
            if (desktopDeviceId != null) {
                if (jwtDeviceId != null && jwtDeviceId != desktopDeviceId) {
                    throw InvalidAppCheckException("desktop_device_session_mismatch")
                }
                call.attributes.put(DesktopVerifiedDeviceIdKey, desktopDeviceId)
                return@onCall
            }
            if (desktopHeaderPresent) {
                throw InvalidAppCheckException("desktop_reenrollment_required")
            }
            when (val result = appCheckService.verify(appCheckToken)) {
                AppCheckVerificationResult.Disabled -> Unit
                is AppCheckVerificationResult.Valid -> {
                    call.application.environment.log.debug(
                        "Firebase App Check verified for path={}, requestId={}, appId={}",
                        call.request.path(),
                        requestId,
                        result.appId
                    )
                }

                AppCheckVerificationResult.Missing -> {
                    call.application.environment.log.warn(
                        "Firebase App Check token missing for path={}, requestId={}, headerPresent={}, tokenLength={}, enforce={}",
                        call.request.path(),
                        requestId,
                        appCheckToken != null,
                        appCheckToken?.length ?: 0,
                        appCheckConfig.enforce
                    )
                    if (appCheckConfig.enforce) {
                        throw InvalidAppCheckException("missing_app_check_token")
                    }
                }

                is AppCheckVerificationResult.Invalid -> {
                    call.application.environment.log.warn(
                        "Firebase App Check token invalid for path={}, requestId={}, headerPresent={}, tokenLength={}, reason={}, enforce={}",
                        call.request.path(),
                        requestId,
                        appCheckToken != null,
                        appCheckToken?.length ?: 0,
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

private suspend fun ApplicationCall.desktopBodyHash(): String? = runCatching {
    val declaredSize = request.headers["Content-Length"]?.toLongOrNull()
    if (declaredSize != null && (declaredSize < 0 || declaredSize > DESKTOP_SIGNED_BODY_MAX_BYTES)) return null
    @Suppress("DEPRECATION")
    val bytes = receiveChannel().readRemaining(DESKTOP_SIGNED_BODY_MAX_BYTES + 1).readBytes()
    if (bytes.size > DESKTOP_SIGNED_BODY_MAX_BYTES) return null
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}.getOrNull()

class InvalidAppCheckException(message: String) : RuntimeException(message)
class DesktopReenrollmentRequiredException : RuntimeException()
