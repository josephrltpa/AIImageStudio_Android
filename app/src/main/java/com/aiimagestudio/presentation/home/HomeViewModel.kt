package com.aiimagestudio.presentation.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiimagestudio.data.local.datastore.SettingsDataStore
import com.aiimagestudio.data.storage.ImageStorageManager
import com.aiimagestudio.domain.model.GenerationJob
import com.aiimagestudio.domain.model.GenerationMode
import com.aiimagestudio.domain.model.GenerationSettings
import com.aiimagestudio.domain.usecase.GenerateImageUseCase
import com.aiimagestudio.domain.usecase.SaveGeneratedImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val selectedImage: Bitmap? = null,
    val prompt: String = "",
    val mode: GenerationMode = GenerationMode.INSTRUCT_PIX2PIX_EDIT,
    val isGenerating: Boolean = false,
    val progressLabel: String? = null,
    val progressFraction: Float = 0f,
    val resultBitmap: Bitmap? = null,
    val errorMessage: String? = null,
    val lastSavedImageId: Long? = null
)

/**
 * Drives the simple, single-screen main flow described in the product
 * spec: upload -> prompt -> generate -> save/share. Advanced settings are
 * read from [SettingsDataStore] so they never need to be surfaced here.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val generateImageUseCase: GenerateImageUseCase,
    private val saveGeneratedImageUseCase: SaveGeneratedImageUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val imageStorageManager: ImageStorageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onImageSelected(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            selectedImage = bitmap,
            // Selecting an image implies the edit flow, matching the product UX.
            mode = GenerationMode.INSTRUCT_PIX2PIX_EDIT
        )
    }

    fun onPromptChanged(text: String) {
        _uiState.value = _uiState.value.copy(prompt = text)
    }

    fun onModeChanged(mode: GenerationMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun generate() {
        val state = _uiState.value
        if (state.prompt.isBlank() || state.isGenerating) return

        viewModelScope.launch {
            val settings: GenerationSettings = settingsDataStore.current()
            _uiState.value = state.copy(
                isGenerating = true, errorMessage = null, resultBitmap = null, progressFraction = 0f
            )

            generateImageUseCase(state.mode, state.prompt, state.selectedImage, settings)
                .collect { job ->
                    when (job) {
                        is GenerationJob.Preprocessing -> _uiState.value = _uiState.value.copy(
                            progressLabel = job.message, progressFraction = 0.05f
                        )
                        is GenerationJob.Denoising -> _uiState.value = _uiState.value.copy(
                            progressLabel = "Generating (${job.step}/${job.totalSteps})",
                            progressFraction = 0.1f + 0.8f * (job.step / job.totalSteps.toFloat())
                        )
                        is GenerationJob.Decoding -> _uiState.value = _uiState.value.copy(
                            progressLabel = job.message, progressFraction = 0.95f
                        )
                        is GenerationJob.Success -> {
                            val id = saveGeneratedImageUseCase(job.image)
                            val resultBitmap = imageStorageManager.loadBitmap(job.image.resultImagePath)
                            _uiState.value = _uiState.value.copy(
                                isGenerating = false,
                                progressFraction = 1f,
                                progressLabel = null,
                                lastSavedImageId = id,
                                resultBitmap = resultBitmap
                            )
                        }
                        is GenerationJob.Failure -> _uiState.value = _uiState.value.copy(
                            isGenerating = false,
                            progressLabel = null,
                            errorMessage = job.throwable.message ?: "Generation failed."
                        )
                    }
                }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
