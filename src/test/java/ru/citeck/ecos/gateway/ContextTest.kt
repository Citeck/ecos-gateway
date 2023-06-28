package ru.citeck.ecos.gateway

import mu.KotlinLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@SpringBootTest(classes = [GatewayApp::class])
@ExtendWith(EcosSpringExtension::class)
class ContextTest {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Test
    fun contextTest() {
        log.info { "====================================" }
        log.info { "Context was successfully initialized" }
        log.info { "====================================" }
    }
}
