package ru.citeck.ecos.gateway.config

class GatewayProps(
    val userNameExtractors: List<UserNameExtractor> = emptyList()
) {

    class UserNameExtractor(
        val matcher: String,
        val regexGroup: Int = 0
    )
}
