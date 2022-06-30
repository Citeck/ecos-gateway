package ru.citeck.ecos.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Properties specific to Gateway.
 *
 * Properties are configured in the application.yml file.
 */
@ConfigurationProperties(prefix = "ecos.gateway", ignoreUnknownFields = false)
class GatewayProperties {

    private val rateLimiting = RateLimiting()

    fun getRateLimiting(): RateLimiting {
        return rateLimiting
    }

    class RateLimiting {
        var enabled: Boolean = true
        var limit: Long = 100_000
        var durationInSeconds: Long = 3600
    }
}
