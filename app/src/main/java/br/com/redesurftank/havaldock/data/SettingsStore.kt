package br.com.redesurftank.havaldock.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Preferências do app (persistidas localmente). Estados observáveis pelo Compose (tela de Configs).
 *
 * - overlayEnabled: a barra está ligada (deve ser mostrada).
 * - visibilityMode: "always" (sempre visível) ou "auto" (auto-ocultar após inatividade).
 * - autoHideSecs: segundos de inatividade até ocultar (modo "auto").
 * - launchOnBoot: religar a barra quando o carro liga (via BootReceiver).
 */
object SettingsStore {
    const val PREFS = "settings"
    const val KEY_OVERLAY = "overlay_enabled"
    const val KEY_MODE = "visibility_mode"
    const val KEY_SECS = "auto_hide_secs"
    const val KEY_BOOT = "launch_on_boot"
    const val KEY_OVERSCAN_PX = "overscan_test_px"

    const val MODE_ALWAYS = "always"
    const val MODE_AUTO = "auto"
    const val DEFAULT_SECS = 10
    const val MIN_SECS = 3
    const val MAX_SECS = 30

    // Teste de overscan (faixa inferior reservada, em px) — só p/ experimentar com CarPlay/AA.
    const val DEFAULT_OVERSCAN_PX = 150
    const val MIN_OVERSCAN_PX = 0
    const val MAX_OVERSCAN_PX = 400
    const val STEP_OVERSCAN_PX = 25

    private lateinit var appCtx: Context

    val overlayEnabled = mutableStateOf(false)
    val visibilityMode = mutableStateOf(MODE_AUTO)
    val autoHideSecs = mutableIntStateOf(DEFAULT_SECS)
    val launchOnBoot = mutableStateOf(true)
    val overscanPx = mutableIntStateOf(DEFAULT_OVERSCAN_PX)

    fun init(context: Context) {
        appCtx = context.applicationContext
        val p = prefs(appCtx)
        overlayEnabled.value = p.getBoolean(KEY_OVERLAY, false)
        visibilityMode.value = p.getString(KEY_MODE, MODE_AUTO) ?: MODE_AUTO
        autoHideSecs.intValue = p.getInt(KEY_SECS, DEFAULT_SECS)
        launchOnBoot.value = p.getBoolean(KEY_BOOT, true)
        overscanPx.intValue = p.getInt(KEY_OVERSCAN_PX, DEFAULT_OVERSCAN_PX)
    }

    fun setOverlayEnabled(v: Boolean) {
        overlayEnabled.value = v
        prefs(appCtx).edit().putBoolean(KEY_OVERLAY, v).apply()
    }

    fun setVisibilityMode(v: String) {
        visibilityMode.value = v
        prefs(appCtx).edit().putString(KEY_MODE, v).apply()
    }

    fun setAutoHideSecs(v: Int) {
        val c = v.coerceIn(MIN_SECS, MAX_SECS)
        autoHideSecs.intValue = c
        prefs(appCtx).edit().putInt(KEY_SECS, c).apply()
    }

    fun setLaunchOnBoot(v: Boolean) {
        launchOnBoot.value = v
        prefs(appCtx).edit().putBoolean(KEY_BOOT, v).apply()
    }

    fun setOverscanPx(v: Int) {
        val c = v.coerceIn(MIN_OVERSCAN_PX, MAX_OVERSCAN_PX)
        overscanPx.intValue = c
        prefs(appCtx).edit().putInt(KEY_OVERSCAN_PX, c).apply()
    }

    fun isLaunchOnBoot(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BOOT, true)

    fun isOverlayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY, false)

    fun mode(context: Context): String =
        prefs(context).getString(KEY_MODE, MODE_AUTO) ?: MODE_AUTO

    fun secs(context: Context): Int =
        prefs(context).getInt(KEY_SECS, DEFAULT_SECS)

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
