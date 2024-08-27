package ru.citeck.ecos.gateway.gateway

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.ctx.EcosContext
import ru.citeck.ecos.gateway.ReactorEcosContextUtils
import ru.citeck.ecos.webapp.lib.web.authenticator.Authentication
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager

@Component
class GatewayOutcomeFilter(
    authenticatorsManager: WebAuthenticatorsManager,
    private val ecosContext: EcosContext
) : GlobalFilter {

    companion object {
        private val HEADERS_TO_FILTER = setOf(
            "upgrade-insecure-requests"
        )
    }

    private val authenticator = authenticatorsManager.getJwtAuthenticator("jwt")

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {

        return ReactorEcosContextUtils.getFromContext().map { context ->
            ecosContext.newScope(context).use {
                val fullAuth = AuthContext.getCurrentFullAuth()
                authenticator.createJwtToken(Authentication(fullAuth.getUser(), fullAuth))
            }
        }.flatMap { token ->
            val newRequest = exchange.request
                .mutate()
                .headers {
                    it.setBearerAuth(token)
                    for (header in HEADERS_TO_FILTER) {
                        it.remove(header)
                    }
                    val iter = it.keys.iterator()
                    while (iter.hasNext()) {
                        val key = iter.next()
                        if (key.startsWith("sec-", ignoreCase = true)) {
                            iter.remove()
                        }
                    }
                }
                .build()
            val newExchange = exchange.mutate()
                .request(newRequest)
                .build()

            chain.filter(newExchange)
        }
    }
}

