package com.aiimagestudio.ai.download

import com.aiimagestudio.domain.model.AIModel
import com.aiimagestudio.domain.model.ModelComponent

/**
 * Declares where every model component comes from and how to validate it.
 *
 * IMPORTANT (see docs/MODEL_SETUP.md): Stable Diffusion 1.5 and
 * InstructPix2Pix are distributed as PyTorch/SafeTensors checkpoints. They
 * must be converted to ONNX (opset 14+, static or dynamic shapes matching
 * the target resolution) before use here — this app's inference engine
 * (ai/inference/OnnxInferenceEngine.kt) loads .onnx graphs via ONNX Runtime
 * Mobile, not raw .safetensors. The URLs and checksums below are
 * placeholders: point them at your own converted, hosted artifacts (e.g. a
 * private HTTPS bucket or GitHub Release you control) before shipping.
 */
object ModelCatalog {

    fun all(): List<AIModel> = listOf(
        AIModel(
            component = ModelComponent.SD15_TOKENIZER,
            displayName = "SD 1.5 Tokenizer",
            description = "CLIP BPE vocabulary + merges used to tokenize prompts.",
            sizeBytes = 2_200_000,
            sha256 = "REPLACE_WITH_REAL_SHA256",
            downloadUrl = "https://example-model-host.invalid/sd15/tokenizer.onnx",
            localFileName = "sd15_tokenizer.onnx"
        ),
        AIModel(
            component = ModelComponent.SD15_TEXT_ENCODER,
            displayName = "SD 1.5 Text Encoder (CLIP)",
            description = "Encodes the prompt into embeddings that condition the UNet.",
            sizeBytes = 492_000_000,
            sha256 = "REPLACE_WITH_REAL_SHA256",
            downloadUrl = "https://example-model-host.invalid/sd15/text_encoder.onnx",
            localFileName = "sd15_text_encoder.onnx"
        ),
        AIModel(
            component = ModelComponent.SD15_UNET,
            displayName = "SD 1.5 UNet",
            description = "The denoising network — the largest and slowest component.",
            sizeBytes = 3_440_000_000,
            sha256 = "REPLACE_WITH_REAL_SHA256",
            downloadUrl = "https://example-model-host.invalid/sd15/unet.onnx",
            localFileName = "sd15_unet.onnx"
        ),
        AIModel(
            component = ModelComponent.SD15_VAE_DECODER,
            displayName = "SD 1.5 VAE Decoder",
            description = "Converts denoised latents back into a pixel image.",
            sizeBytes = 198_000_000,
            sha256 = "REPLACE_WITH_REAL_SHA256",
            downloadUrl = "https://example-model-host.invalid/sd15/vae_decoder.onnx",
            localFileName = "sd15_vae_decoder.onnx"
        ),
        AIModel(
            component = ModelComponent.SD15_VAE_ENCODER,
            displayName = "SD 1.5 VAE Encoder",
            description = "Encodes an input photo into the latent space InstructPix2Pix edits.",
            sizeBytes = 138_000_000,
            sha256 = "REPLACE_WITH_REAL_SHA256",
            downloadUrl = "https://example-model-host.invalid/sd15/vae_encoder.onnx",
            localFileName = "sd15_vae_encoder.onnx"
        ),
        AIModel(
            component = ModelComponent.INSTRUCT_PIX2PIX_UNET,
            displayName = "InstructPix2Pix UNet",
            description = "Instruction-conditioned denoiser for natural-language photo edits.",
            sizeBytes = 3_440_000_000,
            sha256 = "REPLACE_WITH_REAL_SHA256",
            downloadUrl = "https://example-model-host.invalid/ip2p/unet.onnx",
            localFileName = "ip2p_unet.onnx"
        )
    )

    fun find(component: ModelComponent): AIModel =
        all().first { it.component == component }
}
