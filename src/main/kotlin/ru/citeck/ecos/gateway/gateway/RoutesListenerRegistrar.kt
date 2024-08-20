package ru.citeck.ecos.gateway.gateway

import jakarta.annotation.PostConstruct
import org.springframework.cloud.gateway.event.RefreshRoutesEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService

@Component
class RoutesListenerRegistrar(
    private val webAppDiscoveryService: WebAppDiscoveryService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @PostConstruct
    fun init() {
        webAppDiscoveryService.listenChanges {
            eventPublisher.publishEvent(RefreshRoutesEvent(this))
        }
    }
}
