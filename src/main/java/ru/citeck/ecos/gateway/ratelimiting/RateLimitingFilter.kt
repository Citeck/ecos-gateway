package ru.citeck.ecos.gateway.ratelimiting

import com.hazelcast.cache.impl.HazelcastServerCachingProvider
import com.hazelcast.core.HazelcastInstance
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket4j
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.grid.GridBucketState
import io.github.bucket4j.grid.ProxyManager
import io.github.bucket4j.grid.jcache.JCache
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.gateway.config.GatewayProperties
import java.time.Duration
import java.util.function.Supplier
import javax.cache.configuration.CompleteConfiguration
import javax.cache.configuration.MutableConfiguration
import javax.servlet.http.HttpServletRequest

/**
 * Zuul filter for limiting the number of HTTP calls per client.
 *
 * See the Bucket4j documentation at https://github.com/vladimir-bukhtoyarov/bucket4j
 * https://github.com/vladimir-bukhtoyarov/bucket4j/blob/master/doc-pages/jcache-usage
 * .md#example-1---limiting-access-to-http-server-by-ip-address
 */
class RateLimitingFilter(
    private val gatewayProperties: GatewayProperties,
    hazelcastInstance: HazelcastInstance
) : ZuulFilter() {

    companion object {
        const val GATEWAY_RATE_LIMITING_CACHE_NAME = "gateway-rate-limiting"
    }

    private val log = LoggerFactory.getLogger(RateLimitingFilter::class.java)
    private val buckets: ProxyManager<String>

    init {
        val cachingProvider = HazelcastServerCachingProvider.createCachingProvider(hazelcastInstance)
        val cacheManager = cachingProvider.cacheManager
        val config: CompleteConfiguration<String, GridBucketState> = MutableConfiguration<String, GridBucketState>()
            .setTypes(String::class.java, GridBucketState::class.java)
        val cache = cacheManager.createCache(GATEWAY_RATE_LIMITING_CACHE_NAME, config)
        buckets = Bucket4j.extension(JCache::class.java).proxyManagerForCache(cache)
    }

    override fun filterType(): String {
        return "pre"
    }

    override fun filterOrder(): Int {
        return 10
    }

    override fun shouldFilter(): Boolean {
        // specific APIs can be filtered out using
        // if (RequestContext.getCurrentContext().getRequest().getRequestURI().startsWith("/api")) { ... }
        return true
    }

    override fun run(): Any? {
        val bucketId = getId(RequestContext.getCurrentContext().request)
        val bucket = buckets.getProxy(bucketId, getConfigSupplier())
        if (bucket.tryConsume(1)) {
            // the limit is not exceeded
            log.trace("API rate limit OK for {}", bucketId)
        } else {
            // limit is exceeded
            log.info("API rate limit exceeded for {}", bucketId)
            apiLimitExceeded()
        }
        return null
    }

    private fun getConfigSupplier(): Supplier<BucketConfiguration> {
        return Supplier {
            val rateLimiting = gatewayProperties.getRateLimiting()
            Bucket4j.configurationBuilder()
                .addLimit(
                    Bandwidth.simple(
                        rateLimiting.limit,
                        Duration.ofSeconds(rateLimiting.durationInSeconds)
                    )
                )
                .build()
        }
    }

    /**
     * Create a Zuul response error when the API limit is exceeded.
     */
    private fun apiLimitExceeded() {
        val ctx = RequestContext.getCurrentContext()
        ctx.responseStatusCode = HttpStatus.TOO_MANY_REQUESTS.value()
        if (ctx.responseBody == null) {
            ctx.responseBody = "API rate limit exceeded"
            ctx.setSendZuulResponse(false)
        }
    }

    /**
     * The ID that will identify the limit: the user login or the user IP address.
     */
    private fun getId(httpServletRequest: HttpServletRequest): String {
        return AuthContext.getCurrentUser().ifEmpty { httpServletRequest.remoteAddr } ?: ""
    }
}
