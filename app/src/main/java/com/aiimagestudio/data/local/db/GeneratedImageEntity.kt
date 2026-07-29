package com.aiimagestudio.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generated_images")
data class GeneratedImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalImagePath: String?,
    val resultImagePath: String,
    val prompt: String,
    val mode: String,
    // Settings are flattened for simple querying; see mappers.
    val width: Int,
    val height: Int,
    val steps: Int,
    val cfgScale: Float,
    val seed: Long?,
    val scheduler: String,
    val denoisingStrength: Float,
    val memoryMode: String,
    val precision: String,
    val createdAtEpochMillis: Long
)
