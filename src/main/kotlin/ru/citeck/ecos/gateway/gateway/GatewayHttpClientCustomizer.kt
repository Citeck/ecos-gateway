package ru.citeck.ecos.gateway.gateway

import org.springframework.cloud.gateway.config.HttpClientCustomizer
import org.springframework.stereotype.Component
import reactor.netty.http.client.HttpClient
import ru.citeck.ecos.commons.x509.EcosX509Registry
import ru.citeck.ecos.webapp.lib.spring.context.web.webapi.client.NettyHttpClientUtils
import ru.citeck.ecos.webapp.lib.web.webapi.client.props.EcosWebClientProps

@Component
class GatewayHttpClientCustomizer(
    private val x509Registry: EcosX509Registry,
    private val webClientProps: EcosWebClientProps
) : HttpClientCustomizer {

    override fun customize(httpClient: HttpClient): HttpClient {
        return NettyHttpClientUtils.configureTls(httpClient, x509Registry, webClientProps.tls)
    }
}
