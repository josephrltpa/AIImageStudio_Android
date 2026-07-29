package com.aiimagestudio.domain.usecase

import android.graphics.Bitmap
import com.aiimagestudio.domain.model.GenerationJob
import com.aiimagestudio.domain.model.GenerationMode
import com.aiimagestudio.domain.model.GenerationSettings
import com.aiimagestudio.domain.repository.InferenceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Orchestrates a single generation/edit run. Validates preconditions
 * (e.g. InstructPix2Pix requires an input image) before delegating to
 * the inference engine, keeping that validation out of the ViewModel.
 */
class GenerateImageUseCase @Inject constructor(
    private val inferenceRepository: InferenceRepository
) {
    operator fun invoke(
        mode: GenerationMode,
        prompt: String,
        inputImage: Bitmap?,
        settings: GenerationSettings
    ): Flow<GenerationJob> {
        require(prompt.isNotBlank()) { "Prompt must not be empty." }
        if (mode == GenerationMode.INSTRUCT_PIX2PIX_EDIT) {
            requireNotNull(inputImage) { "InstructPix2Pix requires an input image." }
        }
        return inferenceRepository.generate(mode, prompt, inputImage, settings)
    }
}
