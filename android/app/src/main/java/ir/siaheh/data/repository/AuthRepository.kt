package ir.siaheh.data.repository

import android.os.Build
import ir.siaheh.BuildConfig
import ir.siaheh.data.api.AuthApi
import ir.siaheh.data.local.TokenManager
import ir.siaheh.data.model.OtpRequest
import ir.siaheh.data.model.RefreshTokenRequest
import ir.siaheh.data.model.TokensResponse
import ir.siaheh.data.model.User
import ir.siaheh.data.model.VerifyOtpRequest
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
) {
    private val moshi = Moshi.Builder().build()
    private val userAdapter = moshi.adapter(User::class.java)

    suspend fun requestOtp(phone: String): Int {
        val response = authApi.requestOtp(OtpRequest(phone))
        return response.expiresIn
    }

    suspend fun verifyOtp(phone: String, code: String): TokensResponse {
        val response = authApi.verifyOtp(
            VerifyOtpRequest(
                phone = phone,
                code = code,
                clientPlatform = "android",
                clientStore = BuildConfig.STORE.lowercase(),
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                osVersion = Build.VERSION.RELEASE ?: "",
            )
        )
        tokenManager.saveTokens(response.accessToken, response.refreshToken)
        try {
            tokenManager.saveUserJson(userAdapter.toJson(response.user))
        } catch (_: Exception) { }
        return response
    }

    suspend fun logout() {
        try {
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken != null) {
                authApi.logout(RefreshTokenRequest(refreshToken))
            }
        } catch (_: Exception) { }
        finally {
            tokenManager.clearAll()
        }
    }

    suspend fun isLoggedIn(): Boolean = tokenManager.getAccessToken() != null

    suspend fun getSavedUser(): User? {
        val json = tokenManager.getUserJson() ?: return null
        return try {
            userAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveUser(user: User) {
        try {
            tokenManager.saveUserJson(userAdapter.toJson(user))
        } catch (_: Exception) { }
    }
}
