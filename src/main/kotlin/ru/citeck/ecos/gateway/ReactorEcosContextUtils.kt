package ru.citeck.ecos.gateway

import reactor.core.publisher.Mono
import reactor.util.context.Context
import reactor.util.context.ContextView
import ru.citeck.ecos.context.lib.ctx.CtxScopeData
import java.util.*

object ReactorEcosContextUtils {

    private val RR_CONTEXT_KEY = CtxScopeData::class.java

    fun withContextData(data: CtxScopeData): ContextView {
        return Context.of(RR_CONTEXT_KEY, data)
    }

    fun getFromContext(): Mono<Optional<CtxScopeData>> {
        return Mono.deferContextual { contextView ->
            Mono.just(contextView)
        }.map {
            getFromContext(it)
        }
    }

    fun getFromContext(context: ContextView): Optional<CtxScopeData> {
        return context.getOrEmpty(RR_CONTEXT_KEY)
    }
}
