package com.aiimagestudio.data.storage
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
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

    /**
     * Writes [bitmap] into the device's public Pictures gallery via
     * MediaStore, so it actually shows up in the user's Photos/Gallery app.
     * This is distinct from [saveResult], which only writes to app-private
     * scoped storage for in-app history — that alone is why the Save button
     * appeared to do nothing from the user's point of view. minSdk is 29,
     * so no WRITE_EXTERNAL_STORAGE permission is needed for this path.
     */
    fun saveToGallery(bitmap: Bitmap): Boolean {
        return runCatching {
            val filename = "AIImageStudio_${UUID.randomUUID()}.png"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Image Studio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@runCatching false

            val wrote = resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            wrote
        }.getOrElse { false }
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    /** Returns a content:// Uri suitable for ACTION_SEND sharing. */
    fun shareableUriFor(path: String) = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", File(path)
    )
}
