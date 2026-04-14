package ir.siaheh.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.siaheh.data.model.SpaceMember
import ir.siaheh.data.model.PublicUser
import ir.siaheh.data.repository.AuthRepository
import ir.siaheh.data.repository.SpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpaceMembersUiState(
    val members: List<SpaceMember> = emptyList(),
    val suggestions: List<PublicUser> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SpaceMembersViewModel @Inject constructor(
    private val spaceRepository: SpaceRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpaceMembersUiState())
    val uiState: StateFlow<SpaceMembersUiState> = _uiState.asStateFlow()

    fun loadMembers(spaceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val members = spaceRepository.listMembers(spaceId)
                _uiState.value = _uiState.value.copy(members = members, isLoading = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun searchUsers(query: String) {
        viewModelScope.launch {
            try {
                val users = authRepository.searchUsers(query)
                _uiState.value = _uiState.value.copy(suggestions = users, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun discoverFromContacts(phones: List<String>) {
        viewModelScope.launch {
            try {
                val users = authRepository.discoverContacts(phones)
                _uiState.value = _uiState.value.copy(suggestions = users, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun addMember(spaceId: String, username: String, role: String) {
        if (username.isBlank()) return
        viewModelScope.launch {
            try {
                spaceRepository.addMember(spaceId, username.lowercase(), role)
                loadMembers(spaceId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateMember(spaceId: String, userId: String, role: String?, canReadHistory: Boolean?, isActive: Boolean?) {
        viewModelScope.launch {
            try {
                spaceRepository.updateMember(spaceId, userId, role, canReadHistory, isActive)
                loadMembers(spaceId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun removeMember(spaceId: String, userId: String) {
        viewModelScope.launch {
            try {
                spaceRepository.removeMember(spaceId, userId)
                loadMembers(spaceId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearSuggestions() {
        _uiState.value = _uiState.value.copy(suggestions = emptyList())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
