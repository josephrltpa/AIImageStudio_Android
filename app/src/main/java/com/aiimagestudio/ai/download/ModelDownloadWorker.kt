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
 * Downloads a model component in the background using HTTP Range requests
 * so it can be paused/resumed/retried without restarting from zero. Runs
 * under WorkManager so downloads survive process death and respect system
 * constraints (network availability, battery).
 *
 * Some components (e.g. the SD 1.5 UNet, split across model.onnx +
 * model.onnx_data because it exceeds 2GB) require TWO files. Both are
 * downloaded sequentially into the same on-device folder; overall progress
 * reported to the UI is weighted across both by byte count.
 *
 * Every failure path records a human-readable message into
 * ModelEntity.lastError so the Model Manager screen can show *why* a
 * download failed instead of silently resetting the button.
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

        // Clear any previous error and mark as actively downloading right away,
        // so the UI shows progress immediately instead of looking frozen.
        modelDao.upsert(
            ModelEntity(
                component = component.name,
                isInstalled = false,
                downloadProgress = 0f,
                isDownloading = true,
                isPaused = false,
                lastError = null
            )
        )

        try {
            val totalBytes = model.totalBytes.coerceAtLeast(1)
            var bytesDoneBeforeThisFile = 0L

            downloadOne(
                url = model.downloadUrl,
                localFileName = model.localFileName,
                component = component,
                totalBytes = totalBytes,
                bytesOffsetInTotal = bytesDoneBeforeThisFile,
                expectedSha256 = model.sha256
            )
            bytesDoneBeforeThisFile += model.sizeBytes

            if (model.hasSeparateDataFile) {
                downloadOne(
                    url = model.dataDownloadUrl!!,
                    localFileName = model.dataLocalFileName!!,
                    component = component,
                    totalBytes = totalBytes,
                    bytesOffsetInTotal = bytesDoneBeforeThisFile,
                    expectedSha256 = model.dataSha256
                )
            }

            modelDao.upsert(
                ModelEntity(
                    component = component.name,
                    isInstalled = true,
                    downloadProgress = 1f,
                    isDownloading = false,
                    isPaused = false,
                    bytesDownloaded = totalBytes,
                    localUri = storageManager.fileFor(model.localFileName).absolutePath
                )
            )
            Result.success()
        } catch (t: PauseRequested) {
            markPaused(component)
            Result.failure()
        } catch (t: ChecksumMismatch) {
            markFailed(component, "Downloaded file didn't match its expected checksum.")
            Result.failure()
        } catch (t: Throwable) {
            // Record the real exception so it's visible in the Model Manager UI —
            // network errors, HTTP error codes, disk-space issues, etc.
            val message = "${t::class.simpleName}: ${t.message ?: "no details"}"
            markFailed(component, message)
            Result.failure()
        }
    }

    private class PauseRequested : Exception()
    private class ChecksumMismatch : Exception()

    /** Downloads a single file with Range-resume support, reporting weighted progress against [totalBytes]. */
    private suspend fun downloadOne(
        url: String,
        localFileName: String,
        component: ModelComponent,
        totalBytes: Long,
        bytesOffsetInTotal: Long,
        expectedSha256: String
    ) {
        val partialFile = storageManager.partialFileFor(localFileName)
        val finalFile = storageManager.fileFor(localFileName)
        if (finalFile.exists()) return // already downloaded (e.g. resuming after the 2nd file failed)

        val startOffset = if (partialFile.exists()) partialFile.length() else 0L

        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$startOffset-")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code} ${response.message} for ${url.substringAfterLast('/')}")
            }
            val body = response.body ?: throw java.io.IOException("Empty response body for ${url.substringAfterLast('/')}")

            RandomAccessFile(partialFile, "rw").use { raf ->
                raf.seek(startOffset)
                body.byteStream().use { input ->
                    val buffer = ByteArray(1 * 1024 * 1024)
                    var totalRead = startOffset
                    var lastReportedPercent = -1
                    while (true) {
                        if (isStopped) throw PauseRequested()
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        totalRead += read

                        val overallBytes = bytesOffsetInTotal + totalRead
                        val percent = ((overallBytes * 100) / totalBytes).toInt()
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            val fraction = overallBytes.toFloat() / totalBytes
                            setProgressAsync(workDataOf(KEY_PROGRESS to fraction))
                            modelDao.upsert(
                                ModelEntity(
                                    component = component.name,
                                    isInstalled = false,
                                    downloadProgress = fraction,
                                    isDownloading = true,
                                    isPaused = false,
                                    bytesDownloaded = overallBytes
                                )
                            )
                        }
                    }
                }
            }
        }

        if (expectedSha256.isNotBlank()) {
            val actualSha = storageManager.sha256Of(partialFile)
            if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                partialFile.delete()
                throw ChecksumMismatch()
            }
        }

        partialFile.renameTo(finalFile)
    }

    private suspend fun markPaused(component: ModelComponent) {
        val existing = modelDao.get(component.name)
        modelDao.upsert(
            (existing ?: ModelEntity(component = component.name)).copy(
                isDownloading = false,
                isPaused = true
            )
        )
    }

    private suspend fun markFailed(component: ModelComponent, errorMessage: String) {
        val existing = modelDao.get(component.name)
        modelDao.upsert(
            (existing ?: ModelEntity(component = component.name)).copy(
                isDownloading = false,
                isPaused = false,
                isInstalled = false,
                downloadProgress = 0f,
                bytesDownloaded = 0,
                lastError = errorMessage
            )
        )
    }
}
