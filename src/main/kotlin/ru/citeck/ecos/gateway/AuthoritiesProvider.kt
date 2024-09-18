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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
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
    private val concurrentRequestsSemaphore = Semaphore(50)

    // simple cache to avoid strings duplications in memory
    private val authorityStringsCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .softValues()
        .build<String, String> { it }

    private val cacheUpdateExecutor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("cache-update").factory()
    )

    private val authoritiesCache = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(Duration.ofMinutes(1))
        .refreshAfterWrite(Duration.ofSeconds(20))
        .executor(cacheUpdateExecutor)
        .buildAsync<String, UserAuthInfo> { key, _ ->
            // async build required to avoid thread pinning in ConcurrentHashMap.computeIfAbsent
            val result = CompletableFutureWrapper<UserAuthInfo>()
            result.future = cacheUpdateExecutor.submit {
                try {
                    result.complete(evalUserAuthorities(key))
                } catch (e: Throwable) {
                    result.completeExceptionally(e)
                }
            }
            result
        }

    fun getAuthorities(userName: String): List<String> {
        if (userName.isBlank() || userName == AuthUser.GUEST) {
            return emptyList()
        }
        if (userName == AuthUser.SYSTEM) {
            error("System user can't use gateway")
        }
        var authInfo = authoritiesCache.get(userName).get()
        if (userName != USER_ADMIN && authInfo.isDisabled) {
            throw UserDisabledException("User is disabled")
        }
        if (authInfo.notExists) {
            if (userName == USER_ADMIN) {
                // admin will be created by patch in ecos-model
                error("User 'admin' is not created yet. Please try again later")
            }
            createNonexistentUserLock.lock()
            try {

                authoritiesCache.asMap().remove(userName)
                authInfo = authoritiesCache.get(userName).get()
                if (authInfo.notExists) {
                    log.info { "Create new user with username: '$userName'" }
                    AuthContext.runAsSystem {
                        recordsService.create(
                            "${AppName.EMODEL}/person",
                            mapOf("id" to userName)
                        )
                    }
                    authoritiesCache.asMap().remove(userName)
                    authInfo = authoritiesCache.get(userName).get()
                }
            } finally {
                createNonexistentUserLock.unlock()
            }
        }
        return authInfo.authorities
    }

    private fun evalUserAuthorities(userName: String): UserAuthInfo {
        concurrentRequestsSemaphore.acquire()
        try {
            return evalUserAuthoritiesImpl(userName)
        } finally {
            concurrentRequestsSemaphore.release()
        }
    }

    private fun evalUserAuthoritiesImpl(userName: String): UserAuthInfo {

        val userRef = EntityRef.create(AppName.EMODEL, "person", userName)

        val timeout = System.currentTimeMillis() + 60_000
        var userAuth: EmodelUserAuthAtts? = null
        while (userAuth == null) {
            try {
                userAuth = AuthContext.runAsSystem {
                    recordsService.getAtts(userRef, EmodelUserAuthAtts::class.java)
                }
            } catch (e: Throwable) {
                if (remoteWebAppsApi.isAppAvailable(AppName.EMODEL)) {
                    throw e
                }
                while (!remoteWebAppsApi.isAppAvailable(AppName.EMODEL)) {
                    if (System.currentTimeMillis() > timeout) {
                        throw RuntimeException("Application is not available: ${AppName.EMODEL}")
                    }
                    Thread.sleep(1000)
                }
            }
        }

        while (!remoteWebAppsApi.isAppAvailable(AppName.EMODEL)) {
            if (System.currentTimeMillis() > timeout) {
                throw RuntimeException("Application is not available: ${AppName.EMODEL}")
            }
            Thread.sleep(1000)
        }
        val userAtts: EmodelUserAuthAtts = AuthContext.runAsSystem {
            recordsService.getAtts(userRef, EmodelUserAuthAtts::class.java)
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
    )

    data class UserAuthInfo(
        val authorities: List<String>,
        val isDisabled: Boolean = false,
        val notExists: Boolean
    )

    private class CompletableFutureWrapper<T> (
        var future: Future<*>? = null
    ) : CompletableFuture<T>() {

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            future?.cancel(mayInterruptIfRunning)
            return super.cancel(mayInterruptIfRunning)
        }
    }
}
