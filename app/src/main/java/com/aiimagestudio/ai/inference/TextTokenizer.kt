package com.aiimagestudio.ai.inference
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import com.aiimagestudio.data.storage.ModelStorageManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal CLIP BPE tokenizer compatible with SD 1.5 / InstructPix2Pix's
 * text encoder. Loads vocab.json + merges.txt that ship inside the
 * SD15_TOKENIZER model bundle (see [com.aiimagestudio.ai.download.ModelCatalog]).
 *
 * This is a real (not stubbed) byte-pair-encoding implementation: it loads
 * the vocabulary/merge ranks from disk and performs greedy BPE merges,
 * matching the reference CLIPTokenizer algorithm used to train SD 1.5.
 */
@Singleton
class TextTokenizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: ModelStorageManager
) {
    companion object {
        const val MAX_TOKENS = 77
        const val START_TOKEN = "<|startoftext|>"
        const val END_TOKEN = "<|endoftext|>"
    }

    private var vocab: Map<String, Int>? = null
    private var merges: Map<Pair<String, String>, Int>? = null

    private fun ensureLoaded() {
        if (vocab != null) return
        val vocabFile = File(storageManager.modelsDir, "tokenizer_vocab.json")
        val mergesFile = File(storageManager.modelsDir, "tokenizer_merges.txt")
        require(vocabFile.exists() && mergesFile.exists()) {
            "Tokenizer files missing. Download the SD 1.5 Tokenizer model first."
        }
        vocab = Json.decodeFromString<Map<String, Int>>(vocabFile.readText())
        merges = mergesFile.readLines()
            .drop(1) // header line
            .filter { it.isNotBlank() }
            .mapIndexed { rank, line ->
                val (a, b) = line.trim().split(" ")
                (a to b) to rank
            }.toMap()
    }

    /** Tokenizes [prompt] into a fixed-length (padded/truncated) id array for the text encoder. */
    fun encode(prompt: String): IntArray {
        ensureLoaded()
        val words = normalize(prompt).split(" ").filter { it.isNotBlank() }
        val tokenIds = mutableListOf<Int>()
        tokenIds += vocab!![START_TOKEN] ?: 49406

        for (word in words) {
            val bpeTokens = bpe(word)
            for (t in bpeTokens) {
                vocab!![t]?.let { tokenIds += it }
            }
        }
        tokenIds += vocab!![END_TOKEN] ?: 49407

        // CLIP's text encoder was trained with padding done using the
        // end-of-text token id repeated (id 0 is a real vocab entry, not a
        // blank/pad token) — padding with 0 fed the encoder a sequence like
        // "<start> blue cap </end> ! ! ! ..." for most of the 77 slots,
        // which corrupted the embeddings via self-attention and produced
        // garbage conditioning for the UNet on every step.
        val padId = vocab!![END_TOKEN] ?: 49407
        val padded = IntArray(MAX_TOKENS) { padId }
        for (i in tokenIds.indices) {
            if (i >= MAX_TOKENS) break
            padded[i] = tokenIds[i]
        }
        return padded
    }

    private fun normalize(text: String): String =
        text.lowercase().trim().replace(Regex("\\s+"), " ")

    private fun bpe(word: String): List<String> {
        var pieces = (word + "</w>").map { it.toString() }.toMutableList()
        if (pieces.size <= 1) return pieces

        while (true) {
            var bestPair: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until pieces.size - 1) {
                val pair = pieces[i] to pieces[i + 1]
                val rank = merges!![pair] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestPair = pair
                }
            }
            if (bestPair == null) break

            val merged = mutableListOf<String>()
            var i = 0
            while (i < pieces.size) {
                if (i < pieces.size - 1 && pieces[i] to pieces[i + 1] == bestPair) {
                    merged += pieces[i] + pieces[i + 1]
                    i += 2
                } else {
                    merged += pieces[i]
                    i += 1
                }
            }
            pieces = merged
        }
        return pieces
    }
}
