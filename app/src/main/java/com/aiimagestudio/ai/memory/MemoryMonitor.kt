package com.aiimagestudio.ai.memory
import dagger.hilt.android.qualifiers.ApplicationContext

import android.app.ActivityManager
import android.content.Context
import com.aiimagestudio.domain.model.MemoryMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads live system memory pressure so the inference engine can decide
 * whether to keep all ONNX sessions resident (fast, needs more RAM) or
 * load/unload components on demand (slower, safe on low-RAM devices).
 */
@Singleton
class MemoryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager
        get() = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    data class Snapshot(
        val availableBytes: Long,
        val totalBytes: Long,
        val isLowMemory: Boolean
    )

    fun snapshot(): Snapshot {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return Snapshot(
            availableBytes = info.availMem,
            totalBytes = info.totalMem,
            isLowMemory = info.lowMemory
        )
    }

    /**
     * Resolves the effective memory strategy given the user's preference and
     * live conditions. AUTO falls back to LOW_RAM behavior below ~4GB total
     * RAM or when the system reports low-memory pressure, and otherwise
     * behaves like PERFORMANCE on the 12GB target devices this app is tuned for.
     */
    fun resolveEffectiveMode(userPreference: MemoryMode): MemoryMode {
        if (userPreference != MemoryMode.AUTO) return userPreference
        val snap = snapshot()
        val totalGb = snap.totalBytes / (1024.0 * 1024.0 * 1024.0)
        return if (snap.isLowMemory || totalGb < 4.0) MemoryMode.LOW_RAM else MemoryMode.PERFORMANCE
    }
}
