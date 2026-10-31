package com.turbospace.optimizer
import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

// ==================== REPOSITORY ====================
object TurboSpaceRepository {
    private val _selectedGame = MutableStateFlow("NO TARGET SELECTED")
    val selectedGame: StateFlow<String> = _selectedGame
    private val _isMasterOn = MutableStateFlow(false)
    val isMasterOn: StateFlow<Boolean> = _isMasterOn
    var customBackgroundUri: Uri? = null
    var isVideoBackground: Boolean = false

    // Stopwatch and floating statistics live outside the overlay composition so closing/recreating
    // FullHudOverlayFinalNew does not reset elapsed time or enabled floating windows.
    var stopwatchRunning by mutableStateOf(false)
    var stopwatchElapsedMs by mutableStateOf(0L)
    var stopwatchStartedAtMs by mutableStateOf(0L)
    var fpsFloatingEnabled by mutableStateOf(false)
    // The SHOW STATUS floating window displays the full real-time device snapshot.
    var floatingFpsValue by mutableStateOf("FPS: 0 | RAM: 0% | BAT: 0%")
    var powerSaverEnabled by mutableStateOf(false)
    var orientationLockEnabled by mutableStateOf(false)
    // Selected mode used by the pre-launch dashboard before starting the game.
    var preLaunchMode by mutableStateOf("BALANCED")

    fun stopwatchElapsedAt(nowMs: Long = System.currentTimeMillis()): Long =
        if (stopwatchRunning) (nowMs - stopwatchStartedAtMs).coerceAtLeast(0L) else stopwatchElapsedMs.coerceAtLeast(0L)

    fun restoreStopwatch(context: Context) {
        val prefs = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
        stopwatchRunning = prefs.getBoolean("stopwatch_running", false)
        stopwatchElapsedMs = prefs.getLong("stopwatch_elapsed_ms", 0L).coerceAtLeast(0L)
        stopwatchStartedAtMs = prefs.getLong("stopwatch_started_at_ms", 0L).coerceAtLeast(0L)
        if (stopwatchRunning && stopwatchStartedAtMs <= 0L) stopwatchStartedAtMs = System.currentTimeMillis() - stopwatchElapsedMs
    }

    fun persistStopwatch(context: Context) {
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("stopwatch_running", stopwatchRunning)
            .putLong("stopwatch_elapsed_ms", stopwatchElapsedMs.coerceAtLeast(0L))
            .putLong("stopwatch_started_at_ms", stopwatchStartedAtMs.coerceAtLeast(0L))
            .apply()
    }

    fun setFpsFloatingEnabled(context: Context, enabled: Boolean) {
        fpsFloatingEnabled = enabled
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit().putBoolean("fps_floating_enabled", enabled).apply()
    }

    // Persistent active module states - red stays until explicit reset
    val activeModules = MutableStateFlow<Set<String>>(emptySet())
    fun markModuleActive(name: String) { activeModules.value = activeModules.value + name }
    fun resetAllModules() { activeModules.value = emptySet() }

    // === OVERLAY PERSIST FIELDS - FIX FOR UNRESOLVED REFERENCES ===
    var statsOffsetX: Float = 0f
    var statsOffsetY: Float = 0f
    var fpsActivePersist: Boolean = false
    var joyActivePersist: Boolean = false

    // Crosshair toggle + persisted style/color (PART 9C).
    val crosshairActive = MutableStateFlow(false)
    val crosshairStyle = MutableStateFlow(0) // 0=cross, 1=dot, 2=circle
    val crosshairColorIdx = MutableStateFlow(0) // 0=red, 1=blue, 2=cyan, 3=green, 4=white, 5=pink
    var crosshairScale by mutableStateOf(1.0f)

    fun setCrosshairActive(on: Boolean) {
        crosshairActive.value = on
        try {
            prefsContext?.getSharedPreferences("turbospace_prefs", android.content.Context.MODE_PRIVATE)
                ?.edit()?.putBoolean("crosshair_active", on)?.apply()
        } catch (_: Throwable) {}
    }

    fun setCrosshairStyle(v: Int) {
        crosshairStyle.value = v.coerceIn(0, 2)
        persistCrosshair()
    }

    fun setCrosshairColorIdx(v: Int) {
        crosshairColorIdx.value = v.coerceIn(0, 5)
        persistCrosshair()
    }

    fun applyCrosshairScale(v: Float) {
        crosshairScale = v.coerceIn(0.5f, 2.0f)
        persistCrosshair()
    }

    private fun persistCrosshair() {
        try {
            prefsContext?.getSharedPreferences("turbospace_prefs", android.content.Context.MODE_PRIVATE)
                ?.edit()
                ?.putInt("crosshair_style", crosshairStyle.value)
                ?.putInt("crosshair_color_idx", crosshairColorIdx.value)
                ?.putFloat("crosshair_scale", crosshairScale)
                ?.apply()
        } catch (_: Throwable) {}
    }

    fun crosshairColor(idx: Int = crosshairColorIdx.value): Color = when (idx) {
        0 -> Color.Red
        1 -> Color.Blue
        2 -> Color.Cyan
        3 -> Color.Green
        4 -> Color.White
        5 -> Color(0xFFFFC0CB)
        else -> Color.Red
    }

    fun setSelectedGame(pkg: String) { _selectedGame.value = pkg }
    fun setMasterOn(on: Boolean) { _isMasterOn.value = on }

    // Persistent Favorites + command history (latest 50, timestamped).
    private val _favoritePackages = MutableStateFlow<Set<String>>(emptySet())
    val favoritePackages: StateFlow<Set<String>> = _favoritePackages
    private val _recentPackages = MutableStateFlow<List<String>>(emptyList())
    val recentPackages: StateFlow<List<String>> = _recentPackages

    fun loadRecentPackages(context: Context) {
        val stored = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
            .getString("recent_game_packages", "") ?: ""
        _recentPackages.value = stored.split("|").filter { it.isNotBlank() }.take(5)
    }

    fun markRecentPlayed(context: Context, packageName: String) {
        val updated = (listOf(packageName) + _recentPackages.value.filter { it != packageName }).take(5)
        _recentPackages.value = updated
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
            .putString("recent_game_packages", updated.joinToString("|")).apply()
    }

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory

    data class CompileState(
        val pkg: String = "",
        val mode: String = "",
        val inProgress: Boolean = false,
        val lastResult: String = "",
        val startedAtMs: Long = 0L
    )

    private val _compileState = MutableStateFlow(CompileState())
    val compileState: StateFlow<CompileState> = _compileState

    fun setCompileInProgress(context: Context, pkg: String, mode: String) {
        val startedAt = System.currentTimeMillis()
        _compileState.value = CompileState(pkg, mode, true, "", startedAt)
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
            .putString("compile_pkg", pkg)
            .putString("compile_mode", mode)
            .putBoolean("compile_in_progress", true)
            .putLong("compile_started_at", startedAt)
            .apply()
    }

    fun setCompileResult(context: Context, success: Boolean) {
        val result = if (success) "SUCCESS" else "ERROR"
        _compileState.value = _compileState.value.copy(
            inProgress = false,
            lastResult = result
        )
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("compile_in_progress", false)
            .putString("compile_last_result", result)
            .apply()
    }

    fun clearCompileResult(context: Context) {
        _compileState.value = _compileState.value.copy(lastResult = "")
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
            .edit().remove("compile_last_result").apply()
    }

    fun restoreCompileState(context: Context) {
        val prefs = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("compile_in_progress", false)) {
            _compileState.value = CompileState(
                pkg = prefs.getString("compile_pkg", "") ?: "",
                mode = prefs.getString("compile_mode", "") ?: "",
                inProgress = true,
                startedAtMs = prefs.getLong("compile_started_at", 0L)
            )
        }
    }

    fun loadFavoritePackages(context: Context) {
        val stored = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
            .getStringSet("favorite_game_packages", emptySet())?.toSet() ?: emptySet()
        _favoritePackages.value = stored
    }

    fun toggleFavorite(context: Context, packageName: String) {
        val updated = _favoritePackages.value.toMutableSet().apply {
            if (contains(packageName)) remove(packageName) else add(packageName)
        }.toSet()
        _favoritePackages.value = updated
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
            .putStringSet("favorite_game_packages", updated).apply()
    }

    fun loadLogs(context: Context) {
        val prefs = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("command_history_json", "[]") ?: "[]"
        val logs = try {
            val json = org.json.JSONArray(raw)
            (0 until json.length()).mapNotNull { json.optString(it).takeIf(String::isNotBlank) }.take(50)
        } catch (_: Throwable) { emptyList() }
        _commandHistory.value = logs
    }

    fun saveLog(context: Context, message: String) {
        val safeMessage = message.trim().take(300)
        if (safeMessage.isEmpty()) return
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val entry = "[$timestamp] $safeMessage"
        val updated = (listOf(entry) + _commandHistory.value).take(50)
        _commandHistory.value = updated
        val json = org.json.JSONArray()
        updated.forEach(json::put)
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
            .putString("command_history_json", json.toString()).apply()
    }

    // SharedPreferences persistence
    private var prefsContext: android.content.Context? = null

    fun savePrefs(context: android.content.Context) {
        prefsContext = context.applicationContext
        val prefs = context.getSharedPreferences("turbospace_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("selected_game", _selectedGame.value)
            .putBoolean("master_on", _isMasterOn.value)
            .putStringSet("active_modules", activeModules.value)
            .putBoolean("crosshair_active", crosshairActive.value) // FIX SF-02: persist crosshair state
            .putInt("crosshair_style", crosshairStyle.value)
            .putInt("crosshair_color_idx", crosshairColorIdx.value)
            .putFloat("crosshair_scale", crosshairScale)
            .putBoolean("stopwatch_running", stopwatchRunning)
            .putLong("stopwatch_elapsed_ms", stopwatchElapsedMs.coerceAtLeast(0L))
            .putLong("stopwatch_started_at_ms", stopwatchStartedAtMs.coerceAtLeast(0L))
            .putBoolean("fps_floating_enabled", fpsFloatingEnabled)
            .putBoolean("power_saver_active", powerSaverEnabled)
            .putBoolean("orientation_lock_active", orientationLockEnabled)
            .apply()
    }
    fun loadPrefs(context: android.content.Context) {
        prefsContext = context.applicationContext
        val prefs = context.getSharedPreferences("turbospace_prefs", android.content.Context.MODE_PRIVATE)
        _selectedGame.value = prefs.getString("selected_game", "NO TARGET SELECTED") ?: "NO TARGET SELECTED"
        _isMasterOn.value = prefs.getBoolean("master_on", false)
        activeModules.value = prefs.getStringSet("active_modules", emptySet()) ?: emptySet()
        crosshairActive.value = prefs.getBoolean("crosshair_active", false) // FIX SF-02: restore crosshair state
        crosshairStyle.value = prefs.getInt("crosshair_style", 0).coerceIn(0, 2)
        crosshairColorIdx.value = prefs.getInt("crosshair_color_idx", 0).coerceIn(0, 5)
        crosshairScale = prefs.getFloat("crosshair_scale", 1.0f).coerceIn(0.5f, 2.0f)
        stopwatchRunning = prefs.getBoolean("stopwatch_running", stopwatchRunning)
        stopwatchElapsedMs = prefs.getLong("stopwatch_elapsed_ms", stopwatchElapsedMs).coerceAtLeast(0L)
        stopwatchStartedAtMs = prefs.getLong("stopwatch_started_at_ms", stopwatchStartedAtMs).coerceAtLeast(0L)
        if (stopwatchRunning && stopwatchStartedAtMs <= 0L) stopwatchStartedAtMs = System.currentTimeMillis() - stopwatchElapsedMs
        fpsFloatingEnabled = prefs.getBoolean("fps_floating_enabled", fpsFloatingEnabled)
        powerSaverEnabled = prefs.getBoolean("power_saver_active", powerSaverEnabled)
        orientationLockEnabled = prefs.getBoolean("orientation_lock_active", orientationLockEnabled)
    }
}

// ==================== SHIZUKU ENGINE - ZERO-ERROR LAWS ====================
object TurboSpaceManager {

    data class CommandResult(val success: Boolean, val output: String, val exitCode: Int, val error: String)

    fun isShizukuAvailableAndGranted(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.getVersion() < 11) return false
            Shizuku.checkSelfPermission() ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(1000)
            } else {
                Shizuku.requestPermission(1000)
            }
        } catch (_: Throwable) {}
    }

    // [STRICT EXECUTION MATRIX] + [EXIT-CODE INTERLOCK] - every shell macro in high-resiliency try-catch, waitFor, dispatch ERROR if exit !=0
    fun executeCommandDetailedNoCtx(cmd: String): CommandResult {
        return try {
            if (!isShizukuAvailableAndGranted()) {
                return CommandResult(false, "", -1, "Shizuku not granted")
            }
            var resultOutput = ""
            var resultError = ""
            var exitCode = -1
            val latch = CountDownLatch(1)
            try {
                // FIX: Ensure Shizuku permission reaches cmd/pm - no fallback to app context exec
                val process: Process = try {
                    Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                } catch (t: Throwable) {
                    return CommandResult(false, "", -1,
                        "Shizuku.newProcess failed: ${t.message}")
                }
                val outputReader = process.inputStream.bufferedReader()
                val errorReader = process.errorStream.bufferedReader()
                var output = ""
                var error = ""
                val outputThread = Thread { output = outputReader.readText() }
                val errorThread = Thread { error = errorReader.readText() }
                outputThread.start()
                errorThread.start()
                exitCode = process.waitFor() // [EXIT-CODE INTERLOCK] block loop awaiting response
                outputThread.join(2000)
                errorThread.join(2000)
                resultOutput = output
                resultError = error
                latch.countDown()
            } catch (e: Throwable) {
                resultError = e.message ?: "exception"
                latch.countDown()
            }
            latch.await(5, TimeUnit.SECONDS)
            val success = exitCode == 0
            CommandResult(success, resultOutput, exitCode, resultError)
        } catch (e: Exception) {
            CommandResult(false, "", -1, e.message ?: "fatal")
        }
    }

    suspend fun executeCommandDetailed(gamePackage: String, template: String): CommandResult {
        return withContext(Dispatchers.IO) {
            try {
                val pkg = gamePackage // \$pkg dynamic runtime anchor
                // FIX: Clean replacement for all variants \$pkg, \$pkg, \$pkg, <package_name> to ensure Shizuku gets correct package
                val cmd = template
                    .replace("<package_name>", pkg)
                    .replace("\$pkg", pkg)
                    .replace("\$pkg", pkg)
                    .replace("\$pkg", pkg)
                    .replace("\$pkg", pkg)
                // Log for debugging - ensure Shizuku permission reaches cmd/pm
                android.util.Log.d("TurboSpace", "Executing via Shizuku: $cmd for \$pkg")
                executeCommandDetailedNoCtx(cmd)
            } catch (e: Throwable) {
                CommandResult(false, "", -1, e.message ?: "error")
            }
        }
    }

    fun getInstalledGames(context: Context): List<ApplicationInfo> {
        return try {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA).filter {
                val isThird = (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                val isUpdatedSystem = (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                isThird && !isUpdatedSystem
            }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        } catch (_: Throwable) { emptyList() }
    }

    fun getCpuTemp(): String {
        return try {
            val zones = (0..19).mapNotNull { i ->
                val typeFile = java.io.File("/sys/class/thermal/thermal_zone$i/type")
                val tempFile = java.io.File("/sys/class/thermal/thermal_zone$i/temp")
                if (typeFile.exists() && tempFile.exists()) {
                    val type = typeFile.readText().trim().lowercase()
                    val temp = tempFile.readText().trim().toIntOrNull()
                    if (temp != null && (type.contains("cpu") || type.contains("soc"))) temp / 1000 else null
                } else null
            }
            if (zones.isNotEmpty()) "${zones.maxOrNull()}°C"
            else {
                val fallback = java.io.File("/sys/class/thermal/thermal_zone0/temp")
                if (fallback.exists()) "${fallback.readText().trim().toInt() / 1000}°C"
                else "N/A"
            }
        } catch (_: Throwable) { "N/A" }
    }

    suspend fun getRealFpsViaShizuku(gamePackage: String): String = withContext(Dispatchers.IO) {
        if (!gamePackage.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) return@withContext "0"
        try {
            // Reset counter
            executeCommandDetailedNoCtx("dumpsys gfxinfo $gamePackage reset")
            val t0 = System.currentTimeMillis()
            Thread.sleep(1000)

            // Read framestats
            val result = executeCommandDetailedNoCtx("dumpsys gfxinfo $gamePackage")
            val t1 = System.currentTimeMillis()
            val elapsedSec = ((t1 - t0) / 1000.0).coerceAtLeast(0.5)

            if (!result.success || result.output.isBlank()) return@withContext "0"

            // Parse frames rendered - try multiple patterns for different Android versions
            val framesPatterns = listOf(
                Regex("(?i)Total frames rendered[\\s:]+(\\d+)"),
                Regex("(?i)Total frames[\\s:]+(\\d+)"),
                Regex("(?i)Frames rendered[\\s:]+(\\d+)"),
                Regex("(?i)Frame count[\\s:]+(\\d+)")
            )
            var frames: Int? = null
            for (pattern in framesPatterns) {
                frames = pattern.find(result.output)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (frames != null && frames > 0) break
            }

            if (frames != null && frames > 0) {
                val fps = frames / elapsedSec
                if (fps.isFinite() && fps in 1.0..1000.0) {
                    return@withContext String.format(java.util.Locale.US, "%.1f", fps)
                }
            }
            "0"
        } catch (_: Throwable) {
            "0"
        }
    }

    suspend fun getLatencyViaShizuku(): String {
        return try {
            val result = executeCommandDetailedNoCtx("dumpsys gfxinfo | grep -i -E 'jank|latency' -A 2 | head -n 10")
            if (result.success && result.output.isNotBlank()) {
                val msRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*ms", RegexOption.IGNORE_CASE)
                val match = msRegex.find(result.output)
                if (match != null) return match.groupValues[1] + "ms"
            }
            "N/A"
        } catch (_: Throwable) { "N/A" }
    }

    // Battery drain rate in mA (live from BatteryManager)
    fun getRamUsagePercent(context: android.content.Context): Int {
        return try {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            val info = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(info)
            val used = info.totalMem - info.availMem
            ((used.toFloat() / info.totalMem.toFloat()) * 100).toInt().coerceIn(0, 100)
        } catch (_: Throwable) { 0 }
    }

    fun getBatteryDrainMa(context: android.content.Context): Int {
        return try {
            val bm = context.getSystemService(android.os.BatteryManager::class.java)
            val raw = bm?.getIntProperty(
                android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
            ) ?: return 0
            if (raw == Int.MIN_VALUE || raw == 0) return 0
            kotlin.math.abs(raw / 1000).coerceIn(0, 10000)
        } catch (_: Throwable) { 0 }
    }

    fun getBatteryPercent(context: android.content.Context): Int {
        return try {
            val bm = context.getSystemService(android.os.BatteryManager::class.java)
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        } catch (_: Throwable) { 0 }
    }

    // FOREGROUND DYNAMIC IDENTIFICATION LOGIC - safest Kotlin polling via UsageStatsManager + Shizuku fallback
    fun getForegroundPackageViaShizuku(): String {
        return try {
            val result = executeCommandDetailedNoCtx("dumpsys window windows | grep mCurrentFocus | head -n 1")
            if (result.success && result.output.isNotBlank()) {
                val regex = Regex("([a-zA-Z0-9._]+/\\.)")
                // Parse com.package/com.package.Activity
                val pkgRegex = Regex("([a-z0-9.]+)/[a-z0-9.]+", RegexOption.IGNORE_CASE)
                val match = pkgRegex.find(result.output)
                if (match != null) return match.groupValues[1]
            }
            ""
        } catch (_: Throwable) { "" }
    }

}


/**
 * Runs the shared Reset All shell command sequence.
 * FPS / refresh-rate settings are intentionally excluded from this function.
 */
@Suppress("UNUSED_PARAMETER")
fun performFullReset(context: Context, pkg: String): Boolean {
    // A package name is inserted into shell commands, so reject unexpected input.
    val targetPkg = pkg.trim()
    val hasTarget = targetPkg != "NO TARGET SELECTED"
    if (hasTarget && !targetPkg.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) {
        return false
    }
    if (!TurboSpaceManager.isShizukuAvailableAndGranted()) return false

    val commands = mutableListOf(
        "cmd power set-adaptive-power-saver-enabled false",
        "settings put system accelerometer_rotation 1",
        "settings put global wifi_scan_always_enabled 1"
    )
    if (hasTarget) {
        commands += listOf(
            "cmd game mode set standard $targetPkg",
            "cmd game downscale disable $targetPkg",
            "cmd package compile --reset $targetPkg",
            "cmd deviceidle whitelist -$targetPkg"
        )
    }

    var allSucceeded = true
    for (command in commands) {
        val result = try {
            TurboSpaceManager.executeCommandDetailedNoCtx(command)
        } catch (_: Throwable) {
            null
        }
        if (result == null || !result.success || result.exitCode != 0) {
            allSucceeded = false
        }
    }
    return allSucceeded
}

/** Applies the in-app reset only after every Reset All shell command succeeds. */
fun applyFullResetUiState(context: Context) {
    TurboSpaceRepository.resetAllModules()
    TurboSpaceRepository.setCrosshairActive(false)
    TurboSpaceRepository.setFpsFloatingEnabled(context, false)
    TurboSpaceRepository.powerSaverEnabled = false
    TurboSpaceRepository.orientationLockEnabled = false

    // Remove only the requested reset preferences. Never remove or overwrite FPS keys.
    context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
        .remove("crosshair_style")
        .remove("crosshair_color_idx")
        .remove("power_saver_active")
        .remove("orientation_lock_active")
        .apply()
}

// ==================== CYBER MODULES - VECTOR ART CONFIGURATION MATRIX ====================

// ==================== NEW CYBER MODULES - 10 CMDS PER SPEC - OLD CMDS REMOVED ====================
object CyberDesign {
    val PureWhite = MonetColors.Primary
    val MutedGray = MonetColors.Muted
    val SolidRed = MonetColors.Primary
    val DeepDark85 = MonetColors.Inactive.copy(alpha = 0.96f)
    val DeepDark = MonetColors.Background
    val GreenLightning = MonetColors.Secondary
}

// Material You palette used by the dashboard, booster page and HUD.
object MonetColors {
    val Background = Color(0xFF1C1B1F)
    val Inactive = Color(0xFF2B2930)
    val Primary = Color(0xFFF2D7EE)
    val OnPrimary = Color(0xFF1D192B)
    val Secondary = Color(0xFFD0BCFF)
    val Muted = Color(0xFFB9B2C2)
    val Outline = Muted
}

/** Vector icons are drawn with Compose Canvas; no emoji or icon image assets are used. */
@Composable
fun MonetCanvasIcon(
    kind: String,
    modifier: Modifier = Modifier,
    tint: Color = MonetColors.Secondary
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val m = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val sw = (m * 0.075f).coerceAtLeast(1.5f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind.lowercase()) {
            "fps" -> {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = tint.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = h * 0.34f
                    typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
                }
                drawContext.canvas.nativeCanvas.drawText("FPS", cx, cy + paint.textSize * 0.36f, paint)
                drawRoundRect(tint.copy(alpha = 0.42f), Offset(w * 0.08f, h * 0.18f), Size(w * 0.84f, h * 0.64f), androidx.compose.ui.geometry.CornerRadius(m * 0.08f), style = Stroke(sw * 0.65f))
            }
            "gear" -> {
                val r = m * 0.27f
                for (i in 0 until 8) {
                    val a = i * (Math.PI / 4.0)
                    val inner = r * 0.92f
                    val outer = r * 1.55f
                    drawLine(tint, Offset(cx + kotlin.math.cos(a).toFloat() * inner, cy + kotlin.math.sin(a).toFloat() * inner), Offset(cx + kotlin.math.cos(a).toFloat() * outer, cy + kotlin.math.sin(a).toFloat() * outer), sw * 2.1f, cap = StrokeCap.Square)
                }
                drawCircle(tint, r * 1.15f, Offset(cx, cy), style = stroke)
                drawCircle(tint, r * 0.38f, Offset(cx, cy), style = stroke)
            }
            "battery" -> {
                drawRoundRect(tint, Offset(w * 0.08f, h * 0.23f), Size(w * 0.78f, h * 0.54f), androidx.compose.ui.geometry.CornerRadius(h * 0.08f), style = stroke)
                drawRoundRect(tint, Offset(w * 0.88f, h * 0.39f), Size(w * 0.08f, h * 0.22f), androidx.compose.ui.geometry.CornerRadius(h * 0.03f))
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.32f), Size(w * 0.54f, h * 0.36f), androidx.compose.ui.geometry.CornerRadius(h * 0.04f))
            }
            "wifi" -> {
                val path = Path().apply {
                    moveTo(w * 0.13f, h * 0.38f)
                    cubicTo(w * 0.35f, h * 0.14f, w * 0.65f, h * 0.14f, w * 0.87f, h * 0.38f)
                    moveTo(w * 0.28f, h * 0.54f)
                    cubicTo(w * 0.42f, h * 0.40f, w * 0.58f, h * 0.40f, w * 0.72f, h * 0.54f)
                    moveTo(w * 0.43f, h * 0.69f)
                    cubicTo(w * 0.47f, h * 0.65f, w * 0.53f, h * 0.65f, w * 0.57f, h * 0.69f)
                }
                drawPath(path, tint, style = stroke)
                drawCircle(tint, sw * 0.8f, Offset(cx, h * 0.82f))
                drawPath(Path().apply { moveTo(w*0.18f,h*0.13f); lineTo(w*0.82f,h*0.87f) }, MonetColors.Secondary.copy(alpha=0.55f), style = Stroke(sw * 0.55f, cap = StrokeCap.Round))
            }
            "orientation_lock" -> {
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.42f), Size(w * 0.56f, h * 0.45f), androidx.compose.ui.geometry.CornerRadius(m * 0.07f), style = stroke)
                drawArc(tint, 180f, 180f, false, Offset(w * 0.32f, h * 0.10f), Size(w * 0.36f, h * 0.58f), style = stroke)
                drawCircle(tint, m * 0.035f, Offset(cx, h * 0.60f))
                drawLine(tint, Offset(cx, h * 0.61f), Offset(cx, h * 0.72f), sw, cap = StrokeCap.Round)
            }
            "rocket" -> {
                val path = Path().apply {
                    moveTo(cx, h * 0.10f)
                    cubicTo(w * 0.72f, h * 0.22f, w * 0.71f, h * 0.53f, w * 0.62f, h * 0.65f)
                    lineTo(w * 0.38f, h * 0.65f)
                    cubicTo(w * 0.29f, h * 0.53f, w * 0.28f, h * 0.22f, cx, h * 0.10f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawCircle(tint, m * 0.07f, Offset(cx, h * 0.37f), style = stroke)
                drawPath(Path().apply { moveTo(w*0.38f,h*0.56f); lineTo(w*0.19f,h*0.72f); lineTo(w*0.34f,h*0.73f); moveTo(w*0.62f,h*0.56f); lineTo(w*0.81f,h*0.72f); lineTo(w*0.66f,h*0.73f); moveTo(w*0.43f,h*0.69f); lineTo(w*0.39f,h*0.88f); lineTo(w*0.5f,h*0.81f); lineTo(w*0.61f,h*0.88f); lineTo(w*0.57f,h*0.69f) }, tint, style = stroke)
            }
            "tools" -> {
                drawLine(tint, Offset(w*0.20f,h*0.80f), Offset(w*0.68f,h*0.32f), sw*1.6f, cap=StrokeCap.Round)
                drawCircle(tint, m*0.12f, Offset(w*0.19f,h*0.81f), style=stroke)
                drawLine(tint, Offset(w*0.41f,h*0.18f), Offset(w*0.81f,h*0.58f), sw*1.6f, cap=StrokeCap.Round)
                drawArc(tint, 215f, 240f, false, Offset(w*0.58f,h*0.08f), Size(w*0.34f,h*0.34f), style=stroke)
                drawCircle(tint, m*0.10f, Offset(w*0.81f,h*0.59f), style=stroke)
            }
            "broom" -> {
                drawLine(tint, Offset(w*0.28f,h*0.17f), Offset(w*0.65f,h*0.63f), sw*1.8f, cap=StrokeCap.Round)
                val path = Path().apply { moveTo(w*0.52f,h*0.58f); lineTo(w*0.82f,h*0.82f); lineTo(w*0.72f,h*0.93f); lineTo(w*0.34f,h*0.69f); close() }
                drawPath(path, tint, style=stroke)
                for (i in 0..3) drawLine(tint, Offset(w*(0.42f+i*0.08f),h*0.74f), Offset(w*(0.43f+i*0.08f),h*0.86f), sw*0.65f)
            }
            "bell_slash" -> {
                val bell = Path().apply {
                    moveTo(w * 0.28f, h * 0.68f)
                    cubicTo(w * 0.34f, h * 0.60f, w * 0.32f, h * 0.47f, w * 0.35f, h * 0.35f)
                    cubicTo(w * 0.38f, h * 0.21f, w * 0.62f, h * 0.21f, w * 0.65f, h * 0.35f)
                    cubicTo(w * 0.68f, h * 0.47f, w * 0.66f, h * 0.60f, w * 0.72f, h * 0.68f)
                    lineTo(w * 0.28f, h * 0.68f)
                }
                drawPath(bell, tint, style = stroke)
                drawArc(tint, 20f, 140f, false, Offset(w * 0.39f, h * 0.63f), Size(w * 0.22f, h * 0.20f), style = stroke)
                drawCircle(tint, sw * 0.45f, Offset(cx, h * 0.84f))
                drawLine(tint, Offset(w * 0.18f, h * 0.16f), Offset(w * 0.82f, h * 0.84f), sw * 1.15f, cap = StrokeCap.Round)
            }
            "sun" -> {
                drawCircle(tint, m * 0.20f, Offset(cx, cy), style = stroke)
                for (i in 0 until 8) {
                    val a = i * (Math.PI / 4.0)
                    val r1 = m * 0.29f
                    val r2 = m * 0.42f
                    drawLine(tint, Offset(cx + kotlin.math.cos(a).toFloat() * r1, cy + kotlin.math.sin(a).toFloat() * r1), Offset(cx + kotlin.math.cos(a).toFloat() * r2, cy + kotlin.math.sin(a).toFloat() * r2), sw, cap = StrokeCap.Round)
                }
            }
            "volume" -> {
                val speaker = Path().apply {
                    moveTo(w * 0.12f, h * 0.40f)
                    lineTo(w * 0.30f, h * 0.40f)
                    lineTo(w * 0.52f, h * 0.22f)
                    lineTo(w * 0.52f, h * 0.78f)
                    lineTo(w * 0.30f, h * 0.60f)
                    lineTo(w * 0.12f, h * 0.60f)
                    close()
                }
                drawPath(speaker, tint, style = stroke)
                drawArc(tint, -50f, 100f, false, Offset(w * 0.43f, h * 0.32f), Size(w * 0.28f, h * 0.36f), style = stroke)
                drawArc(tint, -50f, 100f, false, Offset(w * 0.40f, h * 0.20f), Size(w * 0.48f, h * 0.60f), style = stroke)
            }
            "timer" -> {
                drawCircle(tint, m * 0.32f, Offset(cx, h * 0.56f), style = stroke)
                drawLine(tint, Offset(cx, h * 0.13f), Offset(cx, h * 0.22f), sw * 1.25f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.39f, h * 0.12f), Offset(w * 0.61f, h * 0.12f), sw * 1.25f, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx, h * 0.56f), Offset(cx, h * 0.37f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx, h * 0.56f), Offset(w * 0.65f, h * 0.64f), sw, cap = StrokeCap.Round)
            }
            "signal" -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.88f), Offset(w * 0.50f, h * 0.24f), sw * 1.2f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.32f, h * 0.88f), Offset(w * 0.68f, h * 0.88f), sw * 1.2f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.38f, h * 0.72f), Offset(w * 0.38f, h * 0.58f), sw * 1.4f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.62f, h * 0.72f), Offset(w * 0.62f, h * 0.58f), sw * 1.4f, cap = StrokeCap.Round)
                drawArc(tint, 215f, 110f, false, Offset(w * 0.17f, h * 0.22f), Size(w * 0.66f, h * 0.66f), style = stroke)
                drawArc(tint, 225f, 90f, false, Offset(w * 0.28f, h * 0.34f), Size(w * 0.44f, h * 0.44f), style = stroke)
                drawCircle(tint, sw * 0.85f, Offset(w * 0.50f, h * 0.18f))
            }
            "lightning" -> {
                val path = Path().apply {
                    moveTo(w * 0.57f, h * 0.06f)
                    lineTo(w * 0.23f, h * 0.54f)
                    lineTo(w * 0.46f, h * 0.54f)
                    lineTo(w * 0.39f, h * 0.94f)
                    lineTo(w * 0.79f, h * 0.39f)
                    lineTo(w * 0.55f, h * 0.39f)
                    close()
                }
                drawPath(path, tint)
            }
            "game" -> {
                drawCircle(tint.copy(alpha=0.22f), m*0.42f, Offset(cx,cy))
                drawCircle(tint, m*0.35f, Offset(cx,cy), style=stroke)
                drawCircle(MonetColors.OnPrimary, m*0.20f, Offset(cx,cy), style=stroke)
                drawPath(Path().apply { moveTo(cx, h*0.24f); cubicTo(w*0.20f,h*0.46f,w*0.78f,h*0.54f,cx,h*0.76f) }, tint, style=stroke)
                drawCircle(tint, sw*1.2f, Offset(w*0.71f,h*0.32f))
            }
            "brush" -> {
                drawLine(tint, Offset(w * 0.23f, h * 0.76f), Offset(w * 0.67f, h * 0.32f), sw * 2.0f, cap = StrokeCap.Round)
                drawRoundRect(tint, Offset(w * 0.50f, h * 0.08f), Size(w * 0.34f, h * 0.28f), androidx.compose.ui.geometry.CornerRadius(m * 0.06f), style = stroke)
                drawPath(Path().apply { moveTo(w * 0.14f, h * 0.82f); cubicTo(w * 0.23f, h * 0.70f, w * 0.32f, h * 0.73f, w * 0.34f, h * 0.84f); lineTo(w * 0.14f, h * 0.88f); close() }, tint, style = stroke)
            }
            "crosshair" -> {
                drawCircle(tint, m * 0.29f, Offset(cx, cy), style = stroke)
                drawCircle(tint.copy(alpha = 0.7f), m * 0.07f, Offset(cx, cy))
                drawLine(tint, Offset(cx, h * 0.08f), Offset(cx, h * 0.34f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx, h * 0.66f), Offset(cx, h * 0.92f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.08f, cy), Offset(w * 0.34f, cy), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, cy), Offset(w * 0.92f, cy), sw, cap = StrokeCap.Round)
            }
            "shield" -> {
                val shield = Path().apply { moveTo(cx, h * 0.10f); lineTo(w * 0.82f, h * 0.24f); lineTo(w * 0.76f, h * 0.62f); cubicTo(w * 0.70f, h * 0.78f, w * 0.56f, h * 0.88f, cx, h * 0.94f); cubicTo(w * 0.44f, h * 0.88f, w * 0.30f, h * 0.78f, w * 0.24f, h * 0.62f); lineTo(w * 0.18f, h * 0.24f); close() }
                drawPath(shield, tint, style = stroke)
                drawLine(tint, Offset(w * 0.34f, h * 0.51f), Offset(w * 0.46f, h * 0.63f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.46f, h * 0.63f), Offset(w * 0.68f, h * 0.36f), sw, cap = StrokeCap.Round)
            }
            "reset" -> {
                drawArc(tint, 45f, 285f, false, Offset(w * 0.18f, h * 0.18f), Size(w * 0.64f, h * 0.64f), style = stroke)
                drawPath(Path().apply { moveTo(w * 0.67f, h * 0.12f); lineTo(w * 0.84f, h * 0.15f); lineTo(w * 0.75f, h * 0.30f) }, tint, style = stroke)
            }
            "game_sword" -> {
                drawRoundRect(tint, Offset(w * 0.10f, h * 0.36f), Size(w * 0.80f, h * 0.40f), androidx.compose.ui.geometry.CornerRadius(m * 0.16f), style = stroke)
                drawLine(tint, Offset(w * 0.24f, h * 0.47f), Offset(w * 0.24f, h * 0.65f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.15f, h * 0.56f), Offset(w * 0.33f, h * 0.56f), sw, cap = StrokeCap.Round)
                drawCircle(tint, m * 0.045f, Offset(w * 0.70f, h * 0.50f))
                drawCircle(tint, m * 0.045f, Offset(w * 0.79f, h * 0.63f))
                drawLine(tint, Offset(w * 0.40f, h * 0.28f), Offset(w * 0.77f, h * 0.05f), sw * 1.25f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.52f, h * 0.19f), Offset(w * 0.63f, h * 0.36f), sw * 1.25f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.36f, h * 0.25f), Offset(w * 0.47f, h * 0.43f), sw * 1.25f, cap = StrokeCap.Round)
            }
            "home" -> {
                val roof = Path().apply { moveTo(w*0.12f,h*0.46f); lineTo(cx,h*0.12f); lineTo(w*0.88f,h*0.46f) }
                drawPath(roof, tint, style=stroke)
                drawRoundRect(tint, Offset(w*0.23f,h*0.42f), Size(w*0.54f,h*0.46f), androidx.compose.ui.geometry.CornerRadius(m*0.04f), style=stroke)
                drawRoundRect(tint, Offset(w*0.43f,h*0.62f), Size(w*0.16f,h*0.26f), androidx.compose.ui.geometry.CornerRadius(m*0.02f), style=stroke)
            }
            "library" -> {
                drawRoundRect(tint, Offset(w*0.14f,h*0.15f), Size(w*0.72f,h*0.70f), androidx.compose.ui.geometry.CornerRadius(m*0.06f), style=stroke)
                drawLine(tint, Offset(w*0.34f,h*0.17f), Offset(w*0.34f,h*0.83f), sw, cap=StrokeCap.Round)
                drawLine(tint, Offset(w*0.46f,h*0.36f), Offset(w*0.73f,h*0.36f), sw, cap=StrokeCap.Round)
                drawLine(tint, Offset(w*0.46f,h*0.52f), Offset(w*0.70f,h*0.52f), sw, cap=StrokeCap.Round)
                drawLine(tint, Offset(w*0.46f,h*0.68f), Offset(w*0.67f,h*0.68f), sw, cap=StrokeCap.Round)
            }
            "booster" -> {
                val bolt = Path().apply { moveTo(w*0.56f,h*0.08f); lineTo(w*0.25f,h*0.53f); lineTo(w*0.47f,h*0.53f); lineTo(w*0.38f,h*0.92f); lineTo(w*0.78f,h*0.39f); lineTo(w*0.55f,h*0.39f); close() }
                drawPath(bolt, tint, style=stroke)
            }
            "settings" -> {
                val r = m*0.28f
                drawCircle(tint, r, Offset(cx,cy), style=stroke)
                drawCircle(tint, r*0.35f, Offset(cx,cy), style=stroke)
                for (i in 0 until 8) {
                    val a = i * (Math.PI / 4.0)
                    drawLine(tint, Offset(cx + kotlin.math.cos(a).toFloat()*r*1.10f, cy + kotlin.math.sin(a).toFloat()*r*1.10f), Offset(cx + kotlin.math.cos(a).toFloat()*r*1.55f, cy + kotlin.math.sin(a).toFloat()*r*1.55f), sw*1.35f, cap=StrokeCap.Round)
                }
            }
            "refresh" -> {
                drawArc(tint, 40f, 285f, false, Offset(w*0.17f,h*0.17f), Size(w*0.66f,h*0.66f), style=stroke)
                drawPath(Path().apply { moveTo(w*0.64f,h*0.12f); lineTo(w*0.84f,h*0.14f); lineTo(w*0.79f,h*0.34f) }, tint, style=stroke)
            }
            "star" -> {
                val path = Path()
                for (i in 0 until 10) {
                    val angle = (-Math.PI / 2.0) + i * Math.PI / 5.0
                    val r = if (i % 2 == 0) m*0.42f else m*0.19f
                    val x = cx + kotlin.math.cos(angle).toFloat()*r
                    val y = cy + kotlin.math.sin(angle).toFloat()*r
                    if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                path.close()
                if (tint.alpha > 0.9f) drawPath(path, tint) else drawPath(path, tint, style=stroke)
            }
            "ram" -> {
                drawRoundRect(tint, Offset(w*0.10f,h*0.25f), Size(w*0.80f,h*0.50f), androidx.compose.ui.geometry.CornerRadius(m*0.05f), style=stroke)
                for (i in 0..3) {
                    val x = w*(0.24f+i*0.17f)
                    drawLine(tint, Offset(x,h*0.10f), Offset(x,h*0.25f), sw, cap=StrokeCap.Round)
                    drawLine(tint, Offset(x,h*0.75f), Offset(x,h*0.90f), sw, cap=StrokeCap.Round)
                }
                drawLine(tint, Offset(w*0.25f,h*0.50f), Offset(w*0.75f,h*0.50f), sw, cap=StrokeCap.Round)
            }
            "cache" -> {
                drawRoundRect(tint, Offset(w*0.16f,h*0.12f), Size(w*0.68f,h*0.76f), androidx.compose.ui.geometry.CornerRadius(m*0.06f), style=stroke)
                drawLine(tint, Offset(w*0.30f,h*0.34f), Offset(w*0.70f,h*0.34f), sw, cap=StrokeCap.Round)
                drawLine(tint, Offset(w*0.30f,h*0.50f), Offset(w*0.62f,h*0.50f), sw, cap=StrokeCap.Round)
                drawLine(tint, Offset(w*0.30f,h*0.66f), Offset(w*0.56f,h*0.66f), sw, cap=StrokeCap.Round)
            }
            "chevron" -> {
                drawLine(tint, Offset(w * 0.35f, h * 0.25f), Offset(w * 0.65f, h * 0.5f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.65f, h * 0.5f), Offset(w * 0.35f, h * 0.75f), sw, cap = StrokeCap.Round)
            }
            "snowflake" -> {
                for (i in 0 until 6) {
                    val a = i * (Math.PI / 3.0)
                    val dx = kotlin.math.cos(a).toFloat()
                    val dy = kotlin.math.sin(a).toFloat()
                    drawLine(tint, Offset(cx, cy), Offset(cx + dx * m * 0.4f, cy + dy * m * 0.4f), sw * 1.5f, cap = StrokeCap.Round)
                    for (offset in listOf(-0.3f, 0.3f)) {
                        val ba = a + offset
                        val bx = cx + dx * m * 0.25f
                        val by = cy + dy * m * 0.25f
                        drawLine(tint, Offset(bx, by), Offset(bx + kotlin.math.cos(ba).toFloat() * m * 0.15f, by + kotlin.math.sin(ba).toFloat() * m * 0.15f), sw, cap = StrokeCap.Round)
                    }
                }
            }
            "cube_green", "cube_orange" -> {
                val top = Path().apply { moveTo(cx, h * 0.15f); lineTo(w * 0.85f, h * 0.35f); lineTo(cx, h * 0.55f); lineTo(w * 0.15f, h * 0.35f); close() }
                val left = Path().apply { moveTo(w * 0.15f, h * 0.35f); lineTo(cx, h * 0.55f); lineTo(cx, h * 0.85f); lineTo(w * 0.15f, h * 0.65f); close() }
                val right = Path().apply { moveTo(w * 0.85f, h * 0.35f); lineTo(cx, h * 0.55f); lineTo(cx, h * 0.85f); lineTo(w * 0.85f, h * 0.65f); close() }
                drawPath(left, tint.copy(alpha = 0.4f))
                drawPath(right, tint.copy(alpha = 0.6f))
                drawPath(top, tint)
                drawPath(top, tint, style = Stroke(sw)); drawPath(left, tint, style = Stroke(sw)); drawPath(right, tint, style = Stroke(sw))
            }
            "rocket_start" -> {
                drawCircle(Color.White, m * 0.3f, Offset(cx, cy))
                val p = Path().apply {
                    moveTo(cx, cy - m * 0.25f); quadraticBezierTo(cx + m * 0.15f, cy, cx + m * 0.15f, cy + m * 0.15f)
                    lineTo(cx - m * 0.15f, cy + m * 0.15f); quadraticBezierTo(cx - m * 0.15f, cy, cx, cy - m * 0.25f)
                }
                drawPath(p, tint)
                drawCircle(tint, m * 0.05f, Offset(cx, cy - m * 0.05f))
                drawLine(tint, Offset(cx - m * 0.15f, cy + m * 0.15f), Offset(cx - m * 0.25f, cy + m * 0.25f), sw)
                drawLine(tint, Offset(cx + m * 0.15f, cy + m * 0.15f), Offset(cx + m * 0.25f, cy + m * 0.25f), sw)
            }
            "cpu" -> {
                drawRoundRect(tint, Offset(w*0.25f, h*0.25f), Size(w*0.5f, h*0.5f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(sw))
                for (i in 0..2) {
                    drawLine(tint, Offset(w*(0.35f+i*0.15f), h*0.25f), Offset(w*(0.35f+i*0.15f), h*0.10f), sw)
                    drawLine(tint, Offset(w*(0.35f+i*0.15f), h*0.75f), Offset(w*(0.35f+i*0.15f), h*0.90f), sw)
                    drawLine(tint, Offset(w*0.25f, h*(0.35f+i*0.15f)), Offset(w*0.10f, h*(0.35f+i*0.15f)), sw)
                    drawLine(tint, Offset(w*0.75f, h*(0.35f+i*0.15f)), Offset(w*0.90f, h*(0.35f+i*0.15f)), sw)
                }
            }
            else -> {
                drawCircle(tint, m*0.36f, Offset(cx,cy), style=stroke)
                drawLine(tint, Offset(w*0.28f,h*0.50f), Offset(w*0.72f,h*0.50f), sw*1.3f, cap=StrokeCap.Round)
                drawLine(tint, Offset(w*0.50f,h*0.28f), Offset(w*0.50f,h*0.72f), sw*1.3f, cap=StrokeCap.Round)
            }
        }
    }
}

@Composable
fun AnimatedMonetGear(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val spin = rememberInfiniteTransition(label = "monet-gear-spin")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(20000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "monet-gear-rotation"
    )
    val gearModifier = modifier.graphicsLayer { rotationZ = rotation }
    if (onClick == null) MonetCanvasIcon("gear", gearModifier, MonetColors.Secondary)
    else MonetCanvasIcon("gear", gearModifier.clickable { onClick() }, MonetColors.Secondary)
}

// ==================== PREMIUM UI/UX ANIMATION RULES - SCI-FI CONSOLE ====================
object PremiumEasing {
    // Ban linear - use custom cubic-bezier for punchy mechanical pop-up
    val AnticipateOvershoot = androidx.compose.animation.core.CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)
    val FastOutSlowIn = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val SpringPop = androidx.compose.animation.core.CubicBezierEasing(0.68f, -0.55f, 0.265f, 1.55f)
}

@Composable
fun Modifier.premiumElasticPress(interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if(isPressed) 0.92f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio=0.4f, stiffness=600f, visibilityThreshold=0.001f),
        label="elasticPress"
    )
    val elevation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if(isPressed) 2f else 8f,
        animationSpec = androidx.compose.animation.core.tween(100, easing=PremiumEasing.AnticipateOvershoot),
        label="elev"
    )
    return this.graphicsLayer{
        scaleX=scale
        scaleY=scale
        // 3D-like compression
        cameraDistance=12f*this.density
        rotationX=if(isPressed) 2f else 0f
    }
}

@Composable
fun Modifier.gameBarTargetLock(isCentered: Boolean): Modifier {
    val targetScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if(isCentered) 1.1f else 0.9f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio=0.5f, stiffness=400f),
        label="targetScale"
    )
    val targetAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if(isCentered) 1f else 0.55f,
        animationSpec = androidx.compose.animation.core.tween(220, easing=PremiumEasing.FastOutSlowIn),
        label="targetAlpha"
    )
    return this.graphicsLayer{
        scaleX=targetScale
        scaleY=targetScale
        alpha=targetAlpha
    }
}




enum class CyberModule(
    val title: String,
    val commandTemplate: String,
    val restoreTemplate: String,
    val iconType: Int
) {
    LIGHTNING_GREEN("WHITELIST", "cmd deviceidle whitelist +\$pkg", "cmd deviceidle whitelist -\$pkg", 9),
    RESET_ALL("RESET ALL", "RESET_ALL", "RESET_ALL", 10),
    MEM_BOOST("MEM BOOST", "am kill-all", "am kill-all", 12),
    CROSSHAIR("CROSSHAIR", "TOGGLE_CROSSHAIR", "TOGGLE_CROSSHAIR", 13)
}

// Helper for new overlay - auto detect foreground game
suspend fun getCurrentForegroundPackageAuto(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val cmds = listOf(
        "dumpsys window | grep mCurrentFocus | awk -F '/' '{print \$1}' | awk '{print \$NF}' | tr -d ' ' | tr -d '\n'",
        "dumpsys activity activities | grep mResumedActivity | awk '{print \$4}' | cut -d '/' -f1",
        "dumpsys window | grep mFocusedApp | awk -F '/' '{print \$1}' | awk '{print \$NF}'"
    )
    for (c in cmds) {
        val r = TurboSpaceManager.executeCommandDetailedNoCtx(c)
        val pkg = r.output.trim().replace("}", "").replace("{", "").replace("ActivityRecord", "").trim()
        if (pkg.isNotEmpty() && pkg.contains(".") && !pkg.contains("turbospace") && !pkg.contains("systemui") && !pkg.contains("launcher") && pkg.length > 3) return@withContext pkg
    }
    return@withContext try { TurboSpaceRepository.selectedGame.value } catch(_: Throwable) { "NO TARGET SELECTED" }
}

// Resolve a target consistently: user's selected game first, then the foreground app.
suspend fun resolveTargetPkg(): String {
    val selected = try { TurboSpaceRepository.selectedGame.value } catch (_: Throwable) { "NO TARGET SELECTED" }
    if (selected != "NO TARGET SELECTED" && selected.contains(".")) return selected
    val foreground = try { getCurrentForegroundPackageAuto() } catch (_: Throwable) { "NO TARGET SELECTED" }
    return if (foreground != "NO TARGET SELECTED" && foreground.contains(".")) foreground else "NO TARGET SELECTED"
}

@Composable
fun VectorIconPerSpecNew(iconType: Int, tint: androidx.compose.ui.graphics.Color, isActive: Boolean){
    val glow = if(isActive) 5.dp else 0.dp
    androidx.compose.foundation.Canvas(
        androidx.compose.ui.Modifier.size(22.dp).shadow(glow, CircleShape, ambientColor=tint, spotColor=tint)
    ){
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val u = size.minDimension / 24f
        val stroke = 1.8.dp.toPx()
        fun pt(x: Float, y: Float) = Offset(cx + x*u, cy + y*u)
        fun flame(offsetX: Float = 0f, scale: Float = 1f) {
            val p = Path().apply {
                moveTo(cx + offsetX*u, cy - 10f*u*scale)
                cubicTo(cx + (offsetX+1f)*u*scale, cy - 6f*u*scale, cx + (offsetX+7f)*u*scale, cy - 4f*u*scale, cx + (offsetX+6f)*u*scale, cy + 2f*u*scale)
                cubicTo(cx + (offsetX+5f)*u*scale, cy + 8f*u*scale, cx + (offsetX+1f)*u*scale, cy + 10f*u*scale, cx + (offsetX-2f)*u*scale, cy + 8f*u*scale)
                cubicTo(cx + (offsetX-8f)*u*scale, cy + 6f*u*scale, cx + (offsetX-7f)*u*scale, cy, cx + (offsetX-4f)*u*scale, cy - 3f*u*scale)
                cubicTo(cx + (offsetX-3f)*u*scale, cy + 1f*u*scale, cx + (offsetX-1f)*u*scale, cy + 2f*u*scale, cx + offsetX*u*scale, cy + 3f*u*scale)
                cubicTo(cx + (offsetX+2f)*u*scale, cy, cx + (offsetX+2f)*u*scale, cy - 4f*u*scale, cx + offsetX*u*scale, cy - 10f*u*scale)
                close()
            }
            drawPath(p, tint, style=Stroke(stroke, cap=StrokeCap.Round, join=StrokeJoin.Round))
        }
        when(iconType){
            // BOOSTER: flame.
            1 -> flame()
            // STABILITY: rocket.
            2 -> {
                val p = Path().apply {
                    moveTo(cx, cy-10f*u); cubicTo(cx+5f*u,cy-7f*u,cx+6f*u,cy-2f*u,cx+5f*u,cy+3f*u)
                    lineTo(cx,cy+8f*u); lineTo(cx-5f*u,cy+3f*u); cubicTo(cx-6f*u,cy-2f*u,cx-5f*u,cy-7f*u,cx,cy-10f*u); close()
                }
                drawPath(p,tint,style=Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))
                drawCircle(tint,2f*u,pt(0f,-2f),style=Stroke(stroke*0.85f))
                drawLine(tint,pt(-4f,3f),pt(-7f,7f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(4f,3f),pt(7f,7f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(-1.5f,8f),pt(0f,11f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(1.5f,8f),pt(0f,11f),stroke,cap=StrokeCap.Round)
            }
            // APP STABILITY: sword.
            3 -> {
                val blade = Path().apply { moveTo(cx+1f*u,cy-10f*u); lineTo(cx+5f*u,cy-5f*u); lineTo(cx-3f*u,cy+4f*u); lineTo(cx-5f*u,cy+1f*u); close() }
                drawPath(blade,tint,style=Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))
                drawLine(tint,pt(-4f,3f),pt(1f,8f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(-7f,5f),pt(-2f,10f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(-4f,3f),pt(-7f,6f),stroke,cap=StrokeCap.Round)
            }
            // OPTIMIZE: dual flames.
            4 -> { flame(-4.1f,0.72f); flame(4.1f,0.72f) }
            // PERFORMANCE TOOL: the word BOOST drawn directly on Canvas.
            6 -> {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = tint.toArgb(); textAlign = android.graphics.Paint.Align.CENTER
                    textSize = h * 0.27f; typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
                }
                drawContext.canvas.nativeCanvas.drawText("BOOST", cx, cy + paint.textSize*0.36f, paint)
                drawLine(tint,pt(-10f,7f),pt(10f,7f),stroke*0.75f)
            }
            // The legacy INTERACTIVE icon remains drawable but is not shown in the grid.
            8 -> {
                drawCircle(tint,3f*u,pt(0f,-5f),style=Stroke(stroke))
                drawLine(tint,pt(0f,-1f),pt(0f,8f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(0f,4f),pt(5f,7f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(5f,7f),pt(8f,4f),stroke,cap=StrokeCap.Round)
            }
            // WHITELIST: clock.
            9 -> {
                drawCircle(tint,9f*u,pt(0f,0f),style=Stroke(stroke))
                drawLine(tint,pt(0f,0f),pt(0f,-5f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(0f,0f),pt(4f,2f),stroke,cap=StrokeCap.Round)
            }
            // RESET ALL: circular arrow.
            10 -> {
                drawArc(tint,50f,285f,false,Offset(cx-8.5f*u,cy-8.5f*u),Size(17f*u,17f*u),style=Stroke(stroke,cap=StrokeCap.Round))
                val p = Path().apply { moveTo(cx-9f*u,cy-8f*u); lineTo(cx-9f*u,cy-2f*u); lineTo(cx-3f*u,cy-8f*u); close() }
                drawPath(p,tint,style=Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))
            }
            // Legacy GAME MODE icon remains available but is not shown in the grid.
            11 -> {
                val p = Path().apply { moveTo(cx-8f*u,cy-4f*u); lineTo(cx+8f*u,cy-4f*u); lineTo(cx+10f*u,cy+4f*u); lineTo(cx+5f*u,cy+7f*u); lineTo(cx-5f*u,cy+7f*u); lineTo(cx-10f*u,cy+4f*u); close() }
                drawPath(p,tint,style=Stroke(stroke,join=StrokeJoin.Round))
                drawLine(tint,pt(-5f,1f),pt(-1f,1f),stroke); drawLine(tint,pt(-3f,-1f),pt(-3f,3f),stroke)
                drawCircle(tint,1f*u,pt(5f,0f)); drawCircle(tint,1f*u,pt(7f,2f))
            }
            // MEM BOOST: stacked RAM modules with pins.
            12 -> {
                for(i in 0..2){
                    val y = cy + (i-1)*5f*u
                    drawRoundRect(tint,Offset(cx-8f*u,y-1.6f*u),Size(16f*u,3.2f*u),androidx.compose.ui.geometry.CornerRadius(0.8f*u),style=Stroke(stroke*0.8f))
                    drawLine(tint,Offset(cx-4f*u,y+1.7f*u),Offset(cx-4f*u,y+3.7f*u),stroke*0.8f)
                    drawLine(tint,Offset(cx+4f*u,y+1.7f*u),Offset(cx+4f*u,y+3.7f*u),stroke*0.8f)
                }
            }
            // CROSSHAIR.
            13 -> {
                drawCircle(tint,2f*u,pt(0f,0f),style=Stroke(stroke*0.8f))
                drawLine(tint,pt(-10f,0f),pt(-3.5f,0f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(3.5f,0f),pt(10f,0f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(0f,-10f),pt(0f,-3.5f),stroke,cap=StrokeCap.Round)
                drawLine(tint,pt(0f,3.5f),pt(0f,10f),stroke,cap=StrokeCap.Round)
            }
        }
    }
}

@Composable
fun ScriptIconLightningRedNew(){
    // FIX PART 8C: compact 20dp lightning icon with a restrained 4dp glow.
    Canvas(Modifier.size(20.dp).shadow(4.dp, ambientColor=MonetColors.Secondary, spotColor=MonetColors.Secondary)) {
        val w = size.width
        val h = size.height
        val bolt = Path().apply {
            moveTo(w * 0.60f, h * 0.06f)
            lineTo(w * 0.28f, h * 0.52f)
            lineTo(w * 0.49f, h * 0.52f)
            lineTo(w * 0.40f, h * 0.96f)
            lineTo(w * 0.75f, h * 0.43f)
            lineTo(w * 0.54f, h * 0.43f)
            close()
        }
        drawPath(bolt, MonetColors.Secondary, style=Stroke(1.2.dp.toPx()))
    }
}

@Composable
fun BoltStackIcon(count: Int, modifier: Modifier = Modifier) {
    // FIX PART 8C: one consistent 20dp icon shell for one/two/three-bolt features.
    Canvas(modifier.size(20.dp)) {
        val stroke = 1.15.dp.toPx()
        val units = when (count.coerceIn(1, 3)) {
            1 -> listOf(Offset(size.width * 0.50f, size.height * 0.50f))
            2 -> listOf(Offset(size.width * 0.37f, size.height * 0.35f), Offset(size.width * 0.63f, size.height * 0.66f))
            else -> listOf(Offset(size.width * 0.36f, size.height * 0.28f), Offset(size.width * 0.64f, size.height * 0.50f), Offset(size.width * 0.39f, size.height * 0.73f))
        }
        units.forEach { c ->
            val bw = size.width * 0.20f
            val bh = size.height * 0.28f
            val p = Path().apply {
                moveTo(c.x + bw * 0.25f, c.y - bh)
                lineTo(c.x - bw * 0.55f, c.y + bh * 0.05f)
                lineTo(c.x - bw * 0.08f, c.y + bh * 0.05f)
                lineTo(c.x - bw * 0.28f, c.y + bh)
                lineTo(c.x + bw * 0.60f, c.y - bh * 0.05f)
                lineTo(c.x + bw * 0.12f, c.y - bh * 0.05f)
                close()
            }
            drawPath(p, MonetColors.Secondary, style = Stroke(stroke))
        }
    }
}

// GAME MODE: outlined controller icon, colored by the real active state.
@Composable
fun RocketIconRedCompact(modifier: Modifier = Modifier, isActive: Boolean = false){
    val tint = if (isActive) MonetColors.Primary else MonetColors.Primary.copy(alpha=0.35f)
    Canvas(modifier.shadow(if (isActive) 8.dp else 0.dp, ambientColor=tint, spotColor=tint)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val stroke = 1.8.dp.toPx()
        val d: (Float) -> Float = { it * size.width / 28f }
        val pad = Path().apply {
            moveTo(cx-d(7f),cy-d(5f)); cubicTo(cx-d(10f),cy-d(5f),cx-d(11f),cy+d(1f),cx-d(10f),cy+d(6f))
            cubicTo(cx-d(9f),cy+d(9f),cx-d(6f),cy+d(7f),cx-d(4f),cy+d(4f))
            lineTo(cx+d(4f),cy+d(4f)); cubicTo(cx+d(6f),cy+d(7f),cx+d(9f),cy+d(9f),cx+d(10f),cy+d(6f))
            cubicTo(cx+d(11f),cy+d(1f),cx+d(10f),cy-d(5f),cx+d(7f),cy-d(5f)); close()
        }
        drawPath(pad, tint, style=Stroke(stroke))
        drawLine(tint, Offset(cx-d(6f),cy), Offset(cx-d(2f),cy), stroke)
        drawLine(tint, Offset(cx-d(4f),cy-d(2f)), Offset(cx-d(4f),cy+d(2f)), stroke)
        drawCircle(tint, d(1f), Offset(cx+d(5f),cy-d(1f)), style=Stroke(stroke))
        drawCircle(tint, d(1f), Offset(cx+d(7f),cy+d(2f)), style=Stroke(stroke))
    }
}
// ==================== NEW GAME SPACE OVERLAY SERVICE - ONLY GAME BAR, MAIN PAGE UNTOUCHED ====================
class GameSpaceOverlayService : Service(), androidx.lifecycle.LifecycleOwner, androidx.lifecycle.ViewModelStoreOwner, androidx.savedstate.SavedStateRegistryOwner {
    private var windowManager: WindowManager? = null
    private var floatingIconView: androidx.compose.ui.platform.ComposeView? = null
    private var logoView: androidx.compose.ui.platform.ComposeView? = null
    private var fullHudView: androidx.compose.ui.platform.ComposeView? = null
    private var fpsStatsView: androidx.compose.ui.platform.ComposeView? = null
    private var fpsStatsParams: WindowManager.LayoutParams? = null
    private var statsPollingJob: Job? = null
    private var prefsLoadedForStats = false

    companion object {
        const val ACTION_SET_FPS_FLOATING = "com.turbospace.optimizer.action.SET_FPS_FLOATING"
        const val EXTRA_ENABLED = "enabled"
        private const val FOREGROUND_CHANNEL_ID = "TurboSpaceService"
        private const val FOREGROUND_NOTIFICATION_ID = 1
    }
    private var immersiveLoop: android.os.Handler? = null
    private var immersiveTask: Runnable? = null
     var crosshairView: ComposeView? = null
     var crosshairParams: WindowManager.LayoutParams? = null
     private var crosshairObserverInstalled = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val viewModelStoreInstance = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInstance
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()

        // Promote this service immediately so Android knows the gaming HUD is an active
        // foreground operation rather than an untracked background service.
        ensureForegroundNotificationChannel()
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            android.util.Log.e("TurboSpace", "startForeground failed: ${t.message}")
            stopSelf()
            return
        }

        try {
            savedStateRegistryController.performRestore(null)
        } catch (_: Throwable) {}
        try {
            lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.CREATED
            lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.STARTED
            lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        } catch (_: Throwable) {}
    }

    private fun ensureForegroundNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(FOREGROUND_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    FOREGROUND_CHANNEL_ID,
                    "TurboSpaceService",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps the Turbo Space gaming HUD running while a game is active."
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        return androidx.core.app.NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("Turbo Space Booster")
            .setContentText("Gaming HUD is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(
                androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )
            .build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
         windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
         if (!prefsLoadedForStats) {
             prefsLoadedForStats = true
             try { TurboSpaceRepository.restoreStopwatch(applicationContext) } catch (_: Throwable) {}
             try {
                 val prefs = getSharedPreferences("turbospace_prefs", MODE_PRIVATE)
                 TurboSpaceRepository.setSelectedGame(prefs.getString("selected_game", "NO TARGET SELECTED") ?: "NO TARGET SELECTED")
                 TurboSpaceRepository.fpsFloatingEnabled = prefs.getBoolean("fps_floating_enabled", false)
             } catch (_: Throwable) {}
         }
         when (intent?.action) {
             ACTION_SET_FPS_FLOATING -> setFpsFloatingEnabled(intent.getBooleanExtra(EXTRA_ENABLED, false))
             // A normal start request must make the main launcher icon available again, even when
             // persistent FPS statistics are currently visible.
             else -> if (fullHudView == null && floatingIconView == null) showBootAnimation { showFloatingIcon() }
         }
         if (TurboSpaceRepository.fpsFloatingEnabled) showFpsStatsWindow()
         ensureStatsPolling()
         try{
             if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                 floatingIconView?.windowInsetsController?.hide(
                     android.view.WindowInsets.Type.statusBars() or
                     android.view.WindowInsets.Type.navigationBars()
                 )
                 floatingIconView?.windowInsetsController?.systemBarsBehavior =
                     android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
             }
         }catch(_: Throwable){}
         if(!crosshairObserverInstalled){
             crosshairObserverInstalled = true
             scope.launch{
                 TurboSpaceRepository.crosshairActive.collect{ on ->
                     if(on) showCrosshairWindow() else hideCrosshairWindow()
                 }
             }
         }
         return START_STICKY
     }
    override fun onDestroy(){
        clearImmersiveLoop()
        statsPollingJob?.cancel(); statsPollingJob = null
        try { floatingIconView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
        try { logoView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
        try { fullHudView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
        try { fpsStatsView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
        floatingIconView = null
        logoView = null
        fullHudView = null
        hideCrosshairWindow()
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        super.onDestroy()
    }
     fun setFpsFloatingEnabled(enabled: Boolean) {
         TurboSpaceRepository.setFpsFloatingEnabled(this, enabled)
         if (enabled) showFpsStatsWindow() else hideFpsStatsWindow()
         ensureStatsPolling()
     }

     private fun statsWindowParams(): WindowManager.LayoutParams {
         val density = resources.displayMetrics.density
         val desiredWidth = (300f * density).toInt()
         val maxWidth = (resources.displayMetrics.widthPixels - 8f * density).toInt().coerceAtLeast((100f * density).toInt())
         val width = desiredWidth.coerceAtMost(maxWidth)
         val height = (50f * density).toInt()
         val prefs = getSharedPreferences("turbospace_overlay", MODE_PRIVATE)
         val defaultX = (8f * density).toInt()
         return WindowManager.LayoutParams(
             width, height,
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
             WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
             PixelFormat.TRANSLUCENT
         ).apply {
             gravity = Gravity.TOP or Gravity.START
             x = prefs.getInt("fps_stats_x", defaultX).coerceIn(0, (resources.displayMetrics.widthPixels - width).coerceAtLeast(0))
             y = prefs.getInt("fps_stats_y", (90f * density).toInt())
                 .coerceIn(0, (resources.displayMetrics.heightPixels - height).coerceAtLeast(0))
         }
     }

     private fun showFpsStatsWindow() {
         if (fpsStatsView != null || windowManager == null) return
         val params = statsWindowParams()
         fpsStatsParams = params
         val created = ComposeView(this).apply {
             setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
             setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
             setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
             setContent {
                 FloatingStatsWindowContent(
                     label = "SHOW STATUS",
                     value = TurboSpaceRepository.floatingFpsValue,
                     onDrag = { dx, dy -> moveFpsStatsWindow(dx, dy) }
                 )
             }
         }
         fpsStatsView = created
         try { windowManager?.addView(created, params) } catch (t: Throwable) {
             android.util.Log.w("TurboSpace", "add FPS stats window failed: ${t.message}")
             fpsStatsView = null; fpsStatsParams = null
         }
     }

     private fun moveFpsStatsWindow(dx: Float, dy: Float) {
         val params = fpsStatsParams
         val view = fpsStatsView
         val wm = windowManager ?: return
         if (params == null || view == null) return
         val dm = resources.displayMetrics
         params.x = (params.x + dx.toInt()).coerceIn(0, (dm.widthPixels - params.width).coerceAtLeast(0))
         params.y = (params.y + dy.toInt()).coerceIn(0, (dm.heightPixels - params.height).coerceAtLeast(0))
         getSharedPreferences("turbospace_overlay", MODE_PRIVATE).edit()
             .putInt("fps_stats_x", params.x).putInt("fps_stats_y", params.y).apply()
         try { wm.updateViewLayout(view, params) } catch (_: Throwable) {}
     }

     private fun hideFpsStatsWindow() {
         try { fpsStatsView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
         fpsStatsView = null; fpsStatsParams = null
     }

     private fun ensureStatsPolling() {
         if (!TurboSpaceRepository.fpsFloatingEnabled) {
             statsPollingJob?.cancel(); statsPollingJob = null
             return
         }
         if (statsPollingJob?.isActive == true) return
         statsPollingJob = scope.launch {
             while (isActive && TurboSpaceRepository.fpsFloatingEnabled) {
                 val snapshot = withContext(Dispatchers.IO) {
                     val selected = TurboSpaceRepository.selectedGame.value
                    val targetPkg = if (selected != "NO TARGET SELECTED" &&
                        selected.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) {
                        selected
                    } else {
                        try { getCurrentForegroundPackageAuto() } catch (_: Throwable) { "NO TARGET SELECTED" }
                    }
                    val fps = try {
                        if (targetPkg != "NO TARGET SELECTED" &&
                            targetPkg.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) {
                            TurboSpaceManager.getRealFpsViaShizuku(targetPkg)
                        } else "0"
                    } catch (_: Throwable) { "0" }
                    val ram = try { TurboSpaceManager.getRamUsagePercent(applicationContext) } catch (_: Throwable) { 0 }
                     val battery = try { TurboSpaceManager.getBatteryPercent(applicationContext).coerceIn(0, 100) } catch (_: Throwable) { 0 }
                     "FPS: $fps | RAM: $ram% | BAT: $battery%"
                 }
                 TurboSpaceRepository.floatingFpsValue = snapshot
                 delay(2000)
             }
             statsPollingJob = null
         }
     }

     // Hide the main HUD and launcher icon while keeping enabled statistics windows alive.
     fun closeHudForFullDismiss() {
         clearImmersiveLoop()
         try { floatingIconView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
         try { fullHudView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
         floatingIconView = null
         fullHudView = null
         if (!TurboSpaceRepository.fpsFloatingEnabled) {
             closeCompletely()
         } else {
             ensureStatsPolling()
         }
     }

     fun showCrosshairWindow(){
         if(crosshairView != null) return
         val dm = resources.displayMetrics
         val crosshairScale = TurboSpaceRepository.crosshairScale.coerceIn(0.5f, 2.0f)
         val size = (60f * crosshairScale * dm.density).toInt().coerceAtLeast((30 * dm.density).toInt())
         // FIX PART 9B: use the current bounds where available, with safe metrics fallback.
         val (screenW, screenH) = try {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                 val bounds = windowManager?.currentWindowMetrics?.bounds
                 Pair(bounds?.width() ?: dm.widthPixels, bounds?.height() ?: dm.heightPixels)
             } else {
                 Pair(dm.widthPixels, dm.heightPixels)
             }
         } catch (_: Throwable) {
             Pair(dm.widthPixels, dm.heightPixels)
         }
         val overlayPrefs = getSharedPreferences("turbospace_overlay", MODE_PRIVATE)
         val savedX = overlayPrefs.getInt("crosshair_x", Int.MIN_VALUE)
         val savedY = overlayPrefs.getInt("crosshair_y", Int.MIN_VALUE)
         crosshairParams = WindowManager.LayoutParams(
             size, size,
             if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                 WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
             else WindowManager.LayoutParams.TYPE_PHONE,
             // FIX PART 9B: remove NOT_FOCUSABLE so this small window receives drag gestures.
             WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                 or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                 or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
             PixelFormat.TRANSLUCENT
         ).apply{
             gravity = Gravity.TOP or Gravity.START
             if(savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE){
                 x = savedX.coerceIn(0, (screenW - size).coerceAtLeast(0))
                 y = savedY.coerceIn(0, (screenH - size).coerceAtLeast(0))
             } else {
                 x = ((screenW - size) / 2f).toInt().coerceAtLeast(0)
                 y = ((screenH - size) / 2f).toInt().coerceAtLeast(0)
             }
         }
         crosshairView = androidx.compose.ui.platform.ComposeView(this).apply{
             setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
             setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
             setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
             setContent{
                 val style by TurboSpaceRepository.crosshairStyle.collectAsState()
                 val colorIdx by TurboSpaceRepository.crosshairColorIdx.collectAsState()
                 val scale = TurboSpaceRepository.crosshairScale
                 MovableCrosshairOverlay(
                     style = style,
                     color = TurboSpaceRepository.crosshairColor(colorIdx),
                     scale = scale,
                     onDrag = { dx, dy ->
                         val p = crosshairParams
                         val view = crosshairView
                         val wm = windowManager
                         if(p != null && view != null && wm != null){
                             p.x = (p.x + dx).toInt().coerceIn(0, (screenW - size).coerceAtLeast(0))
                             p.y = (p.y + dy).toInt().coerceIn(0, (screenH - size).coerceAtLeast(0))
                             getSharedPreferences("turbospace_overlay", MODE_PRIVATE).edit()
                                 .putInt("crosshair_x", p.x)
                                 .putInt("crosshair_y", p.y)
                                 .apply()
                             try { wm.updateViewLayout(view, p) } catch (_: Throwable) {}
                         }
                     }
                 )
             }
         }
         try{ windowManager?.addView(crosshairView, crosshairParams) }
         catch(e: Throwable){ android.util.Log.w("TurboSpace", "add crosshair window failed: ${e.message}") }
     }

     fun hideCrosshairWindow(){
         try{ crosshairView?.let{ windowManager?.removeView(it) } }catch(_: Throwable){}
         crosshairView = null
         crosshairParams = null
     }

     // PART 11C: stop the recurring immersive enforcement loop when the HUD/service closes.
     private fun clearImmersiveLoop() {
         try { immersiveTask?.let { immersiveLoop?.removeCallbacks(it) } } catch (_: Throwable) {}
         immersiveTask = null
         immersiveLoop = null
     }

     // FIX CHANGE 5: remove every overlay window and stop this service completely.
     fun closeCompletely(){
         clearImmersiveLoop()
         TurboSpaceRepository.setFpsFloatingEnabled(this, false)
         statsPollingJob?.cancel(); statsPollingJob = null
         try{ floatingIconView?.let{ windowManager?.removeView(it) } }catch(_: Throwable){}
         try{ logoView?.let{ windowManager?.removeView(it) } }catch(_: Throwable){}
         try{ fullHudView?.let{ windowManager?.removeView(it) } }catch(_: Throwable){}
         try{ hideCrosshairWindow() }catch(_: Throwable){}
         floatingIconView = null
         logoView = null
         fullHudView = null
         stopSelf()
     }

     private fun getCollapsedParams(): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val width = (24f * density).toInt()
        val height = (48f * density).toInt()
        return WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (resources.displayMetrics.heightPixels * 0.10f).toInt()
                .coerceIn(0, (resources.displayMetrics.heightPixels - height).coerceAtLeast(0))
        }
    }
    private fun getExpandedParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_FULLSCREEN
                or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
        private fun hideLogoOverlay() {
            try { logoView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
            logoView = null
        }

        private fun showBootAnimation(onFinished: () -> Unit) {
            // Do not start a second boot card if one is already being displayed.
            if (logoView != null || fullHudView != null) return
            val density = resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                (112f * density).toInt(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                // With BOTTOM gravity, a positive y value raises the window from the bottom edge.
                y = (resources.displayMetrics.heightPixels * 0.20f).toInt()
            }
            val view = androidx.compose.ui.platform.ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
                setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
                setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
                setContent {
                    var visible by remember { mutableStateOf(true) }
                    val alpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (visible) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(420, easing = FastOutSlowInEasing),
                        label = "boot-card-fade"
                    )
                    val slideProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (visible) 0f else 1f,
                        animationSpec = androidx.compose.animation.core.tween(420, easing = FastOutSlowInEasing),
                        label = "boot-card-slide-down"
                    )
                    LaunchedEffect(Unit) {
                        delay(2000)
                        visible = false
                        delay(420)
                        hideLogoOverlay()
                        onFinished()
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    this.alpha = alpha
                                    translationY = slideProgress * size.height
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                                .border(1.dp, Color(0xFF1689FF), RoundedCornerShape(10.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("BOOSTING GAME...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("APPLYING PERFORMANCE...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("RAM BOOSTER READY", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            logoView = view
            try {
                windowManager?.addView(view, params)
            } catch (t: Throwable) {
                android.util.Log.w("TurboSpace", "add boot animation overlay failed: ${t.message}")
                logoView = null
                onFinished()
            }
        }

        fun showFloatingIcon() {
            if (floatingIconView != null || fullHudView != null) return
            val params = getCollapsedParams()
            val view = androidx.compose.ui.platform.ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
                setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
                setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
                setContent {
                    val orange = Color(0xFFFF8A00)
                    val shape = RoundedCornerShape(8.dp)
                    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1f,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 600f),
                        label = "floatingHandlePress"
                    )
                    Box(
                        Modifier
                            .size(width = 24.dp, height = 48.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .shadow(7.dp, shape, ambientColor = orange, spotColor = orange)
                            .clip(shape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.5.dp, orange, shape)
                            .clickable(interactionSource = interactionSource, indication = null) { expandHud() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Circular Turbo Space mark, drawn with Canvas (no emoji/image glyphs).
                            Canvas(Modifier.size(12.dp)) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                drawCircle(orange, radius = size.minDimension * 0.43f, center = center, style = Stroke(width = 1.6.dp.toPx()))
                                drawCircle(orange, radius = 1.7.dp.toPx(), center = center)
                            }
                            // Chevron (>) drawn with Canvas.
                            Canvas(Modifier.size(width = 10.dp, height = 12.dp)) {
                                val path = Path().apply {
                                    moveTo(size.width * 0.30f, size.height * 0.12f)
                                    lineTo(size.width * 0.72f, size.height * 0.50f)
                                    lineTo(size.width * 0.30f, size.height * 0.88f)
                                }
                                drawPath(path, orange, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                            }
                        }
                    }
                }
            }
            floatingIconView = view
            try {
                windowManager?.addView(view, params)
            } catch (t: Throwable) {
                android.util.Log.e("TurboSpace", "addView floating icon failed: ${t.message}")
                floatingIconView = null
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (floatingIconView == null && fullHudView == null) {
                        floatingIconView = view
                        try { windowManager?.addView(view, params) } catch (_: Throwable) { floatingIconView = null }
                    }
                }, 300)
            }
        }
    @Volatile private var isTransitioning = false
    fun expandHud(){
        if(isTransitioning) return
        if(fullHudView != null) return
        isTransitioning = true
        hideLogoOverlay()
        // FIX BUG-02: remove the floating icon defensively; retry on the main queue if it fails.
        val floatingToRemove = floatingIconView
        try {
            if (floatingToRemove != null) {
                try {
                    windowManager?.removeView(floatingToRemove)
                } catch (e: Throwable) {
                    android.util.Log.w("TurboSpace", "remove floating fail: ${e.message}")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            windowManager?.removeView(floatingToRemove)
                        } catch (retryError: Throwable) {
                            android.util.Log.w("TurboSpace", "remove floating retry fail: ${retryError.message}")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("TurboSpace", "floating removal failed safely: ${e.message}")
        } finally {
            floatingIconView = null
        }
        // ใช้เกมที่ผู้ใช้เลือกไว้เท่านั้น ไม่ auto-detect
        val params = getExpandedParams()
        fullHudView = androidx.compose.ui.platform.ComposeView(this).apply{
            setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
            setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
            setContent{ FullHudOverlayFinalNew(onDismiss={ collapseHud() }) }
        }
        // FIX PART 10B: apply immersive mode as soon as the HUD window attaches.
        fullHudView?.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {
                applyImmersive(v, 0)
                v.removeOnAttachStateChangeListener(this)
            }
            override fun onViewDetachedFromWindow(v: android.view.View) {}
        })
        try{ windowManager?.addView(fullHudView, params) }catch(e: Throwable){ android.util.Log.e("TurboSpace","addView HUD failed: ${e.message}") }

        // PART 11C A: if system bars reappear, re-apply the mode-specific immersive flags.
        fullHudView?.setOnSystemUiVisibilityChangeListener { visibility ->
            val hideFull = (visibility and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
            val navMode = try {
                android.provider.Settings.Secure.getInt(contentResolver, "navigation_mode", 0)
            } catch (_: Throwable) { 0 }
            val isGestureMode = navMode == 2
            val needReapply = if (isGestureMode) {
                !hideFull
            } else {
                val hideNav = (visibility and android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
                !hideFull || !hideNav
            }
            if (needReapply) applyImmersive(fullHudView, 0)
        }

        // PART 11C B: lightly enforce immersive mode every 1.2 seconds while expanded.
        clearImmersiveLoop()
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        immersiveLoop = h
        immersiveTask = object : Runnable {
            override fun run() {
                try {
                    fullHudView?.let { v ->
                        if (v.isAttachedToWindow) {
                            val navMode = try {
                                android.provider.Settings.Secure.getInt(contentResolver, "navigation_mode", 0)
                            } catch (_: Throwable) { 0 }
                            val isGestureMode = navMode == 2
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                if (isGestureMode) {
                                    v.windowInsetsController?.hide(android.view.WindowInsets.Type.statusBars())
                                } else {
                                    v.windowInsetsController?.hide(
                                        android.view.WindowInsets.Type.statusBars() or
                                            android.view.WindowInsets.Type.navigationBars()
                                    )
                                }
                                v.windowInsetsController?.systemBarsBehavior =
                                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                            @Suppress("DEPRECATION")
                            v.systemUiVisibility = if (isGestureMode) {
                                (android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                            } else {
                                (android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                            }
                        }
                    }
                } catch (_: Throwable) {}
                immersiveLoop?.postDelayed(this, 1200)
            }
        }
        h.post(immersiveTask!!)

        // FIX PART 9B: when HUD is opened after Crosshair was already visible, re-add it last
        // so the separate draggable window remains above the full-screen HUD layer.
        try {
            if (TurboSpaceRepository.crosshairActive.value) {
                hideCrosshairWindow()
                showCrosshairWindow()
            }
        } catch (_: Throwable) {}
        // Keep persistent stats in front of the full-screen HUD after it is added to WindowManager.
        try {
            if (TurboSpaceRepository.fpsFloatingEnabled) { hideFpsStatsWindow(); showFpsStatsWindow() }
        } catch (_: Throwable) {}
        // FIX PART 10B: aggressive fast retries to prevent status/nav bars flashing on HUD open.
        applyImmersive(fullHudView, 0)
        applyImmersive(fullHudView, 16)   // approximately one frame
        applyImmersive(fullHudView, 50)
        applyImmersive(fullHudView, 120)
        applyImmersive(fullHudView, 250)
        isTransitioning = false
    }

    // PART 11C: hide status + navigation in 3-button mode; preserve the gesture pill in gesture mode.
    private fun applyImmersive(view: android.view.View?, delayMs: Long) {
        val v = view ?: return
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val scheduleApply = Runnable {
            try {
                val applyNow = Runnable {
                    try {
                        val navMode = try {
                            android.provider.Settings.Secure.getInt(
                                contentResolver, "navigation_mode", 0
                            )
                        } catch (_: Throwable) { 0 }
                        val isGestureMode = navMode == 2

                        if (isGestureMode) {
                            // Gesture navigation: hide status bar only, leaving the gesture pill available.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                v.windowInsetsController?.hide(android.view.WindowInsets.Type.statusBars())
                                v.windowInsetsController?.systemBarsBehavior =
                                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                            @Suppress("DEPRECATION")
                            v.systemUiVisibility = (
                                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                )
                        } else {
                            // Three-button navigation (also the fallback when OEM setting lookup fails).
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                v.windowInsetsController?.hide(
                                    android.view.WindowInsets.Type.statusBars() or
                                        android.view.WindowInsets.Type.navigationBars()
                                )
                                v.windowInsetsController?.systemBarsBehavior =
                                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                            @Suppress("DEPRECATION")
                            v.systemUiVisibility = (
                                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                )
                        }
                    } catch (_: Throwable) {}
                }
                if (v.isAttachedToWindow) {
                    v.post(applyNow)
                } else {
                    val attachListener = object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(attachedView: android.view.View) {
                            attachedView.removeOnAttachStateChangeListener(this)
                            attachedView.post(applyNow)
                        }
                        override fun onViewDetachedFromWindow(detachedView: android.view.View) {}
                    }
                    v.addOnAttachStateChangeListener(attachListener)
                    if (v.isAttachedToWindow) {
                        v.removeOnAttachStateChangeListener(attachListener)
                        v.post(applyNow)
                    }
                }
            } catch (_: Throwable) {}
        }
        if (delayMs <= 0L) handler.post(scheduleApply)
        else handler.postDelayed(scheduleApply, delayMs)
    }

    fun collapseHud(){
        if(isTransitioning) return
        clearImmersiveLoop()
        isTransitioning = true
        try{ fullHudView?.let{ windowManager?.removeView(it) } }catch(_: Throwable){}
        fullHudView = null
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // FIX CHANGE 2: don't resurrect the floating icon if a full close stopped the service.
            if (lifecycleRegistry.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED) {
                try{ showFloatingIcon() }catch(_: Throwable){}
                isTransitioning = false
            }
        }, 200)
        // FIX CHANGE 3: collapsing the HUD must not stop scripts or reset active booster commands.
    }
}

@Composable
private fun FloatingStatsWindowContent(label: String, value: String, onDrag: (Float, Float) -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .pointerInput(label) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .background(MonetColors.Background.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
            .border(1.dp, MonetColors.Secondary.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
            .shadow(8.dp, RoundedCornerShape(8.dp), ambientColor = MonetColors.Secondary, spotColor = MonetColors.Secondary)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.Start) {
            Text(label, color = MonetColors.Secondary, fontSize = 7.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(
                value,
                color = MonetColors.Primary,
                fontSize = if (label == "SHOW STATUS") 9.sp else 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
fun MovableCrosshairOverlay(
    style: Int = 0,
    color: Color = MonetColors.Primary,
    scale: Float = 1.0f,
    onDrag: (Float, Float) -> Unit = { _, _ -> }
){
    val safeScale = scale.coerceIn(0.5f, 2.0f)
    Box(
        Modifier.size((60f * safeScale).dp)
            .pointerInput(Unit){
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment=Alignment.Center
    ){
        Canvas(Modifier.fillMaxSize()){
            val cx = size.width / 2f
            val cy = size.height / 2f
            val unit = size.minDimension / 60f
            when(style){
                0 -> {
                    drawCircle(color, 2f * unit, Offset(cx,cy))
                    drawLine(color, Offset(cx-20f*unit,cy), Offset(cx-5f*unit,cy), 2f*unit)
                    drawLine(color, Offset(cx+5f*unit,cy), Offset(cx+20f*unit,cy), 2f*unit)
                    drawLine(color, Offset(cx,cy-20f*unit), Offset(cx,cy-5f*unit), 2f*unit)
                    drawLine(color, Offset(cx,cy+5f*unit), Offset(cx,cy+20f*unit), 2f*unit)
                }
                1 -> {
                    drawCircle(color, 4f*unit, Offset(cx,cy))
                    drawCircle(color.copy(alpha=0.3f), 10f*unit, Offset(cx,cy), style=Stroke(1.5f*unit))
                }
                2 -> {
                    drawCircle(color, 14f*unit, Offset(cx,cy), style=Stroke(2.5f*unit))
                    drawCircle(color, 2.5f*unit, Offset(cx,cy))
                }
            }
        }
    }
}

// FIX PART 9C: picker preview follows the user's draft selection without changing the saved crosshair.
@Composable
fun CrosshairPickerDialog(
    style: Int,
    colorIdx: Int,
    scale: Float = 1.0f,
    onStyle: (Int) -> Unit,
    onColor: (Int) -> Unit,
    onScale: (Float) -> Unit = {},
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onToggleOff: () -> Unit = {}
){
    val previewColor = TurboSpaceRepository.crosshairColor(colorIdx)
    val colors = listOf(
        0 to Color.Red, 1 to Color.Blue, 2 to Color.Cyan,
        3 to Color.Green, 4 to Color.White, 5 to Color(0xFFFFC0CB)
    )
    val previewScale = scale.coerceIn(0.5f, 2.0f)
    Box(
        Modifier.fillMaxSize().background(MonetColors.Background.copy(alpha=0.82f)).clickable { onCancel() },
        contentAlignment = Alignment.Center
    ){
        Box(
            Modifier.fillMaxWidth(0.84f).widthIn(max = 420.dp)
                .background(CyberDesign.DeepDark85, RoundedCornerShape(12.dp))
                .border(1.2.dp, MonetColors.Primary.copy(0.55f), RoundedCornerShape(12.dp))
                .shadow(18.dp, RoundedCornerShape(12.dp), ambientColor=MonetColors.Primary, spotColor=MonetColors.Primary)
                .clickable { /* Keep panel taps from dismissing the picker. */ }
                .padding(14.dp)
        ){
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ){
                Text("CROSSHAIR", color=MonetColors.Primary, fontSize=12.sp,
                    fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace, letterSpacing=1.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically){
                    listOf(0,1,2).forEach { idx ->
                        val selected = idx == style
                        Box(
                            Modifier.size(48.dp)
                                .background(if(selected) MonetColors.Primary.copy(0.10f) else MonetColors.Primary.copy(0.03f), RoundedCornerShape(8.dp))
                                .border(if(selected) 1.5.dp else 0.6.dp,
                                    if(selected) MonetColors.Primary else MonetColors.Primary.copy(0.2f), RoundedCornerShape(8.dp))
                                .clickable { onStyle(idx) },
                            contentAlignment = Alignment.Center
                        ){
                            Canvas(Modifier.size(42.dp)){
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val u = previewScale
                                when(idx){
                                    0 -> {
                                        drawCircle(previewColor, 1.5f*u, Offset(cx,cy))
                                        drawLine(previewColor, Offset(cx-13*u,cy), Offset(cx-3*u,cy), 1.5f*u)
                                        drawLine(previewColor, Offset(cx+3*u,cy), Offset(cx+13*u,cy), 1.5f*u)
                                        drawLine(previewColor, Offset(cx,cy-13*u), Offset(cx,cy-3*u), 1.5f*u)
                                        drawLine(previewColor, Offset(cx,cy+3*u), Offset(cx,cy+13*u), 1.5f*u)
                                    }
                                    1 -> {
                                        drawCircle(previewColor, 3f*u, Offset(cx,cy))
                                        drawCircle(previewColor.copy(alpha=0.3f), 7f*u, Offset(cx,cy), style=Stroke(1.2f*u))
                                    }
                                    2 -> {
                                        drawCircle(previewColor, 9f*u, Offset(cx,cy), style=Stroke(1.8f*u))
                                        drawCircle(previewColor, 2f*u, Offset(cx,cy))
                                    }
                                }
                            }
                        }
                    }
                    Box(
                        Modifier.size(48.dp).background(MonetColors.Primary.copy(0.03f), RoundedCornerShape(8.dp))
                            .border(0.6.dp, MonetColors.Primary.copy(0.2f), RoundedCornerShape(8.dp)).clickable { onToggleOff() },
                        contentAlignment = Alignment.Center
                    ){
                        Canvas(Modifier.size(30.dp)){
                            val cx = size.width / 2f; val cy = size.height / 2f; val p = size.width * 0.28f
                            drawLine(MonetColors.Primary, Offset(cx-p,cy-p), Offset(cx+p,cy+p), 2.2f.dp.toPx(), cap=StrokeCap.Round)
                            drawLine(MonetColors.Primary, Offset(cx+p,cy-p), Offset(cx-p,cy+p), 2.2f.dp.toPx(), cap=StrokeCap.Round)
                        }
                    }
                }
                Text("COLOR", color=MonetColors.Muted, fontSize=9.sp, fontWeight=FontWeight.Bold, fontFamily=FontFamily.Monospace)
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically){
                    colors.forEach { (idx, color) ->
                        val selected = idx == colorIdx
                        Box(
                            Modifier.size(30.dp).padding(1.dp)
                                .background(color, CircleShape)
                                .border(if(selected) 2.5.dp else 0.8.dp, if(selected) Color.White else MonetColors.Outline.copy(alpha=0.35f), CircleShape)
                                .clickable { onColor(idx) }
                        )
                    }
                }
                Text("CROSSHAIR SIZE  ${"%.1f".format(java.util.Locale.US, previewScale)}x", color=MonetColors.Primary,
                    fontSize=10.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace)
                Slider(
                    value = previewScale, onValueChange = onScale, valueRange = 0.5f..2.0f, steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = MonetColors.Primary,
                        activeTrackColor = MonetColors.Primary,
                        inactiveTrackColor = MonetColors.Outline.copy(alpha = 0.35f)
                    )
                )
                // Larger live preview in the center, independent of the icon-sized style samples above.
                Canvas(Modifier.size(60.dp)){
                    val cx=size.width/2f; val cy=size.height/2f; val unit=(size.minDimension/60f)*previewScale
                    when(style){
                        0 -> {
                            drawCircle(previewColor, 2f*unit, Offset(cx,cy))
                            drawLine(previewColor, Offset(cx-20f*unit,cy), Offset(cx-5f*unit,cy), 2f*unit)
                            drawLine(previewColor, Offset(cx+5f*unit,cy), Offset(cx+20f*unit,cy), 2f*unit)
                            drawLine(previewColor, Offset(cx,cy-20f*unit), Offset(cx,cy-5f*unit), 2f*unit)
                            drawLine(previewColor, Offset(cx,cy+5f*unit), Offset(cx,cy+20f*unit), 2f*unit)
                        }
                        1 -> { drawCircle(previewColor,4f*unit,Offset(cx,cy)); drawCircle(previewColor.copy(alpha=0.3f),10f*unit,Offset(cx,cy),style=Stroke(1.5f*unit)) }
                        2 -> { drawCircle(previewColor,14f*unit,Offset(cx,cy),style=Stroke(2.5f*unit)); drawCircle(previewColor,2.5f*unit,Offset(cx,cy)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(Modifier.weight(1f).height(36.dp).background(MonetColors.Primary.copy(0.08f), RoundedCornerShape(6.dp))
                        .border(0.8.dp, MonetColors.Primary.copy(0.35f), RoundedCornerShape(6.dp)).clickable { onCancel() }, contentAlignment=Alignment.Center){
                        Text("CANCEL", color=MonetColors.Primary.copy(0.85f), fontSize=10.sp, fontWeight=FontWeight.Bold, fontFamily=FontFamily.Monospace)
                    }
                    Box(Modifier.weight(1f).height(36.dp).background(MonetColors.Primary.copy(0.22f), RoundedCornerShape(6.dp))
                        .border(1.2.dp, MonetColors.Primary, RoundedCornerShape(6.dp)).clickable { onSave() }, contentAlignment=Alignment.Center){
                        Text("SAVE", color=MonetColors.Primary, fontSize=10.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HudMarqueeLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = text,
            modifier = modifier.basicMarquee(velocity = 9.dp),
            color = color, fontSize = 7.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HudSquareIconButton(
    title: String,
    iconKind: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val bg by animateColorAsState(
        targetValue = if (isActive) MonetColors.Primary else MonetColors.Inactive.copy(alpha = 0.94f),
        animationSpec = tween(180), label = "hud-icon-bg-$title"
    )
    val stroke by animateColorAsState(
        targetValue = if (isActive) MonetColors.Secondary else MonetColors.Outline.copy(alpha = 0.72f),
        animationSpec = tween(180), label = "hud-icon-stroke-$title"
    )
    val tint = if (isActive) MonetColors.OnPrimary else MonetColors.Primary
    val actionModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(1f)
            .semantics { contentDescription = title }
            .shadow(if (isActive) 8.dp else 2.dp, RoundedCornerShape(12.dp), ambientColor = MonetColors.Primary, spotColor = MonetColors.Primary)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.2.dp, stroke, RoundedCornerShape(12.dp))
            .then(actionModifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MonetCanvasIcon(iconKind, Modifier.size(32.dp), tint)
            Text(
                title,
                modifier = Modifier.fillMaxWidth(),
                color = tint,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 10.sp,
                overflow = TextOverflow.Clip
            )
        }
        if (isActive) {
            Canvas(Modifier.align(Alignment.TopEnd).padding(6.dp).size(6.dp)) { drawCircle(MonetColors.OnPrimary) }
        }
    }
}

@Composable
fun RunningAppsSidebarWidget(context: Context) {
    var isExpanded by remember { mutableStateOf(false) }
    var runningApps by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var isFetching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchRunningApps() {
        scope.launch {
            isFetching = true
            val cmd = "ps -A -o NAME,RSS | grep -v '\\[' | sort -k2 -nr | head -n 10"
            val result = withContext(Dispatchers.IO) {
                try { TurboSpaceManager.executeCommandDetailedNoCtx(cmd) } catch (_: Throwable) { null }
            }
            if (result != null && result.success) {
                val lines = result.output.lines().drop(1).filter { it.isNotBlank() }
                val apps = lines.mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val pkg = parts[0]
                        val rssKb = parts[1].toLongOrNull() ?: 0L
                        val rssMb = (rssKb / 1024).toInt()
                        if (pkg.contains(".") && !pkg.contains("turbospace") && rssMb > 0) pkg to rssMb else null
                    } else null
                }
                runningApps = apps
            }
            isFetching = false
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MonetColors.Background.copy(alpha = 0.86f))
            .border(1.dp, MonetColors.Outline.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable {
                isExpanded = !isExpanded
                if (isExpanded) fetchRunningApps()
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("RUNNING APPS", color = MonetColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(if (isExpanded) "▲" else "▼", color = MonetColors.Secondary, fontSize = 10.sp)
        }

        if (isExpanded) {
            if (isFetching) {
                Text("Scanning...", color = MonetColors.Muted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            } else if (runningApps.isEmpty()) {
                Text("No background apps", color = MonetColors.Muted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            } else {
                Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    runningApps.forEach { (pkg, mb) ->
                        val progress = (mb / 500f).coerceIn(0f, 1f)
                        val barColor = when {
                            progress < 0.3f -> Color(0xFF00C978)
                            progress < 0.7f -> Color(0xFFFF8A00)
                            else -> Color(0xFFE83E9D)
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable {
                                scope.launch {
                                    withContext(Dispatchers.IO) { TurboSpaceManager.executeCommandDetailedNoCtx("am force-stop $pkg") }
                                    TurboSpaceRepository.saveLog(context, "Killed: $pkg")
                                    fetchRunningApps()
                                }
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImageRaw(context, pkg, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(pkg.substringAfterLast("."), color = MonetColors.Primary, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                                    drawRoundRect(Color(0xFF4C4F59), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                                    drawRoundRect(barColor, size = Size(size.width * progress, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("${mb}M", color = MonetColors.Muted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullHudOverlayFinalNew(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showCrosshairPicker by remember { mutableStateOf(false) }
    var pickerStyle by remember { mutableStateOf(TurboSpaceRepository.crosshairStyle.value) }
    var pickerColorIdx by remember { mutableStateOf(TurboSpaceRepository.crosshairColorIdx.value) }
    var pickerScale by remember { mutableFloatStateOf(TurboSpaceRepository.crosshairScale) }
    var showFpsBoosterDialog by remember { mutableStateOf(false) }
    var fpsBoosterActive by remember {
        mutableStateOf(context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).getBoolean("fps_booster_active", false))
    }
    var showStatusStrip by remember { mutableStateOf(TurboSpaceRepository.fpsFloatingEnabled) }
    var activeModules by remember { mutableStateOf(TurboSpaceRepository.activeModules.value) }
    var isWifiBoosted by remember { mutableStateOf(false) }
    var lastRamFreedPercent by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        isWifiBoosted = try {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                "wifi_scan_always_enabled",
                1
            ) == 0
        } catch (_: Throwable) { false }
    }

    // DND filters remain mutually exclusive: MUTE = NONE, ALERTS = PRIORITY.
    var isSilent by remember { mutableStateOf(false) }
    var isPriority by remember { mutableStateOf(false) }

    var timerRunning by remember { mutableStateOf(TurboSpaceRepository.stopwatchRunning) }
    var timerSeconds by remember { mutableIntStateOf((TurboSpaceRepository.stopwatchElapsedAt() / 1000L).toInt()) }

    // Slide-left + fade-out state.
    var isVisible by remember { mutableStateOf(true) }
    val slideOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else -1f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "hud_slide"
    )

    fun closeHud() {
        if (!isVisible) return
        isVisible = false
        scope.launch {
            delay(300)
            onDismiss()
        }
    }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            TurboSpaceRepository.stopwatchStartedAtMs = System.currentTimeMillis() - (timerSeconds * 1000L)
            TurboSpaceRepository.stopwatchRunning = true
            TurboSpaceRepository.persistStopwatch(context)
            while (timerRunning) {
                delay(1000)
                timerSeconds++
                TurboSpaceRepository.stopwatchElapsedMs = timerSeconds * 1000L
            }
            TurboSpaceRepository.stopwatchRunning = false
            TurboSpaceRepository.stopwatchElapsedMs = timerSeconds * 1000L
            TurboSpaceRepository.persistStopwatch(context)
        }
    }

    LaunchedEffect(context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            isSilent = manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
            isPriority = manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } catch (_: Throwable) {
            isSilent = false
            isPriority = false
        }
    }

    // Preserve the original MEM BOOST behavior: Shizuku permission check,
    // am kill-all result verification, RAM measurement, logging and feedback.
    fun executeRamBooster() {
        scope.launch {
            if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
                TurboSpaceRepository.saveLog(context, "ERROR: RAM Booster — Shizuku permission missing")
                android.widget.Toast.makeText(context, "ERROR: Shizuku required", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@withContext Triple(false, 0, "ActivityManager unavailable")
                val before = ActivityManager.MemoryInfo()
                try {
                    activityManager.getMemoryInfo(before)
                } catch (t: Throwable) {
                    return@withContext Triple(false, 0, t.message ?: "MemoryInfo failed")
                }
                val totalMem = before.totalMem.coerceAtLeast(1L)
                val beforeAvail = before.availMem
                val command = try {
                    TurboSpaceManager.executeCommandDetailedNoCtx("am kill-all")
                } catch (t: Throwable) {
                    return@withContext Triple(false, 0, t.message ?: "command exception")
                }
                if (!command.success || command.exitCode != 0) {
                    return@withContext Triple(false, 0, command.error.ifBlank { command.output.trim() })
                }
                delay(1000)
                val after = ActivityManager.MemoryInfo()
                try {
                    activityManager.getMemoryInfo(after)
                } catch (t: Throwable) {
                    return@withContext Triple(false, 0, t.message ?: "MemoryInfo failed after clean")
                }
                val freedBytes = after.availMem - beforeAvail
                val freedPercent = ((freedBytes.toFloat() / totalMem.toFloat()) * 100f).toInt().coerceAtLeast(0)
                Triple(true, freedPercent, "")
            }
            withContext(Dispatchers.Main) {
                if (result.first && result.second > 0) {
                    lastRamFreedPercent = result.second
                }
                if (!result.first) {
                    TurboSpaceRepository.saveLog(context, "ERROR: RAM Booster failed")
                    android.widget.Toast.makeText(context, "ERROR: RAM Booster failed", android.widget.Toast.LENGTH_SHORT).show()
                } else if (result.second == 0) {
                    TurboSpaceRepository.saveLog(context, "SUCCESS: RAM Already Optimized")
                    android.widget.Toast.makeText(context, "SUCCESS: RAM Already Optimized", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    TurboSpaceRepository.saveLog(context, "SUCCESS: RAM Cleaned — Freed ${result.second}%")
                    android.widget.Toast.makeText(context, "SUCCESS: RAM Cleaned! Freed ${result.second}%", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Toggle Wi-Fi scan optimization through Shizuku.
    fun toggleWifiBoost() {
        scope.launch {
            if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
                android.widget.Toast.makeText(
                    context,
                    "ERROR: Shizuku required",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val nextState = !isWifiBoosted
            val cmd = if (nextState) {
                "settings put global wifi_scan_always_enabled 0"
            } else {
                "settings put global wifi_scan_always_enabled 1"
            }

            val result = try {
                withContext(Dispatchers.IO) {
                    TurboSpaceManager.executeCommandDetailedNoCtx(cmd)
                }
            } catch (_: Throwable) {
                null
            }

            if (result != null && result.success && result.exitCode == 0) {
                isWifiBoosted = nextState
                android.widget.Toast.makeText(
                    context,
                    if (nextState) "SUCCESS: WiFi Boosted" else "SUCCESS: WiFi Restored",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(
                    context,
                    "ERROR: WiFi command failed",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Compact horizontal button used throughout the HyperOS-style HUD.
    @Composable
    fun HudHorizontalButton(
        title: String,
        subtitle: String,
        iconKind: String,
        isActive: Boolean,
        onClick: () -> Unit
    ) {
        val bgColor = if (isActive) {
            Color(0xFFE83E9D).copy(alpha = 0.15f)
        } else {
            Color(0xFF2B2930).copy(alpha = 0.85f)
        }
        val borderColor = if (isActive) Color(0xFFE83E9D) else Color.Transparent
        val tintColor = if (isActive) Color(0xFFE83E9D) else Color.White

        Row(
            Modifier.fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonetCanvasIcon(iconKind, Modifier.size(20.dp), tintColor)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = tintColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    color = if (isActive) Color(0xFFE83E9D).copy(alpha = 0.7f) else Color.Gray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(MonetColors.Background.copy(alpha = 0.15f * (1f - kotlin.math.abs(slideOffset))))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { closeHud() },
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    translationX = slideOffset * size.width
                    alpha = (1f + slideOffset).coerceIn(0f, 1f)
                }
                .fillMaxWidth(0.55f)
                .fillMaxHeight(0.85f)
                .padding(start = 12.dp, top = 16.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF5242029), Color(0xF01A181C))))
                .border(1.5.dp, MonetColors.Primary.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* Consume panel taps so only outside taps close the HUD. */ },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // HEADER: live RAM usage and CPU temperature.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TURBO HUD",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ram = try {
                        TurboSpaceManager.getRamUsagePercent(context).coerceIn(0, 100)
                    } catch (_: Throwable) {
                        0
                    }
                    val temp = try {
                        TurboSpaceManager.getCpuTemp()
                    } catch (_: Throwable) {
                        "N/A"
                    }
                    Text(
                        "RAM: $ram%",
                        color = Color(0xFFD0BCFF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "TEMP: $temp",
                        color = Color(0xFFFF8A00),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Segmented performance mode selector.
            val selectedMode = TurboSpaceRepository.preLaunchMode
            Row(
                Modifier.fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1A181C)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("COOL", "BALANCED", "FAST").forEach { mode ->
                    val isSelected = selectedMode == mode
                    val color = when (mode) {
                        "COOL" -> Color(0xFF1689FF)
                        "FAST" -> Color(0xFFFF8A00)
                        else -> Color(0xFF00C978)
                    }
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { TurboSpaceRepository.preLaunchMode = mode },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            mode,
                            color = if (isSelected) color else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ROW 1: MUTE and PRIORITY alerts.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    HudHorizontalButton("MUTE", "Silence all", "bell_slash", isSilent, onClick = {
                        try {
                            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            if (manager.isNotificationPolicyAccessGranted) {
                                if (isSilent) {
                                    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                                    isSilent = false
                                } else {
                                    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                                    isSilent = true
                                    isPriority = false
                                }
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } catch (_: Throwable) {
                            android.widget.Toast.makeText(context, "ERROR: MUTE MODE", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                }
                Box(Modifier.weight(1f)) {
                    HudHorizontalButton("ALERTS", "Priority only", "shield", isPriority, onClick = {
                        try {
                            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            if (manager.isNotificationPolicyAccessGranted) {
                                if (isPriority) {
                                    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                                    isPriority = false
                                } else {
                                    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                                    isPriority = true
                                    isSilent = false
                                }
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } catch (_: Throwable) {
                            android.widget.Toast.makeText(context, "ERROR: PRIORITY MODE", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }

            // ROW 2: MAX FPS and MONITOR.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    HudHorizontalButton("MAX FPS", "Unlock rate", "fps", fpsBoosterActive, onClick = {
                        showFpsBoosterDialog = true
                    })
                }
                Box(Modifier.weight(1f)) {
                    HudHorizontalButton("MONITOR", "Show stats", "signal", showStatusStrip, onClick = {
                        val next = !showStatusStrip
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                androidx.core.content.ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, GameSpaceOverlayService::class.java)
                                        .setAction(GameSpaceOverlayService.ACTION_SET_FPS_FLOATING)
                                        .putExtra(GameSpaceOverlayService.EXTRA_ENABLED, next)
                                )
                            } else {
                                context.startService(
                                    Intent(context, GameSpaceOverlayService::class.java)
                                        .setAction(GameSpaceOverlayService.ACTION_SET_FPS_FLOATING)
                                        .putExtra(GameSpaceOverlayService.EXTRA_ENABLED, next)
                                )
                            }
                            showStatusStrip = next
                            TurboSpaceRepository.setFpsFloatingEnabled(context, next)
                        } catch (_: Throwable) {
                            android.widget.Toast.makeText(context, "ERROR: MONITOR", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }

            // ROW 3: CROSSHAIR and RAM CLEAN.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    HudHorizontalButton(
                        "CROSSHAIR",
                        "Aim assist",
                        "crosshair",
                        TurboSpaceRepository.crosshairActive.value,
                        onClick = {
                            if (TurboSpaceRepository.crosshairActive.value) {
                                TurboSpaceRepository.setCrosshairActive(false)
                                TurboSpaceRepository.activeModules.value = TurboSpaceRepository.activeModules.value - "CROSSHAIR"
                                activeModules = TurboSpaceRepository.activeModules.value
                                TurboSpaceRepository.savePrefs(context)
                            } else {
                                pickerStyle = TurboSpaceRepository.crosshairStyle.value
                                pickerColorIdx = TurboSpaceRepository.crosshairColorIdx.value
                                pickerScale = TurboSpaceRepository.crosshairScale
                                showCrosshairPicker = true
                            }
                        }
                    )
                }
                Box(Modifier.weight(1f)) {
                    HudHorizontalButton("RAM CLEAN", if (lastRamFreedPercent > 0) "Freed ${lastRamFreedPercent}%" else "Kill apps", "ram", false, onClick = {
                        executeRamBooster()
                    })
                }
            }

            // ROW 4: TIMER and WIFI side by side.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    HudHorizontalButton(
                        if (timerRunning || timerSeconds > 0) "STOP" else "TIMER",
                        if (timerSeconds > 0) {
                            String.format(java.util.Locale.US, "%02d:%02d", timerSeconds / 60, timerSeconds % 60)
                        } else "Keep time",
                        "timer",
                        timerRunning,
                        onClick = {
                            if (timerSeconds > 0 && !timerRunning) {
                                timerSeconds = 0
                                TurboSpaceRepository.stopwatchElapsedMs = 0L
                                TurboSpaceRepository.stopwatchRunning = false
                                TurboSpaceRepository.stopwatchStartedAtMs = 0L
                                TurboSpaceRepository.persistStopwatch(context)
                            } else {
                                if (timerRunning) {
                                    TurboSpaceRepository.stopwatchElapsedMs = timerSeconds * 1000L
                                    TurboSpaceRepository.stopwatchRunning = false
                                    TurboSpaceRepository.persistStopwatch(context)
                                }
                                timerRunning = !timerRunning
                            }
                        }
                    )
                }
                Box(Modifier.weight(1f)) {
                    HudHorizontalButton(
                        "WIFI",
                        if (isWifiBoosted) "Background scan reduced" else "Optimize Wi-Fi",
                        "wifi",
                        isWifiBoosted,
                        onClick = { toggleWifiBoost() }
                    )
                }
            }
        }
    }

    if (showFpsBoosterDialog) {
        FpsBoosterDialog(
            onDismiss = { showFpsBoosterDialog = false },
            onApplied = { hz, success ->
                fpsBoosterActive = success
                val prefs = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("fps_booster_active", success).apply()
                if (success) prefs.edit().putInt("fps_refresh_rate", hz).apply()
            }
        )
    }

    if (showCrosshairPicker) {
        CrosshairPickerDialog(
            style = pickerStyle,
            colorIdx = pickerColorIdx,
            scale = pickerScale,
            onStyle = { pickerStyle = it },
            onColor = { pickerColorIdx = it },
            onScale = { pickerScale = it.coerceIn(0.5f, 2.0f) },
            onCancel = { showCrosshairPicker = false },
            onToggleOff = {
                TurboSpaceRepository.setCrosshairActive(false)
                TurboSpaceRepository.activeModules.value = TurboSpaceRepository.activeModules.value - "CROSSHAIR"
                activeModules = TurboSpaceRepository.activeModules.value
                TurboSpaceRepository.savePrefs(context)
                showCrosshairPicker = false
            },
            onSave = {
                TurboSpaceRepository.setCrosshairStyle(pickerStyle)
                TurboSpaceRepository.setCrosshairColorIdx(pickerColorIdx)
                TurboSpaceRepository.applyCrosshairScale(pickerScale)
                TurboSpaceRepository.setCrosshairActive(true)
                TurboSpaceRepository.markModuleActive("CROSSHAIR")
                activeModules = TurboSpaceRepository.activeModules.value
                TurboSpaceRepository.savePrefs(context)
                showCrosshairPicker = false
            }
        )
    }
}

@Composable
fun OverlayCloseConfirmDialogNew(onConfirm: () -> Unit, onCancel: () -> Unit){
    Box(
        Modifier.fillMaxSize()
            .background(MonetColors.Background.copy(alpha=0.82f))
            .clickable{ onCancel() },
        contentAlignment=Alignment.Center
    ){
        Box(
            Modifier.fillMaxWidth(0.78f)
                .background(CyberDesign.DeepDark85, RoundedCornerShape(12.dp))
                .border(1.2.dp, MonetColors.Primary.copy(alpha=0.55f), RoundedCornerShape(12.dp))
                .shadow(18.dp, RoundedCornerShape(12.dp), ambientColor=MonetColors.Primary, spotColor=MonetColors.Primary)
                .padding(18.dp)
                .clickable(enabled=false){}
        ){
            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("CLOSE BAR", color=Color(0xFFE83E9D), fontSize=13.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace, letterSpacing=1.sp)
                Text(
                    "Do you want to close the bar?",
                    color=Color(0xFFE83E9D),
                    fontSize=9.sp,
                    fontFamily=FontFamily.Monospace,
                    textAlign=TextAlign.Center,
                    lineHeight=13.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .background(MonetColors.Primary.copy(0.08f), RoundedCornerShape(6.dp))
                            .border(0.8.dp, MonetColors.Primary.copy(0.35f), RoundedCornerShape(6.dp))
                            .clickable{ onCancel() },
                        contentAlignment=Alignment.Center
                    ){ Text("Cancel", color=MonetColors.Primary.copy(0.8f), fontSize=10.sp, fontWeight=FontWeight.Bold, fontFamily=FontFamily.Monospace) }
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .background(MonetColors.Secondary.copy(0.18f), RoundedCornerShape(6.dp))
                            .border(1.6.dp, Color(0xFFE83E9D), RoundedCornerShape(6.dp))
                            .clickable{ onConfirm() },
                        contentAlignment=Alignment.Center
                    ){ Text("OK", color=Color(0xFFE83E9D), fontSize=11.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace) }
                }
            }
        }
    }
}


@Composable
fun GameModeUnsupportedDialog(onDismiss: () -> Unit){
    Box(
        Modifier.fillMaxSize().background(MonetColors.Background.copy(alpha=0.82f)).clickable{ onDismiss() },
        contentAlignment = Alignment.Center
    ){
        Box(
            Modifier.fillMaxWidth(0.82f).background(CyberDesign.DeepDark85, RoundedCornerShape(12.dp))
                .border(1.2.dp, MonetColors.Secondary, RoundedCornerShape(12.dp))
                .shadow(16.dp, RoundedCornerShape(12.dp), ambientColor=MonetColors.Secondary, spotColor=MonetColors.Secondary)
                .padding(18.dp).clickable {}
        ){
            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(12.dp)){
                BlueLightningIconCustom(Modifier.size(44.dp))
                Text("GAME MODE", color=MonetColors.Secondary, fontSize=13.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace, letterSpacing=1.sp)
                Text(
                    "This device does not support Android Game Mode API.\nCommand 'cmd game' is unavailable.\nRequires Android 12+ with OEM support.",
                    color=MonetColors.Primary.copy(0.9f), fontSize=9.sp, fontFamily=FontFamily.Monospace,
                    textAlign=TextAlign.Center, lineHeight=13.sp
                )
                Box(
                    Modifier.fillMaxWidth().height(40.dp).background(MonetColors.Secondary.copy(0.18f), RoundedCornerShape(6.dp))
                        .border(1.2.dp, MonetColors.Secondary, RoundedCornerShape(6.dp))
                        .clickable{ onDismiss() }, contentAlignment=Alignment.Center
                ){ Text("OK", color=MonetColors.Primary, fontSize=11.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace) }
            }
        }
    }
}

@Composable
fun AppIconImageRaw(context: Context, pkg: String, modifier: Modifier = Modifier) {
    val iconBitmap = remember(context.applicationContext, pkg) {
        try {
            val drawable = context.packageManager.getApplicationIcon(pkg)
            // Keep per-app icon bitmaps small for low-memory devices such as Realme 5i.
            val width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 48).coerceAtMost(256).coerceAtLeast(1)
            val height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 48).coerceAtMost(256).coerceAtLeast(1)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: OutOfMemoryError) {
            // Return null so the existing fallback game icon is rendered instead.
            null
        } catch (_: Throwable) {
            null
        }
    }
    if (iconBitmap != null) {
        Image(bitmap = iconBitmap, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(6.dp)))
    } else {
        Box(modifier.background(MonetColors.Inactive, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            MonetCanvasIcon("game", Modifier.fillMaxSize(0.68f), MonetColors.Secondary)
        }
    }
}




@Composable
fun RedGamepadIconCustom(
    modifier: Modifier = Modifier,
    iconColor: Color = MonetColors.Primary
) {
    Canvas(modifier) {
        val red = iconColor
        val w = size.width
        val h = size.height
        // Gamepad body - custom path
        val bodyW = w * 0.9f
        val bodyH = h * 0.6f
        val bodyTop = h * 0.25f
        val corner = w * 0.18f
        // Main body round rect
        drawRoundRect(color = red, topLeft = Offset((w-bodyW)/2, bodyTop), size = Size(bodyW, bodyH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner), style = Stroke(width = 2.2f.dp.toPx()))
        // Left grip extra
        drawCircle(color = red, radius = 3.5f.dp.toPx(), center = Offset(w*0.32f, bodyTop + bodyH*0.35f), style = Stroke(width = 1.8f.dp.toPx()))
        drawCircle(color = red, radius = 3.5f.dp.toPx(), center = Offset(w*0.68f, bodyTop + bodyH*0.35f), style = Stroke(width = 1.8f.dp.toPx()))
        // D-pad plus - left side
        val plusCenter = Offset(w*0.33f, bodyTop + bodyH*0.55f)
        drawLine(color = red, start = Offset(plusCenter.x - 6.dp.toPx(), plusCenter.y), end = Offset(plusCenter.x + 6.dp.toPx(), plusCenter.y), strokeWidth = 1.8f.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = red, start = Offset(plusCenter.x, plusCenter.y - 6.dp.toPx()), end = Offset(plusCenter.x, plusCenter.y + 6.dp.toPx()), strokeWidth = 1.8f.dp.toPx(), cap = StrokeCap.Round)
        // Buttons right side - 4 dots triangle
        val btnCenter = Offset(w*0.67f, bodyTop + bodyH*0.55f)
        drawCircle(color = red, radius = 1.8f.dp.toPx(), center = Offset(btnCenter.x, btnCenter.y - 5.dp.toPx()))
        drawCircle(color = red, radius = 1.8f.dp.toPx(), center = Offset(btnCenter.x, btnCenter.y + 5.dp.toPx()))
        drawCircle(color = red, radius = 1.8f.dp.toPx(), center = Offset(btnCenter.x - 5.dp.toPx(), btnCenter.y))
        drawCircle(color = red, radius = 1.8f.dp.toPx(), center = Offset(btnCenter.x + 5.dp.toPx(), btnCenter.y))
    }
}

@Composable
fun BlueLightningIconCustom(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val blue = MonetColors.Secondary
        val w = size.width
        val h = size.height
        // Lightning bolt path - hand drawn
        val path = Path().apply {
            moveTo(w*0.58f, h*0.08f)
            lineTo(w*0.32f, h*0.48f)
            lineTo(w*0.48f, h*0.48f)
            lineTo(w*0.38f, h*0.92f)
            lineTo(w*0.68f, h*0.42f)
            lineTo(w*0.48f, h*0.42f)
            close()
        }
        drawPath(path = path, color = blue, style = Stroke(width = 2.2f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Inner fill faint
        drawPath(path = path, color = blue.copy(alpha = 0.18f))
        // Glow dots
        drawCircle(color = blue.copy(alpha = 0.9f), radius = 2f.dp.toPx(), center = Offset(w*0.5f, h*0.5f))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CyberModuleCard10New(
    module: CyberModule,
    isActive: Boolean,
    glowIntensity: Float,
    onToggle: (Boolean, Boolean)->Unit,
    onMemBoostClick: () -> Unit = {},
    onTerminalLog: (String) -> Unit = {},
    onCrosshairClick: () -> Unit = {},
    modifier: Modifier = Modifier
){
    var isExecuting by remember { mutableStateOf(false) }
    var showCommandHelp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isReset = module.iconType == 10
    val isAction = module.iconType in setOf(10,12,13)
    val activeTint = MonetColors.Primary
    val inactiveTint = MonetColors.Muted.copy(alpha = 0.60f)
    val iconTint by animateColorAsState(if (isActive) MonetColors.OnPrimary else inactiveTint, tween(180), label="module-icon-tint")
    val bgColor by animateColorAsState(
        when { isReset -> MonetColors.Inactive; isActive -> MonetColors.Primary; else -> MonetColors.Inactive.copy(alpha = 0.78f) },
        tween(180), label="module-bg"
    )
    val borderColor by animateColorAsState(
        when { isActive -> MonetColors.Secondary; isReset -> MonetColors.Outline; else -> MonetColors.Outline.copy(alpha = 0.62f) },
        tween(180), label="module-border"
    )
    val titleColor by animateColorAsState(if (isActive) MonetColors.OnPrimary else inactiveTint, tween(180), label="module-title")

    Box(
        modifier.height(40.dp).fillMaxWidth()
            .background(bgColor, RoundedCornerShape(5.dp))
            .border(0.8.dp, borderColor, RoundedCornerShape(5.dp))
            .shadow(if(isActive && !isAction) 3.dp else 0.dp, RoundedCornerShape(5.dp), ambientColor=MonetColors.Secondary, spotColor=MonetColors.Secondary)
            .combinedClickable(
                enabled = !isExecuting,
                onLongClick = { showCommandHelp = true },
                onClick = {
                    if (isExecuting) return@combinedClickable
                    if (module.commandTemplate == "am kill-all") {
                        onTerminalLog("> MEM BOOST: SELECT APPS TO TRIM")
                        onMemBoostClick()
                        return@combinedClickable
                    }
                    if (module.commandTemplate == "TOGGLE_CROSSHAIR") {
                        if (isActive || TurboSpaceRepository.crosshairActive.value) {
                            TurboSpaceRepository.setCrosshairActive(false)
                            TurboSpaceRepository.activeModules.value = TurboSpaceRepository.activeModules.value - module.title - "CROSSHAIR"
                            onToggle(false, false)
                            TurboSpaceRepository.savePrefs(context)
                            onTerminalLog("> CROSSHAIR: OFF")
                        } else {
                            onTerminalLog("> CROSSHAIR: OPEN STYLE PICKER")
                            onCrosshairClick()
                        }
                        return@combinedClickable
                    }

                    isExecuting = true
                    scope.launch {
                        try {
                            if (isReset) {
                                val pkg = resolveTargetPkg()
                                val validPkg = pkg != "NO TARGET SELECTED" && pkg.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))
                                val resetSucceeded = withContext(Dispatchers.IO) {
                                    performFullReset(context, pkg)
                                }
                                if (resetSucceeded) {
                                    applyFullResetUiState(context)
                                    if (validPkg) {
                                        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
                                            .putString("game_mode_category_$pkg", "standard")
                                            .putString("compile_mode_$pkg", "reset")
                                            .putString("game_downscale_$pkg", "disable")
                                            .apply()
                                    }
                                }
                                onToggle(resetSucceeded, true)
                                android.widget.Toast.makeText(context, if (resetSucceeded) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                                onTerminalLog(if (resetSucceeded) "> RESET ALL: COMPLETED" else "> RESET ALL: ERROR")
                            } else {
                                if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
                                    onToggle(false, false)
                                    onTerminalLog("> ERROR: Shizuku permission unavailable")
                                    return@launch
                                }
                                val needsPkg = module.commandTemplate.contains("\$pkg") || module.restoreTemplate.contains("\$pkg")
                                val pkg = if (needsPkg) resolveTargetPkg() else ""
                                if (needsPkg && (pkg == "NO TARGET SELECTED" || !pkg.contains("."))) {
                                    onToggle(false, false)
                                    onTerminalLog("> ERROR: No game selected")
                                    return@launch
                                }
                                val template = if (isActive) module.restoreTemplate else module.commandTemplate
                                val finalCmd = if (needsPkg) template.replace("\$pkg", pkg) else template
                                val result = TurboSpaceManager.executeCommandDetailedNoCtx(finalCmd)
                                val success = result.success && result.exitCode == 0
                                onToggle(success, false)
                                android.widget.Toast.makeText(context, if (success) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                                val action = if (isActive) "OFF" else "ON"
                                if (success) onTerminalLog("> ${module.title} $action: SUCCESS${if (pkg.isNotBlank()) " ($pkg)" else ""}")
                                else {
                                    val detail = result.error.ifBlank { result.output.trim() }.take(160)
                                    onTerminalLog("> ${module.title} $action: FAILED${if(detail.isNotBlank()) " — $detail" else ""}")
                                }
                            }
                        } catch (e: Throwable) {
                            onToggle(false, false)
                            android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                            onTerminalLog("> ${module.title}: ERROR ${e.message ?: "unknown error"}")
                        } finally { isExecuting = false }
                    }
                }
            )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 1.dp),
            verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                VectorIconPerSpecNew(module.iconType, iconTint, isActive)
            }
            HudMarqueeLabel(module.title, titleColor, Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().height(5.dp)) {
                if (isAction) {
                    Canvas(Modifier.size(4.dp).align(Alignment.CenterEnd)) { drawCircle(if (isActive) activeTint else inactiveTint) }
                } else {
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (isActive) "ON" else "OFF", color = titleColor, fontSize = 5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                        Canvas(Modifier.size(4.dp)) { drawCircle(if (isActive) activeTint else inactiveTint.copy(alpha = 0.45f)) }
                    }
                }
            }
        }
        if (isExecuting) {
            Box(Modifier.fillMaxSize().background(MonetColors.Background.copy(0.55f), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                Text("EXEC", color = activeTint, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        }
    }

    if (showCommandHelp) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCommandHelp = false }) {
            Column(Modifier.fillMaxWidth().background(CyberDesign.DeepDark85, RoundedCornerShape(12.dp))
                .border(1.dp, MonetColors.Primary.copy(0.65f), RoundedCornerShape(12.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(module.title + " / COMMAND DETAILS", color=MonetColors.Primary, fontSize=12.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace)
                Column(Modifier.heightIn(max=260.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("ON / COMMAND:\n${module.commandTemplate}", color=MonetColors.Secondary, fontSize=10.sp, fontFamily=FontFamily.Monospace)
                    Text("OFF / RESTORE:\n${module.restoreTemplate}", color=MonetColors.Primary.copy(0.8f), fontSize=10.sp, fontFamily=FontFamily.Monospace)
                }
                Box(Modifier.fillMaxWidth().height(36.dp).background(MonetColors.Primary.copy(0.08f), RoundedCornerShape(6.dp)).clickable { showCommandHelp = false }, contentAlignment=Alignment.Center) {
                    Text("CLOSE", color=MonetColors.Primary, fontSize=10.sp, fontWeight=FontWeight.Bold, fontFamily=FontFamily.Monospace)
                }
            }
        }
    }
}

// ==================== PERMISSION ONBOARDING & MAIN DASHBOARD ====================

// ==================== NEW MAIN DASHBOARD - OPTIMIZED V2 - NO + BUTTON, SHOW ALL GAMES IMMEDIATELY ====================
@Composable
fun MainMissionControlDashboard() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val selectedGame by TurboSpaceRepository.selectedGame.collectAsState()
    val favoritePackages by TurboSpaceRepository.favoritePackages.collectAsState()
    val recentPackages by TurboSpaceRepository.recentPackages.collectAsState()
    val commandHistory by TurboSpaceRepository.commandHistory.collectAsState()
    var currentTab by remember { mutableIntStateOf(0) }
    var showLaunchDashboard by remember { mutableStateOf(false) }
    var showMainResetConfirm by remember { mutableStateOf(false) }
    var showRefreshDialog by remember { mutableStateOf(false) }
    val dashboardScope = rememberCoroutineScope()
    var hasOverlay by remember { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) }
    var hasShizuku by remember { mutableStateOf(false) }
    var hasWriteSettings by remember { mutableStateOf(false) }
    var hasDndAccess by remember { mutableStateOf(false) }
    var hasBatteryOptimization by remember { mutableStateOf(false) }
    var batteryPercent by remember { mutableIntStateOf(0) }
    var gameList by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    fun refreshPermissions() {
        hasOverlay = try { Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context) } catch (_: Throwable) { false }
        hasShizuku = try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }
        hasWriteSettings = try { Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context) } catch (_: Throwable) { false }
        hasDndAccess = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted else true
        } catch (_: Throwable) { false }
        hasBatteryOptimization = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName) else true
        } catch (_: Throwable) { false }
    }

    fun reloadGames() {
        gameList = try {
            val emulatorPrefixes = listOf(
                "org.ppsspp", "org.dolphinemu", "com.aether.sx2",
                "com.yuzu", "com.winlator", "com.github.stenzek.duckstation",
                "com.flycast", "com.retroarch", "com.m64plus"
            )
            TurboSpaceManager.getInstalledGames(context).filter { app ->
                val pkg = app.packageName
                val isThirdParty = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                val isGame = app.category == ApplicationInfo.CATEGORY_GAME
                val isEmulator = emulatorPrefixes.any { pkg.startsWith(it) }
                pkg != context.packageName && !pkg.startsWith("com.turbospace") &&
                    isThirdParty && (isGame || isEmulator)
            }.distinctBy { it.packageName }
                .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
        } catch (_: Throwable) { emptyList() }
    }

    fun requestPermission(permission: String) {
        try {
            when (permission) {
                "OVERLAY" -> context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                "WRITE_SETTINGS" -> context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                "DND_ACCESS" -> context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                "BATTERY_OPTIMIZATION" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }
                    catch (_: Throwable) { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
                "SHIZUKU" -> Shizuku.requestPermission(1001)
            }
        } catch (_: Throwable) {
            android.widget.Toast.makeText(context, "Unable to open permission settings", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun runQuickCommand(command: String, successMessage: String) {
        dashboardScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!TurboSpaceManager.isShizukuAvailableAndGranted()) null
                else try { TurboSpaceManager.executeCommandDetailedNoCtx(command) } catch (_: Throwable) { null }
            }
            val ok = result != null && result.success && result.exitCode == 0
            TurboSpaceRepository.saveLog(context, if (ok) "$successMessage — Executed: $command" else "ERROR: $command (${result?.error ?: "Shizuku not granted"})")
            android.widget.Toast.makeText(context, if (ok) "SUCCESS" else "ERROR: Shizuku required or command failed", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerReset() {
        showMainResetConfirm = false
        dashboardScope.launch {
            val pkg = selectedGame
            val success = withContext(Dispatchers.IO) {
                performFullReset(context, pkg)
            }
            if (success) {
                applyFullResetUiState(context)
                // Only the dashboard's RESET ALL flow stops the overlay service.
                try {
                    context.stopService(Intent(context, GameSpaceOverlayService::class.java))
                } catch (_: Throwable) {}
                val validPkg = pkg != "NO TARGET SELECTED" && pkg.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))
                if (validPkg) {
                    context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
                        .putString("game_mode_category_$pkg", "standard")
                        .putString("compile_mode_$pkg", "reset")
                        .putString("game_downscale_$pkg", "disable")
                        .apply()
                }
            }
            TurboSpaceRepository.saveLog(
                context,
                if (success) "SUCCESS: All commands reset" else "ERROR: Reset failed — check Shizuku permission and shell command support"
            )
            android.widget.Toast.makeText(
                context,
                if (success) "SUCCESS: Commands reset" else "ERROR: Some commands could not be reset",
                android.widget.Toast.LENGTH_LONG
            ).show()
            refreshPermissions()
        }
    }

    LaunchedEffect(Unit) {
        TurboSpaceRepository.loadPrefs(context)
        TurboSpaceRepository.loadFavoritePackages(context)
        TurboSpaceRepository.loadRecentPackages(context)
        TurboSpaceRepository.loadLogs(context)
        refreshPermissions()
        reloadGames()
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            batteryPercent = try { TurboSpaceManager.getBatteryPercent(context).coerceIn(0, 100) } catch (_: Throwable) { 0 }
            delay(5000)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { refreshPermissions(); reloadGames(); TurboSpaceRepository.loadFavoritePackages(context); TurboSpaceRepository.loadLogs(context) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == 1001) refreshPermissions()
        }
        // Shizuku may be missing, stopped, or temporarily unavailable. Never crash composition.
        var listenerRegistered = false
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            listenerRegistered = true
        } catch (_: Throwable) {
            listenerRegistered = false
        }
        onDispose {
            if (listenerRegistered) {
                try {
                    Shizuku.removeRequestPermissionResultListener(permissionListener)
                } catch (_: Throwable) {
                    // Ignore cleanup failures when Shizuku is unavailable.
                }
            }
        }
    }

    val filteredGames = remember(gameList, searchQuery, favoritePackages) {
        gameList.filter { it.loadLabel(context.packageManager).toString().contains(searchQuery, ignoreCase = true) }
            .sortedWith(compareByDescending<ApplicationInfo> { favoritePackages.contains(it.packageName) }
                .thenBy { it.loadLabel(context.packageManager).toString().lowercase() })
    }
    val favoriteGames = remember(gameList, favoritePackages) {
        gameList.filter { favoritePackages.contains(it.packageName) }
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }
    val allPermissionsGranted = hasOverlay && hasShizuku && hasWriteSettings && hasDndAccess && hasBatteryOptimization

    if (showLaunchDashboard && selectedGame != "NO TARGET SELECTED") {
        GameLaunchDashboard(
            selectedGame = selectedGame,
            onBack = { showLaunchDashboard = false },
            onLaunchGame = {
                try {
                    // Keep the original overlay launch logic; a failure to start the service
                    // should be handled without taking down the dashboard.
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            androidx.core.content.ContextCompat.startForegroundService(
                                context,
                                Intent(context, GameSpaceOverlayService::class.java)
                            )
                        } else {
                            context.startService(Intent(context, GameSpaceOverlayService::class.java))
                        }
                    } catch (_: Throwable) {}
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(450)
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedGame)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val options = android.app.ActivityOptions.makeBasic()
                                    try {
                                        context.startActivity(launchIntent, options.toBundle())
                                    } catch (_: Throwable) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Open game manually from launcher",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    context.startActivity(launchIntent)
                                }
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "ERROR: Unable to open game",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (_: Throwable) {
                            android.widget.Toast.makeText(
                                context,
                                "ERROR: Unable to start game",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (_: Throwable) {
                    android.widget.Toast.makeText(context, "ERROR: Unable to start game", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LaunchDashboardBackground,
        bottomBar = {
            TurboSpaceBottomNavigation(currentTab = currentTab, onTabSelected = { currentTab = it })
        }
    ) { scaffoldPadding ->
        when (currentTab) {
            0 -> HomeTabScreen(
                modifier = Modifier.padding(scaffoldPadding).fillMaxSize(),
                gameList = filteredGames, favoritePackages = favoritePackages, recentPackages = recentPackages, batteryPercent = batteryPercent,
                allPermissionsGranted = allPermissionsGranted, hasOverlay = hasOverlay, hasShizuku = hasShizuku,
                hasWriteSettings = hasWriteSettings, hasDndAccess = hasDndAccess, hasBatteryOptimization = hasBatteryOptimization,
                searchQuery = searchQuery, onSearchChange = { searchQuery = it },
                onRefreshGames = { reloadGames(); android.widget.Toast.makeText(context, "Games refreshed", android.widget.Toast.LENGTH_SHORT).show() },
                onToggleFavorite = { TurboSpaceRepository.toggleFavorite(context, it) },
                onPlay = { pkg -> TurboSpaceRepository.markRecentPlayed(context, pkg); TurboSpaceRepository.setSelectedGame(pkg); showLaunchDashboard = true },
                onRequestPermission = { requestPermission(it) },
                onRunQuickCommand = { command, label -> runQuickCommand(command, label) },
                onResetAll = { showMainResetConfirm = true }
            )
            1 -> LibraryTabScreen(
                modifier = Modifier.padding(scaffoldPadding).fillMaxSize(),
                favoriteGames = favoriteGames, favoritePackages = favoritePackages,
                onToggleFavorite = { TurboSpaceRepository.toggleFavorite(context, it) },
                onPlay = { pkg -> TurboSpaceRepository.markRecentPlayed(context, pkg); TurboSpaceRepository.setSelectedGame(pkg); showLaunchDashboard = true }
            )
            2 -> BoosterTabScreen(modifier = Modifier.padding(scaffoldPadding).fillMaxSize())
            3 -> SettingsTabScreen(
                modifier = Modifier.padding(scaffoldPadding).fillMaxSize(),
                hasOverlay = hasOverlay, hasBattery = hasBatteryOptimization, hasDnd = hasDndAccess,
                hasWriteSettings = hasWriteSettings, hasShizuku = hasShizuku,
                onRequestPermission = { requestPermission(it) }, onResetAll = { showMainResetConfirm = true }
            )
        }
    }

    if (showMainResetConfirm) {
        MainResetConfirmDialogNew(onCancel = { showMainResetConfirm = false }, onConfirm = { triggerReset() })
    }
}

@Composable
private fun TurboSpaceBottomNavigation(currentTab: Int, onTabSelected: (Int) -> Unit) {
    val labels = listOf("Home", "Library", "Booster", "Settings")
    val icons = listOf("home", "library", "booster", "settings")

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier.height(80.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = currentTab == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                icon = {
                    MonetCanvasIcon(
                        icons[index], Modifier.size(24.dp),
                        if (isSelected) Color(0xFFE83E9D) else Color(0xFF9A9BA4)
                    )
                },
                label = {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFE83E9D), selectedTextColor = Color(0xFFE83E9D),
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFF9A9BA4), unselectedTextColor = Color(0xFF9A9BA4)
                )
            )
        }
    }
}

@Composable
private fun HomeTabScreen(
    modifier: Modifier,
    gameList: List<ApplicationInfo>, favoritePackages: Set<String>, recentPackages: List<String>, batteryPercent: Int,
    allPermissionsGranted: Boolean, hasOverlay: Boolean, hasShizuku: Boolean, hasWriteSettings: Boolean,
    hasDndAccess: Boolean, hasBatteryOptimization: Boolean, searchQuery: String,
    onSearchChange: (String) -> Unit, onRefreshGames: () -> Unit,
    onToggleFavorite: (String) -> Unit, onPlay: (String) -> Unit,
    onRequestPermission: (String) -> Unit, onRunQuickCommand: (String, String) -> Unit,
    onResetAll: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(modifier.background(LaunchDashboardBackground).padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("TURBO SPACE", color = LaunchDashboardText, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp, fontFamily = FontFamily.Monospace)
                    Text("GAMING CONTROL CENTER", color = LaunchDashboardPink, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.size(width = 25.dp, height = 15.dp)) {
                        val stroke = 1.8.dp.toPx()
                        drawRoundRect(LaunchDashboardText, topLeft = Offset(0f, size.height * 0.12f), size = Size(size.width * 0.86f, size.height * 0.76f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(stroke))
                        drawRect(LaunchDashboardText, topLeft = Offset(size.width * 0.89f, size.height * 0.34f), size = Size(size.width * 0.11f, size.height * 0.32f))
                        drawRoundRect(LaunchDashboardPink, topLeft = Offset(size.width * 0.08f, size.height * 0.23f), size = Size(size.width * 0.62f, size.height * 0.54f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                    }
                    Text("$batteryPercent%", color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White).border(1.dp, LaunchDashboardPink.copy(alpha = 0.4f), CircleShape).clickable { onRefreshGames() }, contentAlignment = Alignment.Center) {
                        MonetCanvasIcon("refresh", Modifier.size(20.dp), LaunchDashboardPink)
                    }
                }
            }
        }
        if (!allPermissionsGranted) {
            item {
                PermissionSetupCard(hasOverlay, hasShizuku, hasWriteSettings, hasDndAccess, hasBatteryOptimization, onRequestPermission)
            }
        }
        if (recentPackages.isNotEmpty()) {
            item {
                Column {
                    Text("RECENT", color = LaunchDashboardPink, fontSize = 12.sp,
                        fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentPackages) { pkg ->
                            Column(
                                Modifier.width(72.dp).clickable { onPlay(pkg) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppIconImageRaw(context, pkg, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    try { context.packageManager.getApplicationLabel(
                                        context.packageManager.getApplicationInfo(pkg, 0)
                                    ).toString() } catch (_: Throwable) { pkg },
                                    color = LaunchDashboardText, fontSize = 9.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("YOUR GAMES", color = LaunchDashboardText, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("${gameList.size} FOUND", color = LaunchDashboardPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery, onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .border(1.dp, LaunchDashboardText.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                singleLine = true, textStyle = TextStyle(color = LaunchDashboardText, fontSize = 14.sp), cursorBrush = SolidColor(LaunchDashboardPink),
                decorationBox = { inner -> Box { if (searchQuery.isEmpty()) Text("Search games", color = LaunchDashboardText.copy(alpha = 0.45f), fontSize = 14.sp); inner() } }
            )
        }
        if (gameList.isEmpty()) {
            item { EmptyStateCard(if (searchQuery.isBlank()) "NO GAMES FOUND" else "NO MATCHING GAMES", "Use Refresh to scan installed games again.") }
        } else {
            items(gameList, key = { it.packageName }) { app ->
                val pkg = app.packageName
                val gameName = app.loadLabel(context.packageManager).toString()

                // Press animation for the entire game card.
                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
                    label = "cardPress"
                )

                Row(
                    Modifier.fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onPlay(pkg) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconImageRaw(context, pkg, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(gameName, color = Color(0xFF1D192B), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pkg, color = Color(0xFF7A757F), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.width(60.dp).height(32.dp).clip(RoundedCornerShape(50))
                            .background(Color(0xFFE83E9D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PLAY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    MonetCanvasIcon("chevron", Modifier.size(16.dp), Color(0xFF9A9BA4))
                }
            }
        }
    }
}

@Composable
private fun QuickToolButton(title: String, icon: String, modifier: Modifier = Modifier, accent: Color, onClick: () -> Unit) {
    Row(modifier.height(54.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.10f)).border(1.2.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(13.dp)).clickable { onClick() }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        MonetCanvasIcon(icon, Modifier.size(22.dp), accent); Spacer(Modifier.width(7.dp)); Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LibraryTabScreen(modifier: Modifier, favoriteGames: List<ApplicationInfo>, favoritePackages: Set<String>, onToggleFavorite: (String) -> Unit, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    LazyColumn(modifier.background(LaunchDashboardBackground).padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("LIBRARY", color = LaunchDashboardText, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace); Text("YOUR PINNED GAMES", color = LaunchDashboardPink, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) } }
        if (favoriteGames.isEmpty()) item { EmptyStateCard("NO FAVORITE GAMES", "Tap the star on a game in HOME to pin it here.") }
        items(favoriteGames, key = { it.packageName }) { app ->
            val pkg = app.packageName
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White).border(1.dp, LaunchDashboardPink.copy(alpha = 0.22f), RoundedCornerShape(15.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIconImageRaw(context, pkg, Modifier.size(50.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(app.loadLabel(context.packageManager).toString(), color = LaunchDashboardText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(pkg, color = LaunchDashboardText.copy(alpha = 0.55f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Box(Modifier.size(34.dp).clickable { onToggleFavorite(pkg) }, contentAlignment = Alignment.Center) { MonetCanvasIcon("star", Modifier.size(20.dp), if (favoritePackages.contains(pkg)) LaunchDashboardPink else LaunchDashboardText.copy(alpha=0.4f)) }
                Box(Modifier.width(62.dp).height(38.dp).clip(RoundedCornerShape(11.dp)).background(LaunchDashboardPink).clickable { onPlay(pkg) }, contentAlignment = Alignment.Center) { Text("PLAY", color=Color.White, fontSize=11.sp, fontWeight=FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun BoosterTabScreen(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ramUsed by remember { mutableIntStateOf(0) }
    var battery by remember { mutableIntStateOf(0) }
    var cpuTemp by remember { mutableStateOf("N/A") }
    var boosting by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf("READY FOR OPTIMIZATION") }
    LaunchedEffect(Unit) {
        while (isActive) {
            ramUsed = try { TurboSpaceManager.getRamUsagePercent(context).coerceIn(0,100) } catch (_: Throwable) { 0 }
            battery = try { TurboSpaceManager.getBatteryPercent(context).coerceIn(0,100) } catch (_: Throwable) { 0 }
            cpuTemp = try { TurboSpaceManager.getCpuTemp() } catch (_: Throwable) { "N/A" }
            delay(3000)
        }
    }
    Column(modifier.background(Color(0xFF141118)).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("BOOSTER", color = Color(0xFFE83E9D), fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace); Text("LIVE SYSTEM TELEMETRY", color = MonetColors.Secondary, fontSize = 10.sp, letterSpacing = 1.2.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("RAM USED", "$ramUsed%", ramUsed/100f, "ram", Modifier.weight(1f))
            MetricCard("CPU TEMP", cpuTemp, null, "tools", Modifier.weight(1f))
        }
        MetricCard("BATTERY", "$battery%", battery/100f, "battery", Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(17.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFE83E9D), Color(0xFFFF8A00)))).clickable(enabled = !boosting) {
            boosting = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { if (!TurboSpaceManager.isShizukuAvailableAndGranted()) null else try { TurboSpaceManager.executeCommandDetailedNoCtx("am kill-all") } catch (_: Throwable) { null } }
                val ok = result != null && result.success && result.exitCode == 0
                lastResult = if (ok) "SUCCESS: RAM Cleaned" else "ERROR: Shizuku or Shell command failed"
                TurboSpaceRepository.saveLog(context, if (ok) "SUCCESS: RAM Cleaned" else "ERROR: RAM BOOST FAILED")
                android.widget.Toast.makeText(context, if (ok) "SUCCESS: RAM Cleaned" else "ERROR: RAM BOOST FAILED", android.widget.Toast.LENGTH_SHORT).show()
                boosting = false
            }
        }, contentAlignment = Alignment.Center) { Text(if (boosting) "BOOSTING..." else "BOOST NOW", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace) }
        Text(lastResult, color = if (lastResult.startsWith("SUCCESS")) Color(0xFF79D7A2) else MonetColors.Secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("Commands are executed through Shizuku; the result depends on Android version and granted permissions.", color = Color(0xFFB9B2C2), fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun MetricCard(title: String, value: String, progress: Float?, icon: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Color(0xFF24202A)).border(1.dp, Color(0xFF6D5475).copy(alpha=0.65f), RoundedCornerShape(15.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { MonetCanvasIcon(icon, Modifier.size(20.dp), Color(0xFFE83E9D)); Text(title, color = MonetColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp) }
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        if (progress != null) Canvas(Modifier.fillMaxWidth().height(5.dp)) {
            val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            drawRoundRect(Color(0xFF433B49), size = size, cornerRadius = radius)
            drawRoundRect(Color(0xFFE83E9D), size = Size(size.width * progress.coerceIn(0f, 1f), size.height), cornerRadius = radius)
        }
    }
}

@Composable
private fun ToolSquareCard(title: String, icon: String, modifier: Modifier = Modifier, enabled: Boolean, description: String, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 128.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.2.dp, if (enabled) Color(0xFFE83E9D) else Color(0xFFE0D6E5), RoundedCornerShape(14.dp)).clickable { onClick() }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Canvas(Modifier.size(12.dp)) { drawCircle(if (enabled) Color(0xFFE83E9D) else Color(0xFFA8A0AE), size.minDimension/2) } }
        MonetCanvasIcon(icon, Modifier.size(32.dp), if (enabled) Color(0xFFE83E9D) else Color(0xFF776C80))
        Text(title, color = Color(0xFF211B24), fontSize = 10.sp, fontWeight = FontWeight.Black, lineHeight = 13.sp)
        Text(description, color = Color(0xFFE83E9D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsTabScreen(modifier: Modifier, hasOverlay: Boolean, hasBattery: Boolean, hasDnd: Boolean, hasWriteSettings: Boolean, hasShizuku: Boolean, onRequestPermission: (String) -> Unit, onResetAll: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown" } catch (_: Throwable) { "Unknown" } }
    Column(modifier.background(LaunchDashboardBackground).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SETTINGS", color = LaunchDashboardText, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text("PERMISSION STATUS", color = LaunchDashboardPink, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        PermissionStatusRow("OVERLAY", hasOverlay) { onRequestPermission("OVERLAY") }
        PermissionStatusRow("BATTERY OPTIMIZATION", hasBattery) { onRequestPermission("BATTERY_OPTIMIZATION") }
        PermissionStatusRow("DO NOT DISTURB", hasDnd) { onRequestPermission("DND_ACCESS") }
        PermissionStatusRow("WRITE SETTINGS", hasWriteSettings) { onRequestPermission("WRITE_SETTINGS") }
        PermissionStatusRow("SHIZUKU", hasShizuku) { onRequestPermission("SHIZUKU") }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFF1744).copy(alpha=0.09f)).border(1.3.dp, Color(0xFFE34D69), RoundedCornerShape(12.dp)).clickable { onResetAll() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            MonetCanvasIcon("reset", Modifier.size(20.dp), Color(0xFFE34D69)); Spacer(Modifier.width(8.dp)); Text("RESET ALL COMMANDS", color = Color(0xFFE34D69), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("TURBO SPACE BOOSTER", color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text("Version $versionName", color = LaunchDashboardText.copy(alpha = 0.7f), fontSize = 11.sp)
            Text("Kotlin · Jetpack Compose · Shizuku", color = LaunchDashboardPink, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PermissionStatusRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE2D8E7), RoundedCornerShape(12.dp)).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(if (granted) Color(0xFF16834A) else Color(0xFFD84050), size.minDimension/2) }
        Text(title, color = LaunchDashboardText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(if (granted) "GRANTED" else "MISSING", color = if (granted) Color(0xFF16834A) else Color(0xFFD84050), fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, LaunchDashboardPink.copy(alpha=0.2f), RoundedCornerShape(16.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonetCanvasIcon("game", Modifier.size(38.dp), LaunchDashboardPink)
        Text(title, color = LaunchDashboardText, fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(subtitle, color = LaunchDashboardText.copy(alpha=0.58f), fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}

@Composable
fun MainResetConfirmDialogNew(onConfirm: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.fillMaxWidth()
                .background(CyberDesign.DeepDark85, RoundedCornerShape(14.dp))
                .border(1.4.dp, Color(0xFFFF405D).copy(alpha = 0.92f), RoundedCornerShape(14.dp))
                .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = Color(0xFFFF1744), spotColor = Color(0xFFFF1744))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("RESET ALL COMMANDS", color = Color(0xFFFF5B73), fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 0.6.sp)
            Text("Reset all active booster commands and close the overlay?", color = MonetColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
            Text("Game mode, downscale, compilation, whitelist, and power saver settings will be restored.", color = MonetColors.Primary.copy(alpha = 0.78f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, lineHeight = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(7.dp)).background(MonetColors.Primary.copy(alpha = 0.08f)).border(1.dp, MonetColors.Primary.copy(alpha = 0.35f), RoundedCornerShape(7.dp)).clickable { onCancel() }, contentAlignment = Alignment.Center) {
                    Text("CANCEL", color = MonetColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFFF1744).copy(alpha = 0.13f)).border(1.5.dp, Color(0xFFFF405D), RoundedCornerShape(7.dp)).clickable { onConfirm() }, contentAlignment = Alignment.Center) {
                    Text("RESET", color = Color(0xFFFF5B73), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

data class PermissionItem(
    val key: String,
    val title: String,
    val description: String,
    val granted: Boolean
)

@Composable
fun PermissionSetupCard(
    hasOverlay: Boolean,
    hasShizuku: Boolean,
    hasWriteSettings: Boolean,
    hasDndAccess: Boolean,
    hasBatteryOptimization: Boolean,
    onRequestPermission: (String) -> Unit
) {
    val permissionItems = listOf(
        PermissionItem("OVERLAY", "OVERLAY ACCESS", "Allow the floating HUD to appear over games.", hasOverlay),
        PermissionItem("SHIZUKU", "SHIZUKU AUTHORIZATION", "Authorize Shizuku to run system shell commands.", hasShizuku),
        PermissionItem("WRITE_SETTINGS", "WRITE SYSTEM SETTINGS", "Allow supported refresh-rate and system setting changes.", hasWriteSettings),
        PermissionItem("DND_ACCESS", "DO NOT DISTURB ACCESS", "Allow the app to control Do Not Disturb mode.", hasDndAccess),
        PermissionItem("BATTERY_OPTIMIZATION", "BATTERY OPTIMIZATION", "Allow the service to be less restricted by battery optimization.", hasBatteryOptimization)
    )
    val grantedGreen = Color(0xFF16834A)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .border(1.dp, LaunchDashboardPink.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "PERMISSION SETUP",
            color = LaunchDashboardText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            "Grant the required access below to enable the gaming HUD and system controls.",
            color = LaunchDashboardText.copy(alpha = 0.72f),
            fontSize = 12.sp
        )

        permissionItems.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (item.granted) grantedGreen.copy(alpha = 0.08f) else LaunchDashboardBackground)
                    .border(
                        1.dp,
                        if (item.granted) grantedGreen.copy(alpha = 0.45f) else LaunchDashboardText.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        item.title,
                        color = if (item.granted) grantedGreen else LaunchDashboardText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        item.description,
                        color = LaunchDashboardText.copy(alpha = 0.68f),
                        fontSize = 10.sp
                    )
                }
                if (item.granted) {
                    Text(
                        "GRANTED",
                        color = grantedGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                } else {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LaunchDashboardPink)
                            .clickable { onRequestPermission(item.key) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ALLOW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCardSimple(title: String, desc: String, granted: Boolean, onClick: ()->Unit) {
    val borderColor = if (granted) MonetColors.Secondary else MonetColors.Primary
    Row(
        Modifier.fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MonetColors.Background.copy(alpha = 0.85f))
            .border(1.dp, borderColor.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .clickable { if (!granted) onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.fillMaxHeight().width(4.dp).background(borderColor))
        Row(
            Modifier.weight(1f).padding(start = 10.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MonetColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(if (granted) "GRANTED" else desc, color = MonetColors.Muted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.background(if (granted) MonetColors.Secondary else MonetColors.Primary, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(if (granted) "OK" else "ALLOW", color = MonetColors.OnPrimary, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        }
    }
}


// ==================== PRE-LAUNCH DASHBOARD ====================
private val LaunchDashboardBackground = Color(0xFFF3EDF7)
private val LaunchDashboardText = Color(0xFF211B24)
private val LaunchDashboardPink = Color(0xFFE83E9D)

private val compileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

@Composable
fun GameLaunchDashboard(selectedGame: String, onBack: () -> Unit, onLaunchGame: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gameLabel = remember(selectedGame) {
        try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(selectedGame, 0)).toString() }
        catch (_: Throwable) { selectedGame }
    }
    var selectedMode by remember {
        mutableStateOf(TurboSpaceRepository.preLaunchMode.takeIf { it in setOf("COOL", "BALANCED", "FAST") })
    }
    var mainModeCommands by remember { mutableStateOf<List<String>>(emptyList()) }
    var gameModeRadio by remember { mutableStateOf<String?>(null) }
    var gameModeCommand by remember { mutableStateOf<String?>(null) }
    var downscaleRadio by remember { mutableStateOf<String?>(null) }
    var downscaleCommand by remember { mutableStateOf<String?>(null) }
    var compileRadio by remember { mutableStateOf<String?>(null) }
    var showCompileDialog by remember { mutableStateOf(false) }
    var compileStatusText by remember { mutableStateOf("PLEASE WAIT") }
    var isCompiling by remember { mutableStateOf(false) }
    var orientationLocked by remember {
        mutableStateOf(context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).getBoolean("orientation_lock_active", TurboSpaceRepository.orientationLockEnabled))
    }
    var dndEnabled by remember {
        mutableStateOf(try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Throwable) { false })
    }
    var starting by remember { mutableStateOf(false) }
    var showApplyPopup by remember { mutableStateOf(false) }
    var applyPopupMessage by remember { mutableStateOf("PLEASE WAIT") }

    LaunchedEffect(Unit) {
        TurboSpaceRepository.restoreCompileState(context)
    }

    LaunchedEffect(selectedGame) {
        TurboSpaceRepository.compileState.collect { state ->
            if (state.pkg.isNotBlank() && state.pkg == selectedGame) {
                if (state.inProgress) {
                    isCompiling = true
                    compileStatusText = "PLEASE WAIT"
                    if (state.mode.isNotBlank()) compileRadio = state.mode
                    showCompileDialog = true
                } else if (state.lastResult.isNotBlank()) {
                    isCompiling = false
                    compileStatusText = state.lastResult
                    if (state.lastResult == "ERROR") compileRadio = null
                    showCompileDialog = true
                    // FIX: clear BEFORE delay so navigation away doesn't leave stale state
                    TurboSpaceRepository.clearCompileResult(context)
                    delay(1500)
                    if (showCompileDialog) showCompileDialog = false
                }
            }
        }
    }

    val brightBlue = Color(0xFF1689FF)
    val vibrantGreen = Color(0xFF00C978)
    val brightOrange = Color(0xFFFF8A00)
    val gpuTarget = when (selectedMode) { "COOL" -> 50; "FAST" -> 80; else -> 60 }
    val signalTarget = when (selectedMode) { "COOL" -> 60; "FAST" -> 90; else -> 80 }
    val cpuTarget = when (selectedMode) { "COOL" -> 55; "FAST" -> 85; else -> 72 }
    val powerTarget = when (selectedMode) { "COOL" -> 40; "FAST" -> 70; else -> 55 }
    val gpuProgress by animateFloatAsState(targetValue = gpuTarget / 100f, animationSpec = tween(650, easing = FastOutSlowInEasing), label = "launch-gpu-progress")
    val signalProgress by animateFloatAsState(targetValue = signalTarget / 100f, animationSpec = tween(650, easing = FastOutSlowInEasing), label = "launch-signal-progress")
    val cpuProgress by animateFloatAsState(targetValue = cpuTarget / 100f, animationSpec = tween(650, easing = FastOutSlowInEasing), label = "launch-cpu-progress")
    val powerProgress by animateFloatAsState(targetValue = powerTarget / 100f, animationSpec = tween(650, easing = FastOutSlowInEasing), label = "launch-power-progress")

    fun selectLaunchMode(mode: String) {
        val previous = selectedMode
        val commands = mutableListOf<String>()
        if (previous == mode) {
            when (mode) {
                "COOL" -> commands += "settings put global low_power 0"
                "FAST" -> commands += "cmd device_config put activity_manager high_fps_config off"
            }
            selectedMode = null
        } else {
            when (previous) {
                "COOL" -> commands += "settings put global low_power 0"
                "FAST" -> commands += "cmd device_config put activity_manager high_fps_config off"
            }
            selectedMode = mode
            when (mode) {
                "COOL" -> commands += listOf(
                    "settings put system peak_refresh_rate 60",
                    "settings put system min_refresh_rate 60",
                    "settings put global low_power 1"
                )
                "FAST" -> commands += "cmd device_config put activity_manager high_fps_config on"
                // BALANCED has no command.
            }
        }
        mainModeCommands = mainModeCommands + commands
        TurboSpaceRepository.preLaunchMode = selectedMode ?: "NONE"
    }

    fun runImmediateCommand(command: String, onResult: (Boolean) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!TurboSpaceManager.isShizukuAvailableAndGranted()) return@withContext false
                val response = try { TurboSpaceManager.executeCommandDetailedNoCtx(command) }
                catch (_: Throwable) { null }
                response != null && response.success && response.exitCode == 0
            }
            onResult(result)
            android.widget.Toast.makeText(context, if (result) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier.fillMaxSize().background(LaunchDashboardBackground).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("BACK", Modifier.clip(CircleShape).clickable { onBack() }.padding(vertical = 10.dp, horizontal = 12.dp), color = LaunchDashboardPink, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("TURBO SPACE", color = LaunchDashboardText, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.94f))
                .border(1.dp, Color(0xFFE4DDE9), RoundedCornerShape(16.dp)).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("GAME LAUNCH CONFIGURATION", color = LaunchDashboardPink, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
            Text(gameLabel, color = LaunchDashboardText, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        // Clean mode cards: commands remain in selectLaunchMode() and are not shown in the UI.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LaunchModeCard(
                modifier = Modifier.weight(1f).height(180.dp), mode = "COOL", selected = selectedMode == "COOL",
                accent = Color(0xFF1689FF), iconKind = "snowflake", title = "COOL MODE", description = "POWER SAVING",
                onClick = { selectLaunchMode("COOL") }
            )
            LaunchModeCard(
                modifier = Modifier.weight(1f).height(180.dp), mode = "BALANCED", selected = selectedMode == "BALANCED",
                accent = Color(0xFF00C978), iconKind = "cube_green", title = "BALANCED MODE", description = "BALANCED",
                onClick = { selectLaunchMode("BALANCED") }
            )
            LaunchModeCard(
                modifier = Modifier.weight(1f).height(180.dp), mode = "FAST", selected = selectedMode == "FAST",
                accent = Color(0xFFFF8A00), iconKind = "cube_orange", title = "PERFORMANCE", description = "MAX PERFORMANCE",
                onClick = { selectLaunchMode("FAST") }
            )
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.76f))
                .border(1.dp, Color(0xFFE5DEE8), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PERFORMANCE MONITOR",
                    color = LaunchDashboardText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF8A8A8A).copy(alpha = 0.15f))
                        .border(0.6.dp, Color(0xFF8A8A8A).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "SIMULATED",
                        color = Color(0xFF6A6A6A),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            SegmentedPerformanceBar("EST. GPU LOAD", (gpuProgress * 100f).toInt(), gpuProgress, Color(0xFF1689FF).copy(alpha = 0.55f), "cpu")
            SegmentedPerformanceBar("EST. SIGNAL", (signalProgress * 100f).toInt(), signalProgress, Color(0xFF00C978).copy(alpha = 0.55f), "signal")
            SegmentedPerformanceBar("EST. CPU LOAD", (cpuProgress * 100f).toInt(), cpuProgress, Color(0xFFFF8A00).copy(alpha = 0.55f), "gear")
            SegmentedPerformanceBar("EST. POWER USE", (powerProgress * 100f).toInt(), powerProgress, Color(0xFFA576FF).copy(alpha = 0.55f), "battery")
            Text(
                "Estimated values change with the selected mode. Actual performance depends on your device.",
                color = LaunchDashboardText.copy(alpha = 0.55f),
                fontSize = 8.sp,
                lineHeight = 11.sp
            )
        }

        LaunchChipsSection(
            title = "GAME PERFORMANCE CATEGORY",
            options = listOf("Performance", "Battery Saver", "Reset"),
            selectedOption = gameModeRadio,
            onOptionSelected = { option ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    android.widget.Toast.makeText(
                        context,
                        "Game Mode requires Android 12+",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@LaunchChipsSection
                }
                when (option) {
                    "Performance" -> { gameModeRadio = option; gameModeCommand = "performance" }
                    "Battery Saver" -> { gameModeRadio = option; gameModeCommand = "battery" }
                    else -> { gameModeRadio = null; gameModeCommand = "standard" }
                }
            }
        )
        LaunchChipsSection(
            title = "GAME DOWNSCALE",
            options = listOf("95%", "90%", "85%", "Reset"),
            selectedOption = downscaleRadio,
            onOptionSelected = { option ->
                when (option) {
                    "95%" -> { downscaleRadio = option; downscaleCommand = "0.95" }
                    "90%" -> { downscaleRadio = option; downscaleCommand = "0.90" }
                    "85%" -> { downscaleRadio = option; downscaleCommand = "0.85" }
                    else -> { downscaleRadio = null; downscaleCommand = "disable" }
                }
            }
        )
        LaunchChipsSection(
            title = "COMPILE TOOLS",
            options = listOf("Speed", "Speed-Profile", "Reset Compile"),
            selectedOption = compileRadio,
            onOptionSelected = { option ->
                if (!isCompiling && !starting) {
                    val command = when (option) {
                        "Speed" -> "cmd package compile -m speed -f $selectedGame"
                        "Speed-Profile" -> "cmd package compile -m speed-profile -f $selectedGame"
                        "Reset Compile" -> "cmd package compile --reset $selectedGame"
                        else -> null
                    }
                    if (command != null) {
                        if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
                            android.widget.Toast.makeText(
                                context,
                                "ERROR: Shizuku required",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@LaunchChipsSection
                        }

                        isCompiling = true
                        compileStatusText = "PLEASE WAIT"
                        showCompileDialog = true
                        compileRadio = option
                        TurboSpaceRepository.setCompileInProgress(context, selectedGame, option)

                        compileScope.launch {
                            val result = try {
                                TurboSpaceManager.executeCommandDetailedNoCtx(command)
                            } catch (_: Throwable) {
                                null
                            }
                            val succeeded = result != null && result.success && result.exitCode == 0
                            withContext(Dispatchers.Main) {
                                TurboSpaceRepository.setCompileResult(context, succeeded)
                                isCompiling = false
                                compileStatusText = if (succeeded) "SUCCESS" else "ERROR"
                                if (!succeeded) compileRadio = null
                                delay(1500)
                                if (showCompileDialog) showCompileDialog = false
                            }
                        }
                    }
                }
            }
        )

        Text("QUICK TOOLS", color = LaunchDashboardText, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LaunchSwitchCard(Modifier.weight(1f), "ORIENTATION LOCK", orientationLocked) { next ->
                orientationLocked = next
                val command = if (next) {
                    "settings put system user_rotation 0; settings put system accelerometer_rotation 0"
                } else {
                    "settings put system accelerometer_rotation 1"
                }
                runImmediateCommand(command) { success ->
                    if (success) {
                        TurboSpaceRepository.orientationLockEnabled = next
                        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("orientation_lock_active", next).apply()
                    } else orientationLocked = !next
                }
            }
            LaunchSwitchCard(Modifier.weight(1f), "DO NOT DISTURB MODE", dndEnabled) { next ->
                try {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !manager.isNotificationPolicyAccessGranted) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        android.widget.Toast.makeText(context, "ALLOW DO NOT DISTURB ACCESS", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        manager.setInterruptionFilter(if (next) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
                        dndEnabled = next
                    }
                } catch (_: Throwable) {
                    android.widget.Toast.makeText(context, "ERROR: DO NOT DISTURB", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Press animation and pink glow for the START button.
        val startInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isStartPressed by startInteractionSource.collectIsPressedAsState()
        val startScale by animateFloatAsState(
            targetValue = if (isStartPressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
            label = "startPress"
        )

        Box(
            Modifier.fillMaxWidth().height(54.dp)
                .graphicsLayer { scaleX = startScale; scaleY = startScale }
                .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xFFE83E9D), spotColor = Color(0xFFE83E9D))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFF0055), Color(0xFFE83E9D))))
                .clickable(
                    interactionSource = startInteractionSource,
                    indication = null,
                    enabled = !starting && !isCompiling
                ) {
                    if (selectedGame.isBlank() || selectedGame == "NO TARGET SELECTED" || !selectedGame.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) {
                        android.widget.Toast.makeText(context, "ERROR: Select a valid game", android.widget.Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
                        TurboSpaceManager.requestShizukuPermission()
                        android.widget.Toast.makeText(context, "ERROR: Shizuku required", android.widget.Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    starting = true
                    applyPopupMessage = "PLEASE WAIT"
                    showApplyPopup = true
                    scope.launch {
                        val commands = mutableListOf<String>()
                        commands.addAll(mainModeCommands)
                        when (gameModeCommand) {
                            "performance" -> commands.add("cmd game mode set performance $selectedGame")
                            "battery" -> commands.add("cmd game mode set battery $selectedGame")
                            "standard" -> commands.add("cmd game mode set standard $selectedGame")
                        }
                        when (downscaleCommand) {
                            "0.95", "0.90", "0.85" -> commands.add("cmd game downscale $downscaleCommand $selectedGame")
                            "disable" -> commands.add("cmd game downscale disable $selectedGame")
                        }
                        var allSucceeded = true
                        for (command in commands) {
                            val result = withContext(Dispatchers.IO) {
                                try { TurboSpaceManager.executeCommandDetailedNoCtx(command) }
                                catch (_: Throwable) { null }
                            }
                            if (result == null || !result.success || result.exitCode != 0) allSucceeded = false
                        }
                        applyPopupMessage = if (allSucceeded) "SUCCESS" else "ERROR"
                        if (allSucceeded) android.widget.Toast.makeText(context, "SUCCESS: SETTINGS APPLIED", android.widget.Toast.LENGTH_SHORT).show()
                        else android.widget.Toast.makeText(context, "WARNING: SOME SETTINGS FAILED", android.widget.Toast.LENGTH_LONG).show()
                        delay(900)
                        showApplyPopup = false
                        starting = false
                        onLaunchGame()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonetCanvasIcon("rocket_start", Modifier.size(24.dp), Color.White)
                Text(if (starting) "APPLYING SETTINGS..." else "START", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
        if (showApplyPopup) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { if (!starting) showApplyPopup = false }) {
                Column(
                    Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2B2930)).border(1.dp, LaunchDashboardPink, RoundedCornerShape(16.dp)).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (applyPopupMessage == "PLEASE WAIT") {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(18.dp))
                    } else {
                        MonetCanvasIcon(if (applyPopupMessage == "SUCCESS") "check" else "reset", Modifier.size(42.dp), if (applyPopupMessage == "SUCCESS") Color(0xFF00C978) else LaunchDashboardPink)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(applyPopupMessage, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
            }
        }
    }

    if (showCompileDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCompileDialog = false }
        ) {
            Column(
                Modifier.size(220.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2B2930))
                    .border(1.dp, LaunchDashboardPink, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (compileStatusText == "PLEASE WAIT") {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                } else {
                    MonetCanvasIcon(
                        if (compileStatusText == "SUCCESS") "check" else "reset",
                        Modifier.size(42.dp),
                        if (compileStatusText == "SUCCESS") Color(0xFF00C978) else Color(0xFFE83E9D)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    compileStatusText,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                if (compileStatusText == "PLEASE WAIT") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "You can close this dialog.\nCompilation continues in background.",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.height(32.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .clickable { showCompileDialog = false }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "HIDE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun LaunchChipsSection(
    title: String,
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            title,
            color = LaunchDashboardText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val selected = selectedOption == option
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) LaunchDashboardPink else Color(0xFFF0F0F0))
                        .border(
                            1.dp,
                            if (selected) LaunchDashboardPink else Color.Transparent,
                            RoundedCornerShape(50)
                        )
                        .clickable { onOptionSelected(option) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        option,
                        color = if (selected) Color.White else LaunchDashboardText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LaunchModeCard(
    modifier: Modifier,
    mode: String,
    selected: Boolean,
    accent: Color,
    iconKind: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.08f) else Color.White)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) accent else Color(0xFFE0E0E0),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.fillMaxWidth().height(60.dp)) {
                if (selected) {
                    Canvas(Modifier.size(16.dp).align(Alignment.TopEnd)) {
                        drawCircle(accent)
                        drawLine(
                            Color.White,
                            Offset(size.width * 0.3f, size.height * 0.5f),
                            Offset(size.width * 0.45f, size.height * 0.7f),
                            1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            Color.White,
                            Offset(size.width * 0.45f, size.height * 0.7f),
                            Offset(size.width * 0.7f, size.height * 0.3f),
                            1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                MonetCanvasIcon(iconKind, Modifier.size(40.dp).align(Alignment.Center), accent)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                color = Color(0xFF7A757F),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LaunchSwitchCard(modifier: Modifier, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFEBF3))
            .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.weight(1f), color = Color(0xFF211B24), fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFE83E9D),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.White
            )
        )
    }
}

@Composable
private fun SegmentedPerformanceBar(label: String, percent: Int, progress: Float, accent: Color, iconKind: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MonetCanvasIcon(iconKind, Modifier.size(14.dp), accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = LaunchDashboardText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("$percent%", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                drawRoundRect(Color(0xFFE8E3EA), cornerRadius = radius)
                drawRoundRect(
                    color = accent,
                    size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                    cornerRadius = radius
                )
            }
        }
    }
}

@Composable
fun FpsBoosterDialog(
    onDismiss: () -> Unit,
    onApplied: (hz: Int, success: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE) }
    val maxSupportedHz = remember(context) { detectMaxSupportedRefreshRateHz(context) }
    val rateChoices = listOf(60, 90, 120)
    val availableRates = remember(maxSupportedHz) { rateChoices.filter { it <= maxSupportedHz } }
    var page by remember { mutableIntStateOf(0) }
    var selectedHz by remember(prefs, maxSupportedHz) {
        val savedRate = prefs.getInt("fps_refresh_rate", 90)
        mutableIntStateOf(
            savedRate.takeIf { it in rateChoices && it <= maxSupportedHz }
                ?: (availableRates.maxOrNull() ?: 60)
        )
    }
    var applying by remember { mutableStateOf(false) }

    // If an older app version saved a rate above the current display's reported maximum, do not
    // leave the FPS feature marked as active. No over-limit Shell command is executed here.
    LaunchedEffect(maxSupportedHz) {
        val savedRate = prefs.getInt("fps_refresh_rate", 90)
        if (prefs.getBoolean("fps_booster_active", false) && savedRate > maxSupportedHz) {
            prefs.edit().putBoolean("fps_booster_active", false).apply()
            onApplied(savedRate, false)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(MonetColors.Background.copy(alpha = 0.78f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(if (page == 0) 0.78f else 0.84f)
                .clip(RoundedCornerShape(16.dp))
                .background(MonetColors.Inactive)
                .border(1.5.dp, MonetColors.Primary, RoundedCornerShape(16.dp))
                .clickable { /* Consume card taps; only taps outside dismiss. */ }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (page == 0) {
                Text("FPS BOOSTER", color = MonetColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(
                    "This feature will adjust your screen refresh rate to save power (60Hz) or maximize smoothness (90Hz / 120Hz).",
                    color = MonetColors.Muted, fontSize = 11.sp, lineHeight = 15.sp
                )
                Text(
                    "The maximum refresh rate is detected from Android display modes. Rates above that limit will be blocked.",
                    color = MonetColors.Secondary, fontSize = 9.sp, lineHeight = 12.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(9.dp))
                            .background(Color.Transparent).border(1.dp, MonetColors.Outline, RoundedCornerShape(9.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CANCEL", color = MonetColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(9.dp))
                            .background(MonetColors.Primary).border(1.dp, Color(0xFFFFC3E1), RoundedCornerShape(9.dp))
                            .clickable { page = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("START", color = MonetColors.OnPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("FPS BOOSTER", color = MonetColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(MonetColors.Background.copy(alpha = 0.55f))
                            .border(1.dp, MonetColors.Secondary, CircleShape).clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) { Text("X", color = MonetColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                }
                Text(
                    "DISPLAY MAXIMUM: ${maxSupportedHz} HZ",
                    color = MonetColors.Secondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
                Text("SELECT REFRESH RATE", color = MonetColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                val options = listOf(
                    Triple(60, "60 Hz", "Eco / Power Saving"),
                    Triple(90, "90 Hz", "Balanced / Recommended"),
                    Triple(120, "120 Hz", "Smooth / Device may heat up")
                )
                options.forEach { option ->
                    val hz = option.first
                    val withinLimit = hz <= maxSupportedHz
                    val checked = selectedHz == hz && withinLimit
                    val detail = if (withinLimit) option.third
                        else "Blocked: display maximum is ${maxSupportedHz} Hz"
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                            .background(if (checked) MonetColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                1.dp,
                                when {
                                    checked -> MonetColors.Primary
                                    !withinLimit -> MonetColors.Outline.copy(alpha = 0.45f)
                                    else -> MonetColors.Outline
                                },
                                RoundedCornerShape(9.dp)
                            )
                            // Over-limit choices remain tappable only to explain why they are blocked.
                            // They never update selection or execute Shell commands.
                            .clickable(enabled = !applying) {
                                if (!withinLimit) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "ERROR: Unable to unlock the display refresh-rate limit.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } else if (!applying) {
                                    selectedHz = hz
                                    applying = true
                                    scope.launch {
                                        val success = withContext(Dispatchers.IO) {
                                            // Repeat the limit check immediately before executing commands.
                                            val liveMaxHz = detectMaxSupportedRefreshRateHz(context)
                                            if (hz > liveMaxHz) return@withContext false
                                            if (!TurboSpaceManager.isShizukuAvailableAndGranted()) return@withContext false
                                            val peak = try {
                                                TurboSpaceManager.executeCommandDetailedNoCtx(
                                                    "settings put system peak_refresh_rate ${hz}.0"
                                                )
                                            } catch (_: Throwable) { null }
                                            val min = try {
                                                TurboSpaceManager.executeCommandDetailedNoCtx(
                                                    "settings put system min_refresh_rate ${hz}.0"
                                                )
                                            } catch (_: Throwable) { null }
                                            peak != null && peak.success && peak.exitCode == 0 &&
                                                min != null && min.success && min.exitCode == 0
                                        }
                                        applying = false
                                        if (success) {
                                            prefs.edit()
                                                .putBoolean("fps_booster_active", true)
                                                .putInt("fps_refresh_rate", hz)
                                                .apply()
                                        } else {
                                            prefs.edit().putBoolean("fps_booster_active", false).apply()
                                        }
                                        onApplied(hz, success)
                                        android.widget.Toast.makeText(
                                            context,
                                            if (success) "SUCCESS" else "ERROR",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        if (!success && hz > detectMaxSupportedRefreshRateHz(context)) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Unable to unlock the display refresh-rate limit.",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Canvas(Modifier.size(20.dp)) {
                            val ringColor = when {
                                checked -> MonetColors.Primary
                                !withinLimit -> MonetColors.Outline.copy(alpha = 0.35f)
                                else -> MonetColors.Outline
                            }
                            drawCircle(ringColor, radius = size.minDimension * 0.45f, style = Stroke(width = 1.6.dp.toPx()))
                            if (checked) drawCircle(MonetColors.Primary, radius = size.minDimension * 0.24f)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                option.second,
                                color = when {
                                    checked -> MonetColors.Primary
                                    !withinLimit -> MonetColors.Muted.copy(alpha = 0.65f)
                                    else -> MonetColors.Secondary
                                },
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                detail,
                                color = if (withinLimit) MonetColors.Muted else MonetColors.Muted.copy(alpha = 0.8f),
                                fontSize = 9.sp, lineHeight = 12.sp
                            )
                        }
                    }
                }
                if (applying) {
                    Text("APPLYING ${selectedHz} HZ...", color = MonetColors.Secondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun CrashSafeRoot() {
    // Crash-safe wrapper without try-catch around composable (Compose compiler restriction)
    // Error handling moved to state logic inside TurboSpaceAppRoot itself
    TurboSpaceAppRoot()
}

@Composable
fun PolicyScreen(onAccept: () -> Unit, onCancel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1B1F))
                .border(1.5.dp, Color(0xFFE83E9D).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    "TURBO SPACE - TERMS & PRIVACY",
                    color = Color(0xFFE83E9D), fontSize = 16.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "1. NO DATA COLLECTION
" +
                            "We do not collect, store, or transmit any personal data, analytics, or telemetry to external servers. All settings and preferences are stored locally on your device.

" +
                            "2. PERMISSIONS & SYSTEM MODIFICATIONS
" +
                            "This app requires specific permissions to optimize your gaming experience:
" +
                            "- Overlay: To display the Gaming HUD over your games.
" +
                            "- Shizuku: To execute system-level shell commands (e.g., RAM cleaning, FPS unlocking, DND control).
" +
                            "- Battery & DND Access: To manage system states efficiently.

" +
                            "3. SYSTEM TWEAKS DISCLAIMER
" +
                            "By using this app, you acknowledge that it will execute shell commands to modify system settings (e.g., refresh rate, game mode). While we strive for safety, the developer is not responsible for any unexpected system behavior, battery drain, or device instability caused by these tweaks.

" +
                            "4. LOCAL STORAGE ONLY
" +
                            "All data, including your favorite games and selected settings, remains strictly on your device.

" +
                            "5. USER RESPONSIBILITY
" +
                            "You agree to use this application at your own risk. You are responsible for granting Shizuku permissions and ensuring that system commands do not conflict with your device's OEM restrictions.

" +
                            "By clicking \"I ACCEPT\", you agree to these terms and agree to grant the necessary permissions.",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                        lineHeight = 18.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("CANCEL", color = Color.Gray, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE83E9D).copy(alpha = 0.15f))
                        .border(2.dp, Color(0xFFE83E9D), RoundedCornerShape(8.dp))
                        .clickable { onAccept() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("I ACCEPT", color = Color(0xFFE83E9D), fontSize = 12.sp,
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun PermissionOnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var hasOverlay by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context))
    }
    var hasShizuku by remember { mutableStateOf(false) }
    var hasWriteSettings by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context))
    }
    var hasDnd by remember {
        mutableStateOf(try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        } catch (_: Throwable) { false })
    }
    var hasBattery by remember {
        mutableStateOf(try {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Throwable) { false })
    }

    fun refresh() {
        hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        hasShizuku = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
        hasWriteSettings = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
        hasDnd = try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        } catch (_: Throwable) { false }
        hasBattery = try {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Throwable) { false }
    }

    LaunchedEffect(Unit) { refresh() }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestPermission(key: String) {
        try {
            when (key) {
                "OVERLAY" -> context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
                "SHIZUKU" -> Shizuku.requestPermission(1001)
                "WRITE_SETTINGS" -> context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
                "DND_ACCESS" -> context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                "BATTERY" -> {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            }
        } catch (_: Throwable) {
            android.widget.Toast.makeText(context, "Unable to open permission settings", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF1C1B1F)).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2B2930))
                .border(1.dp, Color(0xFFE83E9D).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("PERMISSIONS REQUIRED", color = Color(0xFFE83E9D), fontSize = 16.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("Grant these permissions to unlock the full potential of Turbo Space.",
                color = Color.Gray, fontSize = 11.sp)

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val permissionItems = listOf(
                    Triple("OVERLAY", "Display Gaming HUD", hasOverlay),
                    Triple("SHIZUKU", "Execute System Commands", hasShizuku),
                    Triple("WRITE_SETTINGS", "Modify Refresh Rate", hasWriteSettings),
                    Triple("DND_ACCESS", "Control Notifications", hasDnd),
                    Triple("BATTERY", "Unrestricted Background", hasBattery)
                )
                permissionItems.forEach { (key, description, isGranted) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (isGranted) Color(0xFF16834A).copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.3f))
                            .border(1.dp, if (isGranted) Color(0xFF16834A) else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(key.replace("_", " "),
                                color = if (isGranted) Color(0xFF16834A) else Color.White,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(description, color = Color.Gray, fontSize = 10.sp)
                        }
                        if (isGranted) {
                            Text("GRANTED", color = Color(0xFF16834A), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        } else {
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE83E9D))
                                    .clickable { requestPermission(key) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("ALLOW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE83E9D)).clickable { onContinue() },
                contentAlignment = Alignment.Center
            ) {
                Text("CONTINUE TO DASHBOARD", color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun TurboSpaceAppRoot() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
    }

    var isPolicyAccepted by remember {
        mutableStateOf(prefs.getBoolean("policy_accepted", false))
    }
    var isOnboardingDone by remember {
        mutableStateOf(prefs.getBoolean("onboarding_completed", false))
    }

    val customDarkColorScheme = androidx.compose.material3.darkColorScheme(
        primary = MonetColors.Primary,
        onPrimary = MonetColors.OnPrimary,
        secondary = MonetColors.Secondary,
        background = MonetColors.Background,
        surface = MonetColors.Inactive,
        onBackground = MonetColors.Primary,
        onSurface = MonetColors.Primary
    )

    androidx.compose.material3.MaterialTheme(colorScheme = customDarkColorScheme) {
        when {
            !isPolicyAccepted -> {
                PolicyScreen(
                    onAccept = {
                        prefs.edit().putBoolean("policy_accepted", true).apply()
                        isPolicyAccepted = true
                    },
                    onCancel = { (context as? android.app.Activity)?.finish() }
                )
            }
            !isOnboardingDone -> {
                PermissionOnboardingScreen(
                    onContinue = {
                        prefs.edit().putBoolean("onboarding_completed", true).apply()
                        isOnboardingDone = true
                    }
                )
            }
            else -> MainMissionControlDashboard()
        }
    }
}

@Composable
fun TurboSpaceNewSplash(onFinished: () -> Unit) {
    // Animation state variables
    val hexagonAlpha = remember { Animatable(0f) }
    val hexagonOffsetX = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetX = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Hexagon fades in
        hexagonAlpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        delay(300) // Brief pause

        // 2. Hexagon slides left, text slides right from behind
        launch {
            hexagonOffsetX.animateTo(-60f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f))
        }
        launch {
            textOffsetX.animateTo(70f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f))
            textAlpha.animateTo(1f, animationSpec = tween(400))
        }

        delay(1200) // Let the user see the final logo
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)), // Dark background
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // TEXT (Drawn first, so it sits BEHIND the hexagon)
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = textAlpha.value
                        translationX = textOffsetX.value * density
                    }
                    .padding(start = 20.dp)
            ) {
                Text(
                    text = "turbo",
                    color = Color(0xFFFF0055), // Red
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1).sp,
                    lineHeight = 40.sp
                )
                Text(
                    text = "space",
                    color = Color(0xFFFF4081), // Pink
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1).sp,
                    lineHeight = 40.sp
                )
            }

            // HEXAGON (Drawn second, sits IN FRONT of the text)
            Canvas(
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer {
                        alpha = hexagonAlpha.value
                        translationX = hexagonOffsetX.value * density
                    }
            ) {
                val cx = size.width / 2
                val cy = size.height / 2
                val radius = size.width * 0.45f
                val strokeWidth = 18.dp.toPx()

                val path = Path().apply {
                    for (i in 0 until 6) {
                        val angle = (Math.PI / 3 * i).toFloat() - (Math.PI / 6).toFloat()
                        val x = cx + radius * kotlin.math.cos(angle)
                        val y = cy + radius * kotlin.math.sin(angle)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }

                // Draw the purple hexagon with rounded corners
                drawPath(
                    path = path,
                    color = Color(0xFFA576FF),
                    style = Stroke(
                        width = strokeWidth,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}



// 
class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        val splashScreen = androidx.core.splashscreen.SplashScreen
            .Companion.installSplashScreen(this)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
            }
        }
        setContent {
            TurboSpaceAppRoot()
        }
    }
}
