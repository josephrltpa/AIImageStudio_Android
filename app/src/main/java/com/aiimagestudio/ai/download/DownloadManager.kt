package com.aiimagestudio.ai.download
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import androidx.work.*
import com.aiimagestudio.domain.model.ModelComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over WorkManager that gives the rest of the app simple
 * start/pause/resume/cancel semantics for model downloads, using a unique
 * work name per component so re-enqueueing safely resumes rather than
 * duplicating a job.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun uniqueWorkName(component: ModelComponent) = "download_${component.name}"

    private val workManager get() = WorkManager.getInstance(context)

    fun enqueue(component: ModelComponent) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_COMPONENT to component.name))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(component),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Pausing cancels the WorkManager job; the partial file on disk is preserved. */
    fun pause(component: ModelComponent) {
        workManager.cancelUniqueWork(uniqueWorkName(component))
    }

    fun resume(component: ModelComponent) = enqueue(component)

    fun cancel(component: ModelComponent) {
        workManager.cancelUniqueWork(uniqueWorkName(component))
    }
}
