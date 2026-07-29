package com.aiimagestudio.domain.model

enum class MemoryMode { AUTO, LOW_RAM, PERFORMANCE }
enum class Scheduler { EULER_A, DDIM, PNDM }
enum class Precision { FP16, FP32 }

/**
 * Advanced generation parameters. Sensible defaults per spec are provided
 * so the simple/default UI never needs to show these to the user.
 */
data class GenerationSettings(
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val seed: Long? = null, // null == random seed each run
    val scheduler: Scheduler = Scheduler.EULER_A,
    val denoisingStrength: Float = 0.75f, // used by InstructPix2Pix / img2img
    val memoryMode: MemoryMode = MemoryMode.AUTO,
    val precision: Precision = Precision.FP16
)
