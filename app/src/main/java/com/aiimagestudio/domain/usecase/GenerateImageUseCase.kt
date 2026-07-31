package com.aiimagestudio.domain.usecase

import android.graphics.Bitmap
import com.aiimagestudio.domain.model.GenerationJob
import com.aiimagestudio.domain.model.GenerationMode
import com.aiimagestudio.domain.model.GenerationSettings
import com.aiimagestudio.domain.repository.InferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Orchestrates a single generation/edit run. Validates preconditions
 * (e.g. InstructPix2Pix requires an input image) before delegating to
 * the inference engine, keeping that validation out of the ViewModel.
 *
 * Also gates on [InferenceRepository.isReady]: that check existed in the
 * repository but nothing ever called it, so Generate fell straight into
 * the ONNX pipeline even when the required model components weren't
 * downloaded. Whatever partial/placeholder file happened to sit at a
 * component's expected local path would get loaded and run as if it were
 * the real model, producing a "successful" result that was actually just
 * untrained-weight static — with Save/Share enabled and no indication
 * anything was wrong. This check turns that into an explicit, honest
 * failure before any inference runs.
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
        return flow {
            if (!inferenceRepository.isReady(mode)) {
                emit(
                    GenerationJob.Failure(
                        IllegalStateException(
                            "Required model files for this mode aren't downloaded yet. " +
                                "Open Model Manager and download them before generating."
                        )
                    )
                )
                return@flow
            }
            emitAll(inferenceRepository.generate(mode, prompt, inputImage, settings))
        }
    }
}
