package com.bolnaa.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bolnaa.android.data.models.FlowTone
import com.bolnaa.android.data.models.SttEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flow_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_STT_ENGINE = stringPreferencesKey("stt_engine")
        private val KEY_FLOW_TONE = stringPreferencesKey("flow_tone")
        private val KEY_ENABLE_AI_CLEANUP = booleanPreferencesKey("enable_ai_cleanup")
        private val KEY_AUTO_STOP_SILENCE = booleanPreferencesKey("auto_stop_silence")
        private val KEY_SILENCE_TIMEOUT_MS = intPreferencesKey("silence_timeout_ms")
        private val KEY_BUBBLE_SIZE_DP = intPreferencesKey("bubble_size_dp")
        private val KEY_BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        private val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val KEY_ATTACH_TO_KEYBOARD = booleanPreferencesKey("attach_to_keyboard")
        private val KEY_CUSTOM_VOCABULARY = stringPreferencesKey("custom_vocabulary")
        private val KEY_SERVICE_ACTIVE = booleanPreferencesKey("service_active")
        private val KEY_AUTO_PAUSE_FINANCIAL_APPS = booleanPreferencesKey("auto_pause_financial_apps")
        private val KEY_BUBBLE_POS_X = intPreferencesKey("bubble_pos_x")
        private val KEY_BUBBLE_POS_Y = intPreferencesKey("bubble_pos_y")
        private val KEY_FREE_PLACEMENT = booleanPreferencesKey("free_placement")
        private val KEY_SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        private val KEY_AUTOSTART_CONFIGURED = booleanPreferencesKey("autostart_configured")
    }

    val isSetupCompleted: Flow<Boolean> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_SETUP_COMPLETED] ?: false }

    val isAutostartConfigured: Flow<Boolean> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_AUTOSTART_CONFIGURED] ?: false }

    val isAutoPauseFinancialApps: Flow<Boolean> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_AUTO_PAUSE_FINANCIAL_APPS] ?: true }

    val sttEngine: Flow<SttEngine> = context.dataStore.data
        .catch { handleException(it) }
        .map {
            val name = it[KEY_STT_ENGINE] ?: SttEngine.GROQ.name
            try { SttEngine.valueOf(name) } catch (e: Exception) { SttEngine.LOCAL }
        }

    val flowTone: Flow<FlowTone> = context.dataStore.data
        .catch { handleException(it) }
        .map {
            val name = it[KEY_FLOW_TONE] ?: FlowTone.NATURAL.name
            try { FlowTone.valueOf(name) } catch (e: Exception) { FlowTone.NATURAL }
        }

    val isAiCleanupEnabled: Flow<Boolean> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_ENABLE_AI_CLEANUP] ?: true }

    val isAutoStopSilence: Flow<Boolean> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_AUTO_STOP_SILENCE] ?: true }

    val silenceTimeoutMs: Flow<Int> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_SILENCE_TIMEOUT_MS] ?: 1600 }

    val bubbleSizeDp: Flow<Int> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_BUBBLE_SIZE_DP] ?: 64 }

    val bubbleOpacity: Flow<Float> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_BUBBLE_OPACITY] ?: 0.95f }

    val isHapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_HAPTIC_FEEDBACK] ?: true }

    val isAttachToKeyboardEnabled: Flow<Boolean> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_ATTACH_TO_KEYBOARD] ?: true }

    val isFreePlacementEnabled: Flow<Boolean> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_FREE_PLACEMENT] ?: true }

    val bubblePosX: Flow<Int> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_BUBBLE_POS_X] ?: -1 }

    val bubblePosY: Flow<Int> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_BUBBLE_POS_Y] ?: -1 }

    val customVocabulary: Flow<String> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_CUSTOM_VOCABULARY] ?: "" }

    val isServiceActive: Flow<Boolean> = context.dataStore.data
        .catch { handleException(it) }
        .map { it[KEY_SERVICE_ACTIVE] ?: true }

    suspend fun setBubblePosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[KEY_BUBBLE_POS_X] = x
            it[KEY_BUBBLE_POS_Y] = y
        }
    }

    suspend fun setFreePlacementEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FREE_PLACEMENT] = enabled }
    }

    suspend fun setSttEngine(engine: SttEngine) {
        context.dataStore.edit { it[KEY_STT_ENGINE] = engine.name }
    }

    suspend fun setFlowTone(tone: FlowTone) {
        context.dataStore.edit { it[KEY_FLOW_TONE] = tone.name }
    }

    suspend fun setAiCleanupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ENABLE_AI_CLEANUP] = enabled }
    }

    suspend fun setAutoStopSilence(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_STOP_SILENCE] = enabled }
    }

    suspend fun setSilenceTimeoutMs(timeout: Int) {
        context.dataStore.edit { it[KEY_SILENCE_TIMEOUT_MS] = timeout }
    }

    suspend fun setBubbleSizeDp(sizeDp: Int) {
        context.dataStore.edit { it[KEY_BUBBLE_SIZE_DP] = sizeDp }
    }

    suspend fun setBubbleOpacity(opacity: Float) {
        context.dataStore.edit { it[KEY_BUBBLE_OPACITY] = opacity }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setAttachToKeyboardEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ATTACH_TO_KEYBOARD] = enabled }
    }

    suspend fun setCustomVocabulary(vocab: String) {
        context.dataStore.edit { it[KEY_CUSTOM_VOCABULARY] = vocab }
    }

    suspend fun setServiceActive(active: Boolean) {
        context.dataStore.edit { it[KEY_SERVICE_ACTIVE] = active }
    }

    suspend fun setAutoPauseFinancialApps(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_PAUSE_FINANCIAL_APPS] = enabled }
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_COMPLETED] = completed }
    }

    suspend fun setAutostartConfigured(configured: Boolean) {
        context.dataStore.edit { it[KEY_AUTOSTART_CONFIGURED] = configured }
    }

    private fun handleException(throwable: Throwable) {
        if (throwable is IOException) {
            // Log or fallback
        } else {
            throw throwable
        }
    }
}
