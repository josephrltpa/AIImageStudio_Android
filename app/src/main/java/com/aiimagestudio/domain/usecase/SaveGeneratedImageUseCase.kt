package com.aiimagestudio.domain.usecase

import com.aiimagestudio.domain.model.GeneratedImage
import com.aiimagestudio.domain.repository.GalleryRepository
import javax.inject.Inject

class SaveGeneratedImageUseCase @Inject constructor(
    private val galleryRepository: GalleryRepository
) {
    suspend operator fun invoke(image: GeneratedImage): Long = galleryRepository.save(image)
}
