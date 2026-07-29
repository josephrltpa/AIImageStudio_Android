package com.aiimagestudio.presentation.modelmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiimagestudio.domain.model.AIModel
import com.aiimagestudio.domain.model.ModelComponent
import com.aiimagestudio.domain.usecase.ManageModelDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelManagerUiState(
    val models: List<AIModel> = emptyList(),
    val availableStorageBytes: Long = 0L
)

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val manageModelDownloadUseCase: ManageModelDownloadUseCase
) : ViewModel() {

    private val storageFlow = MutableStateFlow(0L)

    val uiState: StateFlow<ModelManagerUiState> = combine(
        manageModelDownloadUseCase.observeModels(),
        storageFlow
    ) { models, storage ->
        ModelManagerUiState(models = models, availableStorageBytes = storage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelManagerUiState())

    init {
        refreshStorage()
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            storageFlow.value = manageModelDownloadUseCase.availableStorageBytes()
        }
    }

    fun download(component: ModelComponent) = viewModelScope.launch {
        manageModelDownloadUseCase.start(component)
    }

    fun pause(component: ModelComponent) = viewModelScope.launch {
        manageModelDownloadUseCase.pause(component)
    }

    fun resume(component: ModelComponent) = viewModelScope.launch {
        manageModelDownloadUseCase.resume(component)
    }

    fun delete(component: ModelComponent) = viewModelScope.launch {
        manageModelDownloadUseCase.delete(component)
        refreshStorage()
    }
}
