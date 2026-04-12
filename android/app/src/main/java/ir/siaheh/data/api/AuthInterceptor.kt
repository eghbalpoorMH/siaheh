package ir.siaheh.data.api

import ir.siaheh.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        // Skip auth headers for public auth endpoints (OTP, token create/refresh)
        // Sending an expired token to AllowAny endpoints causes DRF to return 401
        // Note: DELETE /auth/tokens (logout) requires auth, so only skip for POST/PUT
        val path = original.url.encodedPath
        val method = original.method
        val isAuthEndpoint = path.contains("auth/otp") ||
                (path.contains("auth/tokens") && method in listOf("POST", "PUT"))

        if (!isAuthEndpoint) {
            val token = runBlocking { tokenManager.getAccessToken() }
            if (token != null) {
                builder.addHeader("Authorization", "Bearer $token")
            }

        }

        return chain.proceed(builder.build())
    }
}
