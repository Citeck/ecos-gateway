package ru.citeck.ecos.gateway

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthUser
import ru.citeck.ecos.gateway.exception.UserDisabledException
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.apps.EcosRemoteWebAppsApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

@Component
class AuthoritiesProvider(
    private val recordsService: RecordsService,
    private val remoteWebAppsApi: EcosRemoteWebAppsApi
) {

    companion object {
        const val USER_ADMIN = "admin"

        private val log = KotlinLogging.logger {}
    }

    private val createNonexistentUserLock = ReentrantLock()

    // simple cache to avoid strings duplications in memory
    private val authorityStringsCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .softValues()
        .build<String, String> { it }

    private val authoritiesCache = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(Duration.ofMinutes(1))
        .refreshAfterWrite(Duration.ofSeconds(20))
        .executor(
            Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("cache-update").factory()
            )
        )
        .build<String, UserAuthInfo> { evalUserAuthorities(it) }

    fun getAuthorities(userName: String): List<String> {
        if (userName == AuthUser.SYSTEM) {
            error("System user can't use gateway")
        }
        var authInfo = authoritiesCache.get(userName)
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
            createNonexistentUserLock.lock()
            try {
                authoritiesCache.invalidate(userName)
                authInfo = authoritiesCache.get(userName)
                if (authInfo.notExists) {
                    log.info { "Create new user with username: '$userName'" }
                    AuthContext.runAsSystem {
                        recordsService.create(
                            "${AppName.EMODEL}/person",
                            mapOf("id" to userName)
                        )
                    }
                    authoritiesCache.invalidate(userName)
                    authInfo = authoritiesCache.get(userName)
                }
            } finally {
                createNonexistentUserLock.unlock()
            }
        }
        return authInfo.authorities
    }

    private fun evalUserAuthorities(userName: String): UserAuthInfo {

        val userRef = EntityRef.create(AppName.EMODEL, "person", userName)

        var userAtts: EmodelUserAuthAtts = EmodelUserAuthAtts.EMPTY

        val timeout = System.currentTimeMillis() + 60_000
        fun checkTimeout(exceptionProvider: () -> Throwable) {
            if (System.currentTimeMillis() > timeout) {
                throw exceptionProvider()
            }
        }
        while (!remoteWebAppsApi.isAppAvailable(AppName.EMODEL)) {
            checkTimeout {
                RuntimeException("Application is not available: ${AppName.EMODEL}")
            }
            Thread.sleep(500)
        }
        try {
            userAtts = AuthContext.runAsSystem {
                recordsService.getAtts(userRef, EmodelUserAuthAtts::class.java)
            }
        } catch (e: Throwable) {
            checkTimeout { e }
            Thread.sleep(2000)
        }

        val authorities = userAtts.authorities ?: error("User authorities is null. User: $userName")
        return UserAuthInfo(
            authorities.map { authorityStringsCache.get(it) },
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
    ) {
        companion object {
            val EMPTY = EmodelUserAuthAtts(
                null,
                null,
                null
            )
        }
    }

    data class UserAuthInfo(
        val authorities: List<String>,
        val isDisabled: Boolean = false,
        val notExists: Boolean
    )
}
