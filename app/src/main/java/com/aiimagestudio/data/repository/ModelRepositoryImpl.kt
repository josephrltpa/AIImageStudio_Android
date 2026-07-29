package com.aiimagestudio.data.repository

import com.aiimagestudio.ai.download.DownloadManager
import com.aiimagestudio.ai.download.ModelCatalog
import com.aiimagestudio.data.local.db.ModelDao
import com.aiimagestudio.data.storage.ModelStorageManager
import com.aiimagestudio.domain.model.AIModel
import com.aiimagestudio.domain.model.ModelComponent
import com.aiimagestudio.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ModelRepositoryImpl @Inject constructor(
    private val modelDao: ModelDao,
    private val downloadManager: DownloadManager,
    private val storageManager: ModelStorageManager
) : ModelRepository {

    override fun catalog(): List<AIModel> = ModelCatalog.all()

    override fun observeModels(): Flow<List<AIModel>> =
        modelDao.observeAll().map { entities ->
            val entityMap = entities.associateBy { it.component }
            ModelCatalog.all().map { catalogModel ->
                val entity = entityMap[catalogModel.component.name]
                catalogModel.copy(
                    isInstalled = entity?.isInstalled ?: false,
                    downloadProgress = entity?.downloadProgress ?: 0f,
                    isDownloading = entity?.isDownloading ?: false,
                    isPaused = entity?.isPaused ?: false,
                    lastError = entity?.lastError
                )
            }
        }

    override suspend fun startDownload(component: ModelComponent) = downloadManager.enqueue(component)
    override suspend fun pauseDownload(component: ModelComponent) = downloadManager.pause(component)
    override suspend fun resumeDownload(component: ModelComponent) = downloadManager.resume(component)
    override suspend fun cancelDownload(component: ModelComponent) {
        downloadManager.cancel(component)
        val model = ModelCatalog.find(component)
        storageManager.delete(model.localFileName)
        model.dataLocalFileName?.let { storageManager.delete(it) }
        modelDao.delete(component.name)
    }

    override suspend fun deleteModel(component: ModelComponent) {
        val model = ModelCatalog.find(component)
        storageManager.delete(model.localFileName)
        model.dataLocalFileName?.let { storageManager.delete(it) }
        modelDao.delete(component.name)
    }

    override suspend fun verifyModel(component: ModelComponent): Boolean {
        val model = ModelCatalog.find(component)
        val file = storageManager.fileFor(model.localFileName)
        if (!file.exists()) return false
        if (model.sha256.isNotBlank() && !storageManager.sha256Of(file).equals(model.sha256, ignoreCase = true)) {
            return false
        }
        if (model.hasSeparateDataFile) {
            val dataFile = storageManager.fileFor(model.dataLocalFileName!!)
            if (!dataFile.exists()) return false
            if (model.dataSha256.isNotBlank() &&
                !storageManager.sha256Of(dataFile).equals(model.dataSha256, ignoreCase = true)
            ) return false
        }
        return true
    }

    override suspend fun availableStorageBytes(): Long = storageManager.availableBytes()
}
