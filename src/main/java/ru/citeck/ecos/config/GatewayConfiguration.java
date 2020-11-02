package ru.citeck.ecos.config;

import io.github.jhipster.config.JHipsterProperties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.citeck.ecos.gateway.ratelimiting.RateLimitingFilter;
import ru.citeck.ecos.gateway.accesscontrol.AccessControlFilter;
import ru.citeck.ecos.gateway.responserewriting.SwaggerBasePathRewritingFilter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records2.RecordsProperties;

@Configuration
public class GatewayConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "ecos-gateway.ecos-records")
    public RecordsProperties recordsProperties() {
        return new RecordsProperties();
    }

    @Configuration
    public static class SwaggerBasePathRewritingConfiguration {

        @Bean
        public SwaggerBasePathRewritingFilter swaggerBasePathRewritingFilter(){
            return new SwaggerBasePathRewritingFilter();
        }
    }

    @Configuration
    public static class AccessControlFilterConfiguration {

        @Bean
        public AccessControlFilter accessControlFilter(RouteLocator routeLocator, JHipsterProperties ecosRegistryProperties){
            return new AccessControlFilter(routeLocator, ecosRegistryProperties);
        }
    }

    /**
     * Configures the Zuul filter that limits the number of API calls per user.
     * <p>
     * This uses Bucket4J to limit the API calls, see {@link ru.citeck.ecos.gateway.ratelimiting.RateLimitingFilter}.
     */
    @Configuration
    @ConditionalOnProperty("jhipster.gateway.rate-limiting.enabled")
    public static class RateLimitingConfiguration {

        private final JHipsterProperties ecosRegistryProperties;

        public RateLimitingConfiguration(JHipsterProperties ecosRegistryProperties) {
            this.ecosRegistryProperties = ecosRegistryProperties;
        }

        @Bean
        public RateLimitingFilter rateLimitingFilter() {
            return new RateLimitingFilter(ecosRegistryProperties);
        }
    }
}
