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
 * Implements InstructPix2Pix: an input photo plus a natural-language
 * instruction ("make the sky sunset orange") produces an edited photo.
 *
 * Pipeline (matches the architecture diagram in the product spec):
 *   input image -> VAE encode -> latents
 *   instruction -> tokenizer -> text encoder -> embeddings
 *   [latents, embeddings, timestep] -> InstructPix2Pix UNet, looped over
 *     [GenerationSettings.steps] denoising steps via [DiffusionScheduler]
 *   final latents -> VAE decode -> output bitmap
 *
 * InstructPix2Pix's UNet is additionally conditioned on the *original*
 * image latents at every step (concatenated on the channel axis) — this is
 * what lets it preserve the subject while only applying the requested edit.
 */
@Singleton
class InstructPix2PixPipeline @Inject constructor(
    private val engine: OnnxInferenceEngine,
    private val tokenizer: TextTokenizer
) {
    private val latentChannels = 4
    private val vaeScaleFactor = 8 // SD VAE downsamples spatial dims by 8x
    private val vaeScalingFactor = 0.18215f // SD1.5 VAE's trained latent scaling_factor

    suspend fun run(
        collector: FlowCollector<GenerationJob>,
        inputImage: Bitmap,
        instruction: String,
        settings: GenerationSettings
    ): Bitmap {
        collector.emit(GenerationJob.Preprocessing("Encoding input image…"))
        val latentH = settings.height / vaeScaleFactor
        val latentW = settings.width / vaeScaleFactor

        // 1. Encode the source image into latent space via the VAE encoder.
        val imageTensor = ImageTensorConverter.bitmapToNchwTensor(inputImage, settings.width, settings.height)
        val (imageLatentsBuf, _) = engine.runFloatOutput(
            component = ModelComponent.SD15_VAE_ENCODER,
            precision = settings.precision,
            inputs = mapOf(
                // The Optimum/Diffusers ONNX export names the VAE encoder's
                // input tensor "sample" (not "pixel_values" — that was wrong
                // and caused ONNX Runtime to reject the input at inference
                // time with "Unknown input name pixel_values, expected one
                // of [sample]").
                "sample" to (imageTensor to longArrayOf(1, 3, settings.height.toLong(), settings.width.toLong()))
            )
        )
        engine.maybeUnloadForLowRam(ModelComponent.SD15_VAE_ENCODER, settings.memoryMode)
        val imageLatents = FloatArray(imageLatentsBuf.remaining()).also { imageLatentsBuf.get(it) }

        // 2. Tokenize + encode the instruction text.
        collector.emit(GenerationJob.Preprocessing("Encoding instruction…"))
        val tokenIds = tokenizer.encode(instruction)
        val (textEmbeddingBuf, textEmbeddingShape) = engine.runTextEncoder(tokenIds)
        engine.maybeUnloadForLowRam(ModelComponent.SD15_TEXT_ENCODER, settings.memoryMode)
        val textEmbedding = FloatArray(textEmbeddingBuf.remaining()).also { textEmbeddingBuf.get(it) }

        // 3. Initialize random latents (the "canvas" the UNet will denoise).
        val scheduler = DiffusionScheduler(settings.scheduler, settings.steps, settings.seed)
        var latents = scheduler.initialNoise(latentChannels * latentH * latentW)

        // 4. Iterative denoising loop, conditioned each step on [latents, imageLatents, textEmbedding].
        for (stepIndex in scheduler.timesteps.indices) {
            collector.emit(GenerationJob.Denoising(stepIndex + 1, settings.steps))

            val unetInputChannels = FloatArray(latents.size + imageLatents.size)
            System.arraycopy(latents, 0, unetInputChannels, 0, latents.size)
            System.arraycopy(imageLatents, 0, unetInputChannels, latents.size, imageLatents.size)

            val (noisePredBuf, _) = engine.runFloatOutput(
                component = ModelComponent.INSTRUCT_PIX2PIX_UNET,
                precision = settings.precision,
                inputs = mapOf(
                    "sample" to (FloatBuffer.wrap(unetInputChannels) to longArrayOf(
                        1, (latentChannels * 2).toLong(), latentH.toLong(), latentW.toLong()
                    )),
                    "encoder_hidden_states" to (FloatBuffer.wrap(textEmbedding) to textEmbeddingShape),
                    "timestep" to (FloatBuffer.wrap(floatArrayOf(scheduler.timesteps[stepIndex].toFloat())) to longArrayOf(1))
                )
            )
            val predictedNoise = FloatArray(noisePredBuf.remaining()).also { noisePredBuf.get(it) }
            latents = scheduler.step(latents, predictedNoise, stepIndex)
        }
        engine.maybeUnloadForLowRam(ModelComponent.INSTRUCT_PIX2PIX_UNET, settings.memoryMode)

        // 5. Decode final latents back to pixel space.
        collector.emit(GenerationJob.Decoding("Decoding image…"))
        // SD1.5's VAE was trained with encoder outputs multiplied by
        // vaeScalingFactor (0.18215); decoding requires inverting that
        // scaling first. Skipping this fed the decoder latents ~5.5x too
        // large — completely outside its trained input distribution —
        // which is what was producing garbage/noise-looking output here.
        // val scaledLatents = FloatArray(latents.size) { i -> latents[i] / vaeScalingFactor }
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
