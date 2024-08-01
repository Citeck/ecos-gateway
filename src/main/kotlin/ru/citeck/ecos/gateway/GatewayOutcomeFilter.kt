package ru.citeck.ecos.gateway

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import ru.citeck.ecos.webapp.lib.web.authenticator.Authentication
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager
import ru.citeck.ecos.webapp.lib.web.http.HttpHeaders

@Component
class GatewayOutcomeFilter(
    authenticatorsManager: WebAuthenticatorsManager
) : GlobalFilter {

    private val authenticator = authenticatorsManager.getJwtAuthenticator("jwt")

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {

        return EcosContextData.getFromContext().map { context ->
            authenticator.createJwtToken(Authentication(context.userAuth.getUser(), context.userAuth))
        }.flatMap { token ->
            val newRequest = exchange.request
                .mutate()
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .build()
            val newExchange = exchange.mutate()
                .request(newRequest)
                .build()

            chain.filter(newExchange)
        }
    }
}

