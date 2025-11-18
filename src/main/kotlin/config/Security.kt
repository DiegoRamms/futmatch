package com.devapplab.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devapplab.model.AppResult
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.auth.JWTConfig
import com.devapplab.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import model.user.UserRole
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

fun Application.configureSecurity() {

    val jwtConfig = getJWTConfig()

    authentication {
        jwt("auth-jwt") {
            realm = jwtConfig.realm
            verifier(
                JWT
                    .require(Algorithm.ECDSA256(jwtConfig.loadECPublicKey()))
                    .withIssuer(jwtConfig.issuer)
                    .build()
            )
            validate { credential ->
                if (!credential.payload.getClaim(ClaimType.USER_IDENTIFIER.value).asString()
                        .isNullOrEmpty() && !credential.payload.getClaim(ClaimType.USER_ROLE.value).asString()
                        .isNullOrEmpty()
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                respondJWTError(call)
            }
        }
    }
}

fun Application.getJWTConfig(): JWTConfig {
    val private = environment.config.property("jwt.private").getString()
    val public = environment.config.property("jwt.public").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val realm = environment.config.property("jwt.realm").getString()
    val algorithm = environment.config.property("jwt.algorithm").getString()

    return JWTConfig(audience, issuer, realm, private, public, algorithm)
}

fun JWTConfig.loadECPrivateKey(): ECPrivateKey {
    val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(private))
    val keyFactory = KeyFactory.getInstance(algorithm)
    return keyFactory.generatePrivate(keySpec) as ECPrivateKey
}

fun JWTConfig.loadECPublicKey(): ECPublicKey {
    val publicKeyBytes = Base64.getDecoder().decode(public)
    val keySpec = X509EncodedKeySpec(publicKeyBytes)
    val keyFactory = KeyFactory.getInstance(algorithm)
    return keyFactory.generatePublic(keySpec) as ECPublicKey
}

private suspend fun respondJWTError(call: ApplicationCall) {
    val locale = call.retrieveLocale()
    val failure: AppResult<String> = locale.createError(
        StringResourcesKey.INVALID_JWT_TITLE,
        StringResourcesKey.INVALID_JWT_DESCRIPTION,
        status = HttpStatusCode.Unauthorized
    )
    call.respond(failure)
}

fun ApplicationCall.getIdentifier(claimType: ClaimType): UUID {
    val principal = principal<JWTPrincipal>()
    val uuid = principal?.payload?.getClaim(claimType.value)?.asString()?.let {
        UUID.fromString(it)
    }
    return uuid ?: throw InvalidTokenException("Token Error")
}

fun ApplicationCall.getRole(): UserRole {
    val principal = principal<JWTPrincipal>()
    val role = principal?.payload?.getClaim(ClaimType.USER_ROLE.value)?.asString()
        ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
    return role ?: throw InvalidTokenException()
}

fun ApplicationCall.requireRole(vararg allowedRoles: UserRole): UserRole {
    val role = getRole()
    this.application.log.info("Current role: $role | Allowed roles: ${allowedRoles.joinToString(", ")}")
    if (!allowedRoles.contains(role)) {
        throw AccessDeniedException()
    }
    return role
}
