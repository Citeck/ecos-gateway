package ru.citeck.ecos.gateway.api

import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.citeck.ecos.context.lib.auth.AuthRole

/**
 * REST controller for managing Gateway configuration.
 */
@RestController
@RequestMapping("/api/gateway")
class GatewayResource {

    @GetMapping("/touch")
    @Secured(AuthRole.USER)
    fun touch(): Mono<String> {
        return Mono.just("OK")
    }
}
