package com.aiimagestudio.domain.model

/** A single gallery entry: the full record of one generation run. */
data class GeneratedImage(
    val id: Long = 0,
    val originalImagePath: String?,
    val resultImagePath: String,
    val prompt: String,
    val mode: GenerationMode,
    val settings: GenerationSettings,
    val createdAtEpochMillis: Long
)
