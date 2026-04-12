package ir.siaheh.data.api

import android.util.Log
import ir.siaheh.data.local.TokenManager
import ir.siaheh.data.model.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApiProvider: dagger.Lazy<AuthApi>,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        Log.d(TAG, "authenticate() called — got ${response.code} from ${response.request.url}")
        return runBlocking {
            mutex.withLock {
                val currentToken = tokenManager.getAccessToken()
                val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")

                if (currentToken != null && currentToken != requestToken) {
                    Log.d(TAG, "Token already refreshed by another thread, retrying with new token")
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                val refreshToken = tokenManager.getRefreshToken() ?: run {
                    Log.e(TAG, "No refresh token found, clearing tokens")
                    tokenManager.clearTokens()
                    return@runBlocking null
                }

                Log.d(TAG, "Attempting token refresh...")
                try {
                    val tokens = authApiProvider.get().refreshToken(RefreshTokenRequest(refreshToken))
                    Log.d(TAG, "Token refresh SUCCESS — new access token: ${tokens.accessToken.take(20)}...")
                    tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${tokens.accessToken}")
                        .build()
                } catch (e: Exception) {
                    Log.e(TAG, "Token refresh FAILED: ${e.message}", e)
                    tokenManager.clearTokens()
                    null
                }
            }
        }
    }

    companion object {
        private const val TAG = "TokenRefresh"
    }
}
