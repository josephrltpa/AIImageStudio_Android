package com.aiimagestudio.presentation.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiimagestudio.domain.model.GeneratedImage
import com.aiimagestudio.domain.usecase.DeleteGeneratedImageUseCase
import com.aiimagestudio.domain.usecase.GetGalleryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    getGalleryUseCase: GetGalleryUseCase,
    private val deleteGeneratedImageUseCase: DeleteGeneratedImageUseCase
) : ViewModel() {

    val images: StateFlow<List<GeneratedImage>> = getGalleryUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(imageId: Long) {
        viewModelScope.launch { deleteGeneratedImageUseCase(imageId) }
    }
}
