package com.aiimagestudio.domain.model

/**
 * Represents one downloadable model artifact tracked by the Model Manager.
 *
 * [sizeBytes] and [sha256] are used to validate a completed download
 * (see [com.aiimagestudio.domain.usecase.VerifyModelUseCase]).
 */
data class AIModel(
    val component: ModelComponent,
    val displayName: String,
    val description: String,
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val localFileName: String,
    val isInstalled: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false
)

/** A logical bundle of components required to run a [GenerationMode]. */
data class ModelBundle(
    val mode: GenerationMode,
    val components: List<AIModel>
) {
    val isFullyInstalled: Boolean get() = components.all { it.isInstalled }
    val totalSizeBytes: Long get() = components.sumOf { it.sizeBytes }
}
