package com.aiimagestudio.data.storage
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import android.os.StatFs
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages on-disk location and integrity checking for downloaded model
 * weights. Models live in app-private internal storage
 * (context.filesDir/models) — not shared/scoped external storage — since
 * they are large binary assets never meant to be user-browsable.
 */
@Singleton
class ModelStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun fileFor(localFileName: String): File = File(modelsDir, localFileName).also { it.parentFile?.mkdirs() }

    fun partialFileFor(localFileName: String): File = File(modelsDir, "$localFileName.part").also { it.parentFile?.mkdirs() }

    fun exists(localFileName: String): Boolean = fileFor(localFileName).exists()

    fun delete(localFileName: String) {
        fileFor(localFileName).delete()
        partialFileFor(localFileName).delete()
    }

    fun availableBytes(): Long {
        val stat = StatFs(context.filesDir.path)
        return stat.availableBytes
    }

    /** Streaming SHA-256 check so multi-GB model files never load fully into RAM. */
    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
