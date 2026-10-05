package com.maximerollin.aqualog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val aquarium: Aquarium? = null,
    val isSaving: Boolean = false,
    val showValidationError: Boolean = false,
)

class HomeViewModel(
    private val aquariumRepository: AquariumRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            aquariumRepository.observeCurrentAquarium().collect { aquarium ->
                mutableUiState.update {
                    it.copy(isLoading = false, aquarium = aquarium, isSaving = false)
                }
            }
        }
    }

    fun createAquarium(name: String, volumeInput: String, volumeUnit: VolumeUnit) {
        val volume = volumeInput.replace(',', '.').toDoubleOrNull()
        if (name.isBlank() || volume == null || volume <= 0.0 || !volume.isFinite()) {
            mutableUiState.update { it.copy(showValidationError = true) }
            return
        }

        mutableUiState.update { it.copy(isSaving = true, showValidationError = false) }
        viewModelScope.launch {
            runCatching {
                aquariumRepository.createAquarium(name, volume, volumeUnit)
            }.onFailure {
                mutableUiState.update {
                    it.copy(isSaving = false, showValidationError = true)
                }
            }
        }
    }

    companion object {
        fun factory(repository: AquariumRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repository) as T
            }
    }
}
