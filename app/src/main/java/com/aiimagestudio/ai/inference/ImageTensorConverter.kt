package com.aiimagestudio.ai.inference

import android.graphics.Bitmap
import java.nio.FloatBuffer

/**
 * Converts between Android [Bitmap]s and the NCHW float tensors the ONNX
 * VAE encoder/decoder and UNet expect. SD 1.5 models are trained on
 * pixel values normalized to [-1, 1].
 */
object ImageTensorConverter {

    /** Resizes [bitmap] to (width x height) and returns an NCHW [1,3,H,W] float buffer in [-1,1]. */
    fun bitmapToNchwTensor(bitmap: Bitmap, width: Int, height: Int): FloatBuffer {
        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else bitmap

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        val buffer = FloatBuffer.allocate(3 * width * height)
        val rPlane = FloatArray(width * height)
        val gPlane = FloatArray(width * height)
        val bPlane = FloatArray(width * height)

        for (i in pixels.indices) {
            val p = pixels[i]
            rPlane[i] = (((p shr 16) and 0xFF) / 127.5f) - 1f
            gPlane[i] = (((p shr 8) and 0xFF) / 127.5f) - 1f
            bPlane[i] = ((p and 0xFF) / 127.5f) - 1f
        }
        buffer.put(rPlane); buffer.put(gPlane); buffer.put(bPlane)
        buffer.rewind()
        return buffer
    }

    /** Inverse of [bitmapToNchwTensor]: decodes an NCHW [1,3,H,W] float buffer in [-1,1] back to a Bitmap. */
    fun nchwTensorToBitmap(buffer: FloatBuffer, width: Int, height: Int): Bitmap {
        buffer.rewind()
        val channelSize = width * height
        val r = FloatArray(channelSize); buffer.get(r)
        val g = FloatArray(channelSize); buffer.get(g)
        val b = FloatArray(channelSize); buffer.get(b)

        val pixels = IntArray(channelSize)
        for (i in 0 until channelSize) {
            val red = (((r[i] + 1f) / 2f).coerceIn(0f, 1f) * 255).toInt()
            val green = (((g[i] + 1f) / 2f).coerceIn(0f, 1f) * 255).toInt()
            val blue = (((b[i] + 1f) / 2f).coerceIn(0f, 1f) * 255).toInt()
            pixels[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
