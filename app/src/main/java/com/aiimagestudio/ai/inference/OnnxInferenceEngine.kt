package com.aiimagestudio.ai.inference

import ai.onnxruntime.*
import com.aiimagestudio.ai.memory.MemoryMonitor
import com.aiimagestudio.data.storage.ModelStorageManager
import com.aiimagestudio.domain.model.MemoryMode
import com.aiimagestudio.domain.model.ModelComponent
import com.aiimagestudio.domain.model.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the ONNX Runtime environment and lazily-loaded [OrtSession]s for each
 * model component. This is the single point of contact with the native
 * inference engine — every ONNX Runtime API call in the app happens here.
 *
 * Session lifecycle is driven by [MemoryMode]:
 *  - PERFORMANCE: sessions stay resident once loaded (fastest repeat runs).
 *  - LOW_RAM: [unload] is called between pipeline stages to cap peak RSS.
 *
 * NNAPI execution provider is requested first for GPU/NPU acceleration
 * where the device supports it, falling back transparently to optimized
 * CPU execution (XNNPACK) otherwise.
 */
@Singleton
class OnnxInferenceEngine @Inject constructor(
    private val storageManager: ModelStorageManager,
    private val memoryMonitor: MemoryMonitor
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessions = mutableMapOf<ModelComponent, OrtSession>()
    private val lock = Mutex()

    suspend fun isLoaded(component: ModelComponent): Boolean = lock.withLock {
        sessions.containsKey(component)
    }

    suspend fun ensureLoaded(component: ModelComponent, precision: Precision): OrtSession =
        withContext(Dispatchers.Default) {
            lock.withLock {
                sessions[component]?.let { return@withContext it }

                val modelFile = com.aiimagestudio.ai.download.ModelCatalog.find(component).localFileName
                val file = storageManager.fileFor(modelFile)
                require(file.exists()) {
                    "Model '$modelFile' is not installed. Download it from the Model Manager first."
                }

                val options = OrtSession.SessionOptions().apply {
                    setMemoryPatternOptimization(true)
                    setCPUArenaAllocator(true)
                    setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
                    // Prefer NNAPI (GPU/DSP/NPU) acceleration when the device exposes it;
                    // silently falls back to pure CPU execution if unavailable.
                    runCatching { addNnapi() }
                }

                val session = env.createSession(file.absolutePath, options)
                sessions[component] = session
                session
            }
        }

    /** Releases a single session's native memory (used by Low-RAM mode between pipeline stages). */
    suspend fun unload(component: ModelComponent) = lock.withLock {
        sessions.remove(component)?.close()
    }

    suspend fun unloadAll() = lock.withLock {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }

    /** Runs a session with the given named float-tensor inputs, returning the first output as a FloatBuffer. */
    suspend fun runFloatOutput(
        component: ModelComponent,
        precision: Precision,
        inputs: Map<String, Pair<FloatBuffer, LongArray>>
    ): Pair<FloatBuffer, LongArray> = withContext(Dispatchers.Default) {
        val session = ensureLoaded(component, precision)
        val ortInputs = inputs.mapValues { (_, value) ->
            val (buffer, shape) = value
            OnnxTensor.createTensor(env, buffer, shape)
        }
        try {
            session.run(ortInputs).use { result ->
                val output = result[0] as OnnxTensor
                val outShape = output.info.shape
                val outBuffer = output.floatBuffer
                // Copy out of the OrtSession-managed buffer before it's released.
                val copy = FloatBuffer.allocate(outBuffer.remaining())
                copy.put(outBuffer)
                copy.rewind()
                copy to outShape
            }
        } finally {
            ortInputs.values.forEach { it.close() }
        }
    }

    /** Runs a session that takes integer token-id inputs (the text encoder). */
    suspend fun runTextEncoder(
        tokenIds: IntArray
    ): Pair<FloatBuffer, LongArray> = withContext(Dispatchers.Default) {
        val session = ensureLoaded(ModelComponent.SD15_TEXT_ENCODER, Precision.FP16)
        val longIds = LongArray(tokenIds.size) { tokenIds[it].toLong() }
        val inputTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(longIds), longArrayOf(1, tokenIds.size.toLong())
        )
        try {
            session.run(mapOf(session.inputNames.first() to inputTensor)).use { result ->
                val output = result[0] as OnnxTensor
                val shape = output.info.shape
                val buf = output.floatBuffer
                val copy = FloatBuffer.allocate(buf.remaining())
                copy.put(buf); copy.rewind()
                copy to shape
            }
        } finally {
            inputTensor.close()
        }
    }

    /** Applies the currently-resolved memory strategy: unloads a stage's session once it's no longer needed. */
    suspend fun maybeUnloadForLowRam(component: ModelComponent, userMode: MemoryMode) {
        if (memoryMonitor.resolveEffectiveMode(userMode) == MemoryMode.LOW_RAM) {
            unload(component)
        }
    }
}
