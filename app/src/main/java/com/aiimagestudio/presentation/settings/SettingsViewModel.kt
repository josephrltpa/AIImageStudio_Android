package com.aiimagestudio.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiimagestudio.data.local.datastore.SettingsDataStore
import com.aiimagestudio.domain.model.GenerationSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<GenerationSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GenerationSettings())

    fun update(transform: (GenerationSettings) -> GenerationSettings) {
        viewModelScope.launch {
            settingsDataStore.update(transform(settings.value))
        }
    }
}
