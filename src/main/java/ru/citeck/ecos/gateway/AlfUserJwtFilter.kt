package ru.citeck.ecos.gateway

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.utils.StringUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.rest.RemoteRecordsUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.spring.web.interceptor.AuthHeaderInterceptor
import ru.citeck.ecos.records3.spring.web.interceptor.AuthHeaderProvider
import ru.citeck.ecos.security.AuthoritiesConstants
import ru.citeck.ecos.security.jwt.JWTFilter
import ru.citeck.ecos.security.jwt.TokenProvider
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

@Component
class AlfUserJwtFilter(
    private val tokenProvider: TokenProvider,
    private val recordsService: RecordsService,
    private val authHeaderInterceptor: AuthHeaderInterceptor
) : ZuulFilter(), AuthHeaderProvider {

    companion object {
        private const val ADMIN_AUTHORITY = "GROUP_ALFRESCO_ADMINISTRATORS"
    }

    private val authoritiesCache = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .maximumSize(200)
        .build(CacheLoader.from<String, List<String>> { evalUserAuthorities(it) })

    private val tokensCache = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
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
            return null
        }

        val header = getAuthHeader(ecosUser)
        if (!header.isNullOrBlank()) {
            ctx.addZuulRequestHeader(JWTFilter.AUTHORIZATION_HEADER, header)
        }
        return null
    }

    override fun getAuthHeader(userName: String): String? {

        val authorities = authoritiesCache.getUnchecked(userName).map { SimpleGrantedAuthority(it) }
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

    private fun evalUserAuthorities(userName: String?) : List<String> {
        userName ?: error("userName can't be null")
        if (userName == "guest") {
            return listOf(AuthoritiesConstants.GUEST)
        }
        if (userName == "system") {
            return listOf(userName, AuthoritiesConstants.ADMIN)
        }
        val userRef = RecordRef.create("alfresco", "people", userName)
        val userInfo = RemoteRecordsUtils.runAsSystem {
            recordsService.getAtts(userRef, UserAuthorities::class.java)
        }
        if (userInfo.authorities == null) {
            error("User authorities is null. User: $userName")
        }
        val authorities = ArrayList(userInfo.authorities)
        if (authorities.contains(ADMIN_AUTHORITY)) {
            authorities.add(AuthoritiesConstants.ADMIN)
        }
        authorities.add(AuthoritiesConstants.USER)
        return authorities
    }

    class UserAuthorities(
        @AttName("authorities.list[]?str")
        val authorities: List<String>? = null
    )
}