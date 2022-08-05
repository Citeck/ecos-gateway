package ru.citeck.ecos.gateway.client

import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.webapp.lib.web.authenticator.Authentication
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticator
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager
import ru.citeck.ecos.webapp.lib.web.client.props.EcosWebClientProps
import ru.citeck.ecos.webapp.lib.web.http.HttpHeaders
import javax.annotation.PostConstruct

@Component
class EcosZuulFilter(
    private val authenticatorsManager: WebAuthenticatorsManager,
    private val clientProps: EcosWebClientProps
) : ZuulFilter() {

    private var authenticator: WebAuthenticator? = null

    @PostConstruct
    fun init() {
        if (clientProps.authenticator.isBlank()) {
            return
        }
        this.authenticator = authenticatorsManager.getAuthenticator(clientProps.authenticator)
    }

    override fun shouldFilter(): Boolean {
        return authenticator != null
    }

    override fun run(): Any? {

        val authenticator = authenticator ?: return null
        val user = AuthContext.getCurrentUser()
        if (user.isEmpty()) {
            error("User is empty")
        }
        val auth = authenticator.getAuthHeader(Authentication(user, AuthContext.getCurrentRunAsAuth()))
        RequestContext.getCurrentContext().addZuulRequestHeader(HttpHeaders.AUTHORIZATION, auth)

        return null
    }

    override fun filterType(): String {
        return "pre"
    }

    override fun filterOrder(): Int {
        return 10001
    }
}
