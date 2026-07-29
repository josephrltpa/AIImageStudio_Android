package com.aiimagestudio.domain.usecase

import com.aiimagestudio.domain.model.AIModel
import com.aiimagestudio.domain.model.ModelComponent
import com.aiimagestudio.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Single entry point the Model Manager UI uses for every download-lifecycle
 * action (start/pause/resume/cancel/delete) plus live status observation.
 */
class ManageModelDownloadUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    fun observeModels(): Flow<List<AIModel>> = modelRepository.observeModels()

    suspend fun start(component: ModelComponent) = modelRepository.startDownload(component)
    suspend fun pause(component: ModelComponent) = modelRepository.pauseDownload(component)
    suspend fun resume(component: ModelComponent) = modelRepository.resumeDownload(component)
    suspend fun cancel(component: ModelComponent) = modelRepository.cancelDownload(component)
    suspend fun delete(component: ModelComponent) = modelRepository.deleteModel(component)
    suspend fun verify(component: ModelComponent): Boolean = modelRepository.verifyModel(component)
    suspend fun availableStorageBytes(): Long = modelRepository.availableStorageBytes()
}
