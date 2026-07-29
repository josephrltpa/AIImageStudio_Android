package com.aiimagestudio.domain.usecase

import com.aiimagestudio.domain.model.GeneratedImage
import com.aiimagestudio.domain.repository.GalleryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGalleryUseCase @Inject constructor(
    private val galleryRepository: GalleryRepository
) {
    operator fun invoke(): Flow<List<GeneratedImage>> = galleryRepository.observeAll()
}
