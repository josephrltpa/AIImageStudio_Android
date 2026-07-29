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
    // Large ONNX graphs (>2GB) are sometimes split into a tiny graph file
    // (localFileName) plus a separate weights file. When non-null, the
    // downloader fetches both and keeps them side-by-side in the same
    // folder — ONNX Runtime automatically finds the data file next to the
    // .onnx graph as long as they share a directory.
    val dataDownloadUrl: String? = null,
    val dataLocalFileName: String? = null,
    val dataSizeBytes: Long = 0,
    val dataSha256: String = "",
    val isInstalled: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false
) {
    val hasSeparateDataFile: Boolean get() = dataDownloadUrl != null
    val totalBytes: Long get() = sizeBytes + dataSizeBytes
}

/** A logical bundle of components required to run a [GenerationMode]. */
data class ModelBundle(
    val mode: GenerationMode,
    val components: List<AIModel>
) {
    val isFullyInstalled: Boolean get() = components.all { it.isInstalled }
    val totalSizeBytes: Long get() = components.sumOf { it.sizeBytes }
}
