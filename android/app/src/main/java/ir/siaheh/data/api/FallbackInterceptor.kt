package ir.siaheh.data.api

import ir.siaheh.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries failed requests on a fallback domain.
 * If the primary domain is unreachable,
 * automatically retries with the fallback base URL, and vice versa.
 */
class FallbackInterceptor : Interceptor {

    private val primaryHost = BuildConfig.BASE_URL.toHttpUrl().host
    private val fallbackHost = BuildConfig.FALLBACK_BASE_URL.toHttpUrl().host

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            // Swap host and retry once
            val currentHost = request.url.host
            val otherHost = if (currentHost == primaryHost) fallbackHost else primaryHost
            val newUrl = request.url.newBuilder().host(otherHost).build()
            val newRequest = request.newBuilder().url(newUrl).build()
            chain.proceed(newRequest)
        }
    }
}
