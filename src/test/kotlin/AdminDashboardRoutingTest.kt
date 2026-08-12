package com.devapplab

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devapplab.data.repository.admin.DashboardRepository
import com.devapplab.features.admin.AdminDashboardController
import com.devapplab.features.admin.adminDashboardRouting
import com.devapplab.model.admin.DashboardSummary
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.user.UserRole
import com.devapplab.service.admin.DashboardService
import com.devapplab.utils.AccessDeniedException
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.module.requestScope
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminDashboardRoutingTest {
    @Test
    fun `summary requires authentication`() = dashboardApplication(DashboardSummary(0, 0, 0)) {
        val response = client.get("/admin/desktop/dashboard/summary")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `summary rejects non admin roles`() = dashboardApplication(DashboardSummary(0, 0, 0)) {
        val response = client.get("/admin/desktop/dashboard/summary") {
            header(HttpHeaders.Authorization, bearerToken(UserRole.PLAYER))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `summary returns dashboard counts for admins`() = dashboardApplication(DashboardSummary(3, 12, 25)) {
        val response = client.get("/admin/desktop/dashboard/summary") {
            header(HttpHeaders.Authorization, bearerToken(UserRole.ADMIN))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(3, data["locationsCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(12, data["fieldsCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(25, data["matchesCount"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `summary returns zero counts when no dashboard entities exist`() = dashboardApplication(DashboardSummary(0, 0, 0)) {
        val response = client.get("/admin/desktop/dashboard/summary") {
            header(HttpHeaders.Authorization, bearerToken(UserRole.ADMIN))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("0", data["locationsCount"]!!.jsonPrimitive.content)
        assertEquals("0", data["fieldsCount"]!!.jsonPrimitive.content)
        assertEquals("0", data["matchesCount"]!!.jsonPrimitive.content)
    }

    private fun dashboardApplication(summary: DashboardSummary, test: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                configureDashboardTest(summary)
            }
            test()
        }

    private fun Application.configureDashboardTest(summary: DashboardSummary) {
        install(ContentNegotiation) { json() }
        install(StatusPages) {
            exception<AccessDeniedException> { call, _ -> call.respond(HttpStatusCode.Forbidden) }
        }
        install(Authentication) {
            jwt("dashboard-test") {
                verifier(
                    JWT.require(Algorithm.HMAC256(JWT_SECRET))
                        .withIssuer(JWT_ISSUER)
                        .withAudience(JWT_AUDIENCE)
                        .build()
                )
                validate { JWTPrincipal(it.payload) }
                challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized) }
            }
        }
        install(Koin) {
            modules(module {
                requestScope {
                    scoped { AdminDashboardController(DashboardService(FakeDashboardRepository(summary))) }
                }
            })
        }
        routing {
            authenticate("dashboard-test") {
                adminDashboardRouting()
            }
        }
    }

    private fun bearerToken(role: UserRole): String = "Bearer " + JWT.create()
        .withIssuer(JWT_ISSUER)
        .withAudience(JWT_AUDIENCE)
        .withClaim(ClaimType.USER_IDENTIFIER.value, "c2f6dc2c-efbf-43bc-8f98-61e1dbce8df3")
        .withClaim(ClaimType.USER_ROLE.value, role.name)
        .sign(Algorithm.HMAC256(JWT_SECRET))

    private class FakeDashboardRepository(private val summary: DashboardSummary) : DashboardRepository {
        override suspend fun getSummary(): DashboardSummary = summary
    }

    private companion object {
        const val JWT_SECRET = "dashboard-test-secret"
        const val JWT_ISSUER = "dashboard-test"
        const val JWT_AUDIENCE = "dashboard-client"
    }
}
