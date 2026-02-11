package ru.citeck.ecos.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import ru.citeck.ecos.webapp.lib.spring.EcosSpringApplication

@SpringBootApplication
class EcosGateway {
    companion object {
        const val NAME = "gateway"
    }
}

fun main(args: Array<String>) {
    EcosSpringApplication(EcosGateway::class.java).run(*args)
}
