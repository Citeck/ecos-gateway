package ru.citeck.ecos.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment

@Configuration
class GatewayConfig {

    @Bean
    fun ecosGatewayProps(ecosWebAppEnvironment: EcosWebAppEnvironment): GatewayProps {
        return ecosWebAppEnvironment.getValue("ecos.gateway", GatewayProps::class.java)
    }
}
