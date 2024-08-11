package ru.citeck.ecos.gateway.api

import jakarta.annotation.PostConstruct
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.records2.source.dao.local.meta.MetaRecordsDao
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.apps.EcosRemoteWebAppsApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridge
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridgeFactory

/**
 * REST controller for managing Gateway configuration.
 */
@RestController
@RequestMapping("/api/gateway")
class GatewayResource(
    private val webAppsApi: EcosRemoteWebAppsApi,
    private val recordsService: RecordsService,
    private val reactorBridgeFactory: ReactorBridgeFactory
) {

    companion object {
        private val ALF_META_REF: EntityRef = EntityRef.create(AppName.ALFRESCO, MetaRecordsDao.ID, "")
    }

    private lateinit var bridge: ReactorBridge

    @PostConstruct
    fun init() {
        bridge = reactorBridgeFactory.getBridge("touch")
    }

    @GetMapping("/touch")
    @Secured(AuthRole.USER)
    fun touch(): Mono<String> {
        return bridge.execute {
            if (webAppsApi.isAppAvailable(AppName.ALFRESCO)) {
                recordsService.getAtt(ALF_META_REF, "time").asText()
            } else {
                "OK"
            }
        }
    }
}
