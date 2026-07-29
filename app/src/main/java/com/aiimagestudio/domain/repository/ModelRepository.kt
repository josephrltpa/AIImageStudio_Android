package com.aiimagestudio.domain.repository

import com.aiimagestudio.domain.model.AIModel
import com.aiimagestudio.domain.model.ModelComponent
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    /** Static catalog of every model component the app knows how to fetch. */
    fun catalog(): List<AIModel>

    /** Live installed/progress state for all components, backed by Room. */
    fun observeModels(): Flow<List<AIModel>>

    suspend fun startDownload(component: ModelComponent)
    suspend fun pauseDownload(component: ModelComponent)
    suspend fun resumeDownload(component: ModelComponent)
    suspend fun cancelDownload(component: ModelComponent)
    suspend fun deleteModel(component: ModelComponent)

    /** Confirms a downloaded file's SHA-256 matches the catalog entry. */
    suspend fun verifyModel(component: ModelComponent): Boolean

    suspend fun availableStorageBytes(): Long
}
