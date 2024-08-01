package ru.citeck.ecos.gateway

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthUser
import ru.citeck.ecos.gateway.exception.UserDisabledException
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.concurrent.TimeUnit

// todo: rewrite to reactive
@Component
class AuthoritiesProvider(
    private val recordsService: RecordsService
) {

    companion object {
        const val USER_ADMIN = "admin"

        private val log = KotlinLogging.logger {}
    }

    private val authoritiesCache = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .maximumSize(400)
        .build(CacheLoader.from<String, UserAuthInfo> { evalUserAuthorities(it) })

    fun getAuthorities(userName: String): List<String> {
        var authInfo = authoritiesCache.getUnchecked(userName)
        if (userName != USER_ADMIN && authInfo.isDisabled) {
            throw UserDisabledException("User is disabled")
        }
        if (authInfo.notExists) {
            if (userName == "guest") {
                error("User 'guest' does not exists and can't be created automatically")
            }
            if (userName == USER_ADMIN) {
                // admin will be created by patch in ecos-model
                error("User 'admin' is not created yet. Please try again later")
            }
            synchronized(this) {
                authoritiesCache.invalidate(userName)
                authInfo = authoritiesCache.getUnchecked(userName)
                if (authInfo.notExists) {
                    log.info { "Create new user with username: '$userName'" }
                    AuthContext.runAsSystem {
                        recordsService.create(
                            "${AppName.EMODEL}/person",
                            mapOf("id" to userName)
                        )
                    }
                    authoritiesCache.invalidate(userName)
                    authInfo = authoritiesCache.getUnchecked(userName)
                }
            }
        }

        return authInfo.authorities
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
