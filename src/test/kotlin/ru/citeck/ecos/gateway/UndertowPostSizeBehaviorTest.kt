package ru.citeck.ecos.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import reactor.core.publisher.Mono
import reactor.netty.http.server.HttpServer
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicLong

// Behavioral regression test for the Undertow transport-layer post-size limit.
//
// Root cause: Spring Boot 3.5.10 changed server.undertow.max-http-post-size default
// from unlimited to 2 MB. The gateway is a reverse proxy and must not impose a size limit;
// the fix is to set the property explicitly to a large positive value (100 GB).
// Note: -1 (unlimited) does NOT work because Spring Boot's DataSize binding rejects it.
//
// java.net.http.HttpClient is used intentionally: it sends an explicit Content-Length
// header, which triggers the Undertow size check. WebTestClient uses chunked transfer
// encoding (no Content-Length), which bypasses the check entirely.
//
// A reactor-netty HttpServer is used as mock backend on /pub/ routes (public — no auth
// required by GatewaySecurityFilter) to avoid authentication setup in the test.
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(
    classes = [EcosGateway::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UndertowPostSizeBehaviorTest {

    companion object {
        private val httpClient = HttpClient.newHttpClient()

        private val mockBackend = HttpServer.create()
            .port(0)
            .route { routes ->
                routes.post("/**") { req, res ->
                    val counter = AtomicLong(0)
                    req.receive()
                        .doOnNext { buf -> counter.addAndGet(buf.readableBytes().toLong()) }
                        .then(
                            res.status(200)
                                .header("Content-Type", "text/plain")
                                .sendString(Mono.fromCallable { counter.get().toString() })
                                .then()
                        )
                }
            }
            .bindNow()

        private val mockBackendPort = mockBackend.port()

        @JvmStatic
        @AfterAll
        fun stopMockBackend() {
            mockBackend.disposeNow()
        }

        @JvmStatic
        @DynamicPropertySource
        fun gatewayRoutes(registry: DynamicPropertyRegistry) {
            registry.add("spring.cloud.gateway.server.webflux.routes[0].id") { "mock-backend" }
            registry.add("spring.cloud.gateway.server.webflux.routes[0].uri") { "http://localhost:$mockBackendPort" }
            registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]") { "Path=/pub/**" }
        }
    }

    @LocalServerPort
    private var gatewayPort: Int = 0

    @ParameterizedTest(name = "{0}MB octet-stream POST must reach backend (HTTP 200), not be rejected by Undertow (HTTP 400)")
    @CsvSource("1", "2", "3", "5")
    fun `octet-stream POST with explicit Content-Length must not be rejected by Undertow`(sizeMb: Int) {
        val body = ByteArray(sizeMb * 1024 * 1024)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$gatewayPort/pub/upload"))
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val receivedBytes = response.body().trim().toLong()

        assertThat(response.statusCode())
            .describedAs("${sizeMb}MB upload must not be rejected by Undertow (400 = Undertow limit hit)")
            .isEqualTo(200)
        assertThat(receivedBytes)
            .describedAs("Mock backend must have received the full ${sizeMb}MB body")
            .isGreaterThanOrEqualTo(sizeMb.toLong() * 1024 * 1024)
    }

    @ParameterizedTest(name = "{0}MB multipart/form-data POST must reach backend (HTTP 200)")
    @CsvSource("1", "2", "3", "5")
    fun `multipart POST with explicit Content-Length must not be rejected by Undertow`(sizeMb: Int) {
        val boundary = "----TestBoundary"
        val fileBytes = ByteArray(sizeMb * 1024 * 1024)
        val prefix = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray()
        val suffix = "\r\n--$boundary--\r\n".toByteArray()
        val body = prefix + fileBytes + suffix

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$gatewayPort/pub/upload"))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val receivedBytes = response.body().trim().toLong()

        assertThat(response.statusCode())
            .describedAs("${sizeMb}MB multipart upload must not be rejected by Undertow")
            .isEqualTo(200)
        assertThat(receivedBytes)
            .describedAs("Mock backend must have received the full ${sizeMb}MB multipart body")
            .isGreaterThanOrEqualTo(sizeMb.toLong() * 1024 * 1024)
    }
}
