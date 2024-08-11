package ru.citeck.ecos.gateway

import reactor.core.publisher.Mono
import reactor.util.context.Context
import reactor.util.context.ContextView
import ru.citeck.ecos.context.lib.ctx.CtxScopeData

object ReactorEcosContextUtils {

    private val RR_CONTEXT_KEY = CtxScopeData::class.java

    fun withContextData(data: CtxScopeData): ContextView {
        return Context.of(RR_CONTEXT_KEY, data)
    }

    fun getFromContext(): Mono<CtxScopeData> {
        return Mono.deferContextual { contextView ->
            Mono.just(contextView)
        }.map {
            getFromContext(it)
        }
    }

    fun getFromContext(context: ContextView): CtxScopeData {
        return context.get(RR_CONTEXT_KEY)
    }

}
