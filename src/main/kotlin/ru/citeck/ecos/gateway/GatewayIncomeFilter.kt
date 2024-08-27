package ru.citeck.ecos.gateway

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.slf4j.MDC
import org.springframework.boot.web.reactive.filter.OrderedWebFilter
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.loadbalancer.Response
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import ru.citeck.ecos.commons.utils.ExceptionUtils
import ru.citeck.ecos.context.lib.auth.AuthConstants
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
import java.net.URI
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

        return if (user.isNullOrEmpty()) {
            chain.filter(exchange)
        } else {
            filterWithUser(user, exchange, chain)
        }.doOnError { error ->
            logRequestError(user, exchange, error)
        }.then(
            Mono.fromRunnable {
                val response = exchange.response
                if (response.statusCode?.is2xxSuccessful != true) {
                    logRequestError(user, exchange, null)
                }
            }
        )
    }

    private fun logRequestError(user: String?, exchange: ServerWebExchange, error: Throwable?) {
        if (user.isNullOrEmpty()) {
            log.error { extractErrorMsg(exchange, error) }
        } else {
            MDC.putCloseable(AuthConstants.MDC_USER_KEY, user).use {
                log.error { extractErrorMsg(exchange, error) }
            }
        }
    }

    private fun filterWithUser(user: String, exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {

        val tzOffset = readTimeZone(exchange.request.headers)
        val locale = exchange.localeContext.locale ?: I18nContext.ENGLISH
        val realIp = exchange.request.headers.getFirst(EcosHttpHeaders.X_REAL_IP)

        val traceId = tracer.currentTraceContext().context()?.traceId() ?: ""
        if (traceId.isNotBlank()) {
            exchange.response.headers.set(EcosHttpHeaders.X_ECOS_TRACE_ID, traceId)
        }

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

    private fun extractErrorMsg(exchange: ServerWebExchange, error: Throwable?): String {

        val statusCode = exchange.response.statusCode?.value() ?: -1

        val loadBalancerResp: Response<ServiceInstance>? =
            exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR)

        val request = exchange.request
        val errorMsg = if (error != null) {
            val rootCause = ExceptionUtils.getRootCause(error)
            rootCause::class.simpleName + ": " + rootCause.message
        } else {
            ""
        }

        return RequestErrorInfo(statusCode, request.method, request.uri, errorMsg, loadBalancerResp).toString()
    }

    private class RequestErrorInfo(
        val statusCode: Int,
        val requestMethod: HttpMethod,
        val requestUri: URI,
        val errorMsg: String,
        val lbResponse: Response<ServiceInstance>?
    ) {
        override fun toString(): String {
            val lbMsg = if (lbResponse != null) {
                if (!lbResponse.hasServer()) {
                    "app-not-found"
                } else {
                    val server = lbResponse.server
                    val uri = String.format("%s://%s:%s", server.scheme, server.host, server.port)
                    server.serviceId + ":" + server.instanceId + " => " + uri
                }
            } else {
                "no-lb-resp"
            }
            return "\"$requestMethod ${requestUri}\" $statusCode ($lbMsg) $errorMsg"
        }
    }
}
