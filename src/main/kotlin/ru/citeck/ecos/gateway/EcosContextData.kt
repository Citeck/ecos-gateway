package ru.citeck.ecos.gateway

import reactor.core.publisher.Mono
import reactor.util.context.Context
import reactor.util.context.ContextView
import ru.citeck.ecos.context.lib.auth.data.AuthData
import java.util.*

class EcosContextData(
    val userAuth: AuthData,
    val timeZoneOffsetInMinutes: Long,
    val locale: Locale,
    val realIp: String?
) {
    companion object {
        private val CONTEXT_KEY = EcosContextData::class.java

        fun withContextData(data: EcosContextData): ContextView {
            return Context.of(CONTEXT_KEY, data)
        }

        fun getFromContext(): Mono<EcosContextData> {
            return Mono.deferContextual { contextView ->
                Mono.just(contextView)
            }.map {
                getFromContext(it)
            }
        }

        fun getFromContext(context: ContextView): EcosContextData {
            return context.get(CONTEXT_KEY)
        }
    }

}
