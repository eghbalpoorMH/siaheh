package ir.siaheh.ui.space

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.siaheh.data.repository.AuthRepository
import ir.siaheh.data.model.Space
import ir.siaheh.data.model.SpaceEntry
import ir.siaheh.data.repository.SpaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpaceChatUiState(
    val space: Space? = null,
    val currentUserId: String? = null,
    val entries: List<SpaceEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val search: String = "",
    val senderUsername: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

@HiltViewModel
class SpaceChatViewModel @Inject constructor(
    private val spaceRepository: SpaceRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpaceChatUiState())
    val uiState: StateFlow<SpaceChatUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun loadMessages(spaceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                if (_uiState.value.space == null || _uiState.value.currentUserId == null) {
                    val me = authRepository.getSavedUser()
                    val space = spaceRepository.getSpace(spaceId)
                    _uiState.value = _uiState.value.copy(space = space, currentUserId = me?.id)
                }
                val entries = spaceRepository.listEntries(
                    spaceId = spaceId,
                    search = _uiState.value.search.ifBlank { null },
                    senderUsername = _uiState.value.senderUsername?.takeIf { it.isNotBlank() },
                    dateFrom = _uiState.value.dateFrom?.takeIf { it.isNotBlank() },
                    dateTo = _uiState.value.dateTo?.takeIf { it.isNotBlank() },
                )
                _uiState.value = _uiState.value.copy(entries = entries, isLoading = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onSearchChange(spaceId: String, value: String) {
        _uiState.value = _uiState.value.copy(search = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadMessages(spaceId)
        }
    }

    fun applyFilters(spaceId: String, senderUsername: String?, dateFrom: String?, dateTo: String?) {
        _uiState.value = _uiState.value.copy(
            senderUsername = senderUsername?.takeIf { it.isNotBlank() },
            dateFrom = dateFrom?.takeIf { it.isNotBlank() },
            dateTo = dateTo?.takeIf { it.isNotBlank() },
        )
        loadMessages(spaceId)
    }

    fun sendEntry(spaceId: String, text: String, attachments: List<Uri>, attachmentKinds: List<String> = emptyList()) {
        if (text.isBlank() && attachments.isEmpty()) return
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSending = true)
                spaceRepository.sendEntry(spaceId, text.trim(), attachments, attachmentKinds)
                loadMessages(spaceId)
                _uiState.value = _uiState.value.copy(isSending = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isSending = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
