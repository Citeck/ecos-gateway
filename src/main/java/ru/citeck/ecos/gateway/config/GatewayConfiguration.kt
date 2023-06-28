package ru.citeck.ecos.gateway.config

import com.hazelcast.core.HazelcastInstance
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.gateway.ratelimiting.RateLimitingFilter

@Configuration
class GatewayConfiguration {

    /**
     * Configures the Zuul filter that limits the number of API calls per user.
     *
     * This uses Bucket4J to limit the API calls, see [ru.citeck.ecos.gateway.ratelimiting.RateLimitingFilter].
     */
    @Configuration
    @ConditionalOnProperty("ecos.gateway.rateLimiting.enabled")
    class RateLimitingConfiguration(private val gatewayProperties: GatewayProperties,
                                    private val hazelcastInstance: HazelcastInstance) {
        @Bean
        fun rateLimitingFilter(): RateLimitingFilter {
            return RateLimitingFilter(gatewayProperties, hazelcastInstance)
        }
    }
}
