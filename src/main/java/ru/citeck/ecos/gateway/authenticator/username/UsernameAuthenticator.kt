package ru.citeck.ecos.gateway.authenticator.username

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import mu.KotlinLogging
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthUser
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.web.authenticator.Authentication
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticator
import java.util.concurrent.TimeUnit

/**
 * Authenticator works with raw username in header defined in config
 * This authenticator should not be used in untrusted networks without TLS
 */
class UsernameAuthenticator(
    private val config: UsernameAuthenticatorConfig,
    private val recordsService: RecordsService
) : WebAuthenticator {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val authoritiesCache = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .maximumSize(400)
        .build(CacheLoader.from<String, UserAuthInfo> { evalUserAuthorities(it) })

    override fun getAuthHeader(auth: Authentication): String {
        return auth.runAs.getUser()
    }

    override fun getAuthFromHeader(header: String): Authentication {
        var authInfo = authoritiesCache.getUnchecked(header)
        if (header != "admin" && authInfo.isDisabled) {
            throw UserDisabledException("User is disabled")
        }
        if (authInfo.notExists) {
            if (header == "guest") {
                error("User 'guest' does not exists and can't be created automatically")
            }
            if (header == "admin") {
                // admin will be created by patch in ecos-model
                error("User 'admin' is not created yet. Please try again later")
            }
            if (config.createUserIfNotExists) {
                synchronized(this) {
                    authoritiesCache.invalidate(header)
                    authInfo = authoritiesCache.getUnchecked(header)
                    if (authInfo.notExists) {
                        log.info { "Create new user with username: '$header'" }
                        AuthContext.runAsSystem {
                            recordsService.create(
                                "${AppName.EMODEL}/person",
                                mapOf(
                                    "id" to header
                                )
                            )
                        }
                        authoritiesCache.invalidate(header)
                        authInfo = authoritiesCache.getUnchecked(header)
                    }
                }
            } else {
                error("User '$header' does not exists and can't be created automatically")
            }
        }

        return Authentication(header, SimpleAuthData(header, authInfo.authorities))
    }

    private fun evalUserAuthorities(userName: String?): UserAuthInfo {
        userName ?: error("userName can't be null")
        if (userName == AuthUser.SYSTEM) {
            error("System user can't use gateway")
        }
        val userRef = EntityRef.create(AppName.EMODEL, "person", userName)
        val userAtts = AuthContext.runAsSystem {
            recordsService.getAtts(userRef, EmodelUserAuthAtts::class.java)
        }
        if (userAtts.authorities == null) {
            error("User authorities is null. User: $userName")
        }
        val authorities = ArrayList(userAtts.authorities)

        return UserAuthInfo(
            authorities,
            userAtts.personDisabled == true,
            userAtts.notExists == true
        )
    }

    override fun getAuthHeaderName(): String {
        return config.header
    }

    class EmodelUserAuthAtts(
        @AttName("authorities.list[]?str")
        val authorities: List<String>? = null,
        val personDisabled: Boolean?,
        @AttName(RecordConstants.ATT_NOT_EXISTS)
        val notExists: Boolean?
    )

    data class UserAuthInfo(
        val authorities: List<String>,
        val isDisabled: Boolean = false,
        val notExists: Boolean
    )
}
