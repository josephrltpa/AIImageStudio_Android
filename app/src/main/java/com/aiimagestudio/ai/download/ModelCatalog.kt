package com.aiimagestudio.ai.download

import com.aiimagestudio.domain.model.AIModel
import com.aiimagestudio.domain.model.ModelComponent

/**
 * Declares where every model component comes from and how to validate it.
 *
 * These URLs point at real, already-converted ONNX weights hosted publicly
 * on Hugging Face — no local PyTorch conversion step is required:
 *  - SD 1.5 components: https://huggingface.co/modularai/stable-diffusion-1.5-onnx
 *  - InstructPix2Pix components: https://huggingface.co/TensorStack/Instruct-pix2pix-onnx
 *
 * SHA-256 hashes below are intentionally blank: Hugging Face doesn't
 * surface them on the file browser page for LFS/xet-backed files, so
 * checksum verification is skipped for entries with a blank sha256 (see
 * ModelRepositoryImpl.verifyModel / ModelDownloadWorker). If you want
 * verification, download a file once, run `sha256sum <file>` (or
 * `certutil -hashfile <file> SHA256` on Windows), and paste the result in.
 *
 * The SD 1.5 UNet is split into a tiny graph file (model.onnx) plus a
 * separate ~3.4GB weights file (model.onnx_data) because it exceeds the
 * 2GB single-file ONNX protobuf limit — see the dataDownloadUrl fields
 * below. Both files download into the same on-device folder so ONNX
 * Runtime can find them together automatically.
 */
object ModelCatalog {

    private const val SD15_BASE = "https://huggingface.co/modularai/stable-diffusion-1.5-onnx/resolve/main"
    private const val IP2P_BASE = "https://huggingface.co/TensorStack/Instruct-pix2pix-onnx/resolve/main"

    fun all(): List<AIModel> = listOf(
        AIModel(
            component = ModelComponent.SD15_TOKENIZER,
            displayName = "SD 1.5 Tokenizer",
            description = "CLIP BPE vocabulary + merges used to tokenize prompts.",
            sizeBytes = 1_060_000, // vocab.json
            sha256 = "",
            downloadUrl = "$SD15_BASE/tokenizer/vocab.json",
            localFileName = "tokenizer_vocab.json",
            // merges.txt piggybacks on the same catalog entry as a "data file"
            // purely as a download-pairing mechanism (not a split ONNX graph).
            dataDownloadUrl = "$SD15_BASE/tokenizer/merges.txt",
            dataLocalFileName = "tokenizer_merges.txt",
            dataSizeBytes = 525_000
        ),
        AIModel(
            component = ModelComponent.SD15_TEXT_ENCODER,
            displayName = "SD 1.5 Text Encoder (CLIP)",
            description = "Encodes the prompt into embeddings that condition the UNet.",
            sizeBytes = 493_000_000,
            sha256 = "",
            downloadUrl = "$SD15_BASE/text_encoder/model.onnx",
            localFileName = "sd15_text_encoder/model.onnx"
        ),
        AIModel(
            component = ModelComponent.SD15_UNET,
            displayName = "SD 1.5 UNet",
            description = "The denoising network — the largest and slowest component.",
            sizeBytes = 1_210_000, // model.onnx (graph only)
            sha256 = "",
            downloadUrl = "$SD15_BASE/unet/model.onnx",
            localFileName = "sd15_unet/model.onnx",
            dataDownloadUrl = "$SD15_BASE/unet/model.onnx_data",
            dataLocalFileName = "sd15_unet/model.onnx_data",
            dataSizeBytes = 3_440_000_000
        ),
        AIModel(
            component = ModelComponent.SD15_VAE_DECODER,
            displayName = "SD 1.5 VAE Decoder",
            description = "Converts denoised latents back into a pixel image.",
            sizeBytes = 198_000_000,
            sha256 = "",
            downloadUrl = "$SD15_BASE/vae_decoder/model.onnx",
            localFileName = "sd15_vae_decoder/model.onnx"
        ),
        AIModel(
            component = ModelComponent.SD15_VAE_ENCODER,
            displayName = "SD 1.5 VAE Encoder",
            description = "Encodes an input photo into the latent space InstructPix2Pix edits.",
            sizeBytes = 137_000_000,
            sha256 = "",
            downloadUrl = "$SD15_BASE/vae_encoder/model.onnx",
            localFileName = "sd15_vae_encoder/model.onnx"
        ),
        AIModel(
            component = ModelComponent.INSTRUCT_PIX2PIX_UNET,
            displayName = "InstructPix2Pix UNet",
            description = "Instruction-conditioned denoiser for natural-language photo edits.",
            sizeBytes = 3_300_000_000,
            sha256 = "",
            downloadUrl = "$IP2P_BASE/unet/model.onnx",
            localFileName = "ip2p_unet/model.onnx"
        )
    )

    fun find(component: ModelComponent): AIModel =
        all().first { it.component == component }
}
