package ru.citeck.ecos.gateway.authenticator.username

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.RecordsService
import org.springframework.context.annotation.Lazy
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticator
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorFactory

@Component
class UsernameAuthenticatorFactory : WebAuthenticatorFactory<UsernameAuthenticatorConfig> {

    private lateinit var recordsService: RecordsService

    override fun create(config: UsernameAuthenticatorConfig): WebAuthenticator {
        return UsernameAuthenticator(config, recordsService)
    }

    override fun getType(): String {
        return "username"
    }

    @Lazy
    @Autowired
    fun setRecordsService(recordsService: RecordsService) {
        this.recordsService = recordsService
    }
}
