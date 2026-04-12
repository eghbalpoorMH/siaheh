package ir.siaheh.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.siaheh.data.model.Group
import ir.siaheh.data.model.GroupMessage
import ir.siaheh.data.repository.GroupRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupChatUiState(
    val group: Group? = null,
    val messages: List<GroupMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val search: String = "",
    val senderId: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupChatUiState())
    val uiState: StateFlow<GroupChatUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun loadMessages(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                if (_uiState.value.group == null) {
                    val group = groupRepository.getGroup(groupId)
                    _uiState.value = _uiState.value.copy(group = group)
                }
                val messages = groupRepository.listMessages(
                    groupId = groupId,
                    search = _uiState.value.search.ifBlank { null },
                    sender = _uiState.value.senderId?.takeIf { it.isNotBlank() },
                    dateFrom = _uiState.value.dateFrom?.takeIf { it.isNotBlank() },
                    dateTo = _uiState.value.dateTo?.takeIf { it.isNotBlank() },
                )
                _uiState.value = _uiState.value.copy(messages = messages, isLoading = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onSearchChange(groupId: String, value: String) {
        _uiState.value = _uiState.value.copy(search = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadMessages(groupId)
        }
    }

    fun applyFilters(groupId: String, senderId: String?, dateFrom: String?, dateTo: String?) {
        _uiState.value = _uiState.value.copy(
            senderId = senderId?.takeIf { it.isNotBlank() },
            dateFrom = dateFrom?.takeIf { it.isNotBlank() },
            dateTo = dateTo?.takeIf { it.isNotBlank() },
        )
        loadMessages(groupId)
    }

    fun sendMessage(groupId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                groupRepository.sendMessage(groupId, text.trim())
                loadMessages(groupId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
