package com.aiimagestudio.domain.repository

import android.graphics.Bitmap
import com.aiimagestudio.domain.model.GenerationJob
import com.aiimagestudio.domain.model.GenerationMode
import com.aiimagestudio.domain.model.GenerationSettings
import kotlinx.coroutines.flow.Flow

/**
 * Abstracts the on-device AI engine. The implementation in
 * [com.aiimagestudio.data.repository.InferenceRepositoryImpl] delegates to
 * [com.aiimagestudio.ai.inference.OnnxInferenceEngine] and never touches the network.
 */
interface InferenceRepository {
    fun generate(
        mode: GenerationMode,
        prompt: String,
        inputImage: Bitmap?,
        settings: GenerationSettings
    ): Flow<GenerationJob>

    /** True once all ONNX sessions needed for [mode] are loaded and warmed up. */
    suspend fun isReady(mode: GenerationMode): Boolean

    /** Frees native ONNX sessions to reclaim RAM (used by Low-RAM mode / backgrounding). */
    suspend fun unloadAll()
}
