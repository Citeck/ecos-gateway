package ru.citeck.ecos.gateway.authenticator.username

import ru.citeck.ecos.webapp.lib.web.http.HttpHeaders

data class UsernameAuthenticatorConfig(
    val createUserIfNotExists: Boolean = false,
    val header: String = HttpHeaders.X_ECOS_USER
)
