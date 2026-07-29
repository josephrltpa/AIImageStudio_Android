package com.aiimagestudio.data.storage
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All image I/O goes through app-scoped external storage
 * (getExternalFilesDir), so no MANAGE_EXTERNAL_STORAGE or legacy
 * WRITE_EXTERNAL_STORAGE permission is ever required (Scoped Storage).
 */
@Singleton
class ImageStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val originalsDir: File
        get() = File(context.getExternalFilesDir("images/originals"), "").apply { mkdirs() }

    private val resultsDir: File
        get() = File(context.getExternalFilesDir("images/generated"), "").apply { mkdirs() }

    fun saveOriginal(bitmap: Bitmap): String {
        val file = File(originalsDir, "orig_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return file.absolutePath
    }

    fun saveResult(bitmap: Bitmap): String {
        val file = File(resultsDir, "result_${UUID.randomUUID()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    fun loadBitmap(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    /** Returns a content:// Uri suitable for ACTION_SEND sharing. */
    fun shareableUriFor(path: String) = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", File(path)
    )
}
