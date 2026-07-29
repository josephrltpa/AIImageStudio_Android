package com.aiimagestudio.ai.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aiimagestudio.data.local.db.ModelDao
import com.aiimagestudio.data.local.db.ModelEntity
import com.aiimagestudio.data.storage.ModelStorageManager
import com.aiimagestudio.domain.model.ModelComponent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.RandomAccessFile

/**
 * Downloads a single model component in the background using HTTP Range
 * requests so it can be paused/resumed/retried without restarting from
 * zero. Runs under WorkManager so downloads survive process death and
 * respect system constraints (network availability, battery).
 *
 * Pause is implemented by simply cancelling the WorkManager job (the
 * partial ".part" file is preserved); Resume re-enqueues this same worker,
 * which detects the partial file and continues from its byte offset.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val storageManager: ModelStorageManager,
    private val modelDao: ModelDao,
    private val httpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_COMPONENT = "component"
        const val KEY_PROGRESS = "progress"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val componentName = inputData.getString(KEY_COMPONENT)
            ?: return@withContext Result.failure()
        val component = ModelComponent.valueOf(componentName)
        val model = ModelCatalog.find(component)

        val partialFile = storageManager.partialFileFor(model.localFileName)
        val finalFile = storageManager.fileFor(model.localFileName)
        val startOffset = if (partialFile.exists()) partialFile.length() else 0L

        try {
            markDownloading(component, startOffset, model.sizeBytes)

            val request = Request.Builder()
                .url(model.downloadUrl)
                .header("Range", "bytes=$startOffset-")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.retry()
                }
                val body = response.body ?: return@withContext Result.retry()

                RandomAccessFile(partialFile, "rw").use { raf ->
                    raf.seek(startOffset)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(1 * 1024 * 1024)
                        var totalRead = startOffset
                        var lastReportedPercent = -1
                        while (true) {
                            if (isStopped) return@withContext Result.failure() // paused
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            totalRead += read

                            val percent = ((totalRead * 100) / model.sizeBytes).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                setProgressAsync(
                                    workDataOf(KEY_PROGRESS to totalRead.toFloat() / model.sizeBytes)
                                )
                                modelDao.upsert(
                                    ModelEntity(
                                        component = component.name,
                                        isInstalled = false,
                                        downloadProgress = totalRead.toFloat() / model.sizeBytes,
                                        isDownloading = true,
                                        isPaused = false,
                                        bytesDownloaded = totalRead
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Validate checksum before promoting the .part file to final.
            val actualSha = storageManager.sha256Of(partialFile)
            if (!actualSha.equals(model.sha256, ignoreCase = true)) {
                partialFile.delete()
                markFailed(component)
                return@withContext Result.failure()
            }

            partialFile.renameTo(finalFile)
            modelDao.upsert(
                ModelEntity(
                    component = component.name,
                    isInstalled = true,
                    downloadProgress = 1f,
                    isDownloading = false,
                    isPaused = false,
                    bytesDownloaded = model.sizeBytes,
                    localUri = finalFile.absolutePath
                )
            )
            Result.success()
        } catch (t: Throwable) {
            // Network hiccup: leave the .part file in place so a retry/resume
            // can continue from where it left off.
            markPaused(component, partialFile.length())
            Result.retry()
        }
    }

    private suspend fun markDownloading(component: ModelComponent, bytes: Long, total: Long) {
        modelDao.upsert(
            ModelEntity(
                component = component.name,
                isInstalled = false,
                downloadProgress = bytes.toFloat() / total,
                isDownloading = true,
                isPaused = false,
                bytesDownloaded = bytes
            )
        )
    }

    private suspend fun markPaused(component: ModelComponent, bytes: Long) {
        val existing = modelDao.get(component.name)
        modelDao.upsert(
            (existing ?: ModelEntity(component = component.name)).copy(
                isDownloading = false,
                isPaused = true,
                bytesDownloaded = bytes
            )
        )
    }

    private suspend fun markFailed(component: ModelComponent) {
        val existing = modelDao.get(component.name)
        modelDao.upsert(
            (existing ?: ModelEntity(component = component.name)).copy(
                isDownloading = false,
                isPaused = false,
                isInstalled = false,
                downloadProgress = 0f,
                bytesDownloaded = 0
            )
        )
    }
}
