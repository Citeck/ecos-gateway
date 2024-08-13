package ru.citeck.ecos.gateway

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.boot.web.reactive.filter.OrderedWebFilter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.data.AuthData
import ru.citeck.ecos.context.lib.auth.data.AuthState
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.context.lib.client.ClientContext
import ru.citeck.ecos.context.lib.client.data.ClientData
import ru.citeck.ecos.context.lib.ctx.EcosContext
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.context.lib.time.TimeZoneContext
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridge
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridgeFactory
import ru.citeck.ecos.webapp.lib.web.http.EcosHttpHeaders
import java.time.Duration

@Component
class GatewayIncomeFilter(
    private val reactorBridgeFactory: ReactorBridgeFactory,
    private val authoritiesProvider: AuthoritiesProvider,
    private val tracer: io.micrometer.tracing.Tracer,
    private val ecosContext: EcosContext
) : OrderedWebFilter {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private lateinit var getAuthoritiesBridge: ReactorBridge

    @PostConstruct
    fun init() {
        getAuthoritiesBridge = reactorBridgeFactory.getBridge("get-auth")
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {

        val user = exchange.request.headers.getFirst(EcosHttpHeaders.X_ECOS_USER)
        val tzOffset = readTimeZone(exchange.request.headers)
        val locale = exchange.localeContext.locale ?: I18nContext.ENGLISH
        val realIp = exchange.request.headers.getFirst(EcosHttpHeaders.X_REAL_IP)

        val traceId = tracer.currentTraceContext().context()?.traceId() ?: ""
        if (traceId.isNotBlank()) {
            exchange.response.headers.set(EcosHttpHeaders.X_ECOS_TRACE_ID, traceId)
        }

        if (!user.isNullOrEmpty()) {
            return getAuthoritiesBridge.execute {
                authoritiesProvider.getAuthorities(user)
            }.flatMap { authorities ->
                val authData = SimpleAuthData(user, authorities)

                val ctxData = ecosContext.newScope().use { scope ->

                    I18nContext.set(scope, locale)
                    ClientContext.set(scope, ClientData(realIp ?: ""))
                    TimeZoneContext.set(scope, tzOffset)
                    AuthContext.set(scope, AuthState(authData))

                    ecosContext.getScopeData()
                }

                chain.filter(exchange)
                    .contextWrite(
                        ReactiveSecurityContextHolder.withAuthentication(
                            buildAuthentication(authData)
                        )
                    ).contextWrite(
                        ReactorEcosContextUtils.withContextData(ctxData)
                    )
            }
        } else {
            return chain.filter(exchange)
        }
    }

    private fun readTimeZone(headers: org.springframework.http.HttpHeaders): Duration {
        val timeZoneHeader = headers.getFirst(EcosHttpHeaders.X_ECOS_TIMEZONE) ?: return Duration.ZERO
        return try {
            timeZoneHeader.split(";")[0].toLong().let { Duration.ofMinutes(it) }
        } catch (e: Exception) {
            log.debug { "Invalid timezone header: $timeZoneHeader" }
            Duration.ZERO
        }
    }

    private fun buildAuthentication(auth: AuthData): Authentication? {

        if (auth.isEmpty()) {
            return null
        }

        val user = auth.getUser()
        val authorities = auth.getAuthorities()

        val grantedAuthorities = authorities
            .filter { it.isNotBlank() }
            .map { SimpleGrantedAuthority(it) }

        val principal = User(user, "", grantedAuthorities)
        return UsernamePasswordAuthenticationToken(principal, "", grantedAuthorities)
    }

    override fun getOrder(): Int {
        return -100
    }
}