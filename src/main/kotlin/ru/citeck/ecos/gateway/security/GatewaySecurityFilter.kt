package ru.citeck.ecos.gateway.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import ru.citeck.ecos.context.lib.auth.AuthRole

@Configuration
@EnableWebFluxSecurity
class GatewaySecurityFilter {

    @Bean
    @Order(-1000)
    fun ecosGatewaySpringSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .httpBasic { it.disable() }
            .csrf {
                it.disable()
            }
            .headers { h ->
                h.frameOptions { it.disable() }
            }
            .authorizeExchange { exchanges ->
                exchanges
                    // authentication will be in EcosWebExecutorsService
                    .pathMatchers("/api/ecos/webapi").permitAll()
                    // Public API doesn't require permissions check on this level
                    .pathMatchers("/pub/**").permitAll()
                    .pathMatchers("/*/pub/**").permitAll()
                    .pathMatchers("/api/**").hasAnyAuthority(AuthRole.USER, AuthRole.ADMIN)
                    .pathMatchers("/*/api/**").hasAnyAuthority(AuthRole.USER, AuthRole.ADMIN)
                    .pathMatchers("/*/alfresco/**").hasAnyAuthority(AuthRole.USER, AuthRole.ADMIN)
                    .pathMatchers("/*/share/**").hasAnyAuthority(AuthRole.USER, AuthRole.ADMIN)
                    .pathMatchers("/management/health").permitAll()
                    // This is for test purposes. Normally management endpoints should be used directly without gateway
                    .pathMatchers("/*/management/**").hasAnyAuthority(AuthRole.ADMIN)
                    .pathMatchers("/management/info").permitAll()
                    // Open metrics, because at current installations we always behind reverse proxy
                    .pathMatchers("/management/prometheus").permitAll()
                    .pathMatchers("/management/**").hasAuthority(AuthRole.ADMIN)
                    .anyExchange().denyAll()
            }
            .build()
    }
}
