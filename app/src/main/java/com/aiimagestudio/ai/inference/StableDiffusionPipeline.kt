package com.aiimagestudio.ai.inference

import android.graphics.Bitmap
import com.aiimagestudio.domain.model.GenerationJob
import com.aiimagestudio.domain.model.GenerationSettings
import com.aiimagestudio.domain.model.ModelComponent
import kotlinx.coroutines.flow.FlowCollector
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Standard SD 1.5 text-to-image pipeline: prompt -> UNet denoising loop ->
 * VAE decode. Included so the app's "Model" selector can also do pure
 * generation, not just instruction-based editing, using the same UNet
 * conditioning contract without the extra image-latent channel that
 * InstructPix2Pix requires (see [InstructPix2PixPipeline]).
 */
@Singleton
class StableDiffusionPipeline @Inject constructor(
    private val engine: OnnxInferenceEngine,
    private val tokenizer: TextTokenizer
) {
    private val latentChannels = 4
    private val vaeScaleFactor = 8
    private val vaeScalingFactor = 0.18215f // SD1.5 VAE's trained latent scaling_factor

    suspend fun run(
        collector: FlowCollector<GenerationJob>,
        prompt: String,
        settings: GenerationSettings
    ): Bitmap {
        collector.emit(GenerationJob.Preprocessing("Encoding prompt…"))
        val latentH = settings.height / vaeScaleFactor
        val latentW = settings.width / vaeScaleFactor

        val tokenIds = tokenizer.encode(prompt)
        val (textEmbeddingBuf, textEmbeddingShape) = engine.runTextEncoder(tokenIds)
        engine.maybeUnloadForLowRam(ModelComponent.SD15_TEXT_ENCODER, settings.memoryMode)
        val textEmbedding = FloatArray(textEmbeddingBuf.remaining()).also { textEmbeddingBuf.get(it) }

        val scheduler = DiffusionScheduler(settings.scheduler, settings.steps, settings.seed)
        var latents = scheduler.initialNoise(latentChannels * latentH * latentW)

        for (stepIndex in scheduler.timesteps.indices) {
            collector.emit(GenerationJob.Denoising(stepIndex + 1, settings.steps))

            val (noisePredBuf, _) = engine.runFloatOutput(
                component = ModelComponent.SD15_UNET,
                precision = settings.precision,
                inputs = mapOf(
                    "sample" to (FloatBuffer.wrap(latents) to longArrayOf(1, latentChannels.toLong(), latentH.toLong(), latentW.toLong())),
                    "encoder_hidden_states" to (FloatBuffer.wrap(textEmbedding) to textEmbeddingShape),
                    "timestep" to (FloatBuffer.wrap(floatArrayOf(scheduler.timesteps[stepIndex].toFloat())) to longArrayOf(1))
                )
            )
            val predictedNoise = FloatArray(noisePredBuf.remaining()).also { noisePredBuf.get(it) }
            latents = scheduler.step(latents, predictedNoise, stepIndex)
        }
        engine.maybeUnloadForLowRam(ModelComponent.SD15_UNET, settings.memoryMode)

        collector.emit(GenerationJob.Decoding("Decoding image…"))
        // SD1.5's VAE was trained with encoder outputs multiplied by
        // vaeScalingFactor (0.18215); decoding requires inverting that
        // scaling first. Skipping this feeds the decoder latents ~5.5x too
        // large — completely outside its trained input distribution —
        // which produces garbage/noise-looking output (see the same fix
        // and comment in InstructPix2PixPipeline.kt).
        val scaledLatents = FloatArray(latents.size) { i -> latents[i] / vaeScalingFactor }
        val (decodedBuf, _) = engine.runFloatOutput(
            component = ModelComponent.SD15_VAE_DECODER,
            precision = settings.precision,
            inputs = mapOf(
                "latent_sample" to (FloatBuffer.wrap(scaledLatents) to longArrayOf(1, latentChannels.toLong(), latentH.toLong(), latentW.toLong()))
            )
        )
        engine.maybeUnloadForLowRam(ModelComponent.SD15_VAE_DECODER, settings.memoryMode)

        return ImageTensorConverter.nchwTensorToBitmap(decodedBuf, settings.width, settings.height)
    }
}
