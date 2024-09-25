package ru.citeck.ecos.gateway.config

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder

class GatewayProps(
    val userNameExtractors: List<UserNameExtractor>
) {

    @JsonDeserialize(builder = UserNameExtractor.Builder::class)
    class UserNameExtractor(
        val matcher: Regex,
        val regexGroup: Int
    ) {

        @JsonPOJOBuilder
        class Builder {

            private lateinit var matcher: Regex
            private var regexGroup: Int = 0

            fun withMatcher(matcher: String): Builder {
                this.matcher = matcher.toRegex()
                return this
            }

            fun withRegexGroup(regexGroup: Int): Builder {
                this.regexGroup = regexGroup
                return this
            }

            fun build(): UserNameExtractor {
                return UserNameExtractor(matcher, regexGroup)
            }
        }
    }
}
