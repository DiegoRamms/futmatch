package com.devapplab

import com.devapplab.config.configureSerialization
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.ErrorResponse
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentPollingStatusResponse
import com.devapplab.utils.respond
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the /payment serialization bug.
 *
 * Root cause: PaymentController used Ktor's built-in `respond` on the generic sealed
 * `AppResult<T>`, which kotlinx serializes via SealedClassSerializer (polymorphic). Generic
 * type arguments are erased in polymorphic mode, so `Success.data: T` fell back to
 * PolymorphicSerializer(Any) and threw "not found in the polymorphic scope of 'Any'".
 *
 * Fix: use the project's `com.devapplab.utils.respond` extension, which converts
 * AppResult<T> -> AppResponse<T> (a plain generic class) before serializing.
 */
class PaymentSerializationTest {

    private val sample = PaymentPollingStatusResponse(
        status = PaymentAttemptStatus.AUTHORIZED,
        isFinal = true,
        isSuccess = true
    )

    @Test
    fun success_throughAppResponseExtension_returns200() = testApplication {
        application {
            configureSerialization()
            routing {
                get("/poll") {
                    val result: AppResult<PaymentPollingStatusResponse> = AppResult.Success(sample)
                    call.respond(result)
                }
            }
        }
        val resp = client.get("/poll")
        val body = resp.bodyAsText()
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(body.contains("\"data\""), "expected AppResponse envelope: $body")
        assertTrue(body.contains("AUTHORIZED"), "expected serialized status: $body")
    }

    @Test
    fun failure_bareFailureOverload_returnsErrorEnvelope() = testApplication {
        application {
            configureSerialization()
            routing {
                get("/poll-err") {
                    val error = AppResult.Failure(
                        ErrorResponse(title = "Error", message = "boom", errorCode = ErrorCode.GENERAL_ERROR),
                        appStatus = HttpStatusCode.NotFound
                    )
                    call.respond(error)
                }
            }
        }
        val resp = client.get("/poll-err")
        val body = resp.bodyAsText()
        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertTrue(body.contains("\"error\""), "expected AppResponse error envelope: $body")
        assertTrue(body.contains("boom"), "expected serialized message: $body")
    }
}
