package ru.citeck.ecos.gateway.rest;

import lombok.RequiredArgsConstructor;
import ru.citeck.ecos.context.lib.auth.AuthRole;
import ru.citeck.ecos.gateway.rest.vm.RouteVM;

import java.util.ArrayList;
import java.util.List;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.http.*;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.source.dao.local.meta.MetaRecordsDao;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.webapp.api.apps.EcosWebAppsApi;
import ru.citeck.ecos.webapp.api.constants.AppName;

/**
 * REST controller for managing Gateway configuration.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gateway")
public class GatewayResource {

    private static final RecordRef ALF_META_REF = RecordRef.create(AppName.ALFRESCO, MetaRecordsDao.ID, "");

    private final RouteLocator routeLocator;
    private final DiscoveryClient discoveryClient;
    private final EcosWebAppsApi webAppsApi;
    private final RecordsService recordsService;

    /**
     * GET  /routes : get the active routes.
     *
     * @return the ResponseEntity with status 200 (OK) and with body the list of routes
     */
    @GetMapping("/routes")
    @Secured(AuthRole.ADMIN)
    public ResponseEntity<List<RouteVM>> activeRoutes() {
        List<Route> routes = routeLocator.getRoutes();
        List<RouteVM> routeVMs = new ArrayList<>();
        routes.forEach(route -> {
            RouteVM routeVM = new RouteVM();
            routeVM.setPath(route.getFullPath());
            routeVM.setServiceId(route.getId());
            routeVM.setServiceInstances(discoveryClient.getInstances(route.getLocation()));
            routeVMs.add(routeVM);
        });
        return new ResponseEntity<>(routeVMs, HttpStatus.OK);
    }

    @GetMapping("/touch")
    @Secured(AuthRole.USER)
    public String touch() {
        if (webAppsApi.isAppAvailable(AppName.ALFRESCO)) {
            recordsService.getAtt(ALF_META_REF, "time");
        }
        return "OK";
    }
}
