package com.aiimagestudio.domain.repository

import com.aiimagestudio.domain.model.GeneratedImage
import kotlinx.coroutines.flow.Flow

interface GalleryRepository {
    fun observeAll(): Flow<List<GeneratedImage>>
    suspend fun save(image: GeneratedImage): Long
    suspend fun delete(imageId: Long)
    suspend fun getById(imageId: Long): GeneratedImage?
}
