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
import ru.citeck.ecos.context.lib.auth.data.AuthData
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridge
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridgeFactory
import ru.citeck.ecos.webapp.lib.web.http.HttpHeaders

@Component
class GatewayIncomeFilter(
    val reactorBridgeFactory: ReactorBridgeFactory,
    val authoritiesProvider: AuthoritiesProvider
) : OrderedWebFilter {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private lateinit var getAuthoritiesBridge: ReactorBridge

    @PostConstruct
    fun init() {
        getAuthoritiesBridge = reactorBridgeFactory.getBridge(
            "out-filter-auth",
            "rr-bridge-records"
        )
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {

        val user = exchange.request.headers.getFirst(HttpHeaders.X_ECOS_USER)
        val timeZoneOffsetInMinutes = readTimeZone(exchange.request.headers)
        val locale = exchange.localeContext.locale ?: I18nContext.ENGLISH
        val realIp = exchange.request.headers.getFirst(HttpHeaders.X_REAL_IP)

        if (!user.isNullOrEmpty()) {
            return getAuthoritiesBridge.execute {
                authoritiesProvider.getAuthorities(user)
            }.flatMap { authorities ->
                val authData = SimpleAuthData(user, authorities)
                chain.filter(exchange)
                    .contextWrite(
                        ReactiveSecurityContextHolder.withAuthentication(
                            buildAuthentication(authData)
                        )
                    ).contextWrite(
                        EcosContextData.withContextData(
                            EcosContextData(
                                userAuth = authData,
                                timeZoneOffsetInMinutes = timeZoneOffsetInMinutes,
                                locale = locale,
                                realIp = realIp,
                            )
                        )
                    )
            }
        } else {
            return chain.filter(exchange)
        }
    }

    private fun readTimeZone(headers: org.springframework.http.HttpHeaders): Long {
        val timeZoneHeader = headers.getFirst(HttpHeaders.X_ECOS_TIMEZONE) ?: return 0
        return try {
            timeZoneHeader.split(";")[0].toLong()
        } catch (e: Exception) {
            log.debug { "Invalid timezone header: $timeZoneHeader" }
            0
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
