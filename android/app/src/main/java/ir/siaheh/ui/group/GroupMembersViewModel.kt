package ir.siaheh.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.siaheh.data.model.GroupMember
import ir.siaheh.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupMembersUiState(
    val members: List<GroupMember> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GroupMembersViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupMembersUiState())
    val uiState: StateFlow<GroupMembersUiState> = _uiState.asStateFlow()

    fun loadMembers(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val members = groupRepository.listMembers(groupId)
                _uiState.value = _uiState.value.copy(members = members, isLoading = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun addMember(groupId: String, userId: String, role: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            try {
                groupRepository.addMember(groupId, userId, role)
                loadMembers(groupId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateMember(groupId: String, userId: String, role: String?, canReadHistory: Boolean?, isActive: Boolean?) {
        viewModelScope.launch {
            try {
                groupRepository.updateMember(groupId, userId, role, canReadHistory, isActive)
                loadMembers(groupId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                groupRepository.removeMember(groupId, userId)
                loadMembers(groupId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
