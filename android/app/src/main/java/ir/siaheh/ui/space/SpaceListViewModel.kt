package ir.siaheh.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.siaheh.data.model.Space
import ir.siaheh.data.repository.SpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpaceListUiState(
    val spaces: List<Space> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SpaceListViewModel @Inject constructor(
    private val spaceRepository: SpaceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpaceListUiState())
    val uiState: StateFlow<SpaceListUiState> = _uiState.asStateFlow()

    init {
        loadSpaces()
    }

    fun loadSpaces() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val spaces = spaceRepository.listSpaces()
                _uiState.value = _uiState.value.copy(spaces = spaces, isLoading = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createSpace(title: String, description: String?, memberIds: List<String>?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                spaceRepository.createSpace(title.trim(), description?.trim().orEmpty(), memberIds)
                loadSpaces()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updatePreferences(spaceId: String, isPinned: Boolean? = null, isHidden: Boolean? = null) {
        viewModelScope.launch {
            try {
                spaceRepository.updatePreferences(spaceId, isPinned, isHidden)
                loadSpaces()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun convertPersonalSpace(spaceId: String) {
        viewModelScope.launch {
            try {
                spaceRepository.convertPersonalSpace(spaceId)
                loadSpaces()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
