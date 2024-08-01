package ru.citeck.ecos.gateway.api

import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.client.ClientContext
import ru.citeck.ecos.context.lib.client.data.ClientData
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.context.lib.time.TimeZoneContext
import ru.citeck.ecos.gateway.EcosContextData
import ru.citeck.ecos.records2.request.result.RecordsResult
import ru.citeck.ecos.records2.utils.SecurityUtils
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.request.msg.MsgLevel
import ru.citeck.ecos.records3.rest.RestHandlerAdapter
import ru.citeck.ecos.records3.rest.v1.RequestResp
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridge
import ru.citeck.ecos.webapp.lib.spring.context.webflux.bridge.ReactorBridgeFactory
import java.time.Duration

@RestController
@RequestMapping("/api/records")
class RecordsRestApi @Autowired constructor(
    services: RecordsServiceFactory,
    private val reactorBridgeFactory: ReactorBridgeFactory,
) {

    private lateinit var recordsReactorBridge: ReactorBridge

    private val restHandlerAdapter: RestHandlerAdapter = services.restHandlerAdapter
    private var isProdProfile = true
    private var environment: Environment? = null

    @PostConstruct
    fun init() {
        recordsReactorBridge = reactorBridgeFactory.getBridge(
            "records-rest-api",
            "rr-bridge-records"
        )
    }

    @EventListener
    fun onApplicationEvent(event: ContextRefreshedEvent?) {
        isProdProfile = environment?.matchesProfiles("prod") == true
    }

    @PostMapping(value = ["/query"])
    fun recordsQuery(
        @RequestBody
        body: ByteArray
    ): Mono<ResponseEntity<ByteArray>> {
        val bodyData = Json.mapper.readNotNull(body, ObjectNode::class.java)
        val version = bodyData.get("version").asInt(2)
        return doInContext {
            encodeResponse(restHandlerAdapter.queryRecords(bodyData, version))
        }
    }

    @PostMapping(value = ["/mutate"])
    fun recordsMutate(
        @RequestBody
        body: ByteArray
    ): Mono<ResponseEntity<ByteArray>> {
        val mutationBody = Json.mapper.readNotNull(body, ObjectNode::class.java)
        return doInContext {
            encodeResponse(restHandlerAdapter.mutateRecords(mutationBody, 1))
        }
    }

    @PostMapping(value = ["/delete"])
    fun recordsDelete(
        @RequestBody
        body: ByteArray
    ): Mono<ResponseEntity<ByteArray>> {
        val deletionBody = Json.mapper.readNotNull(body, ObjectNode::class.java)
        return doInContext {
            encodeResponse(restHandlerAdapter.deleteRecords(deletionBody, 1))
        }
    }

    private inline fun doInContext(
        crossinline action: () -> ResponseEntity<ByteArray>
    ): Mono<ResponseEntity<ByteArray>> {

        return EcosContextData.getFromContext().flatMap { contextData ->
            recordsReactorBridge.execute {
                I18nContext.doWithLocale(contextData.locale) {
                    AuthContext.runAsFull(contextData.userAuth) {
                        TimeZoneContext.doWithUtcOffset(Duration.ofMinutes(contextData.timeZoneOffsetInMinutes)) {
                            doWithClientData(contextData.realIp) {
                                action.invoke()
                            }
                        }
                    }
                }
            }
        }
    }

    private inline fun <T> doWithClientData(realIp: String?, crossinline action: () -> T): T {
        return if (realIp.isNullOrBlank()) {
            action.invoke()
        } else {
            ClientContext.doWithClientData(ClientData(realIp)) { action.invoke() }
        }
    }

    private fun encodeResponse(response: Any): ResponseEntity<ByteArray> {
        var status = HttpStatus.OK
        if (response is RecordsResult<*>) {
            if (response.errors.isNotEmpty()) {
                status = HttpStatus.INTERNAL_SERVER_ERROR
            }
            if (isProdProfile) {
                SecurityUtils.encodeResult(response)
            }
        } else if (response is RequestResp) {
            if (response.messages.any { it.level == MsgLevel.ERROR }) {
                status = HttpStatus.INTERNAL_SERVER_ERROR
            }
            if (isProdProfile) {
                SecurityUtils.encodeResult(response)
            }
        }
        val headers = HttpHeaders()
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        return ResponseEntity<ByteArray>(Json.mapper.toBytesNotNull(response), headers, status)
    }

    @Autowired(required = false)
    fun setEnvironment(environment: Environment?) {
        this.environment = environment
    }
}
