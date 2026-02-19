package ru.citeck.ecos.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import ru.citeck.ecos.context.lib.ctx.CtxScopeData

class ReactorEcosContextUtilsTest {

    private val stubData = object : CtxScopeData {
        override fun <T : Any> get(key: Any): T? = null
    }

    @Test
    fun `withContextData and getFromContext round-trip on ContextView`() {
        val view = ReactorEcosContextUtils.withContextData(stubData)
        val result = ReactorEcosContextUtils.getFromContext(view)
        assertThat(result.isPresent).isTrue()
        assertThat(result.get()).isSameAs(stubData)
    }

    @Test
    fun `getFromContext Mono reads data written via contextWrite`() {
        val mono = ReactorEcosContextUtils.getFromContext()
            .contextWrite(ReactorEcosContextUtils.withContextData(stubData))

        StepVerifier.create(mono)
            .assertNext { optional ->
                assertThat(optional.isPresent).isTrue()
                assertThat(optional.get()).isSameAs(stubData)
            }
            .verifyComplete()
    }
}
