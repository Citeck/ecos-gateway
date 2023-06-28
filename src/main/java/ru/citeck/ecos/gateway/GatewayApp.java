package ru.citeck.ecos.gateway;

import ru.citeck.ecos.gateway.config.GatewayProperties;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import ru.citeck.ecos.webapp.lib.spring.EcosSpringApplication;

@SpringBootApplication
@EnableConfigurationProperties({GatewayProperties.class})
@EnableDiscoveryClient
@EnableZuulProxy
public class GatewayApp {

    public static final String NAME = "gateway";

    public static void main(String[] args) {
        new EcosSpringApplication(GatewayApp.class).run(args);
    }
}
