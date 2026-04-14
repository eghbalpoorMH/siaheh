package ir.siaheh.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.siaheh.BuildConfig
import ir.siaheh.data.api.AuthApi
import ir.siaheh.data.local.TokenManager
import ir.siaheh.data.model.DiscoverContactsRequest
import ir.siaheh.data.model.OtpRequest
import ir.siaheh.data.model.PublicUser
import ir.siaheh.data.model.RefreshTokenRequest
import ir.siaheh.data.model.TokensResponse
import ir.siaheh.data.model.User
import ir.siaheh.data.model.VerifyOtpRequest
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context,
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

    suspend fun refreshMe(): User {
        val me = authApi.me()
        saveUser(me)
        return me
    }

    suspend fun updateProfile(displayName: String?, username: String?, about: String?, avatarUri: Uri? = null): User {
        val displayNameBody = displayName?.trim()?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val usernameBody = username?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val aboutBody = about?.trim()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val avatarPart = avatarUri?.let { uri ->
            val mimeType = context.contentResolver.getType(uri) ?: "image/*"
            val fileName = inferDisplayName(uri) ?: "avatar.jpg"
            val temp = copyToCache(uri, fileName) ?: return@let null
            MultipartBody.Part.createFormData(
                "avatar",
                fileName,
                temp.asRequestBody(mimeType.toMediaTypeOrNull()),
            )
        }
        val updated = authApi.updateProfile(
            displayName = displayNameBody,
            username = usernameBody,
            about = aboutBody,
            avatar = avatarPart,
        )
        saveUser(updated)
        return updated
    }

    suspend fun searchUsers(query: String): List<PublicUser> {
        if (query.isBlank()) return emptyList()
        return authApi.searchUsers(query.trim()).users
    }

    suspend fun discoverContacts(phones: List<String>): List<PublicUser> {
        if (phones.isEmpty()) return emptyList()
        return authApi.discoverContacts(DiscoverContactsRequest(phones)).users
    }

    private fun inferDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    private fun copyToCache(uri: Uri, fileName: String): File? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val out = File(context.cacheDir, "avatar_${System.currentTimeMillis()}_$fileName")
        input.use { inp ->
            FileOutputStream(out).use { fos ->
                inp.copyTo(fos)
            }
        }
        return out
    }
}
