package com.aiimagestudio.data.local.datastore
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.aiimagestudio.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Persists the Advanced Settings the user has configured, so the app
 * remembers preferences (e.g. Low-RAM mode) between launches.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val WIDTH = intPreferencesKey("width")
        val HEIGHT = intPreferencesKey("height")
        val STEPS = intPreferencesKey("steps")
        val CFG = floatPreferencesKey("cfg_scale")
        val SEED = longPreferencesKey("seed")
        val SCHEDULER = stringPreferencesKey("scheduler")
        val DENOISE = floatPreferencesKey("denoising_strength")
        val MEMORY_MODE = stringPreferencesKey("memory_mode")
        val PRECISION = stringPreferencesKey("precision")
    }

    val settingsFlow: Flow<GenerationSettings> = context.dataStore.data.map { prefs ->
        GenerationSettings(
            width = prefs[Keys.WIDTH] ?: 512,
            height = prefs[Keys.HEIGHT] ?: 512,
            steps = prefs[Keys.STEPS] ?: 20,
            cfgScale = prefs[Keys.CFG] ?: 7.0f,
            seed = prefs[Keys.SEED],
            scheduler = prefs[Keys.SCHEDULER]?.let { Scheduler.valueOf(it) } ?: Scheduler.EULER_A,
            denoisingStrength = prefs[Keys.DENOISE] ?: 0.75f,
            memoryMode = prefs[Keys.MEMORY_MODE]?.let { MemoryMode.valueOf(it) } ?: MemoryMode.AUTO,
            precision = prefs[Keys.PRECISION]?.let { Precision.valueOf(it) } ?: Precision.FP16
        )
    }

    suspend fun current(): GenerationSettings = settingsFlow.first()

    suspend fun update(settings: GenerationSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIDTH] = settings.width
            prefs[Keys.HEIGHT] = settings.height
            prefs[Keys.STEPS] = settings.steps
            prefs[Keys.CFG] = settings.cfgScale
            settings.seed?.let { prefs[Keys.SEED] = it } ?: prefs.remove(Keys.SEED)
            prefs[Keys.SCHEDULER] = settings.scheduler.name
            prefs[Keys.DENOISE] = settings.denoisingStrength
            prefs[Keys.MEMORY_MODE] = settings.memoryMode.name
            prefs[Keys.PRECISION] = settings.precision.name
        }
    }
}
