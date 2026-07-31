package com.aiimagestudio.data.repository

import android.graphics.Bitmap
import com.aiimagestudio.ai.inference.InstructPix2PixPipeline
import com.aiimagestudio.ai.inference.OnnxInferenceEngine
import com.aiimagestudio.ai.inference.StableDiffusionPipeline
import com.aiimagestudio.data.storage.ImageStorageManager
import com.aiimagestudio.data.storage.ModelStorageManager
import com.aiimagestudio.domain.model.*
import com.aiimagestudio.domain.repository.InferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class InferenceRepositoryImpl @Inject constructor(
    private val sdPipeline: StableDiffusionPipeline,
    private val ip2pPipeline: InstructPix2PixPipeline,
    private val engine: OnnxInferenceEngine,
    private val imageStorageManager: ImageStorageManager,
    private val modelStorageManager: ModelStorageManager
) : InferenceRepository {

    override fun generate(
        mode: GenerationMode,
        prompt: String,
        inputImage: Bitmap?,
        settings: GenerationSettings
    ): Flow<GenerationJob> = flow {
        try {
            val resultBitmap: Bitmap = when (mode) {
                GenerationMode.STABLE_DIFFUSION_TXT2IMG -> sdPipeline.run(this, prompt, settings)
                GenerationMode.INSTRUCT_PIX2PIX_EDIT -> {
                    requireNotNull(inputImage)
                    ip2pPipeline.run(this, inputImage, prompt, settings)
                }
            }

            val originalPath = inputImage?.let { imageStorageManager.saveOriginal(it) }
            val resultPath = imageStorageManager.saveResult(resultBitmap)

            emit(
                GenerationJob.Success(
                    GeneratedImage(
                        originalImagePath = originalPath,
                        resultImagePath = resultPath,
                        prompt = prompt,
                        mode = mode,
                        settings = settings,
                        createdAtEpochMillis = System.currentTimeMillis()
                    )
                )
            )
        } catch (t: Throwable) {
            emit(GenerationJob.Failure(t))
        }
    }

    override suspend fun isReady(mode: GenerationMode): Boolean {
        val required = when (mode) {
            GenerationMode.STABLE_DIFFUSION_TXT2IMG -> listOf(
                ModelComponent.SD15_TOKENIZER, ModelComponent.SD15_TEXT_ENCODER,
                ModelComponent.SD15_UNET, ModelComponent.SD15_VAE_DECODER
            )
            GenerationMode.INSTRUCT_PIX2PIX_EDIT -> listOf(
                ModelComponent.SD15_TOKENIZER, ModelComponent.SD15_TEXT_ENCODER,
                ModelComponent.INSTRUCT_PIX2PIX_UNET, ModelComponent.SD15_VAE_ENCODER,
                ModelComponent.SD15_VAE_DECODER
            )
        }
        return required.all { component ->
            val model = com.aiimagestudio.ai.download.ModelCatalog.find(component)
            val primaryOk = modelStorageManager.isValidModelFile(model.localFileName, model.sizeBytes)
            val dataOk = if (model.hasSeparateDataFile) {
                modelStorageManager.isValidModelFile(model.dataLocalFileName!!, model.dataSizeBytes)
            } else true
            primaryOk && dataOk
        }
    }

    override suspend fun unloadAll() = engine.unloadAll()
}
