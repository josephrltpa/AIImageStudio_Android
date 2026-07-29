package com.aiimagestudio.domain.usecase

import com.aiimagestudio.domain.repository.GalleryRepository
import javax.inject.Inject

class DeleteGeneratedImageUseCase @Inject constructor(
    private val galleryRepository: GalleryRepository
) {
    suspend operator fun invoke(imageId: Long) = galleryRepository.delete(imageId)
}
