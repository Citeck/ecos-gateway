package ru.citeck.ecos.gateway.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.data.AuthState
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.context.lib.ctx.GlobalEcosContext
import ru.citeck.ecos.gateway.ReactorEcosContextUtils
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager
import ru.citeck.ecos.webapp.lib.web.authenticator.jwt.JwtAuthenticator

class GatewayOutcomeFilterTest {

    private lateinit var authenticatorsManager: WebAuthenticatorsManager
    private lateinit var jwtAuthenticator: JwtAuthenticator
    private lateinit var filter: GatewayOutcomeFilter
    private lateinit var chain: GatewayFilterChain

    private var capturedExchange: org.springframework.web.server.ServerWebExchange? = null

    @BeforeEach
    fun setUp() {
        jwtAuthenticator = mock()
        authenticatorsManager = mock()
        whenever(authenticatorsManager.getJwtAuthenticator("jwt")).thenReturn(jwtAuthenticator)

        val ecosContext = GlobalEcosContext.createChild()
        filter = GatewayOutcomeFilter(authenticatorsManager, ecosContext)
        capturedExchange = null
        chain = GatewayFilterChain { exchange ->
            capturedExchange = exchange
            Mono.empty()
        }
    }

    @Test
    fun `no Bearer header when context absent`() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        assertThat(capturedExchange).isNotNull
        assertThat(capturedExchange!!.request.headers["Authorization"]).isNull()
    }

    @Test
    fun `Bearer header set when context present`() {
        whenever(jwtAuthenticator.createJwtToken(any())).thenReturn("test-jwt-token")

        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)

        val ecosContext = GlobalEcosContext.createChild()
        val authData = SimpleAuthData("testuser", listOf("ROLE_USER"))
        val ctxData = ecosContext.newScope().use { scope ->
            AuthContext.set(scope, AuthState(authData))
            ecosContext.getScopeData()
        }

        val mono = filter.filter(exchange, chain)
            .contextWrite(ReactorEcosContextUtils.withContextData(ctxData))

        StepVerifier.create(mono).verifyComplete()

        assertThat(capturedExchange).isNotNull
        val authHeader = capturedExchange!!.request.headers.getFirst("Authorization")
        assertThat(authHeader).isEqualTo("Bearer test-jwt-token")
    }

    @Test
    fun `removes upgrade-insecure-requests header`() {
        val request = MockServerHttpRequest.get("/api/test")
            .header("upgrade-insecure-requests", "1")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        assertThat(capturedExchange!!.request.headers["upgrade-insecure-requests"]).isNull()
    }

    @Test
    fun `removes sec- prefixed headers case-insensitively and preserves others`() {
        val request = MockServerHttpRequest.get("/api/test")
            .header("Sec-Fetch-Mode", "cors")
            .header("sec-fetch-site", "same-origin")
            .header("Sec-CH-UA", "chromium")
            .header("Content-Type", "application/json")
            .header("X-Custom-Header", "value")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        val headers = capturedExchange!!.request.headers
        assertThat(headers["Sec-Fetch-Mode"]).isNull()
        assertThat(headers["sec-fetch-site"]).isNull()
        assertThat(headers["Sec-CH-UA"]).isNull()
        assertThat(headers.getFirst("Content-Type")).isEqualTo("application/json")
        assertThat(headers.getFirst("X-Custom-Header")).isEqualTo("value")
    }
}
