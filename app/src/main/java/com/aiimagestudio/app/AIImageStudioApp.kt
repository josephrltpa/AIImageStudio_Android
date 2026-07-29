package com.aiimagestudio.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Wires Hilt DI across the app and configures WorkManager so that background
 * jobs (model downloads, and — on low-RAM devices — chunked inference work)
 * can have dependencies injected into them via [HiltWorkerFactory].
 */
@HiltAndroidApp
class AIImageStudioApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
