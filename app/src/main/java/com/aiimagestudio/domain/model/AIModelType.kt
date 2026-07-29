package com.aiimagestudio.domain.model

/**
 * The two supported pipelines, per the product requirement:
 * text-to-image generation (SD 1.5) and instruction-based image editing (InstructPix2Pix).
 */
enum class GenerationMode {
    STABLE_DIFFUSION_TXT2IMG,
    INSTRUCT_PIX2PIX_EDIT
}

/** Identifies an individual downloadable model component (ONNX weight file). */
enum class ModelComponent {
    SD15_UNET,
    SD15_TEXT_ENCODER,
    SD15_TOKENIZER,
    SD15_VAE_DECODER,
    SD15_VAE_ENCODER,
    INSTRUCT_PIX2PIX_UNET
}
