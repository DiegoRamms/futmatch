package com.devapplab

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.devapplab.config.configureMicrometerMetrics
import com.devapplab.config.configureLogger
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyCompatibilityTest {
    @Test
    fun nettyServesRequestsWithAlignedRuntimeModules() = runBlocking {
        val loggedRequest = CountDownLatch(1)
        val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val appender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                if (event.formattedMessage.startsWith("200 OK: GET - /health in ")) {
                    loggedRequest.countDown()
                }
            }
        }
        appender.start()
        rootLogger.addAppender(appender)
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            configureLogger()
            routing { get("/health") { call.respondText("ok") } }
        }
        try {
            server.start(wait = false)
            val port = server.engine.resolvedConnectors().single().port
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/health"))
                .timeout(Duration.ofSeconds(10))
                .build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            assertEquals("ok", response.body())
            assertTrue(loggedRequest.await(5, TimeUnit.SECONDS), "Request logging must work without Jansi")
        } finally {
            server.stop(0, 1000)
            rootLogger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun prometheusScrapeWorksAndStillRequiresAuthentication() = testApplication {
        environment { config = MapApplicationConfig("metrics.bearer_token" to "test-metrics-token") }
        application { configureMicrometerMetrics() }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/metrics").status)
        val response = client.get("/metrics") {
            bearerAuth("test-metrics-token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("jvm_memory_used_bytes"))
    }
}
