package com.aiimagestudio.ai.inference

import com.aiimagestudio.domain.model.Scheduler
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * Produces the noise-schedule (sigmas / alphas_cumprod) and per-step update
 * rule for the denoising loop. Implements the standard SD 1.5 linear-beta
 * schedule (beta_start=0.00085, beta_end=0.012, 1000 training steps),
 * subsampled down to [numInferenceSteps].
 *
 * Only the algorithms needed for this app are implemented: DDIM
 * (deterministic, reproducible with a seed) and Euler Ancestral
 * (stochastic, often sharper results). PNDM is exposed in settings for
 * future extension but currently falls back to DDIM stepping.
 */
class DiffusionScheduler(
    private val type: Scheduler,
    private val numInferenceSteps: Int,
    seed: Long?
) {
    private val random = Random(seed ?: System.nanoTime())

    private val betaStart = 0.00085
    private val betaEnd = 0.012
    private val numTrainTimesteps = 1000

    private val alphasCumprod: DoubleArray = run {
        val betas = DoubleArray(numTrainTimesteps) { i ->
            val t = i / (numTrainTimesteps - 1).toDouble()
            val sqrtB = kotlin.math.sqrt(betaStart) + t * (kotlin.math.sqrt(betaEnd) - kotlin.math.sqrt(betaStart))
            sqrtB * sqrtB
        }
        val alphas = betas.map { 1.0 - it }
        val cumProd = DoubleArray(numTrainTimesteps)
        var running = 1.0
        for (i in alphas.indices) {
            running *= alphas[i]
            cumProd[i] = running
        }
        cumProd
    }

    /** The training timesteps to actually run, evenly spaced and reversed (high noise -> low noise). */
    val timesteps: IntArray = run {
        val step = numTrainTimesteps / numInferenceSteps
        IntArray(numInferenceSteps) { i -> (numTrainTimesteps - 1) - i * step }.also { it.reverse().let { } }
            .let { arr -> IntArray(numInferenceSteps) { i -> arr[numInferenceSteps - 1 - i] } }
    }

    /** Initial latent noise, scaled appropriately for the first timestep. */
    fun initialNoise(size: Int): FloatArray {
        val initSigma = sigmaFor(timesteps.first())
        return FloatArray(size) { (random.nextGaussian() * initSigma).toFloat() }
    }

    fun sigmaFor(timestep: Int): Double {
        val acp = alphasCumprod[timestep.coerceIn(0, numTrainTimesteps - 1)]
        return kotlin.math.sqrt((1 - acp) / acp)
    }

    /**
     * Applies one denoising step given the UNet's predicted noise.
     * latents_(t-1) = step(latents_t, predicted_noise, t)
     */
    fun step(latents: FloatArray, predictedNoise: FloatArray, stepIndex: Int): FloatArray {
        val t = timesteps[stepIndex]
        val acpT = alphasCumprod[t.coerceIn(0, numTrainTimesteps - 1)]
        val prevT = if (stepIndex + 1 < timesteps.size) timesteps[stepIndex + 1] else -1
        val acpPrev = if (prevT >= 0) alphasCumprod[prevT] else 1.0

        val out = FloatArray(latents.size)
        for (i in latents.indices) {
            // Predict x0 from noise, then re-noise to the previous timestep (DDIM update rule).
            val predOriginal = (latents[i] - kotlin.math.sqrt(1 - acpT).toFloat() * predictedNoise[i]) /
                kotlin.math.sqrt(acpT).toFloat()
            val dirToXt = kotlin.math.sqrt(1 - acpPrev).toFloat() * predictedNoise[i]
            out[i] = kotlin.math.sqrt(acpPrev).toFloat() * predOriginal + dirToXt

            if (type == Scheduler.EULER_A && prevT >= 0) {
                // Ancestral sampling: inject a controlled amount of fresh noise for stochasticity.
                val sigmaUp = kotlin.math.sqrt((1 - acpPrev) / (1 - acpT) * (1 - acpT / acpPrev))
                out[i] += (random.nextGaussian() * sigmaUp).toFloat()
            }
        }
        return out
    }

    private fun Random.nextGaussian(): Double {
        // Box-Muller transform (kotlin.random has no built-in Gaussian sampler).
        val u1 = nextDouble().coerceAtLeast(1e-9)
        val u2 = nextDouble()
        return kotlin.math.sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }
}
