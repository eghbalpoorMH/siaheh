package ir.siaheh.ui.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.siaheh.BuildConfig
import ir.siaheh.data.api.AppApi
import ir.siaheh.data.model.OtpChannel
import ir.siaheh.data.model.User
import ir.siaheh.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateInfo(
    val isForced: Boolean,
    val latestVersion: String,
    val updateUrl: String,
)

data class AuthUiState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val user: User? = null,
    val otpExpiresIn: Int = 0,
    val otpChannels: List<OtpChannel> = emptyList(),
    val error: String? = null,
    val updateInfo: UpdateInfo? = null,
    val needsProfileSetup: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appApi: AppApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init { initialize() }

    private fun initialize() {
        viewModelScope.launch {
            try {
                val isLoggedIn = authRepository.isLoggedIn()
                val cachedUser = if (isLoggedIn) authRepository.getSavedUser() else null
                val freshUser = if (isLoggedIn) runCatching { authRepository.refreshMe() }.getOrNull() else null
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = isLoggedIn,
                    user = freshUser ?: cachedUser,
                    needsProfileSetup = (freshUser ?: cachedUser)?.displayName?.isBlank() == true,
                )
                checkAppVersion()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun checkAppVersion() {
        try {
            val version = appApi.getAppVersion(BuildConfig.STORE)
            val currentCode = parseVersion(BuildConfig.VERSION_NAME)
            val minCode = parseVersion(version.minVersion)
            val latestCode = parseVersion(version.latestVersion)

            when {
                currentCode < minCode -> {
                    _uiState.value = _uiState.value.copy(
                        updateInfo = UpdateInfo(
                            isForced = true,
                            latestVersion = version.latestVersion,
                            updateUrl = version.updateUrl,
                        )
                    )
                }
                currentCode < latestCode -> {
                    _uiState.value = _uiState.value.copy(
                        updateInfo = UpdateInfo(
                            isForced = false,
                            latestVersion = version.latestVersion,
                            updateUrl = version.updateUrl,
                        )
                    )
                }
            }
        } catch (_: Exception) { }
    }

    private fun parseVersion(version: String): Long {
        val parts = version.split(".").map { it.toLongOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 1_000_000 + minor * 1_000 + patch
    }

    fun dismissUpdate() {
        _uiState.value = _uiState.value.copy(updateInfo = null)
    }

    fun requestOtp(phone: String, onSuccess: (() -> Unit)? = null) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val expiresIn = authRepository.requestOtp(phone)
                _uiState.value = _uiState.value.copy(isLoading = false, otpExpiresIn = expiresIn)
                onSuccess?.invoke()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "خطایی رخ داد")
                return@launch
            }
            try {
                val response = appApi.getOtpChannels()
                _uiState.value = _uiState.value.copy(otpChannels = response.channels)
            } catch (_: Exception) { }
        }
    }

    fun verifyOtp(phone: String, code: String, onSuccess: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = authRepository.verifyOtp(phone, code)
                val updateInfo = response.update?.let {
                    if (it.status == "optional" || it.status == "required") {
                        UpdateInfo(
                            isForced = it.status == "required",
                            latestVersion = it.latestVersion,
                            updateUrl = it.updateUrl,
                        )
                    } else null
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = response.user,
                    updateInfo = updateInfo,
                    needsProfileSetup = response.user.displayName.isBlank(),
                )
                onSuccess?.invoke(response.user.displayName.isBlank())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "کد وارد شده اشتباه است")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState(isLoading = false, isAuthenticated = false)
        }
    }

    fun completeProfile(
        displayName: String,
        username: String,
        about: String,
        avatarUri: Uri? = null,
        onSuccess: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val updated = authRepository.updateProfile(displayName, username, about, avatarUri)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = updated,
                    needsProfileSetup = false,
                )
                onSuccess?.invoke()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "خطا در تکمیل پروفایل")
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
