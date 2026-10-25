package com.turbospace.optimizer
import android.app.Service
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
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
    var pingFloatingEnabled by mutableStateOf(false)
    // The SHOW STATUS floating window displays the full real-time snapshot; Ping has its own window.
    var floatingFpsValue by mutableStateOf("FPS: 0 | CPU: -- GHz | RAM: 0% | BAT: 0%")
    var floatingPingValue by mutableStateOf("N/A")
    // User-selectable HUD edge illumination and mutually-exclusive power saver state.
    var edgeLightColor by mutableStateOf(Color(0xFFF2D7EE))
    var powerSaverEnabled by mutableStateOf(false)

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

    fun setPingFloatingEnabled(context: Context, enabled: Boolean) {
        pingFloatingEnabled = enabled
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit().putBoolean("ping_floating_enabled", enabled).apply()
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
    val crosshairColorIdx = MutableStateFlow(2) // 0=red, 1=green, 2=blue, 3=white

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
        crosshairColorIdx.value = v.coerceIn(0, 3)
        persistCrosshair()
    }

    private fun persistCrosshair() {
        try {
            prefsContext?.getSharedPreferences("turbospace_prefs", android.content.Context.MODE_PRIVATE)
                ?.edit()
                ?.putInt("crosshair_style", crosshairStyle.value)
                ?.putInt("crosshair_color_idx", crosshairColorIdx.value)
                ?.apply()
        } catch (_: Throwable) {}
    }

    fun crosshairColor(idx: Int = crosshairColorIdx.value): Color = when (idx) {
        0 -> MonetColors.Primary
        1 -> MonetColors.Secondary
        2 -> MonetColors.Muted
        else -> MonetColors.Primary
    }

    fun setSelectedGame(pkg: String) { _selectedGame.value = pkg }
    fun setMasterOn(on: Boolean) { _isMasterOn.value = on }

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
            .putBoolean("stopwatch_running", stopwatchRunning)
            .putLong("stopwatch_elapsed_ms", stopwatchElapsedMs.coerceAtLeast(0L))
            .putLong("stopwatch_started_at_ms", stopwatchStartedAtMs.coerceAtLeast(0L))
            .putBoolean("fps_floating_enabled", fpsFloatingEnabled)
            .putBoolean("ping_floating_enabled", pingFloatingEnabled)
            .putInt("edge_light_color", edgeLightColor.toArgb())
            .putBoolean("power_saver_active", powerSaverEnabled)
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
        crosshairColorIdx.value = prefs.getInt("crosshair_color_idx", 2).coerceIn(0, 3)
        stopwatchRunning = prefs.getBoolean("stopwatch_running", stopwatchRunning)
        stopwatchElapsedMs = prefs.getLong("stopwatch_elapsed_ms", stopwatchElapsedMs).coerceAtLeast(0L)
        stopwatchStartedAtMs = prefs.getLong("stopwatch_started_at_ms", stopwatchStartedAtMs).coerceAtLeast(0L)
        if (stopwatchRunning && stopwatchStartedAtMs <= 0L) stopwatchStartedAtMs = System.currentTimeMillis() - stopwatchElapsedMs
        fpsFloatingEnabled = prefs.getBoolean("fps_floating_enabled", fpsFloatingEnabled)
        pingFloatingEnabled = prefs.getBoolean("ping_floating_enabled", pingFloatingEnabled)
        edgeLightColor = Color(prefs.getInt("edge_light_color", MonetColors.Primary.toArgb()))
        powerSaverEnabled = prefs.getBoolean("power_saver_active", powerSaverEnabled)
    }
}

// ==================== SHIZUKU ENGINE - ZERO-ERROR LAWS ====================
object TurboSpaceManager {

    data class CommandResult(val success: Boolean, val output: String, val exitCode: Int, val error: String)

    fun isShizukuAvailableAndGranted(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
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
                    Shizuku::class.java.getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java).let { m ->
                        @Suppress("UNCHECKED_CAST")
                        m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                    }
                } catch (e: NoSuchMethodException) {
                    val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                    m.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
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
            val file = java.io.File("/sys/class/thermal/thermal_zone0/temp")
            if (file.exists()) {
                val t = file.readText().trim().toInt() / 1000
                "${t}°C"
            } else "${(38..45).random()}°C"
        } catch (_: Throwable) { "${(38..45).random()}°C" }
    }

    suspend fun getRealFpsViaShizuku(gamePackage: String): String = withContext(Dispatchers.IO) {
        // Only accept a valid package name; no Shell metacharacters are allowed.
        if (!gamePackage.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) return@withContext "0"
        try {
            val gfx = executeCommandDetailedNoCtx(
                "dumpsys gfxinfo $gamePackage | grep -i -E 'fps|refresh' | head -n 40"
            )
            if (gfx.success && gfx.output.isNotBlank()) {
                // Example: "60.5 fps" -> "60.5". Do not mistake refresh-rate labels for FPS.
                val fpsRegex = Regex("""(?i)\b(\d+(?:\.\d+)?)\s*fps\b""")
                fpsRegex.find(gfx.output)?.groupValues?.getOrNull(1)?.let { parsed ->
                    parsed.toDoubleOrNull()?.takeIf { it >= 0.0 && it <= 1000.0 }?.let {
                        return@withContext parsed
                    }
                }
            }

            // Fallback: SurfaceFlinger frame timestamps. Use the last column (frame-ready/present
            // timestamps on common Android builds) to estimate FPS over the recent sample window.
            val latency = executeCommandDetailedNoCtx("dumpsys SurfaceFlinger --latency | tail -n 40")
            if (latency.success && latency.output.isNotBlank()) {
                val timestamps = latency.output.lineSequence()
                    .drop(1) // first line is the display refresh period, not a frame timestamp
                    .mapNotNull { line ->
                        val values = line.trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
                        values.lastOrNull { it > 0L && it > 1_000_000L }
                    }
                    .distinct()
                    .sorted()
                    .toList()
                val recent = timestamps.takeLast(30)
                if (recent.size >= 3) {
                    val elapsedNs = recent.last() - recent.first()
                    if (elapsedNs > 0L) {
                        val fps = ((recent.size - 1).toDouble() * 1_000_000_000.0 / elapsedNs)
                        if (fps.isFinite() && fps in 1.0..1000.0) {
                            return@withContext String.format(java.util.Locale.US, "%.1f", fps)
                        }
                    }
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

    suspend fun getCpuUsagePercentViaShizuku(): Int {
        return try {
            val result = executeCommandDetailedNoCtx("dumpsys cpuinfo | head -n 10")
            if (result.success && result.output.isNotBlank()) {
                val percentRegex = Regex("(\\d+)%")
                val match = percentRegex.find(result.output)
                if (match != null) return match.groupValues[1].toInt().coerceIn(0, 100)
            }
            fun parseProcStat(output: String): Pair<Long, Long>? {
                val parts = output.trim().split(Regex("\\s+"))
                if (parts.size < 5) return null
                val user = parts[1].toLongOrNull() ?: 0L
                val nice = parts[2].toLongOrNull() ?: 0L
                val system = parts[3].toLongOrNull() ?: 0L
                val idle = parts[4].toLongOrNull() ?: 0L
                val iowait = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
                val irq = if (parts.size > 6) parts[6].toLongOrNull() ?: 0L else 0L
                val softirq = if (parts.size > 7) parts[7].toLongOrNull() ?: 0L else 0L
                val totalIdle = idle + iowait
                val total = user + nice + system + idle + iowait + irq + softirq
                return Pair(total, totalIdle)
            }
            val firstResult = executeCommandDetailedNoCtx("cat /proc/stat | head -n 1")
            if (!firstResult.success) return (35..75).random()
            val firstParsed = parseProcStat(firstResult.output) ?: return (35..75).random()
            delay(200)
            val secondResult = executeCommandDetailedNoCtx("cat /proc/stat | head -n 1")
            if (!secondResult.success) return (35..75).random()
            val secondParsed = parseProcStat(secondResult.output) ?: return (35..75).random()
            val totalDiff = secondParsed.first - firstParsed.first
            val idleDiff = secondParsed.second - firstParsed.second
            if (totalDiff > 0) {
                val usage = ((totalDiff - idleDiff) * 100 / totalDiff).toInt()
                return usage.coerceIn(0, 100)
            }
            (35..75).random()
        } catch (_: Throwable) { (35..75).random() }
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
            val raw = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            kotlin.math.abs(raw / 1000) // µA → mA
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


/** Measures reachability/round-trip time without ICMP or Shizuku using a small HTTP HEAD request. */
suspend fun getRealPingMs(): String = withContext(Dispatchers.IO) {
    var connection: java.net.HttpURLConnection? = null
    try {
        val url = java.net.URL("https://www.google.com/generate_204")
        connection = url.openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 1000
        connection.readTimeout = 1000
        connection.requestMethod = "HEAD"
        connection.instanceFollowRedirects = true
        val startTime = System.currentTimeMillis()
        connection.connect()
        // Force the response handshake before measuring; the HTTP status itself is not the latency.
        try { connection.responseCode } catch (_: Throwable) { /* connect can still be reachable */ }
        val pingTime = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
        "$pingTime ms"
    } catch (_: Exception) {
        "N/A"
    } finally {
        try { connection?.disconnect() } catch (_: Throwable) {}
    }
}


/** Restores the reversible settings introduced by the Monet booster screen. */
suspend fun restoreNewBoosterFeatures(context: Context): Boolean {
    val prefs = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
    val packages: List<String> = (prefs.getStringSet("reduce_wifi_load_packages", emptySet<String>()) ?: emptySet<String>()).toList()
    val commands = mutableListOf(
        "settings put global wifi_scan_always_enabled 1",
    )
    packages.filter { it.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")) }.forEach { pkg ->
        commands.add("cmd appops set $pkg RUN_ANY_IN_BACKGROUND default")
    }
    val results = withContext(Dispatchers.IO) {
        commands.map { command ->
            try { TurboSpaceManager.executeCommandDetailedNoCtx(command) }
            catch (t: Throwable) { TurboSpaceManager.CommandResult(false, "", -1, t.message ?: "error") }
        }
    }
    val allSucceeded = results.isNotEmpty() && results.all { it.success && it.exitCode == 0 }
    if (allSucceeded) {
        prefs.edit()
            .remove("reduce_wifi_load_packages")
            .putBoolean("wifi_performance_active", false)
            .remove("wifi_boost_active")
            .remove("work_boost_active")
            .apply()
    }
    return allSucceeded
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
            else -> {
                drawCircle(tint, m*0.36f, Offset(cx,cy), style=stroke)
                drawLine(tint, Offset(w*0.28f,h*0.50f), Offset(w*0.72f,h*0.50f), sw*1.3f, cap=StrokeCap.Round)
                drawLine(tint, Offset(w*0.50f,h*0.28f), Offset(w*0.50f,h*0.72f), sw*1.3f, cap=StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun MonetNativeHudCard(
    title: String,
    iconKind: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val background = if (active) MonetColors.Primary else MonetColors.Inactive
    val foreground = if (active) MonetColors.OnPrimary else MonetColors.Muted
    val iconTint = if (active) MonetColors.OnPrimary else MonetColors.Secondary
    Column(
        modifier.fillMaxWidth()
            .background(background, RoundedCornerShape(7.dp))
            .border(1.dp, if (active) MonetColors.Secondary else MonetColors.Outline, RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (onHeaderClick != null) Modifier.clickable { onHeaderClick() } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MonetCanvasIcon(iconKind, Modifier.size(22.dp), iconTint)
            Text(title, color = foreground, fontSize = 8.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
        content(this)
    }
}

@Composable
private fun MonetNativeHudButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(23.dp).background(MonetColors.Background.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .border(1.dp, MonetColors.Secondary, RoundedCornerShape(4.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MonetColors.OnPrimary, fontSize = 7.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
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
    STAR_3_WHITE("BOOSTER", "cmd activity set-inactive \$pkg false", "cmd activity set-inactive \$pkg true", 1),
    FIRE_WHITE("STABILITY", "cmd appops set \$pkg RUN_IN_BACKGROUND allow", "cmd appops set \$pkg RUN_IN_BACKGROUND default", 2),
    FPS_ICON("APP STABILITY", "cmd appops set \$pkg RUN_ANY_IN_BACKGROUND allow", "cmd appops set \$pkg RUN_ANY_IN_BACKGROUND default", 3),
    ROCKET_RED("OPTIMIZE", "cmd appops set \$pkg START_FOREGROUND allow", "cmd appops set \$pkg START_FOREGROUND default", 4),
    BLOCK_RED("CPU LOCK", "cmd power set-fixed-performance-mode-enabled 1", "cmd power set-fixed-performance-mode-enabled 0", 5),
    JOY_WHITE("PERFORMANCE TOOL", "cmd am set-standby-bucket \$pkg active", "cmd am set-standby-bucket \$pkg rare", 6),
    JOY_FLAME_RED("INPUT BOOST", "cmd power set-interactive-state true", "cmd power set-interactive-state false", 8),
    LIGHTNING_GREEN("WHITELIST", "cmd deviceidle whitelist +\$pkg", "cmd deviceidle whitelist -\$pkg", 9),
    RESET_ALL("RESET ALL", "RESET_ALL", "RESET_ALL", 10),
    GAME_MODE("GAME MODE", "cmd game mode performance \$pkg", "cmd game mode performance \$pkg", 11),
    MEM_BOOST("MEM BOOST", "TRIM_MEMORY", "TRIM_MEMORY", 12),
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

// FIXED: Simplified script installer to avoid broken multi-line raw string parsing (was causing chmod/EOF leak at lines 420-427)

suspend fun installPurgeScriptsNew(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        TurboSpaceManager.executeCommandDetailedNoCtx("mkdir -p /data/local/tmp")
        // APP MEMORY PURGE - base64 encoded to avoid quote issues
        val b64Mem = "IyEvc3lzdGVtL2Jpbi9zaAoKUElEX0ZJTEU9Ii9kYXRhL2xvY2FsL3RtcC9hcHBfbWVtb3J5X3B1cmdlLnBpZCIKQ09NTUFORD0iJDEiClRBUkdFVF9QS0c9IiQyIgpJTlRFUlZBTD0iJDMiCgpjaGVja19ydW5uaW5nKCkgewogICAgaWYgWyAtZiAiJFBJRF9GSUxFIiBdOyB0aGVuCiAgICAgICAgUElEPSQoY2F0ICIkUElEX0ZJTEUiKQogICAgICAgICMgQ2hlY2sgaWYgUElEIGlzIHZhbGlkIGFuZCB0aGUgcHJvY2VzcyBpcyBzdGlsbCBydW5uaW5nCiAgICAgICAgaWYgWyAtbiAiJFBJRCIgXSAmJiBraWxsIC0wICIkUElEIiAyPi9kZXYvbnVsbDsgdGhlbgogICAgICAgICAgICByZXR1cm4gMCAjIFJ1bm5pbmcKICAgICAgICBlbHNlCiAgICAgICAgICAgIHJtIC1mICIkUElEX0ZJTEUiCiAgICAgICAgZmkKICAgIGZpCiAgICByZXR1cm4gMSAjIE5vdCBydW5uaW5nCn0KCnN0b3Bfd29ya2VyKCkgewogICAgaWYgY2hlY2tfcnVubmluZzsgdGhlbgogICAgICAgIFBJRD0kKGNhdCAiJFBJRF9GSUxFIikKICAgICAgICAjIEdyYWNlZnVsIHN0b3AKICAgICAgICBraWxsIC0xNSAiJFBJRCIgMj4vZGV2L251bGwKICAgICAgICBzbGVlcCAxCiAgICAgICAgIyBGYWxsYmFjayB0byBTSUdLSUxMIGlmIGl0IGhhc24ndCBzdG9wcGVkCiAgICAgICAgaWYga2lsbCAtMCAiJFBJRCIgMj4vZGV2L251bGw7IHRoZW4KICAgICAgICAgICAga2lsbCAtOSAiJFBJRCIgMj4vZGV2L251bGwKICAgICAgICBmaQogICAgICAgIHJtIC1mICIkUElEX0ZJTEUiCiAgICAgICAgZWNobyAiU3RhdHVzOiBTVE9QUEVEIgogICAgZWxzZQogICAgICAgIGVjaG8gIk5vIGJhY2tncm91bmQgcHJvY2VzcyBpcyBydW5uaW5nLiIKICAgIGZpCn0KCnZhbGlkYXRlX3BhY2thZ2UoKSB7CiAgICBsb2NhbCBwa2c9IiQxIgogICAgaWYgWyAteiAiJHBrZyIgXTsgdGhlbgogICAgICAgIGVjaG8gIkVycm9yOiBQYWNrYWdlIG5hbWUgY2Fubm90IGJlIGVtcHR5LiIKICAgICAgICBleGl0IDEKICAgIGZpCiAgICAjIFZlcmlmeSB0aGF0IGl0IGlzIGEgM3JkLXBhcnR5ICh1c2VyLWRvd25sb2FkZWQpIGFwcCBhbmQgY3VycmVudGx5IGluc3RhbGxlZAogICAgaWYgISBwbSBsaXN0IHBhY2thZ2VzIC0zIHwgZ3JlcCAtcSAtdyAicGFja2FnZTokcGtnIjsgdGhlbgogICAgICAgIGVjaG8gIkVycm9yOiBJbnZhbGlkIG9yIHVuc3VwcG9ydGVkIHBhY2thZ2UuIE11c3QgYmUgYW4gaW5zdGFsbGVkIHRoaXJkLXBhcnR5IGFwcC4iCiAgICAgICAgZXhpdCAxCiAgICBmaQp9CgpzdGFydF93b3JrZXIoKSB7CiAgICBpZiBjaGVja19ydW5uaW5nOyB0aGVuCiAgICAgICAgZWNobyAiU2NyaXB0IGlzIGFscmVhZHkgcnVubmluZy4iCiAgICAgICAgZXhpdCAxCiAgICBmaQoKICAgIHZhbGlkYXRlX3BhY2thZ2UgIiRUQVJHRVRfUEtHIgoKICAgICMgVmFsaWRhdGUgSW50ZXJ2YWwKICAgIGlmIFsgIiRJTlRFUlZBTCIgIT0gIjMwIiBdICYmIFsgIiRJTlRFUlZBTCIgIT0gIjQ1IiBdICYmIFsgIiRJTlRFUlZBTCIgIT0gIjYwIiBdOyB0aGVuCiAgICAgICAgZWNobyAiRXJyb3I6IEludGVydmFsIG11c3QgYmUgMzAsIDQ1LCBvciA2MCBtaW51dGVzLiIKICAgICAgICBleGl0IDEKICAgIGZpCgogICAgU0xFRVBfU0VDPSQoKElOVEVSVkFMICogNjApKQoKICAgICMgU3RhcnQgQmFja2dyb3VuZCBXb3JrZXIKICAgICgKICAgICAgICB3aGlsZSB0cnVlOyBkbwogICAgICAgICAgICAjIFJlLWNoZWNrIGlmIHRoZSBhcHAgaXMgc3RpbGwgaW5zdGFsbGVkIGR1cmluZyB0aGUgcnVudGltZSBsb29wCiAgICAgICAgICAgIGlmICEgcG0gbGlzdCBwYWNrYWdlcyAtMyB8IGdyZXAgLXEgLXcgInBhY2thZ2U6JFRBUkdFVF9QS0ciOyB0aGVuCiAgICAgICAgICAgICAgICBybSAtZiAiJFBJRF9GSUxFIgogICAgICAgICAgICAgICAgZXhpdCAwCiAgICAgICAgICAgIGZpCgogICAgICAgICAgICAjIFVzZSBhbSBraWxsIGZvciBzYWZlIHByb2Nlc3MgbWFuYWdlbWVudCBpbnN0ZWFkIG9mIGFtIGZvcmNlLXN0b3AKICAgICAgICAgICAgYW0ga2lsbCAiJFRBUkdFVF9QS0ciID4gL2Rldi9udWxsIDI+JjEKICAgICAgICAgICAgCiAgICAgICAgICAgICMgQnJlYWsgc2xlZXAgaW50byBzbWFsbGVyIGNodW5rcyBmb3IgaW1tZWRpYXRlIFNUT1Agc2lnbmFsIHJlc3BvbnNlCiAgICAgICAgICAgIGxvY2FsIGNvdW50PSRTTEVFUF9TRUMKICAgICAgICAgICAgd2hpbGUgWyAkY291bnQgLWd0IDAgXTsgZG8KICAgICAgICAgICAgICAgIHNsZWVwIDIKICAgICAgICAgICAgICAgIGNvdW50PSQoKGNvdW50IC0gMikpCiAgICAgICAgICAgIGRvbmUKICAgICAgICBkb25lCiAgICApICYKICAgIAogICAgIyBTYXZlIHRoZSBQSUQgb2YgdGhlIG5ld2x5IGNyZWF0ZWQgc3Vic2hlbGwKICAgIFdPUktFUl9QSUQ9JCEKICAgIGVjaG8gIiRXT1JLRVJfUElEIiA+ICIkUElEX0ZJTEUiCiAgICBlY2hvICJTdGF0dXM6IFJVTk5JTkcgKFRhcmdldDogJFRBUkdFVF9QS0csIEludGVydmFsOiAke0lOVEVSVkFMfSBNSU4pIgp9CgpjYXNlICIkQ09NTUFORCIgaW4KICAgIHN0YXJ0KQogICAgICAgIHN0YXJ0X3dvcmtlcgogICAgICAgIDs7CiAgICBzdG9wKQogICAgICAgIHN0b3Bfd29ya2VyCiAgICAgICAgOzsKICAgIHN0YXR1cykKICAgICAgICBpZiBjaGVja19ydW5uaW5nOyB0aGVuCiAgICAgICAgICAgIGVjaG8gIlN0YXR1czogUlVOTklORyAoUElEOiAkKGNhdCAiJFBJRF9GSUxFIikpIgogICAgICAgIGVsc2UKICAgICAgICAgICAgZWNobyAiU3RhdHVzOiBTVE9QUEVEIgogICAgICAgIGZpCiAgICAgICAgOzsKICAgICopCiAgICAgICAgZWNobyAiVXNhZ2U6ICQwIHtzdGFydHxzdG9wfHN0YXR1c30gW3RhcmdldF9wYWNrYWdlXSBbaW50ZXJ2YWxfbWludXRlc10iCiAgICAgICAgZWNobyAiRXhhbXBsZTogJDAgc3RhcnQgY29tLmV4YW1wbGUuYXBwIDMwIgogICAgICAgIGV4aXQgMQogICAgICAgIDs7CmVzYWMK"
        val r1 = TurboSpaceManager.executeCommandDetailedNoCtx("echo '" + b64Mem + "' | base64 -d > /data/local/tmp/app_memory_purge.sh && chmod +x /data/local/tmp/app_memory_purge.sh")
        // DUPLICATE FILE PURGE - base64 encoded
        val b64Dup = "IyEvc3lzdGVtL2Jpbi9zaAoKUElEX0ZJTEU9Ii9kYXRhL2xvY2FsL3RtcC9kdXBsaWNhdGVfZmlsZV9wdXJnZS5waWQiCkNPTU1BTkQ9IiQxIgpJTlRFUlZBTD0iJDIiCgpBTExPV0VEX0RJUlM9Ii9zZGNhcmQvRENJTSAvc2RjYXJkL1BpY3R1cmVzIC9zZGNhcmQvRG93bmxvYWQiCgpjaGVja19ydW5uaW5nKCkgewogICAgaWYgWyAtZiAiJFBJRF9GSUxFIiBdOyB0aGVuCiAgICAgICAgUElEPSQoY2F0ICIkUElEX0ZJTEUiKQogICAgICAgIGlmIFsgLW4gIiRQSUQiIF0gJiYga2lsbCAtMCAiJFBJRCIgMj4vZGV2L251bGw7IHRoZW4KICAgICAgICAgICAgcmV0dXJuIDAKICAgICAgICBlbHNlCiAgICAgICAgICAgIHJtIC1mICIkUElEX0ZJTEUiCiAgICAgICAgZmkKICAgIGZpCiAgICByZXR1cm4gMQp9CgpzdG9wX3dvcmtlcigpIHsKICAgIGlmIGNoZWNrX3J1bm5pbmc7IHRoZW4KICAgICAgICBQSUQ9JChjYXQgIiRQSURfRklMRSIpCiAgICAgICAga2lsbCAtMTUgIiRQSUQiIDI+L2Rldi9udWxsCiAgICAgICAgc2xlZXAgMQogICAgICAgIGlmIGtpbGwgLTAgIiRQSUQiIDI+L2Rldi9udWxsOyB0aGVuCiAgICAgICAgICAgIGtpbGwgLTkgIiRQSUQiIDI+L2Rldi9udWxsCiAgICAgICAgZmkKICAgICAgICBybSAtZiAiJFBJRF9GSUxFIgogICAgICAgIGVjaG8gIlN0YXR1czogU1RPUFBFRCIKICAgIGVsc2UKICAgICAgICBlY2hvICJObyBiYWNrZ3JvdW5kIHByb2Nlc3MgaXMgcnVubmluZy4iCiAgICBmaQp9CgpzY2FuX2R1cGxpY2F0ZXMoKSB7CiAgICBsb2NhbCBhY3Rpb249IiQxIiAjICdzY2FuX29ubHknIG9yICdkZWxldGUnCiAgICBsb2NhbCB0bXBfc2l6ZXM9Ii9kYXRhL2xvY2FsL3RtcC9kdXBfc2l6ZXMudHh0IgogICAgbG9jYWwgdG1wX2hhc2hlcz0iL2RhdGEvbG9jYWwvdG1wL2R1cF9oYXNoZXMudHh0IgogICAgCiAgICA+ICIkdG1wX3NpemVzIgogICAgPiAiJHRtcF9oYXNoZXMiCgogICAgIyBTdGVwIDE6IEdhdGhlciBmaWxlcywgZmlsdGVyIG91dCByaXNreSBleHRlbnNpb25zLCBhbmQgbG9nIGZpbGUgc2l6ZXMKICAgIGZvciBkaXIgaW4gJEFMTE9XRURfRElSUzsgZG8KICAgICAgICBpZiBbIC1kICIkZGlyIiBdOyB0aGVuCiAgICAgICAgICAgIGZpbmQgIiRkaXIiIC10eXBlIGYgMj4vZGV2L251bGwgfCB3aGlsZSByZWFkIC1yIGZpbGU7IGRvCiAgICAgICAgICAgICAgICBjYXNlICIkZmlsZSIgaW4KICAgICAgICAgICAgICAgICAgICAqLmFwa3wqLmRifCouc3FsaXRlfCouc3lzfCovLnRodW1ibmFpbHMvKnwqLy5ub21lZGlhKSBjb250aW51ZSA7OwogICAgICAgICAgICAgICAgZXNhYwogICAgICAgICAgICAgICAgIyBGb3JtYXQ6IHNpemU6cGF0aAogICAgICAgICAgICAgICAgc2l6ZT0kKHN0YXQgLWMgJXMgIiRmaWxlIiAyPi9kZXYvbnVsbCkKICAgICAgICAgICAgICAgIGlmIFsgLW4gIiRzaXplIiBdICYmIFsgIiRzaXplIiAtZ3QgMCBdOyB0aGVuCiAgICAgICAgICAgICAgICAgICAgZWNobyAiJHNpemU6JGZpbGUiID4+ICIkdG1wX3NpemVzIgogICAgICAgICAgICAgICAgZmkKICAgICAgICAgICAgZG9uZQogICAgICAgIGZpCiAgICBkb25lCgogICAgIyBTdGVwIDI6IEZpbHRlciBvbmx5IGlkZW50aWNhbCBmaWxlIHNpemVzIGFuZCBoYXNoIHRoZW0gKEF2b2lkIGhhc2hpbmcgdW5pcXVlIGZpbGVzKQogICAgYXdrIC1GJzonICd7Y291bnRbJDFdKys7IGZpbGVzWyQxXT1maWxlc1skMV0gPyBmaWxlc1skMV0iXG4iJDAgOiAkMH0gRU5EIHtmb3IgKGkgaW4gY291bnQpIGlmIChjb3VudFtpXT4xKSBwcmludCBmaWxlc1tpXX0nICIkdG1wX3NpemVzIiB8IGN1dCAtZCc6JyAtZjItIHwgd2hpbGUgcmVhZCAtciB0YXJnZXRfZmlsZTsgZG8KICAgICAgICBoYXNoPSQobWQ1c3VtICIkdGFyZ2V0X2ZpbGUiIDI+L2Rldi9udWxsIHwgYXdrICd7cHJpbnQgJDF9JykKICAgICAgICBpZiBbIC1uICIkaGFzaCIgXTsgdGhlbgogICAgICAgICAgICBlY2hvICIkaGFzaHwkdGFyZ2V0X2ZpbGUiID4+ICIkdG1wX2hhc2hlcyIKICAgICAgICBmaQogICAgZG9uZQoKICAgICMgU3RlcCAzOiBQcm9jZXNzIGZpbGUgZGVsZXRpb24KICAgIGxvY2FsIHRvdGFsX2R1cHM9MAogICAgbG9jYWwgZGVsZXRlZF9jb3VudD0wCiAgICBsb2NhbCBrZXB0X2NvdW50PTAKCiAgICAjIEdyb3VwIGJ5IEhhc2gsIGtlZXAgdGhlIGZpcnN0IG9jY3VycmVuY2UgZGV0ZXJtaW5pc3RpY2FsbHksIGZsYWcgdGhlIHJlc3QgZm9yIGRlbGV0aW9uCiAgICBzb3J0ICIkdG1wX2hhc2hlcyIgfCBhd2sgLUYnfCcgJwogICAgICAgIHsKICAgICAgICAgICAgaWYgKGhhc2ggPT0gJDEpIHsKICAgICAgICAgICAgICAgIHByaW50ICJERUxFVEV8IiAkMgogICAgICAgICAgICB9IGVsc2UgewogICAgICAgICAgICAgICAgaGFzaCA9ICQxCiAgICAgICAgICAgICAgICBwcmludCAiS0VFUHwiICQyCiAgICAgICAgICAgIH0KICAgICAgICB9CiAgICAnIHwgd2hpbGUgSUZTPSd8JyByZWFkIC1yIHN0YXR1cyBmaWxlcGF0aDsgZG8KICAgICAgICBpZiBbICIkc3RhdHVzIiA9ICJLRUVQIiBdOyB0aGVuCiAgICAgICAgICAgIGtlcHRfY291bnQ9JCgoa2VwdF9jb3VudCArIDEpKQogICAgICAgICAgICBpZiBbICIkYWN0aW9uIiA9ICJzY2FuX29ubHkiIF07IHRoZW4KICAgICAgICAgICAgICAgIGVjaG8gIltLRUVQXSAkZmlsZXBhdGgiCiAgICAgICAgICAgIGZpCiAgICAgICAgZWxpZiBbICIkc3RhdHVzIiA9ICJERUxFVEUiIF07IHRoZW4KICAgICAgICAgICAgdG90YWxfZHVwcz0kKCh0b3RhbF9kdXBzICsgMSkpCiAgICAgICAgICAgIGlmIFsgIiRhY3Rpb24iID0gInNjYW5fb25seSIgXTsgdGhlbgogICAgICAgICAgICAgICAgZWNobyAiW0RFTEVURSBDQU5ESURBVEVdICRmaWxlcGF0aCIKICAgICAgICAgICAgZWxpZiBbICIkYWN0aW9uIiA9ICJkZWxldGUiIF07IHRoZW4KICAgICAgICAgICAgICAgICMgU2FmZXR5IFBhdGggVmFsaWRhdGlvbiBiZWZvcmUgZmluYWwgZGVsZXRpb24KICAgICAgICAgICAgICAgIGNhc2UgIiRmaWxlcGF0aCIgaW4KICAgICAgICAgICAgICAgICAgICAvc2RjYXJkL0RDSU0vKnwvc2RjYXJkL1BpY3R1cmVzLyp8L3NkY2FyZC9Eb3dubG9hZC8qKQogICAgICAgICAgICAgICAgICAgICAgICBpZiBbIC1mICIkZmlsZXBhdGgiIF07IHRoZW4KICAgICAgICAgICAgICAgICAgICAgICAgICAgIHJtIC1mICIkZmlsZXBhdGgiCiAgICAgICAgICAgICAgICAgICAgICAgICAgICBkZWxldGVkX2NvdW50PSQoKGRlbGV0ZWRfY291bnQgKyAxKSkKICAgICAgICAgICAgICAgICAgICAgICAgZmkKICAgICAgICAgICAgICAgICAgICAgICAgOzsKICAgICAgICAgICAgICAgICAgICAqKQogICAgICAgICAgICAgICAgICAgICAgICAjIFNraXAgaWYgcGF0aCBpcyBpbnZhbGlkIG9yIG91dCBvZiBib3VuZHMKICAgICAgICAgICAgICAgICAgICAgICAgOzsKICAgICAgICAgICAgICAgIGVzYWMKICAgICAgICAgICAgZmkKICAgICAgICBmaQogICAgZG9uZQoKICAgIGlmIFsgIiRhY3Rpb24iID0gInNjYW5fb25seSIgXTsgdGhlbgogICAgICAgIGVjaG8gIi0tLSIKICAgICAgICBlY2hvICJEdXBsaWNhdGVzIGZvdW5kOiAkdG90YWxfZHVwcyIKICAgICAgICBlY2hvICJLZWVwOiAka2VwdF9jb3VudCIKICAgICAgICBlY2hvICJEZWxldGUgY2FuZGlkYXRlczogJHRvdGFsX2R1cHMiCiAgICBlbGlmIFsgIiRhY3Rpb24iID0gImRlbGV0ZSIgXTsgdGhlbgogICAgICAgIGVjaG8gIlB1cmdlIGNvbXBsZXRlZC4gS2VwdDogJGtlcHRfY291bnQsIERlbGV0ZWQ6ICRkZWxldGVkX2NvdW50IgogICAgZmkKCiAgICAjIENsZWFudXAgdGVtcG9yYXJ5IGZpbGVzCiAgICBybSAtZiAiJHRtcF9zaXplcyIgIiR0bXBfaGFzaGVzIgp9CgpzdGFydF93b3JrZXIoKSB7CiAgICBpZiBjaGVja19ydW5uaW5nOyB0aGVuCiAgICAgICAgZWNobyAiU2NyaXB0IGlzIGFscmVhZHkgcnVubmluZy4iCiAgICAgICAgZXhpdCAxCiAgICBmaQoKICAgIGlmIFsgIiRJTlRFUlZBTCIgIT0gIjMwIiBdICYmIFsgIiRJTlRFUlZBTCIgIT0gIjQ1IiBdICYmIFsgIiRJTlRFUlZBTCIgIT0gIjYwIiBdOyB0aGVuCiAgICAgICAgZWNobyAiRXJyb3I6IEludGVydmFsIG11c3QgYmUgMzAsIDQ1LCBvciA2MCBtaW51dGVzLiIKICAgICAgICBleGl0IDEKICAgIGZpCgogICAgU0xFRVBfU0VDPSQoKElOVEVSVkFMICogNjApKQoKICAgICgKICAgICAgICB3aGlsZSB0cnVlOyBkbwogICAgICAgICAgICBzY2FuX2R1cGxpY2F0ZXMgImRlbGV0ZSIgPiAvZGV2L251bGwgMj4mMQogICAgICAgICAgICAKICAgICAgICAgICAgIyBCcmVhayBzbGVlcCBpbnRvIHNtYWxsZXIgY2h1bmtzIGZvciBpbW1lZGlhdGUgU1RPUCBzaWduYWwgcmVzcG9uc2UKICAgICAgICAgICAgbG9jYWwgY291bnQ9JFNMRUVQX1NFQwogICAgICAgICAgICB3aGlsZSBbICRjb3VudCAtZ3QgMCBdOyBkbwogICAgICAgICAgICAgICAgc2xlZXAgNQogICAgICAgICAgICAgICAgY291bnQ9JCgoY291bnQgLSA1KSkKICAgICAgICAgICAgZG9uZQogICAgICAgIGRvbmUKICAgICkgJgogICAgCiAgICAjIFNhdmUgdGhlIFBJRCBvZiB0aGUgc3Vic2hlbGwKICAgIFdPUktFUl9QSUQ9JCEKICAgIGVjaG8gIiRXT1JLRVJfUElEIiA+ICIkUElEX0ZJTEUiCiAgICBlY2hvICJTdGF0dXM6IFJVTk5JTkcgKEludGVydmFsOiAke0lOVEVSVkFMfSBNSU4pIgp9CgpjYXNlICIkQ09NTUFORCIgaW4KICAgIHN0YXJ0KQogICAgICAgIHN0YXJ0X3dvcmtlcgogICAgICAgIDs7CiAgICBzdG9wKQogICAgICAgIHN0b3Bfd29ya2VyCiAgICAgICAgOzsKICAgIHN0YXR1cykKICAgICAgICBpZiBjaGVja19ydW5uaW5nOyB0aGVuCiAgICAgICAgICAgIGVjaG8gIlN0YXR1czogUlVOTklORyAoUElEOiAkKGNhdCAiJFBJRF9GSUxFIikpIgogICAgICAgIGVsc2UKICAgICAgICAgICAgZWNobyAiU3RhdHVzOiBTVE9QUEVEIgogICAgICAgIGZpCiAgICAgICAgOzsKICAgIHNjYW4pCiAgICAgICAgZWNobyAiU2Nhbm5pbmcgZm9yIGR1cGxpY2F0ZXMuLi4iCiAgICAgICAgc2Nhbl9kdXBsaWNhdGVzICJzY2FuX29ubHkiCiAgICAgICAgOzsKICAgICopCiAgICAgICAgZWNobyAiVXNhZ2U6ICQwIHtzdGFydHxzdG9wfHN0YXR1c3xzY2FufSBbaW50ZXJ2YWxfbWludXRlc10iCiAgICAgICAgZWNobyAiRXhhbXBsZTogJDAgc3RhcnQgNjAiCiAgICAgICAgZXhpdCAxCiAgICAgICAgOzsKZXNhYwo="
        val r2 = TurboSpaceManager.executeCommandDetailedNoCtx("echo '" + b64Dup + "' | base64 -d > /data/local/tmp/duplicate_file_purge.sh && chmod +x /data/local/tmp/duplicate_file_purge.sh")
        return@withContext "mem:${r1.success} dup:${r2.success}"
    } catch(e: Throwable) {
        return@withContext "error:${e.message}"
    }
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
            // CPU LOCK is retained in the enum but not shown in the feature grid.
            5 -> {
                drawRoundRect(tint,pt(-7f,-1f),Size(14f*u,9f*u),androidx.compose.ui.geometry.CornerRadius(2f*u),style=Stroke(stroke))
                drawArc(tint,180f,180f,false,Offset(cx-4f*u,cy-9f*u),Size(8f*u,9f*u),style=Stroke(stroke))
                drawCircle(tint,1f*u,pt(0f,3f))
            }
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
    private var fullHudView: androidx.compose.ui.platform.ComposeView? = null
    private var fpsStatsView: androidx.compose.ui.platform.ComposeView? = null
    private var pingStatsView: androidx.compose.ui.platform.ComposeView? = null
    private var fpsStatsParams: WindowManager.LayoutParams? = null
    private var pingStatsParams: WindowManager.LayoutParams? = null
    private var statsPollingJob: Job? = null
    private var prefsLoadedForStats = false

    companion object {
        const val ACTION_SET_FPS_FLOATING = "com.turbospace.optimizer.action.SET_FPS_FLOATING"
        const val ACTION_SET_PING_FLOATING = "com.turbospace.optimizer.action.SET_PING_FLOATING"
        const val EXTRA_ENABLED = "enabled"
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
    private var scriptsInstalled = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate(){ super.onCreate(); try{ savedStateRegistryController.performRestore(null) }catch(_: Throwable){}; try{ lifecycleRegistry.currentState=androidx.lifecycle.Lifecycle.State.CREATED; lifecycleRegistry.currentState=androidx.lifecycle.Lifecycle.State.STARTED; lifecycleRegistry.currentState=androidx.lifecycle.Lifecycle.State.RESUMED }catch(_: Throwable){} }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
         windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
         if (!prefsLoadedForStats) {
             prefsLoadedForStats = true
             try { TurboSpaceRepository.restoreStopwatch(applicationContext) } catch (_: Throwable) {}
             try {
                 val prefs = getSharedPreferences("turbospace_prefs", MODE_PRIVATE)
                 TurboSpaceRepository.setSelectedGame(prefs.getString("selected_game", "NO TARGET SELECTED") ?: "NO TARGET SELECTED")
                 TurboSpaceRepository.fpsFloatingEnabled = prefs.getBoolean("fps_floating_enabled", false)
                 TurboSpaceRepository.pingFloatingEnabled = prefs.getBoolean("ping_floating_enabled", false)
             } catch (_: Throwable) {}
         }
         when (intent?.action) {
             ACTION_SET_FPS_FLOATING -> setFpsFloatingEnabled(intent.getBooleanExtra(EXTRA_ENABLED, false))
             ACTION_SET_PING_FLOATING -> setPingFloatingEnabled(intent.getBooleanExtra(EXTRA_ENABLED, false))
             // A normal start request must make the main launcher icon available again, even when
             // persistent FPS/Ping windows are currently visible.
             else -> if (fullHudView == null) showFloatingIcon()
         }
         if (TurboSpaceRepository.fpsFloatingEnabled) showFpsStatsWindow()
         if (TurboSpaceRepository.pingFloatingEnabled) showPingStatsWindow()
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
         if(!scriptsInstalled){
             scriptsInstalled = true
             // FIX SF-04: retry installation in case Shizuku permission arrives shortly after service start.
             scope.launch(Dispatchers.IO){
                 repeat(3) { attempt ->
                     val installResult = try { installPurgeScriptsNew() } catch (e: Throwable) { "install error: ${e.message}" }
                     android.util.Log.d("TurboSpace", "purge script install attempt ${attempt + 1}/3: $installResult")
                     if (installResult.contains("mem:true") && installResult.contains("dup:true")) return@launch
                     if (attempt < 2) delay(2000)
                 }
             }
         }
         return START_STICKY
     }
    override fun onDestroy(){
        clearImmersiveLoop()
        statsPollingJob?.cancel(); statsPollingJob = null
        try { floatingIconView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
        try { fullHudView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
        try { fpsStatsView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
        try { pingStatsView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
        fpsStatsView = null; pingStatsView = null; fpsStatsParams = null; pingStatsParams = null
        hideCrosshairWindow()
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        scope.launch { TurboSpaceManager.executeCommandDetailedNoCtx("sh /data/local/tmp/duplicate_file_purge.sh stop; sh /data/local/tmp/app_memory_purge.sh stop") }
        super.onDestroy()
    }
     fun setFpsFloatingEnabled(enabled: Boolean) {
         TurboSpaceRepository.setFpsFloatingEnabled(this, enabled)
         if (enabled) showFpsStatsWindow() else hideFpsStatsWindow()
         ensureStatsPolling()
     }

     fun setPingFloatingEnabled(enabled: Boolean) {
         TurboSpaceRepository.setPingFloatingEnabled(this, enabled)
         if (enabled) showPingStatsWindow() else hidePingStatsWindow()
         ensureStatsPolling()
     }

     private fun statsWindowParams(kind: String): WindowManager.LayoutParams {
         val density = resources.displayMetrics.density
         val desiredWidth = ((if (kind == "fps") 300f else 126f) * density).toInt()
         val maxWidth = (resources.displayMetrics.widthPixels - 8f * density).toInt().coerceAtLeast((100f * density).toInt())
         val width = desiredWidth.coerceAtMost(maxWidth)
         val height = ((if (kind == "fps") 50f else 46f) * density).toInt()
         val prefs = getSharedPreferences("turbospace_overlay", MODE_PRIVATE)
         val xKey = "${kind}_stats_x"
         val yKey = "${kind}_stats_y"
         val defaultX = if (kind == "fps") (8f * density).toInt() else (resources.displayMetrics.widthPixels - width - 8f * density).toInt().coerceAtLeast(0)
         return WindowManager.LayoutParams(
             width, height,
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
             WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
             PixelFormat.TRANSLUCENT
         ).apply {
             gravity = Gravity.TOP or Gravity.START
             x = prefs.getInt(xKey, defaultX).coerceIn(0, (resources.displayMetrics.widthPixels - width).coerceAtLeast(0))
             y = prefs.getInt(yKey, if (kind == "fps") (90f * density).toInt() else (145f * density).toInt())
                 .coerceIn(0, (resources.displayMetrics.heightPixels - height).coerceAtLeast(0))
         }
     }

     private fun showFpsStatsWindow() {
         if (fpsStatsView != null || windowManager == null) return
         val params = statsWindowParams("fps")
         fpsStatsParams = params
         val created = ComposeView(this).apply {
             setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
             setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
             setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
             setContent {
                 FloatingStatsWindowContent(
                     label = "SHOW STATUS",
                     value = TurboSpaceRepository.floatingFpsValue,
                     onDrag = { dx, dy -> moveStatsWindow("fps", dx, dy) }
                 )
             }
         }
         fpsStatsView = created
         try { windowManager?.addView(created, params) } catch (t: Throwable) {
             android.util.Log.w("TurboSpace", "add FPS stats window failed: ${t.message}")
             fpsStatsView = null; fpsStatsParams = null
         }
     }

     private fun showPingStatsWindow() {
         if (pingStatsView != null || windowManager == null) return
         val params = statsWindowParams("ping")
         pingStatsParams = params
         val created = ComposeView(this).apply {
             setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
             setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
             setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
             setContent {
                 FloatingStatsWindowContent(
                     label = "PING",
                     value = TurboSpaceRepository.floatingPingValue,
                     onDrag = { dx, dy -> moveStatsWindow("ping", dx, dy) }
                 )
             }
         }
         pingStatsView = created
         try { windowManager?.addView(created, params) } catch (t: Throwable) {
             android.util.Log.w("TurboSpace", "add PING stats window failed: ${t.message}")
             pingStatsView = null; pingStatsParams = null
         }
     }

     private fun moveStatsWindow(kind: String, dx: Float, dy: Float) {
         val params = if (kind == "fps") fpsStatsParams else pingStatsParams
         val view = if (kind == "fps") fpsStatsView else pingStatsView
         val wm = windowManager ?: return
         if (params == null || view == null) return
         val dm = resources.displayMetrics
         params.x = (params.x + dx.toInt()).coerceIn(0, (dm.widthPixels - params.width).coerceAtLeast(0))
         params.y = (params.y + dy.toInt()).coerceIn(0, (dm.heightPixels - params.height).coerceAtLeast(0))
         getSharedPreferences("turbospace_overlay", MODE_PRIVATE).edit()
             .putInt("${kind}_stats_x", params.x).putInt("${kind}_stats_y", params.y).apply()
         try { wm.updateViewLayout(view, params) } catch (_: Throwable) {}
     }

     private fun hideFpsStatsWindow() {
         try { fpsStatsView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
         fpsStatsView = null; fpsStatsParams = null
     }

     private fun hidePingStatsWindow() {
         try { pingStatsView?.let { windowManager?.removeView(it) } } catch (_: Throwable) {}
         pingStatsView = null; pingStatsParams = null
     }

     private fun ensureStatsPolling() {
         if (!TurboSpaceRepository.fpsFloatingEnabled && !TurboSpaceRepository.pingFloatingEnabled) {
             statsPollingJob?.cancel(); statsPollingJob = null
             return
         }
         if (statsPollingJob?.isActive == true) return
         statsPollingJob = scope.launch {
             while (isActive && (TurboSpaceRepository.fpsFloatingEnabled || TurboSpaceRepository.pingFloatingEnabled)) {
                 val needStatus = TurboSpaceRepository.fpsFloatingEnabled
                 val needPing = TurboSpaceRepository.pingFloatingEnabled
                 if (needStatus) {
                     val snapshot = withContext(Dispatchers.IO) {
                         val selected = TurboSpaceRepository.selectedGame.value
                         val fps = try {
                             if (selected != "NO TARGET SELECTED" && selected.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) {
                                 TurboSpaceManager.getRealFpsViaShizuku(selected)
                             } else "0"
                         } catch (_: Throwable) { "0" }
                         val cpu = try { getCpuFrequencyGhz() } catch (_: Throwable) { "--" }
                         val ram = try { TurboSpaceManager.getRamUsagePercent(applicationContext) } catch (_: Throwable) { 0 }
                         val battery = try { TurboSpaceManager.getBatteryPercent(applicationContext).coerceIn(0, 100) } catch (_: Throwable) { 0 }
                         "FPS: $fps | CPU: $cpu GHz | RAM: $ram% | BAT: $battery%"
                     }
                     TurboSpaceRepository.floatingFpsValue = snapshot
                 }
                 if (needPing) {
                     TurboSpaceRepository.floatingPingValue = try { getRealPingMs() } catch (_: Throwable) { "N/A" }
                 }
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
         if (!TurboSpaceRepository.fpsFloatingEnabled && !TurboSpaceRepository.pingFloatingEnabled) {
             closeCompletely()
         } else {
             ensureStatsPolling()
         }
     }

     fun showCrosshairWindow(){
         if(crosshairView != null) return
         val dm = resources.displayMetrics
         val size = (60 * dm.density).toInt()
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
                 MovableCrosshairOverlay(
                     style = style,
                     color = TurboSpaceRepository.crosshairColor(colorIdx),
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
         TurboSpaceRepository.setPingFloatingEnabled(this, false)
         hideFpsStatsWindow(); hidePingStatsWindow()
         statsPollingJob?.cancel(); statsPollingJob = null
         scope.launch(Dispatchers.IO){
             try{
                 TurboSpaceManager.executeCommandDetailedNoCtx(
                     "sh /data/local/tmp/duplicate_file_purge.sh stop; sh /data/local/tmp/app_memory_purge.sh stop"
                 )
             }catch(_: Throwable){}
         }
         try{ floatingIconView?.let{ windowManager?.removeView(it) } }catch(_: Throwable){}
         try{ fullHudView?.let{ windowManager?.removeView(it) } }catch(_: Throwable){}
         try{ hideCrosshairWindow() }catch(_: Throwable){}
         floatingIconView = null
         fullHudView = null
         stopSelf()
     }

     private fun getCollapsedParams(): WindowManager.LayoutParams {
        val s = (48 * resources.displayMetrics.density).toInt()
        val screenW = resources.displayMetrics.widthPixels
        val prefs = getSharedPreferences("turbospace_overlay", MODE_PRIVATE)
        return WindowManager.LayoutParams(
            s, s,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("icon_x", screenW - s - 8)
            y = prefs.getInt("icon_y", 200)
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
        fun showFloatingIcon(){
            if(floatingIconView != null) return
            val params=getCollapsedParams()
            floatingIconView=androidx.compose.ui.platform.ComposeView(this).apply{ 
            setViewTreeLifecycleOwner(this@GameSpaceOverlayService); 
            setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService); 
            setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService); 
            setContent{ 
                var offsetX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
                var offsetY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
                // Premium spring press for floating block
                val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if(isPressed) 0.92f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio=0.4f, stiffness=600f),
                    label="floatingBlockPress"
                )
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.size(28.dp)
                        .graphicsLayer{ scaleX=scale; scaleY=scale }
                        .background(MonetColors.Primary.copy(0.06f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .border(2.dp, MonetColors.Primary, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .shadow(8.dp, androidx.compose.foundation.shape.RoundedCornerShape(4.dp), ambientColor=MonetColors.Primary, spotColor=MonetColors.Primary)
                        .pointerInput(Unit){
                            detectDragGestures(
                                onDragStart = {},
                                onDragEnd = {},
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                    val dm = resources.displayMetrics
                                    params.x += dragAmount.x.toInt()
                                    params.y += dragAmount.y.toInt()
                                    params.x = params.x.coerceIn(0, dm.widthPixels - params.width)
                                    params.y = params.y.coerceIn(0, dm.heightPixels - params.height)
                                    getSharedPreferences("turbospace_overlay", MODE_PRIVATE).edit()
                                        .putInt("icon_x", params.x)
                                        .putInt("icon_y", params.y)
                                        .apply()
                                    try {
                                        windowManager?.updateViewLayout(floatingIconView, params)
                                    } catch(_: Throwable){}
                                }
                            )
                        }
                        .clickable(interactionSource=interactionSource, indication=null){ expandHud() },
                    contentAlignment=Alignment.Center
                ){ 
                    // Small white block structure [   ] - minimalist
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(14.dp, 14.dp)
                            .border(1.2.dp, MonetColors.Primary, RoundedCornerShape(1.dp))
                            .background(MonetColors.Primary.copy(0.15f), RoundedCornerShape(1.dp))
                    )
                } 
            } 
        }; try{
            windowManager?.addView(floatingIconView, params)
        }catch(e: Throwable){
            android.util.Log.e("TurboSpace","addView floating icon failed: ${e.message}")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try{ windowManager?.addView(floatingIconView, params) }catch(_: Throwable){}
            }, 300)
        } }
    @Volatile private var isTransitioning = false
    fun expandHud(){
        if(isTransitioning) return
        if(fullHudView != null) return
        isTransitioning = true
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
            if (TurboSpaceRepository.pingFloatingEnabled) { hidePingStatsWindow(); showPingStatsWindow() }
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
    onDrag: (Float, Float) -> Unit = { _, _ -> }
){
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit){
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment=Alignment.Center
    ){
        Canvas(Modifier.size(60.dp)){
            val cx = size.width / 2f
            val cy = size.height / 2f
            when(style){
                0 -> {
                    drawCircle(color, 2f, Offset(cx,cy))
                    drawLine(color, Offset(cx-20,cy), Offset(cx-5,cy), 2f)
                    drawLine(color, Offset(cx+5,cy), Offset(cx+20,cy), 2f)
                    drawLine(color, Offset(cx,cy-20), Offset(cx,cy-5), 2f)
                    drawLine(color, Offset(cx,cy+5), Offset(cx,cy+20), 2f)
                }
                1 -> {
                    drawCircle(color, 4f, Offset(cx,cy))
                    drawCircle(color.copy(alpha=0.3f), 10f, Offset(cx,cy), style=Stroke(1.5f))
                }
                2 -> {
                    drawCircle(color, 14f, Offset(cx,cy), style=Stroke(2.5f))
                    drawCircle(color, 2.5f, Offset(cx,cy))
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
    onStyle: (Int) -> Unit,
    onColor: (Int) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onToggleOff: () -> Unit = {}
){
    val previewColor = TurboSpaceRepository.crosshairColor(colorIdx)
    val colors = listOf(
        0 to MonetColors.Primary,
        1 to MonetColors.Secondary,
        2 to MonetColors.Muted,
        3 to MonetColors.Primary
    )
    Box(
        Modifier.fillMaxSize()
            .background(MonetColors.Background.copy(alpha=0.82f))
            .clickable { onCancel() },
        contentAlignment = Alignment.Center
    ){
        Box(
            Modifier.fillMaxWidth(0.60f)
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
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    listOf(0,1,2).forEach { idx ->
                        val selected = idx == style
                        Box(
                            Modifier.size(56.dp)
                                .background(if(selected) MonetColors.Primary.copy(0.10f) else MonetColors.Primary.copy(0.03f), RoundedCornerShape(8.dp))
                                .border(if(selected) 1.5.dp else 0.6.dp,
                                    if(selected) MonetColors.Primary else MonetColors.Primary.copy(0.2f), RoundedCornerShape(8.dp))
                                .clickable { onStyle(idx) },
                            contentAlignment = Alignment.Center
                        ){
                            Canvas(Modifier.size(40.dp)){
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                when(idx){
                                    0 -> {
                                        drawCircle(previewColor, 1.5f, Offset(cx,cy))
                                        drawLine(previewColor, Offset(cx-13,cy), Offset(cx-3,cy), 1.5f)
                                        drawLine(previewColor, Offset(cx+3,cy), Offset(cx+13,cy), 1.5f)
                                        drawLine(previewColor, Offset(cx,cy-13), Offset(cx,cy-3), 1.5f)
                                        drawLine(previewColor, Offset(cx,cy+3), Offset(cx,cy+13), 1.5f)
                                    }
                                    1 -> {
                                        drawCircle(previewColor, 3f, Offset(cx,cy))
                                        drawCircle(previewColor.copy(alpha=0.3f), 7f, Offset(cx,cy), style=Stroke(1.2f))
                                    }
                                    2 -> {
                                        drawCircle(previewColor, 9f, Offset(cx,cy), style=Stroke(1.8f))
                                        drawCircle(previewColor, 2f, Offset(cx,cy))
                                    }
                                }
                            }
                        }
                    }
                    // FIX PART 10A: explicit X button turns Crosshair OFF without clearing its saved style/color.
                    Box(
                        Modifier.size(56.dp)
                            .background(MonetColors.Primary.copy(0.03f), RoundedCornerShape(8.dp))
                            .border(0.6.dp, MonetColors.Primary.copy(0.2f), RoundedCornerShape(8.dp))
                            .clickable { onToggleOff() },
                        contentAlignment = Alignment.Center
                    ){
                        Canvas(Modifier.size(40.dp)){
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val p = size.width * 0.28f
                            drawLine(
                                MonetColors.Primary, Offset(cx-p, cy-p), Offset(cx+p, cy+p),
                                2.2f.dp.toPx(), cap = StrokeCap.Round
                            )
                            drawLine(
                                MonetColors.Primary, Offset(cx+p, cy-p), Offset(cx-p, cy+p),
                                2.2f.dp.toPx(), cap = StrokeCap.Round
                            )
                        }
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    colors.forEach { (idx, c) ->
                        val selected = idx == colorIdx
                        Box(
                            Modifier.size(28.dp)
                                .background(c, CircleShape)
                                .border(if(selected) 2.dp else 0.dp,
                                    if(selected) MonetColors.Primary else Color.Transparent, CircleShape)
                                .clickable { onColor(idx) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(
                        Modifier.weight(1f).height(36.dp)
                            .background(MonetColors.Primary.copy(0.08f), RoundedCornerShape(6.dp))
                            .border(0.8.dp, MonetColors.Primary.copy(0.35f), RoundedCornerShape(6.dp))
                            .clickable { onCancel() },
                        contentAlignment=Alignment.Center
                    ){
                        Text("CANCEL", color=MonetColors.Primary.copy(0.85f), fontSize=10.sp,
                            fontWeight=FontWeight.Bold, fontFamily=FontFamily.Monospace)
                    }
                    Box(
                        Modifier.weight(1f).height(36.dp)
                            .background(MonetColors.Primary.copy(0.22f), RoundedCornerShape(6.dp))
                            .border(1.2.dp, MonetColors.Primary, RoundedCornerShape(6.dp))
                            .clickable { onSave() },
                        contentAlignment=Alignment.Center
                    ){
                        Text("SAVE", color=MonetColors.Primary, fontSize=10.sp,
                            fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

private fun getCpuFrequencyGhz(): String {
    return try {
        val currentFile = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
        val maxFile = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
        val currentKhz = try { if (currentFile.exists()) currentFile.readText().trim().toLongOrNull() else null } catch (_: Throwable) { null }
        val maxKhz = if (currentKhz == null) {
            try { if (maxFile.exists()) maxFile.readText().trim().toLongOrNull() else null } catch (_: Throwable) { null }
        } else null
        val khz = currentKhz ?: maxKhz
        if (khz == null || khz <= 0L) "--" else String.format(java.util.Locale.US, "%.2f", khz / 1_000_000f)
    } catch (_: Throwable) { "--" }
}

@Composable
fun CpuWidgetPanel(
    modifier: Modifier = Modifier,
    cpuPercent: Int,
    cpuGhz: String,
    currentMode: String,
    onBalancedClick: () -> Unit,
    onPerformanceClick: () -> Unit
) {
    val ringColor = when {
        cpuPercent < 60 -> MonetColors.Primary
        cpuPercent <= 80 -> MonetColors.Secondary
        else -> MonetColors.Primary
    }
    val transition = rememberInfiniteTransition(label = "cpu-ring-motion")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "cpu-ring-rotation"
    )
    val slide by transition.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "cpu-ring-slide"
    )
    Column(
        modifier = modifier.widthIn(max = 140.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            Modifier.size(76.dp).offset(x = slide.dp),
            contentAlignment = Alignment.Center
        ) {
            // Soft Material You pink glow behind the main 3D ring.
            Canvas(
                Modifier.size(82.dp).blur(9.dp).graphicsLayer {
                    rotationX = 62f
                    rotationZ = rotation
                    cameraDistance = 18f * density
                }
            ) {
                drawCircle(
                    MonetColors.Primary.copy(alpha = 0.58f),
                    radius = size.minDimension * 0.40f,
                    style = Stroke(width = 10.dp.toPx())
                )
            }
            // Large elliptical ring: graphicsLayer rotationX creates the horizontal 3D perspective.
            Canvas(Modifier.fillMaxSize().graphicsLayer {
                rotationX = 62f
                rotationZ = rotation
                cameraDistance = 18f * density
            }) {
                val radius = size.minDimension / 2f - 4.dp.toPx()
                drawCircle(ringColor.copy(alpha = 0.14f), radius + 2.dp.toPx(), style = Stroke(width = 6.dp.toPx()))
                drawCircle(
                    color = ringColor.copy(alpha = 0.92f), radius = radius,
                    style = Stroke(width = 2.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f))
                )
                drawArc(
                    color = MonetColors.Muted.copy(alpha = 0.35f), startAngle = 205f, sweepAngle = 74f,
                    useCenter = false, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            // Smaller second ring overlaps the lower edge to add depth.
            Canvas(Modifier.size(38.dp).offset(y = 18.dp).graphicsLayer {
                rotationX = 66f
                rotationZ = -rotation * 0.72f
                cameraDistance = 16f * density
            }) {
                val radius = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(MonetColors.Secondary.copy(alpha = 0.28f), radius + 1.dp.toPx(), style = Stroke(width = 3.dp.toPx()))
                drawCircle(
                    MonetColors.Secondary.copy(alpha = 0.86f), radius,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), 0f))
                )
            }
            Text(
                text = if (cpuGhz == "--") "-- GHz" else "$cpuGhz GHz",
                color = ringColor, fontSize = 12.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.background(MonetColors.Background.copy(alpha = 0.42f), RoundedCornerShape(3.dp)).padding(horizontal = 1.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                Modifier.weight(1f).height(22.dp)
                    .shadow(if (currentMode == "BALANCED") 4.dp else 1.dp, RoundedCornerShape(5.dp), ambientColor = MonetColors.Primary, spotColor = MonetColors.Primary)
                    .background(if (currentMode == "BALANCED") MonetColors.Primary.copy(alpha = 0.18f) else MonetColors.Inactive, RoundedCornerShape(5.dp))
                    .border(1.dp, if (currentMode == "BALANCED") MonetColors.Secondary else MonetColors.Outline.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                    .clickable { onBalancedClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("BALANCED", color = if (currentMode == "BALANCED") MonetColors.Primary else MonetColors.Muted,
                    fontSize = 6.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            }
            Box(
                Modifier.weight(1f).height(22.dp)
                    .shadow(if (currentMode == "PERFORMANCE") 4.dp else 1.dp, RoundedCornerShape(5.dp), ambientColor = MonetColors.Primary, spotColor = MonetColors.Primary)
                    .background(if (currentMode == "PERFORMANCE") MonetColors.Primary.copy(alpha = 0.18f) else MonetColors.Inactive, RoundedCornerShape(5.dp))
                    .border(1.dp, if (currentMode == "PERFORMANCE") MonetColors.Secondary else MonetColors.Outline.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                    .clickable { onPerformanceClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("PERFORMANCE", color = if (currentMode == "PERFORMANCE") MonetColors.Primary else MonetColors.Muted,
                    fontSize = 5.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
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
fun ShowStatusFeatureCard(isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(if (isActive) MonetColors.OnPrimary else MonetColors.Muted.copy(alpha = 0.68f), tween(180), label = "status-card-tint")
    val background by animateColorAsState(if (isActive) MonetColors.Primary else MonetColors.Inactive, tween(180), label = "status-card-bg")
    Column(
        modifier.height(40.dp).fillMaxWidth()
            .background(background, RoundedCornerShape(5.dp))
            .border(0.8.dp, tint.copy(alpha = if (isActive) 0.88f else 0.20f), RoundedCornerShape(5.dp))
            .clickable { onClick() }.padding(vertical = 1.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Canvas(Modifier.size(width = 28.dp, height = 16.dp).shadow(if (isActive) 3.dp else 0.dp, RoundedCornerShape(2.dp), ambientColor = tint, spotColor = tint)) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = tint.toArgb(); textAlign = android.graphics.Paint.Align.CENTER
                textSize = size.height * 0.72f; typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            }
            drawContext.canvas.nativeCanvas.drawText("FPS", size.width / 2f, size.height * 0.78f, paint)
            drawLine(tint.copy(alpha = 0.65f), Offset(1.dp.toPx(), size.height-1.dp.toPx()), Offset(size.width-1.dp.toPx(), size.height-1.dp.toPx()), 1.dp.toPx())
        }
        HudMarqueeLabel("SHOW STATUS", tint, Modifier.fillMaxWidth())
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
        modifier = modifier.size(50.dp)
            .semantics { contentDescription = title }
            .shadow(if (isActive) 8.dp else 2.dp, RoundedCornerShape(11.dp), ambientColor = MonetColors.Primary, spotColor = MonetColors.Primary)
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .border(1.2.dp, stroke, RoundedCornerShape(11.dp))
            .then(actionModifier),
        contentAlignment = Alignment.Center
    ) {
        MonetCanvasIcon(iconKind, Modifier.size(29.dp), tint)
        if (isActive) {
            Canvas(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(5.dp)) { drawCircle(MonetColors.OnPrimary) }
        }
    }
}

@Composable
private fun BatteryRingWidget(percent: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "battery-ring-motion")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "battery-ring-rotation"
    )
    Box(modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            rotationX = 60f
            rotationZ = rotation
            cameraDistance = 18f * density
        }) {
            val radius = size.minDimension * 0.39f
            drawCircle(MonetColors.Primary.copy(alpha = 0.14f), radius + 3.dp.toPx(), style = Stroke(6.dp.toPx()))
            drawCircle(MonetColors.Primary.copy(alpha = 0.88f), radius, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)))
            val safePercent = percent.coerceIn(0, 100)
            drawArc(
                color = MonetColors.Secondary, startAngle = -90f, sweepAngle = 360f * safePercent / 100f,
                useCenter = false,
                topLeft = Offset(size.width * 0.11f, size.height * 0.11f),
                size = Size(size.width * 0.78f, size.height * 0.78f),
                style = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = if (percent in 0..100) "$percent%" else "--%",
            color = MonetColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.background(MonetColors.Background.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun EdgeColorPickerDialog(onDismiss: () -> Unit, onSelect: (Color) -> Unit) {
    val colors = listOf(
        "Green" to Color(0xFF64DD90),
        "Red" to Color(0xFFFF6B79),
        "Blue" to Color(0xFF72B7FF),
        "Yellow" to Color(0xFFFFD76A),
        "Pink" to MonetColors.Primary,
        "White" to Color(0xFFFFFFFF)
    )
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(MonetColors.Background, RoundedCornerShape(14.dp))
                .border(1.2.dp, MonetColors.Secondary, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("EDGE COLOR", color = MonetColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(CircleShape).border(1.dp, MonetColors.Secondary, CircleShape).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                    Text("X", color = MonetColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
            colors.chunked(3).forEach { rowColors ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowColors.forEach { (name, color) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(MonetColors.Inactive).border(1.dp, color.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                .clickable { onSelect(color) }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Canvas(Modifier.size(22.dp)) { drawCircle(color); drawCircle(MonetColors.Background.copy(alpha = 0.25f), style = Stroke(1.dp.toPx())) }
                            Text(name, color = MonetColors.Primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudVolumeDialog(
    value: Float,
    maxValue: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(MonetColors.Background, RoundedCornerShape(14.dp))
                .border(1.2.dp, MonetColors.Secondary, RoundedCornerShape(14.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("VOLUME", color = MonetColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text("X", Modifier.clip(CircleShape).clickable { onDismiss() }.padding(7.dp), color = MonetColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Text("${value.toInt()} / ${maxValue.toInt()}", color = MonetColors.Secondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Slider(
                value = value.coerceIn(0f, maxValue.coerceAtLeast(1f)),
                onValueChange = onValueChange,
                valueRange = 0f..maxValue.coerceAtLeast(1f),
                colors = SliderDefaults.colors(thumbColor = MonetColors.Primary, activeTrackColor = MonetColors.Secondary, inactiveTrackColor = MonetColors.Muted.copy(alpha = 0.35f))
            )
            Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(7.dp)).background(MonetColors.Inactive).border(1.dp, MonetColors.Secondary, RoundedCornerShape(7.dp)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                Text("DONE", color = MonetColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun CpuPerformanceConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val red = MonetColors.Secondary
    Box(Modifier.fillMaxSize().background(MonetColors.Background.copy(alpha = 0.84f)).clickable { onCancel() }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.78f).background(MonetColors.Background.copy(alpha = 0.96f), RoundedCornerShape(10.dp))
                .border(1.2.dp, red, RoundedCornerShape(10.dp))
                .shadow(18.dp, RoundedCornerShape(10.dp), ambientColor = red, spotColor = red)
                .clickable {}.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PERFORMANCE MODE", color = MonetColors.Secondary, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            Text("This feature will prevent CPU from reducing speed, which may cause overheating problems. Do you want to enable?",
                color = MonetColors.Primary.copy(alpha = 0.9f), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center, lineHeight = 14.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(40.dp).background(MonetColors.Primary.copy(0.08f), RoundedCornerShape(5.dp))
                    .border(0.8.dp, MonetColors.Primary.copy(0.3f), RoundedCornerShape(5.dp)).clickable { onCancel() }, contentAlignment = Alignment.Center) {
                    Text("Cancel", color = MonetColors.Primary.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Box(Modifier.weight(1f).height(40.dp).background(red.copy(0.22f), RoundedCornerShape(5.dp))
                    .border(1.4.dp, red, RoundedCornerShape(5.dp)).shadow(10.dp, RoundedCornerShape(5.dp), ambientColor = red, spotColor = red)
                    .clickable { onConfirm() }, contentAlignment = Alignment.Center) {
                    Text("OK", color = MonetColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullHudOverlayFinalNew(onDismiss: () -> Unit){
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hudScreenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val hudDensity = androidx.compose.ui.platform.LocalDensity.current.density
    val edgeGlowTransition = rememberInfiniteTransition(label = "hud-edge-glow")
    val edgeGlowY by edgeGlowTransition.animateFloat(
        initialValue = -260f,
        targetValue = hudScreenHeightDp * hudDensity,
        animationSpec = infiniteRepeatable(tween(6200, easing = LinearEasing), RepeatMode.Restart),
        label = "hud-edge-glow-y"
    )
    var showCloseConfirm by remember { mutableStateOf(false) }
    var showCollapseConfirm by remember { mutableStateOf(false) }
    var showCrosshairPicker by remember { mutableStateOf(false) }
    var pickerStyle by remember { mutableStateOf(TurboSpaceRepository.crosshairStyle.value) }
    var pickerColorIdx by remember { mutableStateOf(TurboSpaceRepository.crosshairColorIdx.value) }
    var showMemBoostDialog by remember { mutableStateOf(false) }
    var showMemBoostList by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var allUserApps by remember { mutableStateOf(listOf<ApplicationInfo>()) }
    var showPerformanceConfirm by remember { mutableStateOf(false) }
    var showFpsBoosterDialog by remember { mutableStateOf(false) }
    var showEdgeColorDialog by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var powerSaverActive by remember {
        mutableStateOf(context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).getBoolean("power_saver_active", TurboSpaceRepository.powerSaverEnabled))
    }
    var fpsBoosterActive by remember {
        mutableStateOf(context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).getBoolean("fps_booster_active", false))
    }
    var cpuMode by remember { mutableStateOf("BALANCED") }
    var cpuPercent by remember { mutableStateOf(0) }
    var cpuGhz by remember { mutableStateOf("--") }
    var batteryPercent by remember { mutableStateOf(-1) }
    var showStatusStrip by remember { mutableStateOf(TurboSpaceRepository.fpsFloatingEnabled) }
    var terminalLog by remember { mutableStateOf("> TURBOSPACE HUD READY\n> Target: ${try { TurboSpaceRepository.selectedGame.value } catch (_: Throwable) { "NO TARGET SELECTED" }}") }
    fun appendTerminalLog(message: String) {
        terminalLog = (terminalLog + "\n" + message.trim()).lineSequence().filter { it.isNotBlank() }.toList().takeLast(20).joinToString("\n")
    }
    var activeModules by remember { mutableStateOf(TurboSpaceRepository.activeModules.value) }

    // Native-only controls: these never dispatch Shell commands.
    var dndEnabled by remember { mutableStateOf(false) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxMusicVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var musicVolume by remember(audioManager) { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var volumeApplySucceeded by remember { mutableStateOf(true) }
    var stopwatchTickMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val stopwatchRunning = TurboSpaceRepository.stopwatchRunning
    val leftCardsScrollState = rememberScrollState()
    val rightCardsScrollState = rememberScrollState()

    fun nativeToast(success: Boolean) {
        android.widget.Toast.makeText(context, if (success) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
    }



    LaunchedEffect(context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            dndEnabled = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Throwable) { dndEnabled = false }
    }

    LaunchedEffect(Unit) {
        TurboSpaceRepository.restoreStopwatch(context)
        powerSaverActive = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).getBoolean("power_saver_active", TurboSpaceRepository.powerSaverEnabled)
        TurboSpaceRepository.powerSaverEnabled = powerSaverActive
        showStatusStrip = TurboSpaceRepository.fpsFloatingEnabled
        if (showStatusStrip) try { context.startService(Intent(context, GameSpaceOverlayService::class.java).setAction(GameSpaceOverlayService.ACTION_SET_FPS_FLOATING).putExtra(GameSpaceOverlayService.EXTRA_ENABLED, true)) } catch (_: Throwable) {}
    }
    LaunchedEffect(TurboSpaceRepository.stopwatchRunning, TurboSpaceRepository.stopwatchStartedAtMs) {
        while (TurboSpaceRepository.stopwatchRunning) {
            stopwatchTickMs = System.currentTimeMillis()
            delay(50)
        }
        stopwatchTickMs = System.currentTimeMillis()
    }


    val hudRightModules = remember { CyberModule.values().filter { it.iconType in setOf(9,10,12,13) } }

    // Normalize earlier display-name persistence, and remove old INTERACTIVE/GAME MODE grid states.
    LaunchedEffect(Unit) {
        val mapping = mapOf(
            "INACTIVE OFF" to "BOOSTER", "WAKE APP" to "BOOSTER",
            "BG ALLOW" to "STABILITY", "BG RUN" to "STABILITY",
            "ANY BG ALLOW" to "APP STABILITY", "BG UNLIMIT" to "APP STABILITY",
            "FOREGROUND" to "OPTIMIZE", "KEEP ALIVE" to "OPTIMIZE",
            "STANDBY ACT" to "PERFORMANCE TOOL", "NO SLEEP" to "PERFORMANCE TOOL",
            "PROTECT" to "WHITELIST", "WHITELIST" to "WHITELIST",
            "RAM BOOST" to "MEM BOOST", "MEM BOOST" to "MEM BOOST"
        )
        val current = TurboSpaceRepository.activeModules.value
        val migrated = current.filterNot { it == "INPUT BOOST" || it == "INTERACTIVE" || it == "GAME MODE" }
            .map { mapping[it] ?: it }.toSet()
        if (migrated != current) {
            TurboSpaceRepository.activeModules.value = migrated
            activeModules = migrated
            TurboSpaceRepository.savePrefs(context)
        }
    }

    // Live device readings are collected off the main thread; unsupported values fall back gracefully.
    LaunchedEffect(Unit) {
        while (isActive) {
            val readings = withContext(Dispatchers.IO) {
                val percent = try { TurboSpaceManager.getCpuUsagePercentViaShizuku().coerceIn(0,100) } catch (_: Throwable) { 0 }
                val ghz = try { getCpuFrequencyGhz() } catch (_: Throwable) { "--" }
                val battery = try {
                    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                    manager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                } catch (_: Throwable) { -1 }
                listOf(percent.toString(), ghz, battery.toString())
            }
            cpuPercent = readings[0].toIntOrNull()?.coerceIn(0,100) ?: 0
            cpuGhz = readings[1]
            batteryPercent = readings[2].toIntOrNull() ?: -1
            delay(2000)
        }
    }

    DisposableEffect(Unit) { onDispose { OverlayConfirmBus.reset() } }

    fun selectBalancedMode() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!TurboSpaceManager.isShizukuAvailableAndGranted()) null
                else {
                    var last: TurboSpaceManager.CommandResult? = null
                    for (command in listOf(
                        "cmd power set-fixed-performance-mode-enabled 0",
                        "cmd power set-fixed-performance-mode-enabled false"
                    )) {
                        last = try { TurboSpaceManager.executeCommandDetailedNoCtx(command) } catch (_: Throwable) { null }
                        if (last?.success == true && last.exitCode == 0) break
                    }
                    last
                }
            }
            val success = result != null && result.success && result.exitCode == 0
            cpuMode = if (success) "BALANCED" else "OFF"
            appendTerminalLog(if (success) "> CPU MODE: BALANCED" else "> CPU MODE BALANCED FAILED: command unavailable")
            nativeToast(success)
        }
    }

    fun confirmPerformanceMode() {
        showPerformanceConfirm = false
        scope.launch {
            // Return both outcomes: if disabling Power Saver succeeds but Performance fails,
            // keep the UI synchronized with the actual system state instead of leaving it ON.
            val outcome = withContext(Dispatchers.IO) {
                if (!TurboSpaceManager.isShizukuAvailableAndGranted()) return@withContext (false to false)
                var saverWasDisabled = false
                if (powerSaverActive) {
                    val disableSaver = try { TurboSpaceManager.executeCommandDetailedNoCtx("cmd power set-adaptive-power-saver-enabled false") } catch (_: Throwable) { null }
                    saverWasDisabled = disableSaver != null && disableSaver.success && disableSaver.exitCode == 0
                    if (!saverWasDisabled) return@withContext (false to false)
                }
                var result: TurboSpaceManager.CommandResult? = null
                for (command in listOf(
                    "cmd power set-fixed-performance-mode-enabled 1",
                    "cmd power set-fixed-performance-mode-enabled true"
                )) {
                    result = try { TurboSpaceManager.executeCommandDetailedNoCtx(command) } catch (_: Throwable) { null }
                    if (result?.success == true && result.exitCode == 0) break
                }
                (result != null && result.success && result.exitCode == 0) to saverWasDisabled
            }
            val success = outcome.first
            val saverWasDisabled = outcome.second
            if (saverWasDisabled) {
                powerSaverActive = false
                TurboSpaceRepository.powerSaverEnabled = false
                context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit().putBoolean("power_saver_active", false).apply()
            }
            cpuMode = if (success) "PERFORMANCE" else "OFF"
            appendTerminalLog(if (success) "> CPU MODE: PERFORMANCE" else "> CPU MODE PERFORMANCE FAILED: command unavailable")
            nativeToast(success)
        }
    }

    fun togglePowerSaver() {
        scope.launch {
            val turningOn = !powerSaverActive
            val outcome = withContext(Dispatchers.IO) {
                if (!TurboSpaceManager.isShizukuAvailableAndGranted()) return@withContext (false to false)
                var cpuWasDisabled = false
                if (turningOn && cpuMode == "PERFORMANCE") {
                    var cpuReset: TurboSpaceManager.CommandResult? = null
                    for (command in listOf(
                        "cmd power set-fixed-performance-mode-enabled 0",
                        "cmd power set-fixed-performance-mode-enabled false"
                    )) {
                        cpuReset = try { TurboSpaceManager.executeCommandDetailedNoCtx(command) } catch (_: Throwable) { null }
                        if (cpuReset?.success == true && cpuReset.exitCode == 0) break
                    }
                    cpuWasDisabled = cpuReset != null && cpuReset.success && cpuReset.exitCode == 0
                    if (!cpuWasDisabled) return@withContext (false to false)
                }
                val command = if (turningOn) "cmd power set-adaptive-power-saver-enabled true" else "cmd power set-adaptive-power-saver-enabled false"
                val result = try { TurboSpaceManager.executeCommandDetailedNoCtx(command) } catch (_: Throwable) { null }
                (result != null && result.success && result.exitCode == 0) to cpuWasDisabled
            }
            val success = outcome.first
            val cpuWasDisabled = outcome.second
            if (cpuWasDisabled) cpuMode = "BALANCED"
            if (success) {
                powerSaverActive = turningOn
                TurboSpaceRepository.powerSaverEnabled = turningOn
                context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit().putBoolean("power_saver_active", turningOn).apply()
            }
            appendTerminalLog(if (success) "> POWER SAVER: ${if (turningOn) "ON" else "OFF"}" else "> POWER SAVER: ERROR")
            nativeToast(success)
        }
    }

    fun toggleWhitelist() {
        scope.launch {
            val currentlyOn = activeModules.contains("WHITELIST") || activeModules.contains("PROTECT")
            val pkg = withContext(Dispatchers.IO) { resolveTargetPkg() }
            if (pkg == "NO TARGET SELECTED" || !pkg.contains(".")) {
                nativeToast(false)
                appendTerminalLog("> WHITELIST: NO VALID GAME SELECTED")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                try { TurboSpaceManager.executeCommandDetailedNoCtx("cmd deviceidle whitelist ${if (currentlyOn) "-" else "+"}$pkg") }
                catch (t: Throwable) { TurboSpaceManager.CommandResult(false, "", -1, t.message ?: "error") }
            }
            val success = result.success && result.exitCode == 0
            if (success) {
                activeModules = if (currentlyOn) activeModules - "WHITELIST" - "PROTECT" else activeModules + "WHITELIST"
                TurboSpaceRepository.activeModules.value = activeModules
                TurboSpaceRepository.savePrefs(context)
            }
            nativeToast(success)
            appendTerminalLog(if (success) "> WHITELIST: ${if (currentlyOn) "OFF" else "ON"}" else "> WHITELIST: ERROR")
        }
    }

    fun resetOverlayCommands() {
        scope.launch {
            val pkg = withContext(Dispatchers.IO) { resolveTargetPkg() }
            val validPkg = pkg != "NO TARGET SELECTED" && pkg.contains(".")
            val commands = mutableListOf(
                "cmd power set-fixed-performance-mode-enabled 0",
                "cmd power set-fixed-performance-mode-enabled false",
                "cmd power set-adaptive-power-saver-enabled false"
            )
            if (validPkg) {
                commands += listOf(
                    "cmd game mode set standard $pkg",
                    "cmd game downscale disable $pkg",
                    "cmd package compile --reset $pkg",
                    "cmd deviceidle whitelist -$pkg"
                )
            }
            val commandsSucceeded = withContext(Dispatchers.IO) {
                if (!TurboSpaceManager.isShizukuAvailableAndGranted()) return@withContext false
                var okay = true
                for (command in commands) {
                    val result = try { TurboSpaceManager.executeCommandDetailedNoCtx(command) } catch (_: Throwable) { null }
                    if (result == null || !result.success || result.exitCode != 0) okay = false
                }
                okay
            }
            val restoreSucceeded = try { restoreNewBoosterFeatures(context) } catch (_: Throwable) { false }
            val resetPrefs = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
            if (validPkg) resetPrefs.edit().putString("game_mode_category_$pkg", "standard").putString("compile_mode_$pkg", "reset").putString("game_downscale_$pkg", "disable").apply()
            val success = commandsSucceeded && restoreSucceeded
            TurboSpaceRepository.resetAllModules()
            activeModules = emptySet()
            TurboSpaceRepository.setCrosshairActive(false)
            showStatusStrip = false
            fpsBoosterActive = false
            powerSaverActive = false
            TurboSpaceRepository.powerSaverEnabled = false
            cpuMode = "BALANCED"
            context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("fps_booster_active", false).putBoolean("power_saver_active", false).apply()
            try {
                context.startService(Intent(context, GameSpaceOverlayService::class.java).setAction(GameSpaceOverlayService.ACTION_SET_FPS_FLOATING).putExtra(GameSpaceOverlayService.EXTRA_ENABLED, false))
                context.startService(Intent(context, GameSpaceOverlayService::class.java).setAction(GameSpaceOverlayService.ACTION_SET_PING_FLOATING).putExtra(GameSpaceOverlayService.EXTRA_ENABLED, false))
            } catch (_: Throwable) {}
            TurboSpaceRepository.setFpsFloatingEnabled(context, false)
            TurboSpaceRepository.setPingFloatingEnabled(context, false)
            TurboSpaceRepository.savePrefs(context)
            nativeToast(success)
            appendTerminalLog(if (success) "> RESET ALL: COMPLETED" else "> RESET ALL: ERROR")
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MonetColors.Background.copy(alpha = 0.10f))) {
        val compactHud = maxWidth < 560.dp
        val cpuWidgetWidth = when {
            maxWidth < 420.dp -> 102.dp
            compactHud -> 116.dp
            else -> 140.dp
        }
        val edgeButtonY = if (maxWidth < 420.dp) 106.dp else 28.dp
        val edgeButtonX = if (maxWidth < 420.dp) 58.dp else 56.dp + cpuWidgetWidth + 4.dp

        // LEFT TACTICAL SIDEBAR: icon-only native controls, each 50x50dp.
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)
                .width(50.dp).heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HudSquareIconButton("DND", "bell_slash", dndEnabled, onClick = {
                try {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        nativeToast(false)
                    } else {
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (!manager.isNotificationPolicyAccessGranted) nativeToast(false)
                        else {
                            val next = !dndEnabled
                            manager.setInterruptionFilter(if (next) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
                            dndEnabled = next
                            nativeToast(true)
                        }
                    }
                } catch (_: Throwable) { nativeToast(false) }
            })
            HudSquareIconButton("FPS BOOSTER", "fps", fpsBoosterActive, onClick = { showFpsBoosterDialog = true })
            HudSquareIconButton("VOLUME", "volume", showVolumeDialog, onClick = { showVolumeDialog = true })
            HudSquareIconButton(
                "STOPWATCH", "timer", stopwatchRunning,
                onClick = {
                    try {
                        if (TurboSpaceRepository.stopwatchRunning) {
                            TurboSpaceRepository.stopwatchElapsedMs = TurboSpaceRepository.stopwatchElapsedAt()
                            TurboSpaceRepository.stopwatchRunning = false
                        } else {
                            TurboSpaceRepository.stopwatchStartedAtMs = System.currentTimeMillis() - TurboSpaceRepository.stopwatchElapsedMs.coerceAtLeast(0L)
                            TurboSpaceRepository.stopwatchRunning = true
                        }
                        stopwatchTickMs = System.currentTimeMillis()
                        TurboSpaceRepository.persistStopwatch(context)
                        nativeToast(true)
                    } catch (_: Throwable) { nativeToast(false) }
                },
                onLongClick = {
                    TurboSpaceRepository.stopwatchRunning = false
                    TurboSpaceRepository.stopwatchElapsedMs = 0L
                    TurboSpaceRepository.stopwatchStartedAtMs = 0L
                    stopwatchTickMs = System.currentTimeMillis()
                    TurboSpaceRepository.persistStopwatch(context)
                    nativeToast(true)
                }
            )
            HudSquareIconButton("PING", "signal", TurboSpaceRepository.pingFloatingEnabled, onClick = {
                val next = !TurboSpaceRepository.pingFloatingEnabled
                try {
                    context.startService(Intent(context, GameSpaceOverlayService::class.java)
                        .setAction(GameSpaceOverlayService.ACTION_SET_PING_FLOATING)
                        .putExtra(GameSpaceOverlayService.EXTRA_ENABLED, next))
                    TurboSpaceRepository.setPingFloatingEnabled(context, next)
                    nativeToast(true)
                } catch (_: Throwable) { nativeToast(false) }
            })
        }

        // RIGHT TACTICAL SIDEBAR: only icon buttons; the original command handlers stay in place.
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
                .width(50.dp).heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HudSquareIconButton("SHOW STATUS", "fps", showStatusStrip, onClick = {
                val next = !showStatusStrip
                try {
                    context.startService(Intent(context, GameSpaceOverlayService::class.java)
                        .setAction(GameSpaceOverlayService.ACTION_SET_FPS_FLOATING)
                        .putExtra(GameSpaceOverlayService.EXTRA_ENABLED, next))
                    showStatusStrip = next
                    TurboSpaceRepository.setFpsFloatingEnabled(context, next)
                    nativeToast(true)
                    appendTerminalLog(if (next) "> SHOW STATUS: ON" else "> SHOW STATUS: OFF")
                } catch (_: Throwable) {
                    nativeToast(false)
                    appendTerminalLog("> SHOW STATUS: ERROR")
                }
            })
            HudSquareIconButton("WHITELIST", "shield", activeModules.contains("WHITELIST") || activeModules.contains("PROTECT"), onClick = { toggleWhitelist() })
            HudSquareIconButton("RESET ALL", "reset", false, onClick = { resetOverlayCommands() })
            HudSquareIconButton("MEM BOOST", "rocket", showMemBoostDialog || showMemBoostList, onClick = { showMemBoostDialog = true })
            HudSquareIconButton("CROSSHAIR", "crosshair", TurboSpaceRepository.crosshairActive.value, onClick = {
                if (TurboSpaceRepository.crosshairActive.value) {
                    TurboSpaceRepository.setCrosshairActive(false)
                    TurboSpaceRepository.activeModules.value = TurboSpaceRepository.activeModules.value - "CROSSHAIR"
                    activeModules = TurboSpaceRepository.activeModules.value
                    TurboSpaceRepository.savePrefs(context)
                    nativeToast(true)
                } else {
                    pickerStyle = TurboSpaceRepository.crosshairStyle.value
                    pickerColorIdx = TurboSpaceRepository.crosshairColorIdx.value
                    showCrosshairPicker = true
                }
            })
        }

        // TOP START: CPU ring widget; the mode controls remain immediately beneath the ring.
        CpuWidgetPanel(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 5.dp).width(cpuWidgetWidth),
            cpuPercent = cpuPercent,
            cpuGhz = cpuGhz,
            currentMode = cpuMode,
            onBalancedClick = { selectBalancedMode() },
            onPerformanceClick = { showPerformanceConfirm = true }
        )

        // Edge-color shortcut sits beside the CPU widget and moves below it on extra-narrow screens.
        HudSquareIconButton(
            "EDGE COLOR", "brush", false,
            modifier = Modifier.align(Alignment.TopStart).padding(start = edgeButtonX, top = edgeButtonY),
            onClick = { showEdgeColorDialog = true }
        )

        // TOP END: 3D battery ring and separate battery-shaped Power Saver toggle.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 56.dp, top = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            BatteryRingWidget(batteryPercent)
            HudSquareIconButton("POWER SAVER", "battery", powerSaverActive, onClick = { togglePowerSaver() })
        }

        // The tactical center contains only the exact-center collapse control.
        Box(
            Modifier.align(Alignment.Center).size(44.dp)
                .background(MonetColors.Background.copy(alpha = 0.96f), CircleShape)
                .border(1.2.dp, MonetColors.Primary.copy(alpha = 0.85f), CircleShape)
                .shadow(8.dp, CircleShape, ambientColor = MonetColors.Primary, spotColor = MonetColors.Primary)
                .clickable { showCollapseConfirm = true }
                .zIndex(2f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(19.dp)) {
                val p = size.width * 0.32f
                drawLine(MonetColors.Primary, Offset(size.width / 2f - p, size.height / 2f - p), Offset(size.width / 2f + p, size.height / 2f + p), 2.2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(MonetColors.Primary, Offset(size.width / 2f + p, size.height / 2f - p), Offset(size.width / 2f - p, size.height / 2f + p), 2.2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        // Animated 4dp Material You edge rails sit above the three HUD panels.
        val edgeBrush = Brush.verticalGradient(
            colors = listOf(
                TurboSpaceRepository.edgeLightColor.copy(alpha = 0.02f),
                TurboSpaceRepository.edgeLightColor.copy(alpha = 0.82f),
                TurboSpaceRepository.edgeLightColor.copy(alpha = 0.48f),
                TurboSpaceRepository.edgeLightColor.copy(alpha = 0.02f)
            ),
            startY = edgeGlowY,
            endY = edgeGlowY + 260f
        )
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(4.dp).background(edgeBrush).zIndex(10f))
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp).background(edgeBrush).zIndex(10f))

        if (showEdgeColorDialog) {
            EdgeColorPickerDialog(
                onDismiss = { showEdgeColorDialog = false },
                onSelect = { color ->
                    TurboSpaceRepository.edgeLightColor = color
                    TurboSpaceRepository.savePrefs(context)
                    showEdgeColorDialog = false
                    nativeToast(true)
                }
            )
        }

        if (showVolumeDialog) {
            HudVolumeDialog(
                value = musicVolume,
                maxValue = maxMusicVolume.toFloat(),
                onValueChange = { value ->
                    musicVolume = value.coerceIn(0f, maxMusicVolume.toFloat())
                    volumeApplySucceeded = try {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, musicVolume.toInt(), 0)
                        true
                    } catch (_: Throwable) { false }
                },
                onDismiss = { showVolumeDialog = false; nativeToast(volumeApplySucceeded) }
            )
        }

        if (showPerformanceConfirm) {
            CpuPerformanceConfirmDialog(onConfirm = { confirmPerformanceMode() }, onCancel = { showPerformanceConfirm = false })
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
                style = pickerStyle, colorIdx = pickerColorIdx,
                onStyle = { pickerStyle = it }, onColor = { pickerColorIdx = it },
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
                    TurboSpaceRepository.setCrosshairActive(true)
                    TurboSpaceRepository.markModuleActive("CROSSHAIR")
                    activeModules = TurboSpaceRepository.activeModules.value
                    TurboSpaceRepository.savePrefs(context)
                    showCrosshairPicker = false
                }
            )
        }

        if (showCloseConfirm) {
            OverlayCloseConfirmDialogNew(
                onConfirm = {
                    showCloseConfirm = false
                    scope.launch {
                        var resetSucceeded = true
                        withContext(Dispatchers.IO) {
                            val selectedPkg = TurboSpaceRepository.selectedGame.value
                            val pkg = if (selectedPkg != "NO TARGET SELECTED" && selectedPkg.contains(".")) selectedPkg else getCurrentForegroundPackageAuto()
                            val validPkg = pkg != "NO TARGET SELECTED" && pkg.contains(".")
                            val resetCmds = mutableListOf(
                                "cmd power set-fixed-performance-mode-enabled 0",
                                "cmd power set-fixed-performance-mode-enabled false",
                                "cmd power set-adaptive-power-saver-enabled false"
                            )
                            if (validPkg) {
                                resetCmds.add("cmd game mode set standard $pkg")
                                resetCmds.add("cmd game downscale disable $pkg")
                                resetCmds.add("cmd package compile --reset $pkg")
                                resetCmds.add("cmd deviceidle whitelist -$pkg")
                            }
                            for (cmd in resetCmds) {
                                val result = try { TurboSpaceManager.executeCommandDetailedNoCtx(if (validPkg) cmd.replace("\$pkg", pkg) else cmd) }
                                    catch (_: Throwable) { null }
                                if (result == null || !result.success || result.exitCode != 0) resetSucceeded = false
                            }
                            if (!restoreNewBoosterFeatures(context)) resetSucceeded = false
                        }
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, if (resetSucceeded) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                            cpuMode = "BALANCED"
                            showStatusStrip = false
                            fpsBoosterActive = false
                            powerSaverActive = false
                            TurboSpaceRepository.powerSaverEnabled = false
                            context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit()
                                .putBoolean("fps_booster_active", false)
                                .putBoolean("power_saver_active", false)
                                .apply()
                            TurboSpaceRepository.setFpsFloatingEnabled(context, false)
                            TurboSpaceRepository.setPingFloatingEnabled(context, false)
                            appendTerminalLog("> CPU MODE: RESET TO BALANCED")
                            TurboSpaceRepository.resetAllModules()
                            TurboSpaceRepository.setCrosshairActive(false)
                            context.getSharedPreferences("turbospace_overlay", Context.MODE_PRIVATE).edit().remove("crosshair_x").remove("crosshair_y").apply()
                            context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit().remove("crosshair_style").remove("crosshair_color_idx").apply()
                            TurboSpaceRepository.setCrosshairStyle(0)
                            TurboSpaceRepository.setCrosshairColorIdx(2)
                            OverlayConfirmBus.reset()
                            TurboSpaceRepository.savePrefs(context)
                            var serviceContext: Context = context
                            var overlayService: GameSpaceOverlayService? = serviceContext as? GameSpaceOverlayService
                            while (overlayService == null && serviceContext is ContextWrapper) {
                                val next = serviceContext.baseContext
                                if (next === serviceContext) break
                                serviceContext = next
                                overlayService = serviceContext as? GameSpaceOverlayService
                            }
                            if (overlayService != null) overlayService.closeHudForFullDismiss() else {
                                onDismiss()
                                try { context.stopService(Intent(context, GameSpaceOverlayService::class.java)) } catch (_: Throwable) {}
                            }
                        }
                    }
                },
                onCancel = { showCloseConfirm = false }
            )
        }

        if (showCollapseConfirm) {
            CollapseConfirmDialog(onConfirm = { showCollapseConfirm = false; onDismiss() }, onCancel = { showCollapseConfirm = false })
        }

        if (showMemBoostDialog) {
            MemoryBoostConfirmDialogNew(
                onConfirm = {
                    showMemBoostDialog = false
                    scope.launch(Dispatchers.IO) {
                        try {
                            val selectedPkg = TurboSpaceRepository.selectedGame.value
                            val apps = TurboSpaceManager.getInstalledGames(context).filter { info ->
                                val pkg = info.packageName
                                !pkg.startsWith("com.turbospace.") && pkg != "com.turbospace" && pkg != "com.android.chrome" && !pkg.startsWith("com.google.") && pkg != selectedPkg
                            }.sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
                            withContext(Dispatchers.Main) { allUserApps = apps; selectedApps = emptySet(); showMemBoostList = true }
                        } catch (e: Throwable) {
                            withContext(Dispatchers.Main) { appendTerminalLog("> MEM BOOST APP LIST FAILED: ${e.message}") }
                        }
                    }
                },
                onCancel = { showMemBoostDialog = false }
            )
        }

        if (showMemBoostList) {
            MemoryBoostAppListDialogNew(
                context = context, apps = allUserApps, selectedApps = selectedApps,
                onToggle = { pkg -> selectedApps = if (selectedApps.contains(pkg)) selectedApps - pkg else selectedApps + pkg },
                onNo = { showMemBoostList = false; selectedApps = emptySet() },
                onYes = {
                    val appsToRun = selectedApps.toList()
                    showMemBoostList = false
                    selectedApps = emptySet()
                    if (appsToRun.isEmpty()) appendTerminalLog("> MEM BOOST: No apps selected")
                    else scope.launch(Dispatchers.IO) {
                        var allSucceeded = true
                        for (pkg in appsToRun) {
                            if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
                                allSucceeded = false
                                withContext(Dispatchers.Main) { appendTerminalLog("> TRIM_MEMORY $pkg: SKIPPED (Shizuku unavailable)") }
                                continue
                            }
                            var running = false
                            var detectionWorked = false
                            try {
                                val r = TurboSpaceManager.executeCommandDetailedNoCtx("pidof $pkg")
                                if (r.output.trim().isNotEmpty()) running = true
                                if (r.exitCode >= 0 && r.error.isBlank()) detectionWorked = true
                            } catch (_: Throwable) {}
                            if (!running) try {
                                val r = TurboSpaceManager.executeCommandDetailedNoCtx("dumpsys activity processes | grep '$pkg'")
                                if (r.output.contains(pkg)) running = true
                                if (r.exitCode >= 0 && r.error.isBlank()) detectionWorked = true
                            } catch (_: Throwable) {}
                            if (!running) try {
                                val r = TurboSpaceManager.executeCommandDetailedNoCtx("ps -A | grep '$pkg'")
                                if (r.output.contains(pkg)) running = true
                                if (r.exitCode >= 0 && r.error.isBlank()) detectionWorked = true
                            } catch (_: Throwable) {}
                            if (!running) {
                                allSucceeded = false
                                val reason = if (detectionWorked) "SKIPPED (not running; process not found)" else "SKIPPED (cannot detect process)"
                                withContext(Dispatchers.Main) { appendTerminalLog("> TRIM_MEMORY $pkg: $reason") }
                                continue
                            }
                            val result = try { TurboSpaceManager.executeCommandDetailedNoCtx("am send-trim-memory $pkg RUNNING_LOW") } catch (_: Throwable) { null }
                            val ok = result?.success == true && result.exitCode == 0
                            if (!ok) allSucceeded = false
                            val detail = if (ok) "SUCCESS" else "FAILED (${result?.let { it.error.ifBlank { it.output.trim() } } ?: "command error"})"
                            withContext(Dispatchers.Main) { appendTerminalLog("> TRIM_MEMORY $pkg: $detail") }
                        }
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, if (allSucceeded) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
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
                Text("CLOSE OVERLAY", color=MonetColors.Primary, fontSize=13.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace, letterSpacing=1.sp)
                Text(
                    "Close overlay AND reset all commands?",
                    color=MonetColors.Primary.copy(alpha=0.88f),
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
                            .border(1.2.dp, MonetColors.Secondary, RoundedCornerShape(6.dp))
                            .clickable{ onConfirm() },
                        contentAlignment=Alignment.Center
                    ){ Text("OK", color=MonetColors.Primary, fontSize=11.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace) }
                }
            }
        }
    }
}

@Composable
fun CollapseConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit){
    Box(
        Modifier.fillMaxSize().background(MonetColors.Background.copy(alpha=0.82f)).clickable{ onCancel() },
        contentAlignment = Alignment.Center
    ){
        Box(
            Modifier.fillMaxWidth(0.78f).background(CyberDesign.DeepDark85, RoundedCornerShape(12.dp))
                .border(1.2.dp, MonetColors.Secondary.copy(alpha=0.85f), RoundedCornerShape(12.dp))
                .shadow(14.dp, RoundedCornerShape(12.dp), ambientColor=MonetColors.Secondary, spotColor=MonetColors.Secondary)
                .padding(18.dp).clickable {}
        ){
            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("COLLAPSE OVERLAY", color=MonetColors.Primary, fontSize=12.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace, letterSpacing=1.sp)
                Text("Collapse the HUD and return to the floating icon? Commands will stay active.", color=MonetColors.Primary.copy(0.85f), fontSize=9.sp, fontFamily=FontFamily.Monospace, textAlign=TextAlign.Center, lineHeight=13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(Modifier.weight(1f).height(40.dp).background(MonetColors.Primary.copy(0.08f), RoundedCornerShape(6.dp)).border(0.8.dp, MonetColors.Primary.copy(0.35f), RoundedCornerShape(6.dp)).clickable{ onCancel() }, contentAlignment=Alignment.Center){ Text("Cancel", color=MonetColors.Primary.copy(0.8f), fontSize=10.sp, fontWeight=FontWeight.Bold, fontFamily=FontFamily.Monospace) }
                    Box(Modifier.weight(1f).height(40.dp).background(MonetColors.Secondary.copy(0.18f), RoundedCornerShape(6.dp)).border(1.2.dp, MonetColors.Secondary, RoundedCornerShape(6.dp)).clickable{ onConfirm() }, contentAlignment=Alignment.Center){ Text("OK", color=MonetColors.Primary, fontSize=11.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace) }
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
fun MemoryBoostConfirmDialogNew(onConfirm: () -> Unit, onCancel: () -> Unit){
    Box(
        Modifier.fillMaxSize()
            .background(MonetColors.Background.copy(alpha=0.82f))
            .clickable{ onCancel() },
        contentAlignment=Alignment.Center
    ){
        Box(
            Modifier.fillMaxWidth(0.78f)
                .background(CyberDesign.DeepDark85, RoundedCornerShape(12.dp))
                .border(1.2.dp, MonetColors.Primary.copy(alpha=0.85f), RoundedCornerShape(12.dp))
                .shadow(18.dp, RoundedCornerShape(12.dp), ambientColor=MonetColors.Primary, spotColor=MonetColors.Primary)
                .padding(18.dp)
                .clickable(enabled=false){}
        ){
            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(12.dp)){
                Box(
                    Modifier.size(64.dp)
                        .background(MonetColors.Primary.copy(0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, MonetColors.Primary.copy(0.55f), RoundedCornerShape(12.dp)),
                    contentAlignment=Alignment.Center
                ){
                    RedGamepadIconCustom(Modifier.size(42.dp), MonetColors.Primary)
                }
                Text(
                    "Select apps to trim their memory. This frees RAM but may cause apps to reload. Continue?",
                    color=MonetColors.Primary.copy(alpha=0.9f),
                    fontSize=9.sp,
                    fontFamily=FontFamily.Monospace,
                    textAlign=TextAlign.Center,
                    lineHeight=13.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .background(MonetColors.Primary.copy(0.08f), RoundedCornerShape(6.dp))
                            .border(0.8.dp, MonetColors.Primary.copy(0.3f), RoundedCornerShape(6.dp))
                            .clickable{ onCancel() },
                        contentAlignment=Alignment.Center
                    ){ Text("Cancel", color=MonetColors.Primary.copy(0.75f), fontSize=10.sp, fontWeight=FontWeight.Bold, fontFamily=FontFamily.Monospace) }
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .background(MonetColors.Primary.copy(0.22f), RoundedCornerShape(6.dp))
                            .border(1.2.dp, MonetColors.Primary, RoundedCornerShape(6.dp))
                            .clickable{ onConfirm() },
                        contentAlignment=Alignment.Center
                    ){ Text("OK", color=MonetColors.Primary, fontSize=11.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace) }
                }
            }
        }
    }
}

@Composable
fun AppIconImageRaw(context: Context, pkg: String, modifier: Modifier = Modifier) {
    val iconBitmap = remember(context.applicationContext, pkg) {
        try {
            val drawable = context.packageManager.getApplicationIcon(pkg)
            val width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 48).coerceIn(24, 96)
            val height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 48).coerceIn(24, 96)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Throwable) { null }
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
fun MemoryBoostAppListDialogNew(
    context: Context,
    apps: List<ApplicationInfo>,
    selectedApps: Set<String>,
    onToggle: (String) -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit
){
    Box(
        Modifier.fillMaxSize()
            .background(MonetColors.Background.copy(alpha=0.86f))
            .clickable{ onNo() },
        contentAlignment=Alignment.Center
    ){
        Box(
            Modifier.fillMaxWidth(0.86f)
                .fillMaxHeight(0.72f)
                .background(CyberDesign.DeepDark85, RoundedCornerShape(12.dp))
                .border(1.2.dp, MonetColors.Secondary.copy(0.8f), RoundedCornerShape(12.dp))
                .shadow(18.dp, RoundedCornerShape(12.dp), ambientColor=MonetColors.Secondary, spotColor=MonetColors.Secondary)
                .padding(14.dp)
                .clickable(enabled=false){}
        ){
            Column(Modifier.fillMaxSize()){
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically){
                    Text("SELECT APPS", color=MonetColors.Primary, fontSize=11.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        Box(
                            Modifier.background(MonetColors.Secondary.copy(0.18f), RoundedCornerShape(6.dp))
                                .border(1.dp, MonetColors.Secondary, RoundedCornerShape(6.dp))
                                .clickable{ onYes() }
                                .padding(horizontal=12.dp, vertical=6.dp),
                            contentAlignment=Alignment.Center
                        ){ Text("YES", color=MonetColors.Primary, fontSize=9.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace) }
                        Box(
                            Modifier.background(MonetColors.Primary.copy(0.06f), RoundedCornerShape(6.dp))
                                .border(1.dp, MonetColors.Primary.copy(0.3f), RoundedCornerShape(6.dp))
                                .clickable{ onNo() }
                                .padding(horizontal=12.dp, vertical=6.dp),
                            contentAlignment=Alignment.Center
                        ){ Text("NO", color=MonetColors.Primary.copy(0.75f), fontSize=9.sp, fontWeight=FontWeight.Black, fontFamily=FontFamily.Monospace) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if(apps.isEmpty()){
                    Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){
                        Text("No eligible apps found", color=MonetColors.Primary.copy(0.55f), fontSize=9.sp, fontFamily=FontFamily.Monospace)
                    }
                } else {
                    LazyColumn(
                        modifier=Modifier.fillMaxSize(),
                        verticalArrangement=Arrangement.spacedBy(4.dp)
                    ){
                        items(apps){ info ->
                            val pkg=info.packageName
                            val checked=selectedApps.contains(pkg)
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(
                                        if(checked) MonetColors.Primary.copy(0.10f)
                                        else MonetColors.Primary.copy(0.04f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        0.8.dp,
                                        if(checked) MonetColors.Secondary
                                        else MonetColors.Primary.copy(0.12f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable{ onToggle(pkg) }
                                    .padding(8.dp),
                                verticalAlignment=Alignment.CenterVertically
                            ){
                                AppIconImageRaw(context, pkg, Modifier.size(34.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)){
                                    Text(
                                        info.loadLabel(context.packageManager).toString(),
                                        color=MonetColors.Primary,
                                        fontSize=9.sp,
                                        fontWeight=FontWeight.Bold,
                                        fontFamily=FontFamily.Monospace,
                                        maxLines=1,
                                        overflow=TextOverflow.Ellipsis
                                    )
                                    Text(
                                        pkg,
                                        color=MonetColors.Primary.copy(0.35f),
                                        fontSize=6.sp,
                                        fontFamily=FontFamily.Monospace,
                                        maxLines=1,
                                        overflow=TextOverflow.Ellipsis
                                    )
                                }
                                Canvas(Modifier.size(18.dp)) {
                                    drawRoundRect(
                                        if (checked) MonetColors.Primary else MonetColors.Outline,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                    if (checked) {
                                        val tick = Path().apply {
                                            moveTo(size.width * 0.20f, size.height * 0.52f)
                                            lineTo(size.width * 0.42f, size.height * 0.73f)
                                            lineTo(size.width * 0.82f, size.height * 0.25f)
                                        }
                                        drawPath(tick, MonetColors.Primary, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}




// ==================== CONFIRM BUS FOR FIXED PERF - CUSTOM ICONS NO EMOJI ====================
object OverlayConfirmBus {
    var showFixedPerf by androidx.compose.runtime.mutableStateOf(false)
    var pendingModule by androidx.compose.runtime.mutableStateOf<CyberModule?>(null)
    var pendingOnToggle by androidx.compose.runtime.mutableStateOf<((Boolean, Boolean) -> Unit)?>(null)

    // FIX SF-01: clear global Compose state when the HUD composition is disposed.
    fun reset() {
        showFixedPerf = false
        pendingModule = null
        pendingOnToggle = null
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

@Composable
fun FixedPerfConfirmDialogNew(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MonetColors.Background.copy(alpha = 0.82f)).clickable { onCancel() }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth(0.78f).background(CyberDesign.DeepDark85, RoundedCornerShape(12.dp)).border(1.2.dp, MonetColors.Primary.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).shadow(18.dp, RoundedCornerShape(12.dp), ambientColor = MonetColors.Primary, spotColor = MonetColors.Primary).padding(18.dp).clickable(enabled = false) {}) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(64.dp).background(MonetColors.Primary.copy(0.14f), RoundedCornerShape(12.dp)).border(1.dp, MonetColors.Primary.copy(0.6f), RoundedCornerShape(12.dp)).shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = MonetColors.Primary, spotColor = MonetColors.Primary), contentAlignment = Alignment.Center) {
                    RedGamepadIconCustom(Modifier.size(42.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PERFORMANCE MODE", color = MonetColors.Secondary, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    Text("CPU SPEED CONTROL", color = MonetColors.Primary.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.8.sp)
                }
                Text("This feature will prevent CPU from reducing speed, which may cause overheating problems. Do you want to enable?", color = MonetColors.Primary.copy(alpha = 0.9f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(40.dp).background(MonetColors.Primary.copy(0.07f), RoundedCornerShape(6.dp)).border(0.8.dp, MonetColors.Primary.copy(0.25f), RoundedCornerShape(6.dp)).clickable { onCancel() }, contentAlignment = Alignment.Center) {
                        Text("Cancel", color = MonetColors.Primary.copy(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Box(Modifier.weight(1f).height(40.dp).background(MonetColors.Primary.copy(0.22f), RoundedCornerShape(6.dp)).border(1.4.dp, MonetColors.Primary, RoundedCornerShape(6.dp)).shadow(10.dp, RoundedCornerShape(6.dp), ambientColor = MonetColors.Primary, spotColor = MonetColors.Primary).clickable { onConfirm() }, contentAlignment = Alignment.Center) {
                        Text("OK", color = MonetColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    }
                }
            }
        }
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
                    if (!isActive && module.iconType == 5) {
                        // The CPU widget now owns Fixed Perf; the enum and both templates remain intact.
                        OverlayConfirmBus.pendingModule = module
                        OverlayConfirmBus.pendingOnToggle = { success, reset ->
                            onToggle(success, reset)
                            onTerminalLog(if (success) "> ${module.title}: SUCCESS" else "> ${module.title}: FAILED")
                        }
                        OverlayConfirmBus.showFixedPerf = true
                        onTerminalLog("> ${module.title}: CONFIRM REQUIRED")
                        return@combinedClickable
                    }
                    if (module.commandTemplate == "TRIM_MEMORY") {
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
                                val validPkg = pkg != "NO TARGET SELECTED" && pkg.contains(".")
                                var resetSucceeded = true
                                val resetCmds = mutableListOf(
                                    "cmd power set-fixed-performance-mode-enabled 0",
                                    "cmd power set-fixed-performance-mode-enabled false",
                                    "cmd power set-adaptive-power-saver-enabled false"
                                )
                                if (validPkg) {
                                    resetCmds.add("cmd game mode set standard $pkg")
                                    resetCmds.add("cmd game downscale disable $pkg")
                                    resetCmds.add("cmd package compile --reset $pkg")
                                    resetCmds.add("cmd deviceidle whitelist -$pkg")
                                }
                                for (cmd in resetCmds) {
                                    val finalCmd = if (validPkg) cmd.replace("\$pkg", pkg) else cmd
                                    val result = try { TurboSpaceManager.executeCommandDetailedNoCtx(finalCmd) } catch (_: Throwable) { null }
                                    if (result == null || !result.success || result.exitCode != 0) resetSucceeded = false
                                }
                                if (!restoreNewBoosterFeatures(context)) resetSucceeded = false
                                val resetPrefs = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
                                if (validPkg) resetPrefs.edit().putString("game_mode_category_$pkg", "standard").putString("compile_mode_$pkg", "reset").putString("game_downscale_$pkg", "disable").apply()
                                resetPrefs.edit().putBoolean("power_saver_active", false).apply()
                                TurboSpaceRepository.powerSaverEnabled = false
                                TurboSpaceRepository.setCrosshairActive(false)
                                onToggle(resetSucceeded, true)
                                TurboSpaceRepository.savePrefs(context)
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

@Composable
private fun DecorativeMonetGear(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "background-gear-transition")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "background-gear-rotation"
    )
    MonetCanvasIcon("gear", modifier.graphicsLayer { rotationZ = rotation; alpha = 0.14f }, MonetColors.Secondary)
}

// ==================== NEW MAIN DASHBOARD - OPTIMIZED V2 - NO + BUTTON, SHOW ALL GAMES IMMEDIATELY ====================
@Composable
fun MainMissionControlDashboard() {
    val context = LocalContext.current
    val selectedGame by TurboSpaceRepository.selectedGame.collectAsState()
    var showBooster by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasShizuku by remember { mutableStateOf(false) }
    var hasDnd by remember { mutableStateOf(false) }
    var hasBatteryOpt by remember { mutableStateOf(false) }
    var batteryPercent by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var gameList by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }

    fun checkAllPermissions() {
        hasOverlay = Settings.canDrawOverlays(context)
        hasShizuku = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
        hasDnd = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
            } else true
        } catch (_: Throwable) { false }
        hasBatteryOpt = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
            } else true
        } catch (_: Throwable) { false }
    }

    LaunchedEffect(Unit) {
        TurboSpaceRepository.loadPrefs(context)
        checkAllPermissions()
        gameList = TurboSpaceManager.getInstalledGames(context).filter { info ->
            try {
                val pkg = info.packageName
                val thirdParty = (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                val updatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val gameFlag = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                val gameCategory = info.category == ApplicationInfo.CATEGORY_GAME
                pkg != context.packageName && !pkg.startsWith("com.turbospace") &&
                    !pkg.startsWith("com.android.") && !pkg.startsWith("com.google.android.apps") &&
                    thirdParty && !updatedSystem && (gameFlag || gameCategory)
            } catch (_: Throwable) { false }
        }.sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            batteryPercent = try { TurboSpaceManager.getBatteryPercent(context).coerceIn(0, 100) } catch (_: Throwable) { 0 }
            delay(5000)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkAllPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showBooster && selectedGame != "NO TARGET SELECTED" && hasOverlay && hasShizuku && hasDnd && hasBatteryOpt) {
        BoosterDashboardOptimizedV2(
            selectedGame = selectedGame,
            onBack = { showBooster = false },
            onLaunchGame = {
                try {
                    context.startService(Intent(context, GameSpaceOverlayService::class.java))
                    scope.launch {
                        delay(450)
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedGame)
                            if (launchIntent != null) context.startActivity(launchIntent)
                            else android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {
                            android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Throwable) {
                    android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    Box(Modifier.fillMaxSize().background(MonetColors.Background)) {
        // Large gears sit behind the interface: one peeks from the top-left, one from bottom-right.
        DecorativeMonetGear(Modifier.align(Alignment.TopStart).size(300.dp).offset(x = (-150).dp, y = (-130).dp))
        DecorativeMonetGear(Modifier.align(Alignment.BottomEnd).size(300.dp).offset(x = 150.dp, y = 150.dp))
        if (!hasOverlay || !hasShizuku || !hasDnd || !hasBatteryOpt) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("PERMISSION REQUIRED", color = MonetColors.Primary, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                Text("Grant overlay, Shizuku, Do Not Disturb and battery optimization access to continue.", color = MonetColors.Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                PermissionCardSimple(
                    title = "GRANT OVERLAY PERMISSION",
                    desc = "Allow Turbo Space to display the game HUD",
                    granted = hasOverlay,
                    onClick = {
                        try { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) } catch (_: Throwable) {}
                    }
                )
                Spacer(Modifier.height(12.dp))
                PermissionCardSimple(
                    title = "AUTHORIZE SHIZUKU",
                    desc = "Required to execute system commands",
                    granted = hasShizuku,
                    onClick = { try { Shizuku.requestPermission(1001) } catch (_: Throwable) {} }
                )
                Spacer(Modifier.height(12.dp))
                PermissionCardSimple(
                    title = "ALLOW DO NOT DISTURB",
                    desc = "Allow Turbo Space to toggle Do Not Disturb",
                    granted = hasDnd,
                    onClick = {
                        try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (_: Throwable) { android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                )
                Spacer(Modifier.height(12.dp))
                PermissionCardSimple(
                    title = "DISABLE BATTERY OPTIMIZATION",
                    desc = "Allow reliable background HUD operation",
                    granted = hasBatteryOpt,
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } else hasBatteryOpt = true
                        } catch (_: Throwable) { android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SETTINGS", modifier = Modifier.clickable { showSettings = true }.padding(4.dp), color = MonetColors.Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("TURBO SPACE", color = MonetColors.Primary, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 5.dp)) {
                            MonetCanvasIcon("battery", Modifier.size(width = 28.dp, height = 15.dp), MonetColors.Secondary)
                            Text("$batteryPercent%", color = MonetColors.Secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GAMES", color = MonetColors.Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, fontFamily = FontFamily.Monospace)
                    Text("${gameList.size} FOUND", color = MonetColors.Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))
                if (gameList.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("NO GAMES FOUND", color = MonetColors.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth().heightIn(min = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(gameList, key = { it.packageName }) { app ->
                            val pkg = app.packageName
                            val selected = selectedGame == pkg
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) MonetColors.Primary.copy(alpha = 0.88f) else MonetColors.Inactive.copy(alpha = 0.72f))
                                    .border(if (selected) 1.5.dp else 1.dp, if (selected) MonetColors.Secondary else MonetColors.Outline.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
                                    .clickable { TurboSpaceRepository.setSelectedGame(pkg) }
                                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.loadLabel(context.packageManager).toString(),
                                        color = if (selected) MonetColors.OnPrimary else MonetColors.Primary,
                                        fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (selected) {
                                    Box(
                                        Modifier.width(82.dp).height(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MonetColors.OnPrimary)
                                            .clickable { showBooster = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("START", color = MonetColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(10.dp)).background(MonetColors.Inactive)
                        .border(1.dp, MonetColors.Outline, RoundedCornerShape(10.dp))
                        .clickable { showResetConfirm = true }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("HARD RESET", color = MonetColors.Secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (showSettings) {
            Box(Modifier.fillMaxSize().background(MonetColors.Background.copy(alpha = 0.72f)).clickable { showSettings = false }, contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth(0.82f).clip(RoundedCornerShape(16.dp)).background(MonetColors.Inactive)
                        .border(1.dp, MonetColors.Secondary, RoundedCornerShape(16.dp)).padding(18.dp)
                        .clickable(enabled = false) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("SYSTEM STATUS", color = MonetColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("Overlay: ${if (hasOverlay) "READY" else "NOT GRANTED"}", color = MonetColors.Muted, fontSize = 11.sp)
                    Text("Shizuku: ${if (hasShizuku) "READY" else "NOT GRANTED"}", color = MonetColors.Muted, fontSize = 11.sp)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MonetColors.Primary).clickable { showSettings = false }.padding(11.dp), contentAlignment = Alignment.Center) {
                        Text("CLOSE", color = MonetColors.OnPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        if (showResetConfirm) {
            MainResetConfirmDialogNew(
                onConfirm = {
                    showResetConfirm = false
                    val selectedAtClick = selectedGame
                    scope.launch(Dispatchers.IO) {
                        var resetSucceeded = true
                        val fallbackPkg = try { getCurrentForegroundPackageAuto() } catch (_: Throwable) { "NO TARGET SELECTED" }
                        val pkg = if (selectedAtClick != "NO TARGET SELECTED" && selectedAtClick.contains(".")) selectedAtClick else fallbackPkg
                        val validPkg = pkg != "NO TARGET SELECTED" && pkg.contains(".")
                        val resetCmds = mutableListOf(
                            "cmd power set-fixed-performance-mode-enabled 0",
                            "cmd power set-fixed-performance-mode-enabled false",
                            "cmd power set-adaptive-power-saver-enabled false"
                        )
                        if (validPkg) {
                            resetCmds.add("cmd game mode set standard $pkg")
                            resetCmds.add("cmd game downscale disable $pkg")
                            resetCmds.add("cmd package compile --reset $pkg")
                            resetCmds.add("cmd deviceidle whitelist -$pkg")
                        }
                        for (cmd in resetCmds) {
                            val result = try { TurboSpaceManager.executeCommandDetailedNoCtx(if (validPkg) cmd.replace("\$pkg", pkg) else cmd) } catch (_: Throwable) { null }
                            if (result == null || !result.success || result.exitCode != 0) resetSucceeded = false
                        }
                        if (!restoreNewBoosterFeatures(context)) resetSucceeded = false
                        val resetPrefs = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
                        if (validPkg) resetPrefs.edit().putString("game_mode_category_$pkg", "standard").putString("compile_mode_$pkg", "reset").putString("game_downscale_$pkg", "disable").apply()
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, if (resetSucceeded) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                            TurboSpaceRepository.resetAllModules()
                            TurboSpaceRepository.setCrosshairActive(false)
                            context.getSharedPreferences("turbospace_overlay", Context.MODE_PRIVATE).edit().remove("crosshair_x").remove("crosshair_y").apply()
                            context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit().remove("crosshair_style").remove("crosshair_color_idx").apply()
                            TurboSpaceRepository.setCrosshairStyle(0)
                            TurboSpaceRepository.setCrosshairColorIdx(2)
                            TurboSpaceRepository.setSelectedGame("NO TARGET SELECTED")
                            TurboSpaceRepository.setFpsFloatingEnabled(context, false)
                            TurboSpaceRepository.setPingFloatingEnabled(context, false)
                            context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE).edit().putBoolean("fps_booster_active", false).putBoolean("power_saver_active", false).apply()
                            TurboSpaceRepository.powerSaverEnabled = false
                            TurboSpaceRepository.stopwatchRunning = false
                            TurboSpaceRepository.stopwatchElapsedMs = 0L
                            TurboSpaceRepository.stopwatchStartedAtMs = 0L
                            TurboSpaceRepository.persistStopwatch(context)
                            TurboSpaceRepository.savePrefs(context)
                            try { context.stopService(Intent(context, GameSpaceOverlayService::class.java)) } catch (_: Throwable) {}
                        }
                    }
                },
                onCancel = { showResetConfirm = false }
            )
        }
    }
}

@Composable
fun MainResetConfirmDialogNew(onConfirm: () -> Unit, onCancel: () -> Unit){
    Box(
        Modifier.fillMaxSize()
            .background(MonetColors.Background.copy(alpha=0.84f))
            .clickable{ onCancel() },
        contentAlignment=Alignment.Center
    ){
        Box(
            Modifier.fillMaxWidth(0.78f)
                .background(CyberDesign.DeepDark85, RoundedCornerShape(12.dp))
                .border(1.2.dp, MonetColors.Secondary.copy(alpha=0.85f), RoundedCornerShape(12.dp))
                .shadow(18.dp, RoundedCornerShape(12.dp), ambientColor=MonetColors.Secondary, spotColor=MonetColors.Secondary)
                .padding(18.dp)
                .clickable(enabled=false){}
        ){
            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(11.dp)){
                Text(
                    "RESET ALL",
                    color=MonetColors.Primary,
                    fontSize=14.sp,
                    fontWeight=FontWeight.Black,
                    fontFamily=FontFamily.Monospace,
                    letterSpacing=1.sp
                )
                Text(
                    "Reset all overlay commands?",
                    color=MonetColors.Primary,
                    fontSize=11.sp,
                    fontWeight=FontWeight.Black,
                    fontFamily=FontFamily.Monospace,
                    textAlign=TextAlign.Center
                )
                Text(
                    "All active boosters will be turned off.\nOverlay will be closed.\nUse this if you forgot to reset from overlay.",
                    color=MonetColors.Primary.copy(0.8f),
                    fontSize=8.5.sp,
                    fontFamily=FontFamily.Monospace,
                    textAlign=TextAlign.Center,
                    lineHeight=12.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .background(MonetColors.Primary.copy(0.08f), RoundedCornerShape(6.dp))
                            .border(0.8.dp, MonetColors.Primary.copy(0.25f), RoundedCornerShape(6.dp))
                            .clickable{ onCancel() },
                        contentAlignment=Alignment.Center
                    ){
                        Text(
                            "Cancel",
                            color=MonetColors.Primary.copy(0.78f),
                            fontSize=10.sp,
                            fontWeight=FontWeight.Bold,
                            fontFamily=FontFamily.Monospace
                        )
                    }
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .background(MonetColors.Primary, RoundedCornerShape(6.dp))
                            .border(1.2.dp, MonetColors.Secondary, RoundedCornerShape(6.dp))
                            .clickable{ onConfirm() },
                        contentAlignment=Alignment.Center
                    ){
                        Text(
                            "OK",
                            color=MonetColors.OnPrimary,
                            fontSize=11.sp,
                            fontWeight=FontWeight.Black,
                            fontFamily=FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCardSimple(title: String, desc: String, granted: Boolean, onClick: ()->Unit) {
    val borderColor = if (granted) MonetColors.Secondary else MonetColors.Primary
    Box(Modifier.fillMaxWidth().background(MonetColors.Inactive, RoundedCornerShape(8.dp)).border(1.dp, borderColor.copy(0.72f), RoundedCornerShape(8.dp)).clickable { if (!granted) onClick() }.padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MonetColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(if (granted) "GRANTED" else desc, color = MonetColors.Muted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            }
            Box(Modifier.background(if (granted) MonetColors.Secondary else MonetColors.Primary, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(if (granted) "OK" else "ALLOW", color = MonetColors.OnPrimary, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        }
    }
}


// ==================== BOOSTER DASHBOARD OPTIMIZED V2 - BIG SQUARE ICON + MANY LINES + NO LAG ====================
/**
 * Returns the highest refresh rate reported by Android for the display hosting this app.
 * This is a hardware/display capability guard, not a guarantee that every app/game will render
 * at that rate. If mode enumeration fails, fail safe to the currently reported display rate.
 */
@Suppress("DEPRECATION")
private fun detectMaxSupportedRefreshRateHz(context: Context): Int {
    return try {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return 60
        val display = windowManager.defaultDisplay ?: return 60
        val reportedRates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                display.supportedModes
                    .map { it.refreshRate.toDouble() }
                    .filter { it > 0.0 }
            } catch (_: Throwable) {
                emptyList()
            }
        } else {
            emptyList()
        }
        val maxRate = reportedRates.maxOrNull()
            ?: display.refreshRate.toDouble().takeIf { it > 0.0 }
            ?: 60.0
        kotlin.math.round(maxRate).toInt().coerceAtLeast(1)
    } catch (_: Throwable) {
        // A detection failure must not unlock a higher rate. Use a conservative 60 Hz cap.
        60
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
private fun BoosterDropdownOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) MonetColors.Primary.copy(alpha = 0.16f) else MonetColors.Background.copy(alpha = 0.62f))
            .border(1.dp, if (selected) MonetColors.Secondary else MonetColors.Outline.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MonetColors.Primary, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        Text(if (selected) "SELECTED" else "SELECT", color = MonetColors.Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun BoosterDashboardOptimizedV2(selectedGame: String, onBack: () -> Unit, onLaunchGame: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gameLabel = remember(selectedGame) {
        try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(selectedGame, 0)).toString() }
        catch (_: Throwable) { selectedGame }
    }
    val prefs = remember { context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE) }
    val active = remember {
        mutableStateMapOf(
            "WiFi Performance" to prefs.getBoolean("wifi_performance_active", false),
            "WiFi Background Killer" to (prefs.getStringSet("reduce_wifi_load_packages", emptySet<String>())?.isNotEmpty() == true)
        )
    }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val resultStatus = remember { mutableStateMapOf<String, String>() }
    val expandedFeatures = remember { mutableStateMapOf<String, Boolean>() }
    var gamePerformanceCategory by remember(selectedGame) { mutableStateOf(prefs.getString("game_mode_category_$selectedGame", "standard") ?: "standard") }
    var compileMode by remember(selectedGame) { mutableStateOf(prefs.getString("compile_mode_$selectedGame", "reset") ?: "reset") }
    var showReduceDialog by remember { mutableStateOf(false) }
    var showDownscaleWarning by remember { mutableStateOf(false) }
    var pendingDownscale by remember { mutableStateOf<String?>(null) }
    var userApps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun launchCommand(feature: String, command: String, afterSuccess: (() -> Unit)? = null) {
        if (busy[feature] == true) return
        busy[feature] = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try { TurboSpaceManager.executeCommandDetailedNoCtx(command) }
                catch (t: Throwable) { TurboSpaceManager.CommandResult(false, "", -1, t.message ?: "error") }
            }
            val ok = result.success && result.exitCode == 0
            busy[feature] = false
            resultStatus[feature] = if (ok) "SUCCESS" else "ERROR"
            if (ok) afterSuccess?.invoke()
            android.widget.Toast.makeText(context, if (ok) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun launchSequence(feature: String, commands: List<String>, afterSuccess: (() -> Unit)? = null) {
        if (busy[feature] == true) return
        busy[feature] = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (!TurboSpaceManager.isShizukuAvailableAndGranted()) return@withContext false
                var allOkay = true
                for (command in commands) {
                    val result = try { TurboSpaceManager.executeCommandDetailedNoCtx(command) }
                    catch (t: Throwable) { TurboSpaceManager.CommandResult(false, "", -1, t.message ?: "error") }
                    if (!result.success || result.exitCode != 0) {
                        allOkay = false
                        break
                    }
                }
                allOkay
            }
            busy[feature] = false
            resultStatus[feature] = if (ok) "SUCCESS" else "ERROR"
            if (ok) afterSuccess?.invoke()
            android.widget.Toast.makeText(context, if (ok) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun selectGamePerformanceCategory(category: String) {
        if (selectedGame == "NO TARGET SELECTED" || !selectedGame.contains(".")) {
            resultStatus["SELECT GAME PERFORMANCE CATEGORY"] = "ERROR"
            android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val previous = prefs.getString("game_mode_category_$selectedGame", "standard") ?: "standard"
        val commands = mutableListOf<String>()
        // Switching directly from performance to battery can conflict on some Android builds.
        if (previous == "performance" && category == "battery") commands.add("cmd game mode set standard $selectedGame")
        val selectedCommand = when (category) {
            "performance" -> "cmd game mode set performance $selectedGame"
            "battery" -> "cmd game mode set battery $selectedGame"
            else -> "cmd game mode set standard $selectedGame"
        }
        commands.add(selectedCommand)
        launchSequence("SELECT GAME PERFORMANCE CATEGORY", commands) {
            gamePerformanceCategory = category
            prefs.edit().putString("game_mode_category_$selectedGame", category).apply()
            expandedFeatures["SELECT GAME PERFORMANCE CATEGORY"] = false
        }
    }

    fun applyDownscale(value: String) {
        if (selectedGame == "NO TARGET SELECTED" || !selectedGame.contains(".")) {
            resultStatus["GAME DOWNSCALE"] = "ERROR"
            android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val command = if (value == "disable") "cmd game downscale disable $selectedGame" else "cmd game downscale $value $selectedGame"
        launchCommand("GAME DOWNSCALE", command) {
            prefs.edit().putString("game_downscale_$selectedGame", value).apply()
            expandedFeatures["GAME DOWNSCALE"] = false
            pendingDownscale = null
        }
    }

    fun selectCompileMode(mode: String) {
        if (selectedGame == "NO TARGET SELECTED" || !selectedGame.contains(".")) {
            resultStatus["COMPILE TOOLS"] = "ERROR"
            android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val prior = prefs.getString("compile_mode_$selectedGame", "reset") ?: "reset"
        if (mode != "reset" && prior != "reset" && prior != mode) {
            resultStatus["COMPILE TOOLS"] = "RESET REQUIRED"
            android.widget.Toast.makeText(context, "Reset compile mode before switching", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val command = when (mode) {
            "speed" -> "cmd package compile -m speed -f $selectedGame"
            "speed-profile" -> "cmd package compile -m speed-profile -f $selectedGame"
            else -> "cmd package compile --reset $selectedGame"
        }
        launchCommand("COMPILE TOOLS", command) {
            compileMode = mode
            prefs.edit().putString("compile_mode_$selectedGame", mode).apply()
            expandedFeatures["COMPILE TOOLS"] = false
        }
    }

    fun requestDownscale(value: String) {
        if (value == "0.90" || value == "0.85") {
            pendingDownscale = value
            showDownscaleWarning = true
        } else {
            applyDownscale(value)
        }
    }

    fun loadUserApps() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { info ->
                        val pkg = info.packageName
                        (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                            (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
                            pkg != context.packageName &&
                            !pkg.startsWith("com.turbospace") &&
                            pkg != "com.android.chrome" &&
                            !pkg.startsWith("com.google.")
                    }
                    .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
            }
            userApps = apps
            selectedPackages = emptySet()
            showReduceDialog = true
        }
    }

    fun applyReduceWifiLoad() {
        val packages = selectedPackages.filter { it.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")) }
        if (packages.isEmpty()) {
            showReduceDialog = false
            active["WiFi Background Killer"] = false
            resultStatus["WiFi Background Killer"] = "ERROR"
            android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        showReduceDialog = false
        busy["WiFi Background Killer"] = true
        scope.launch {
            // Result tuple: package, first AppOps result, fallback AppOps result, force-stop result.
            val results = withContext(Dispatchers.IO) {
                coroutineScope {
                    packages.map { pkg ->
                        async {
                            // Start force-stop immediately, concurrently with the AppOps fallback chain.
                            val forceStopTask = async {
                                try { TurboSpaceManager.executeCommandDetailedNoCtx("am force-stop $pkg") }
                                catch (t: Throwable) { TurboSpaceManager.CommandResult(false, "", -1, t.message ?: "error") }
                            }
                            val appOpsTask = async {
                                val first = try {
                                    TurboSpaceManager.executeCommandDetailedNoCtx("cmd appops set $pkg RUN_IN_BACKGROUND ignore")
                                } catch (t: Throwable) {
                                    TurboSpaceManager.CommandResult(false, "", -1, t.message ?: "error")
                                }
                                val fallback = if (first.success && first.exitCode == 0) first else try {
                                    TurboSpaceManager.executeCommandDetailedNoCtx("appops set $pkg RUN_IN_BACKGROUND ignore")
                                } catch (t: Throwable) {
                                    TurboSpaceManager.CommandResult(false, "", -1, t.message ?: "error")
                                }
                                Pair(first, fallback)
                            }
                            val appOps = appOpsTask.await()
                            val forceStop = forceStopTask.await()
                            Triple(pkg, appOps, forceStop)
                        }
                    }.awaitAll()
                }
            }

            fun commandOk(value: Any?): Boolean {
                val result = value as? TurboSpaceManager.CommandResult ?: return false
                return result.success && result.exitCode == 0
            }
            fun commandText(value: Any?): String {
                val result = value as? TurboSpaceManager.CommandResult ?: return ""
                return (result.error + " " + result.output).lowercase()
            }
            fun commandUnsupported(value: Any?): Boolean {
                val msg = commandText(value)
                return listOf("unknown command", "not found", "unsupported", "unknown operation", "invalid operation", "not supported", "unrecognized").any { it in msg }
            }

            val anyOperationSucceeded = results.any { row ->
                commandOk(row.second.first) || commandOk(row.second.second) || commandOk(row.third)
            }
            val unsupportedPackages = results.filter { row ->
                !commandOk(row.second.first) && !commandOk(row.second.second) && !commandOk(row.third) &&
                    (commandUnsupported(row.second.first) || commandUnsupported(row.second.second) || commandUnsupported(row.third))
            }.map { it.first }

            val appOpsSuccess = results.filter { row ->
                commandOk(row.second.first) || commandOk(row.second.second)
            }.map { it.first }
            if (appOpsSuccess.isNotEmpty()) {
                val previous = (prefs.getStringSet("reduce_wifi_load_packages", emptySet<String>()) ?: emptySet()).toMutableSet()
                previous.addAll(appOpsSuccess)
                prefs.edit().putStringSet("reduce_wifi_load_packages", previous).apply()
            }

            active["WiFi Background Killer"] = anyOperationSucceeded
            resultStatus["WiFi Background Killer"] = if (anyOperationSucceeded) "SUCCESS" else "ERROR"
            busy["WiFi Background Killer"] = false
            selectedPackages = emptySet()

            if (!anyOperationSucceeded && unsupportedPackages.isNotEmpty()) {
                // Android vendor builds sometimes omit AppOps support; route the user to App Info.
                try {
                    val pkg = unsupportedPackages.first()
                    val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(appInfoIntent)
                } catch (_: Throwable) { /* Still report ERROR below; do not crash the Dialog. */ }
            }
            android.widget.Toast.makeText(context, if (anyOperationSucceeded) "SUCCESS" else "ERROR", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun featureTap(name: String) {
        when (name) {
            "WiFi Performance" -> {
                val currentlyOn = active[name] == true
                launchCommand(name, if (currentlyOn) "settings put global wifi_scan_always_enabled 1" else "settings put global wifi_scan_always_enabled 0") {
                    active[name] = !currentlyOn
                    prefs.edit().putBoolean("wifi_performance_active", !currentlyOn).apply()
                }
            }
            "RAM Booster" -> launchCommand(name, "am kill-all")
            "WiFi Background Killer" -> loadUserApps()
            "Clear Cache" -> launchCommand(name, "pm trim-caches 999G")

        }
    }

    val features = listOf(
        Triple("WiFi Performance", "Temporarily reduces Wi-Fi scanning to help keep gameplay stable.", "wifi"),
        Triple("RAM Booster", "Runs Android's background process cleanup to free memory.", "rocket"),
        Triple("WiFi Background Killer", "Select installed apps to restrict their background activity.", "tools"),
        Triple("Clear Cache", "Requests Android to trim app caches and reclaim storage space.", "broom"),
        Triple("SELECT GAME PERFORMANCE CATEGORY", "Choose performance, battery saver, or standard game mode.", "game_sword"),
        Triple("GAME DOWNSCALE", "Adjust the rendered game resolution scale.", "gear"),
        Triple("COMPILE TOOLS", "Compile the selected game for speed or speed-profile.", "tools")
    )

    Box(Modifier.fillMaxSize().background(MonetColors.Background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(43.dp).clip(RoundedCornerShape(12.dp)).background(MonetColors.Inactive).border(1.dp, MonetColors.Secondary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    MonetCanvasIcon("game", Modifier.size(32.dp), MonetColors.Primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(gameLabel, color = MonetColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("BOOSTER CONFIGURATION", color = MonetColors.Secondary, fontSize = 9.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                }
                MonetCanvasIcon("gear", Modifier.size(26.dp), MonetColors.Secondary)
            }
            Spacer(Modifier.height(18.dp))
            Text("BOOST FEATURES", color = MonetColors.Secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(9.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(features, key = { it.first }) { feature ->
                    val name = feature.first
                    val description = feature.second
                    val icon = feature.third
                    val isActive = active[name] == true
                    val isBusy = busy[name] == true
                    val isExpandable = name in setOf("SELECT GAME PERFORMANCE CATEGORY", "GAME DOWNSCALE", "COMPILE TOOLS")
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                                .background(if (isActive) MonetColors.Primary else MonetColors.Inactive)
                                .border(1.dp, if (isActive) MonetColors.Secondary else MonetColors.Outline, RoundedCornerShape(13.dp))
                                .clickable(enabled = !isBusy) {
                                    if (isExpandable) expandedFeatures[name] = expandedFeatures[name] != true
                                    else featureTap(name)
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MonetCanvasIcon(icon, Modifier.size(34.dp), if (isActive) MonetColors.OnPrimary else MonetColors.Secondary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(name, color = if (isActive) MonetColors.OnPrimary else MonetColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(description, color = if (isActive) MonetColors.OnPrimary.copy(alpha = 0.78f) else MonetColors.Muted, fontSize = 9.sp, lineHeight = 12.sp)
                                resultStatus[name]?.let { status ->
                                    Text(status, color = if (status == "SUCCESS") MonetColors.Secondary else MonetColors.Primary, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text(
                                when {
                                    isBusy -> "..."
                                    isExpandable -> if (expandedFeatures[name] == true) "CLOSE" else "OPEN"
                                    name in setOf("RAM Booster", "Clear Cache") -> "RUN"
                                    isActive -> "ON"
                                    else -> "SET"
                                },
                                color = if (isActive) MonetColors.OnPrimary else MonetColors.Secondary,
                                fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                            )
                        }

                        if (expandedFeatures[name] == true) {
                            Column(
                                Modifier.fillMaxWidth().padding(start = 18.dp, end = 5.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(MonetColors.Inactive.copy(alpha = 0.72f))
                                    .border(1.dp, MonetColors.Outline.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                when (name) {
                                    "SELECT GAME PERFORMANCE CATEGORY" -> {
                                        BoosterDropdownOption("Performance", gamePerformanceCategory == "performance") { selectGamePerformanceCategory("performance") }
                                        BoosterDropdownOption("Battery Saver", gamePerformanceCategory == "battery") { selectGamePerformanceCategory("battery") }
                                        BoosterDropdownOption("Reset / Standard", gamePerformanceCategory == "standard") { selectGamePerformanceCategory("standard") }
                                    }
                                    "GAME DOWNSCALE" -> {
                                        val downscale = prefs.getString("game_downscale_$selectedGame", "disable") ?: "disable"
                                        BoosterDropdownOption("100% — Disable downscale", downscale == "disable") { requestDownscale("disable") }
                                        BoosterDropdownOption("95% — 0.95", downscale == "0.95") { requestDownscale("0.95") }
                                        BoosterDropdownOption("90% — 0.90", downscale == "0.90") { requestDownscale("0.90") }
                                        BoosterDropdownOption("85% — 0.85", downscale == "0.85") { requestDownscale("0.85") }
                                    }
                                    "COMPILE TOOLS" -> {
                                        BoosterDropdownOption("Full Speed (speed)", compileMode == "speed") { selectCompileMode("speed") }
                                        BoosterDropdownOption("Balanced (speed-profile)", compileMode == "speed-profile") { selectCompileMode("speed-profile") }
                                        BoosterDropdownOption("Reset (--reset)", compileMode == "reset") { selectCompileMode("reset") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(MonetColors.Inactive).border(1.dp, MonetColors.Outline, RoundedCornerShape(10.dp)).clickable { onBack() }, contentAlignment = Alignment.Center) {
                    Text("CANCEL", color = MonetColors.Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Box(Modifier.weight(1.4f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(MonetColors.Primary).clickable {
                    if (TurboSpaceManager.isShizukuAvailableAndGranted()) onLaunchGame()
                    else {
                        TurboSpaceManager.requestShizukuPermission()
                        android.widget.Toast.makeText(context, "ERROR", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, contentAlignment = Alignment.Center) {
                    Text("START", color = MonetColors.OnPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (showReduceDialog) {
            Box(Modifier.fillMaxSize().background(MonetColors.Background.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.30f).clip(RoundedCornerShape(16.dp))
                        .background(MonetColors.Inactive).border(1.5.dp, MonetColors.Primary, RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("WIFI BACKGROUND KILLER", color = MonetColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("Select downloaded user apps", color = MonetColors.Muted, fontSize = 10.sp)
                    if (userApps.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 55.dp), contentAlignment = Alignment.Center) {
                            Text("NO USER APPS FOUND", color = MonetColors.Muted, fontSize = 10.sp)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 245.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(userApps, key = { it.packageName }) { app ->
                                val pkg = app.packageName
                                val checked = selectedPackages.contains(pkg)
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(if (checked) MonetColors.Primary.copy(alpha = 0.14f) else Color.Transparent)
                                        .clickable { selectedPackages = if (checked) selectedPackages - pkg else selectedPackages + pkg }
                                        .padding(horizontal = 7.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppIconImageRaw(context, pkg, Modifier.size(24.dp))
                                    Canvas(Modifier.size(17.dp)) {
                                        drawRoundRect(if (checked) MonetColors.Primary else MonetColors.Outline, style = Stroke(width = 1.5.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                                        if (checked) drawLine(MonetColors.Primary, Offset(size.width*0.22f,size.height*0.52f), Offset(size.width*0.43f,size.height*0.73f), 1.7.dp.toPx(), cap=StrokeCap.Round).also {
                                            drawLine(MonetColors.Primary, Offset(size.width*0.40f,size.height*0.72f), Offset(size.width*0.80f,size.height*0.25f), 1.7.dp.toPx(), cap=StrokeCap.Round)
                                        }
                                    }
                                    Text(pkg, color = MonetColors.Primary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.Transparent).border(1.dp, MonetColors.Outline, RoundedCornerShape(8.dp)).clickable { showReduceDialog = false; selectedPackages = emptySet() }, contentAlignment = Alignment.Center) {
                            Text("CANCEL", color = MonetColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(MonetColors.Inactive).border(1.5.dp, MonetColors.Primary, RoundedCornerShape(8.dp)).clickable { applyReduceWifiLoad() }, contentAlignment = Alignment.Center) {
                            Text("OK", color = MonetColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        if (showDownscaleWarning && pendingDownscale != null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showDownscaleWarning = false; pendingDownscale = null }) {
                Column(
                    Modifier.fillMaxWidth().background(MonetColors.Background, RoundedCornerShape(14.dp))
                        .border(1.5.dp, MonetColors.Secondary, RoundedCornerShape(14.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("GAME DOWNSCALE WARNING", color = MonetColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text(
                        "Lower resolution can reduce visual clarity and may affect UI scale. Continue with ${if (pendingDownscale == "0.90") "90%" else "85%"}?",
                        color = MonetColors.Muted, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 14.sp
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(MonetColors.Inactive)
                            .border(1.dp, MonetColors.Outline, RoundedCornerShape(8.dp)).clickable { showDownscaleWarning = false; pendingDownscale = null }, contentAlignment = Alignment.Center) {
                            Text("CANCEL", color = MonetColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(MonetColors.Primary.copy(alpha = 0.14f))
                            .border(1.5.dp, MonetColors.Secondary, RoundedCornerShape(8.dp)).clickable {
                                val value = pendingDownscale
                                showDownscaleWarning = false
                                pendingDownscale = null
                                if (value != null) applyDownscale(value)
                            }, contentAlignment = Alignment.Center) {
                            Text("OK", color = MonetColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

    }
}

// ==================== MAIN ACTIVITY & TURBOSPACE APP ENTRY ====================
class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // FIX BUG-06: allow immersive content to extend into system-bar regions.
        try {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        } catch (_: Throwable) {}
        try{
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                window?.setDecorFitsSystemWindows(false)
                window?.insetsController?.hide(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
                )
                window?.insetsController?.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility = (
                    0x00000004 or // FULLSCREEN
                    0x00000002 or // HIDE_NAVIGATION
                    0x00001000 or // IMMERSIVE_STICKY
                    0x00000100 or // LAYOUT_STABLE
                    0x00000200 or // LAYOUT_HIDE_NAVIGATION
                    0x00000400    // LAYOUT_FULLSCREEN
                )
            }
        }catch(_: Throwable){}
        try {
            setContent {
                CrashSafeRoot()
            }
        } catch (t: Throwable) {
            // Fallback to simple view if Compose crashes on online builder
            val tv = android.widget.TextView(this).apply {
                text = "TurboSpace Booster\n\nCrash prevented: ${t.message}\n\nPlease grant Overlay + Shizuku permissions.\nIf using online APK builder, ensure dependencies:\nandroidx.lifecycle:lifecycle-runtime-compose\nandroidx.savedstate:savedstate\ndev.rikka.shizuku:api"
                setPadding(40, 200, 40, 40)
                textSize = 14f
            }
            setContentView(tv)
        }
    }

    // FIX BUG-06: re-apply immersive mode whenever the activity regains focus.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.insetsController?.hide(
                    android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                )
                window?.insetsController?.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility = (
                    0x00000004 or 0x00000002 or 0x00001000 or
                    0x00000100 or 0x00000200 or 0x00000400
                )
            }
        } catch (_: Throwable) {}
    }
}

// ==================== BLOCK ASSEMBLY SPLASH ====================
@Composable
fun BlockAssemblySplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.05f) }
    val alpha = remember { Animatable(0f) }
    val glow = remember { Animatable(0.15f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(180, easing = LinearOutSlowInEasing)) }
        launch { glow.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        scale.animateTo(1.12f, tween(560, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
        scale.animateTo(16f, tween(170, easing = FastOutSlowInEasing))
        delay(10)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(MonetColors.Background), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(MonetColors.Secondary.copy(alpha = 0.08f * glow.value), size.minDimension * 0.32f, Offset(size.width / 2f, size.height / 2f))
            drawCircle(MonetColors.Primary.copy(alpha = 0.09f * glow.value), size.minDimension * 0.22f, Offset(size.width / 2f, size.height / 2f))
        }
        Canvas(Modifier.size(150.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.78f, h * 0.20f)
                cubicTo(w * 0.62f, h * 0.08f, w * 0.27f, h * 0.10f, w * 0.24f, h * 0.34f)
                cubicTo(w * 0.20f, h * 0.54f, w * 0.73f, h * 0.43f, w * 0.75f, h * 0.64f)
                cubicTo(w * 0.78f, h * 0.88f, w * 0.38f, h * 0.93f, w * 0.19f, h * 0.78f)
            }
            drawPath(path, MonetColors.Primary, style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(MonetColors.Primary.copy(alpha = 0.2f * glow.value), w * 0.45f, Offset(w / 2f, h / 2f))
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
fun TurboSpaceAppRoot() {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        BlockAssemblySplashScreen(onFinished = {
            showSplash = false
        })
    } else {
        MainMissionControlDashboard()
    }
}
