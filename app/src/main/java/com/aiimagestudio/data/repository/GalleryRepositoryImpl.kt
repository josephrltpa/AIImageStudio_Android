package com.aiimagestudio.data.repository

import com.aiimagestudio.data.local.db.GeneratedImageDao
import com.aiimagestudio.data.local.db.GeneratedImageEntity
import com.aiimagestudio.data.storage.ImageStorageManager
import com.aiimagestudio.domain.model.*
import com.aiimagestudio.domain.repository.GalleryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GalleryRepositoryImpl @Inject constructor(
    private val dao: GeneratedImageDao,
    private val imageStorageManager: ImageStorageManager
) : GalleryRepository {

    override fun observeAll(): Flow<List<GeneratedImage>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(image: GeneratedImage): Long = dao.insert(image.toEntity())

    override suspend fun delete(imageId: Long) {
        val entity = dao.getById(imageId) ?: return
        imageStorageManager.delete(entity.resultImagePath)
        entity.originalImagePath?.let { imageStorageManager.delete(it) }
        dao.deleteById(imageId)
    }

    override suspend fun getById(imageId: Long): GeneratedImage? = dao.getById(imageId)?.toDomain()

    private fun GeneratedImageEntity.toDomain() = GeneratedImage(
        id = id,
        originalImagePath = originalImagePath,
        resultImagePath = resultImagePath,
        prompt = prompt,
        mode = GenerationMode.valueOf(mode),
        settings = GenerationSettings(
            width = width, height = height, steps = steps, cfgScale = cfgScale,
            seed = seed, scheduler = Scheduler.valueOf(scheduler),
            denoisingStrength = denoisingStrength,
            memoryMode = MemoryMode.valueOf(memoryMode),
            precision = Precision.valueOf(precision)
        ),
        createdAtEpochMillis = createdAtEpochMillis
    )

    private fun GeneratedImage.toEntity() = GeneratedImageEntity(
        id = id,
        originalImagePath = originalImagePath,
        resultImagePath = resultImagePath,
        prompt = prompt,
        mode = mode.name,
        width = settings.width, height = settings.height, steps = settings.steps,
        cfgScale = settings.cfgScale, seed = settings.seed,
        scheduler = settings.scheduler.name, denoisingStrength = settings.denoisingStrength,
        memoryMode = settings.memoryMode.name, precision = settings.precision.name,
        createdAtEpochMillis = createdAtEpochMillis
    )
}
