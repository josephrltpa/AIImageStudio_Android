package com.aiimagestudio.domain.model

/** Progress/result contract emitted while a generation runs, for UI observation. */
sealed class GenerationJob {
    data class Preprocessing(val message: String) : GenerationJob()
    data class Denoising(val step: Int, val totalSteps: Int) : GenerationJob()
    data class Decoding(val message: String) : GenerationJob()
    data class Success(val image: GeneratedImage) : GenerationJob()
    data class Failure(val throwable: Throwable) : GenerationJob()
}
