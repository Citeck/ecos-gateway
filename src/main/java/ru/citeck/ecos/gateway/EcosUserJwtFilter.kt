package ru.citeck.ecos.gateway

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.utils.StringUtils
import ru.citeck.ecos.context.lib.auth.AuthConstants
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.spring.web.interceptor.AuthHeaderProvider
import ru.citeck.ecos.records3.spring.web.interceptor.RecordsAuthInterceptor
import ru.citeck.ecos.security.jwt.UserHeaderFilter
import ru.citeck.ecos.security.jwt.TokenProvider
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

@Component
class EcosUserJwtFilter(
    private val tokenProvider: TokenProvider,
    private val recordsService: RecordsService,
    private val authHeaderInterceptor: RecordsAuthInterceptor
) : ZuulFilter(), AuthHeaderProvider {

    companion object {
        // should not be used when userStorageType != ALFRESCO
        private const val ADMIN_AUTHORITY = "GROUP_ALFRESCO_ADMINISTRATORS"
    }

    @Value("\${ecos.zuul.fiter.ecosUser.userStorageType:EMODEL}")
    private lateinit var userStorageType: UserStorageType

    private val authoritiesCache = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .maximumSize(200)
        .build(CacheLoader.from<String, UserAuthInfo> { evalUserAuthorities(it) })

    private val tokensCache = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .maximumSize(400)
        .build(CacheLoader.from<Authentication, String> { evalJwtToken(it) })

    @PostConstruct
    fun init() {
        authHeaderInterceptor.setAuthHeaderProvider(this)
    }

    override fun run(): Any? {

        val ctx = RequestContext.getCurrentContext()

        val request = ctx.request

        val ecosUser = request.getHeader("X-ECOS-User")
        if (StringUtils.isBlank(ecosUser)) {
            error("ECOS User is not found!")
        }

        val header = getAuthHeader(ecosUser)
        if (!header.isNullOrBlank()) {
            ctx.addZuulRequestHeader(UserHeaderFilter.AUTHORIZATION_HEADER, header)
        }
        return null
    }

    override fun getAuthHeader(userName: String): String? {
        if (userName == AuthConstants.SYSTEM_USER) {
            error("System user can't be used outside of system context")
        }
        return getAuthHeaderImpl(userName)
    }

    private fun getAuthHeaderImpl(userName: String): String {

        val authInfo = authoritiesCache.getUnchecked(userName);
        if (userName != "admin" && authInfo.isDisabled) {
            throw UserDisabledException("User is disabled")
        }

        val authorities = authInfo.authorities.map { SimpleGrantedAuthority(it) }
        val authentication = UsernamePasswordAuthenticationToken(userName, null, authorities)

        var token = tokensCache.getUnchecked(authentication)
        try {
            tokenProvider.validateToken(token)
        } catch (e: Exception) {
            tokensCache.invalidate(authentication)
            token = tokensCache.getUnchecked(authentication)
        }

        return "Bearer $token"
    }

    override fun getSystemAuthHeader(userName: String): String? {
        return getAuthHeaderImpl(AuthConstants.SYSTEM_USER)
    }

    override fun shouldFilter(): Boolean {
        return true
    }

    override fun filterType(): String {
        return "pre"
    }

    override fun filterOrder(): Int {
        return 10001
    }

    private fun evalJwtToken(authentication: Authentication?) : String {
        authentication ?: error("authentication can't be null")
        return tokenProvider.createToken(authentication, false)
    }

    private fun evalUserAuthorities(userName: String?) : UserAuthInfo {
        userName ?: error("userName can't be null")
        if (userName == "guest") {
            return UserAuthInfo(listOf(AuthRole.GUEST))
        }
        if (userName == AuthConstants.SYSTEM_USER) {
            return UserAuthInfo(AuthContext.getSystemAuthorities())
        }

        val userAtts = AuthContext.runAsSystem { getAuthoritiesData(userName) }
        if (userAtts.authorities == null) {
            error("User authorities is null. User: $userName")
        }
        val authorities = ArrayList(userAtts.authorities)

        if (userStorageType == UserStorageType.ALFRESCO) {
            if (authorities.contains(ADMIN_AUTHORITY)) {
                authorities.add(AuthRole.ADMIN)
            }
            authorities.add(AuthRole.USER)
        }
        return UserAuthInfo(authorities, userAtts.personDisabled == true)
    }

    private fun getAuthoritiesData(userName: String): EmodelUserAuthAtts {
        return when (userStorageType) {
            UserStorageType.ALFRESCO -> {
                val userRef = RecordRef.create("alfresco", "people", userName)
                val userAtts = recordsService.getAtts(userRef, AlfUserAuthAtts::class.java)
                EmodelUserAuthAtts(userAtts.authorities, userAtts.isDisabled)
            }
            UserStorageType.EMODEL -> {
                val userRef = RecordRef.create("emodel", "person", userName)
                recordsService.getAtts(userRef, EmodelUserAuthAtts::class.java)
            }
        }
    }

    data class UserAuthInfo(
        val authorities: List<String>,
        val isDisabled: Boolean = false
    )

    class EmodelUserAuthAtts(
        @AttName("authorities.list[]?str")
        val authorities: List<String>? = null,
        val personDisabled: Boolean?
    )

    class AlfUserAuthAtts(
        @AttName("authorities.list[]?str")
        val authorities: List<String>? = null,
        val isDisabled: Boolean?
    )

    enum class UserStorageType {
        EMODEL,
        ALFRESCO
    }
}
