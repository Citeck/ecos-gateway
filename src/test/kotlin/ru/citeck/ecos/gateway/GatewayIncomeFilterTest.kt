package ru.citeck.ecos.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.context.lib.ctx.GlobalEcosContext
import ru.citeck.ecos.gateway.config.GatewayProps
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridge
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridgeFactory
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager
import ru.citeck.ecos.webapp.lib.web.authenticator.jwt.JwtAuthenticator
import ru.citeck.ecos.webapp.lib.web.http.EcosHttpHeaders

class GatewayIncomeFilterTest {

    private lateinit var reactorBridgeFactory: ReactorBridgeFactory
    private lateinit var reactorBridge: ReactorBridge
    private lateinit var authoritiesProvider: AuthoritiesProvider
    private lateinit var tracer: io.micrometer.tracing.Tracer
    private lateinit var ecosContext: ru.citeck.ecos.context.lib.ctx.EcosContext
    private lateinit var authenticatorsManager: WebAuthenticatorsManager
    private lateinit var jwtAuthenticator: JwtAuthenticator
    private lateinit var filter: GatewayIncomeFilter
    private lateinit var chain: WebFilterChain

    @BeforeEach
    fun setUp() {
        reactorBridge = mock()
        reactorBridgeFactory = mock()
        whenever(reactorBridgeFactory.getBridge("get-auth")).thenReturn(reactorBridge)

        whenever(reactorBridge.execute<Any>(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val action = invocation.getArgument<() -> Any>(0)
            Mono.just(action())
        }

        authoritiesProvider = mock()

        val traceContext: io.micrometer.tracing.TraceContext = mock()
        whenever(traceContext.traceId()).thenReturn("abc123")
        val currentTraceContext: io.micrometer.tracing.CurrentTraceContext = mock()
        whenever(currentTraceContext.context()).thenReturn(traceContext)
        tracer = mock()
        whenever(tracer.currentTraceContext()).thenReturn(currentTraceContext)

        ecosContext = GlobalEcosContext.createChild()

        jwtAuthenticator = mock()
        authenticatorsManager = mock()
        whenever(authenticatorsManager.getJwtAuthenticator("jwt")).thenReturn(jwtAuthenticator)

        chain = WebFilterChain { Mono.empty() }
    }

    private fun createFilter(props: GatewayProps = GatewayProps()): GatewayIncomeFilter {
        val f = GatewayIncomeFilter(
            reactorBridgeFactory,
            authoritiesProvider,
            tracer,
            ecosContext,
            props,
            authenticatorsManager
        )
        val field = GatewayIncomeFilter::class.java.getDeclaredField("actuatorBasePath")
        field.isAccessible = true
        field.set(f, "/actuator/")
        f.init()
        return f
    }

    @Test
    fun `no user header passes through without auth lookup`() {
        filter = createFilter()
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(authoritiesProvider, never()).getAuthorities(any())
    }

    @Test
    fun `user header triggers authority lookup`() {
        filter = createFilter()
        whenever(authoritiesProvider.getAuthorities("testuser"))
            .thenReturn(listOf("ROLE_USER"))

        val request = MockServerHttpRequest.get("/api/test")
            .header(EcosHttpHeaders.X_ECOS_USER, "testuser")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(authoritiesProvider).getAuthorities("testuser")
    }

    @Test
    fun `username extraction with matching regex extractor`() {
        val props = GatewayProps(
            userNameExtractors = listOf(
                GatewayProps.UserNameExtractor(matcher = "(.+)@.+", regexGroup = 1)
            )
        )
        filter = createFilter(props)
        whenever(authoritiesProvider.getAuthorities("john"))
            .thenReturn(listOf("ROLE_USER"))

        val request = MockServerHttpRequest.get("/api/test")
            .header(EcosHttpHeaders.X_ECOS_USER, "john@example.com")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(authoritiesProvider).getAuthorities("john")
    }

    @Test
    fun `username extraction falls through when regex does not match`() {
        val props = GatewayProps(
            userNameExtractors = listOf(
                GatewayProps.UserNameExtractor(matcher = "(.+)@company\\.com", regexGroup = 1)
            )
        )
        filter = createFilter(props)
        whenever(authoritiesProvider.getAuthorities("other@external.com"))
            .thenReturn(listOf("ROLE_USER"))

        val request = MockServerHttpRequest.get("/api/test")
            .header(EcosHttpHeaders.X_ECOS_USER, "other@external.com")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(authoritiesProvider).getAuthorities("other@external.com")
    }

    @Test
    fun `invalid timezone header does not break request processing`() {
        filter = createFilter()
        whenever(authoritiesProvider.getAuthorities("testuser"))
            .thenReturn(listOf("ROLE_USER"))

        val request = MockServerHttpRequest.get("/api/test")
            .header(EcosHttpHeaders.X_ECOS_USER, "testuser")
            .header(EcosHttpHeaders.X_ECOS_TIMEZONE, "not-a-number")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()
    }

    @Test
    fun `trace id set on response headers`() {
        filter = createFilter()
        whenever(authoritiesProvider.getAuthorities("testuser"))
            .thenReturn(listOf("ROLE_USER"))

        val request = MockServerHttpRequest.get("/api/test")
            .header(EcosHttpHeaders.X_ECOS_USER, "testuser")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        assertThat(exchange.response.headers.getFirst(EcosHttpHeaders.X_ECOS_TRACE_ID))
            .isEqualTo("abc123")
    }

    @Test
    fun `actuator path without auth passes through`() {
        filter = createFilter()

        val request = MockServerHttpRequest.get("/actuator/health").build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(authoritiesProvider, never()).getAuthorities(any())
    }

    @Test
    fun `actuator path with JWT authorization uses jwt authenticator`() {
        filter = createFilter()
        val authData = SimpleAuthData("jwtuser", listOf("ROLE_ADMIN"))
        whenever(jwtAuthenticator.getAuthFromHeader("Bearer token123"))
            .thenReturn(
                ru.citeck.ecos.webapp.lib.web.authenticator.Authentication("jwtuser", authData)
            )

        val request = MockServerHttpRequest.get("/actuator/health")
            .header(EcosHttpHeaders.AUTHORIZATION, "Bearer token123")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(jwtAuthenticator).getAuthFromHeader("Bearer token123")
        verify(authoritiesProvider, never()).getAuthorities(any())
    }
}
