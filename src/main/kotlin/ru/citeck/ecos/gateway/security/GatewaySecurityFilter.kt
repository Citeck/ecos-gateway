package ru.citeck.ecos.gateway.security

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import ru.citeck.ecos.context.lib.auth.AuthRole

@Configuration
@EnableWebFluxSecurity
class GatewaySecurityFilter {

    @Bean
    @Order(-1000)
    fun ecosGatewaySpringSecurityFilterChain(
        http: ServerHttpSecurity,
        discoveryClient: ReactiveDiscoveryClient
    ): SecurityWebFilterChain {
        return http
            .httpBasic { it.disable() }
            .csrf { it.disable() }
            .headers { h ->
                h.frameOptions { it.disable() }
            }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers(
                        "/api/ecos/webapi",
                        "/pub/**",
                        "/*/pub/**",
                        "/management/health",
                        "/management/info",
                        // Open metrics, because at current installations we always behind reverse proxy
                        "/management/prometheus"
                    ).permitAll()
                    .pathMatchers(
                        "/api/**",
                        "/*/api/**",
                        "/*/alfresco/**",
                        "/*/share/**"
                    ).hasAnyAuthority(AuthRole.USER)
                    .pathMatchers(
                        "/management",
                        "/management/**",
                        "/*/management",
                        "/*/management/**"
                    ).hasAnyAuthority(AuthRole.ADMIN, AuthRole.SYSTEM)
                    // allow to call custom endpoints in registered applications
                    .matchers(RegisteredWebAppMatcher(discoveryClient)).hasAnyAuthority(AuthRole.USER)
                    .anyExchange().denyAll()
            }
            .build()
    }

    /**
     * Matcher for custom endpoints in registered services.
     */
    private class RegisteredWebAppMatcher(val discoveryClient: ReactiveDiscoveryClient) : ServerWebExchangeMatcher {

        private val appsCache = Caffeine.newBuilder()
            .maximumSize(500)
            .build<String, Mono<Boolean>> {
                discoveryClient.services.hasElement(it)
            }

        override fun matches(exchange: ServerWebExchange): Mono<ServerWebExchangeMatcher.MatchResult> {
            val path = exchange.request.path.pathWithinApplication()
            return if (path.elements().size < 4) {
                ServerWebExchangeMatcher.MatchResult.notMatch()
            } else {
                val appName = path.subPath(1, 2).value()
                if (appName.length > 50) {
                    return ServerWebExchangeMatcher.MatchResult.notMatch()
                }
                appsCache.get(appName).flatMap { res ->
                    if (res) {
                        ServerWebExchangeMatcher.MatchResult.match()
                    } else {
                        ServerWebExchangeMatcher.MatchResult.notMatch()
                    }
                }
            }
        }
    }
}
