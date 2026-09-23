package com.turbospace.optimizer

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

// NOTE: LocalFireDepartment requires material-icons-extended in build.gradle:
// implementation "androidx.compose.material:material-icons-extended:$compose_version"

// ============================================================================
// 1. MAIN ENTRY POINT ACTIVITY
// ============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TurboSpaceLandscapeHub()
        }
    }
}

// ============================================================================
// 2. COLOR PALETTE (GAMING THEME)
// ============================================================================
object TurboColors {
    val BrightRed = Color(0xFFFF1A1A)
    val BrightGlowRed = Color(0xFFFF4D4D)
    val ActiveRedBg = Color(0xFF4A0000)
    val DarkBackground = Color(0xFF0F0E13)
    val CardBackground = Color(0xFF1E1C24)
    val BorderGray = Color(0xFF3D3A46)
    val TextGray = Color(0xFFA09DAA)
    val EngineStopGray = Color(0xFF555555)
}

// ============================================================================
// 2b. SESSION FEATURES AND STATE
// ============================================================================
enum class PerformanceFeature(
    val label: String,
    val stateful: Boolean,
    val applyCommand: (String) -> String,
    val resetCommand: ((String) -> String)?
) {
    GAME_PERFORMANCE("Game Performance", true,
        { pkg -> "cmd game mode performance $pkg" },
        { pkg -> "cmd game mode reset $pkg" }),
    THERMAL_CONTROL("Thermal Control", true,
        { "cmd thermalservice override-status 0" },
        { "cmd thermalservice reset" }),
    PROCESS_CONTROL("Process Control", true,
        { "cmd activity set-process-limit 2" },
        { "cmd activity set-process-limit default" }),
    IDLE_CONTROL("Idle Control", true,
        { "cmd deviceidle force-idle light" },
        { "cmd deviceidle unforce" }),
    RESOURCE_CLEANUP("Resource Cleanup", false,
        { "cmd activity purge-process-resources" }, null),
    GFX_BOOST("GFX Boost", false,
        { "cmd activity boost-gfx" }, null),
    APP_OPTIMIZATION("App Optimization", false,
        { "cmd package bg-dexopt-job" }, null),
    INPUT_CONFIGURATION("Input Configuration", false,
        { "cmd inputflinger reload-config" }, null)
}

enum class BackgroundType { DEFAULT, IMAGE, VIDEO, STATIC, ANIMATED }
data class BackgroundChoice(val type: BackgroundType, val uri: String = "")
data class SessionSnapshot(
    val gamePackage: String,
    val originalGameMode: String?,
    val originalProcessLimit: String?,
    val appliedStateful: Set<PerformanceFeature>
)

object TurboSpaceRepository {
    private const val NO_GAME = "NO TARGET SELECTED"
    private val _selectedGame = MutableStateFlow(NO_GAME)
    val selectedGame: StateFlow<String> = _selectedGame.asStateFlow()
    private val _selectedFeatures = MutableStateFlow(PerformanceFeature.values().toSet())
    val selectedFeatures: StateFlow<Set<PerformanceFeature>> = _selectedFeatures.asStateFlow()
    private val _background = MutableStateFlow(BackgroundChoice(BackgroundType.DEFAULT))
    val background: StateFlow<BackgroundChoice> = _background.asStateFlow()
    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()
    private val _sessionBusy = MutableStateFlow(false)
    val sessionBusy: StateFlow<Boolean> = _sessionBusy.asStateFlow()
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // Existing CPU/GPU implementations and selection state remain intact.
    private val _selectedCpuApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedCpuApps: StateFlow<Set<String>> = _selectedCpuApps.asStateFlow()
    private val _selectedGpuGame = MutableStateFlow(NO_GAME)
    val selectedGpuGame: StateFlow<String> = _selectedGpuGame.asStateFlow()
    private val _isCpuOptActive = MutableStateFlow(false)
    val isCpuOptActive: StateFlow<Boolean> = _isCpuOptActive.asStateFlow()
    private val _isGpuOptActive = MutableStateFlow(false)
    val isGpuOptActive: StateFlow<Boolean> = _isGpuOptActive.asStateFlow()
    private val _isCpuLoading = MutableStateFlow(false)
    val isCpuLoading: StateFlow<Boolean> = _isCpuLoading.asStateFlow()
    private val _isGpuLoading = MutableStateFlow(false)
    val isGpuLoading: StateFlow<Boolean> = _isGpuLoading.asStateFlow()

    fun restoreFromPrefs(context: Context) {
        val saved = TurboSpaceManager.loadLauncherState(context)
        _selectedGame.value = saved.gamePackage.takeIf { TurboSpaceManager.isGamePackage(context, it) } ?: NO_GAME
        _selectedGpuGame.value = saved.gpuGame.takeIf { TurboSpaceManager.isGamePackage(context, it) } ?: NO_GAME
        _selectedCpuApps.value = saved.cpuApps.filterNot { it == context.packageName }.toSet()
        _selectedFeatures.value = saved.features.mapNotNull { runCatching { PerformanceFeature.valueOf(it) }.getOrNull() }.toSet()
        _background.value = BackgroundChoice(
            runCatching { BackgroundType.valueOf(saved.backgroundType) }.getOrDefault(BackgroundType.DEFAULT),
            saved.backgroundUri
        )
        _isSessionActiveFromPrefs(context)
    }

    private fun _isSessionActiveFromPrefs(context: Context) {
        _sessionActive.value = TurboSpaceManager.loadSessionSnapshot(context) != null
    }

    private fun persist(context: Context) = TurboSpaceManager.saveLauncherState(
        context, _selectedGame.value, _selectedFeatures.value.map { it.name }.toSet(),
        _background.value, _selectedCpuApps.value, _selectedGpuGame.value
    )

    fun setSelectedGame(context: Context, pkg: String) {
        if (!TurboSpaceManager.isGamePackage(context, pkg)) return
        _selectedGame.value = pkg
        persist(context)
    }

    fun setSelectedFeatures(context: Context, features: Set<PerformanceFeature>) {
        _selectedFeatures.value = features
        persist(context)
    }

    fun setBackground(context: Context, choice: BackgroundChoice) {
        _background.value = choice
        persist(context)
    }

    fun setSelectedCpuApps(context: Context, apps: Set<String>) {
        _selectedCpuApps.value = apps.filterNot { it == context.packageName }.toSet()
        persist(context)
    }

    fun setSelectedGpuGame(context: Context, pkg: String) {
        if (!TurboSpaceManager.isGamePackage(context, pkg)) return
        _selectedGpuGame.value = pkg
        persist(context)
    }

    fun setServiceState(running: Boolean) { _isServiceRunning.value = running }

    suspend fun startCpuOpt(context: Context) {
        _isCpuLoading.value = true
        try {
            val results = _selectedCpuApps.value.map { TurboSpaceManager.reduceCpuLoad(context, it) }
            val ok = results.isNotEmpty() && results.all { it != TurboSpaceManager.CommandOutcome.BOTH_FAILED }
            _isCpuOptActive.value = ok
            TurboSpaceManager.showOutcomeToast(context, if (ok) TurboSpaceManager.CommandOutcome.PRIMARY_SUCCESS else TurboSpaceManager.CommandOutcome.BOTH_FAILED)
        } finally { _isCpuLoading.value = false }
    }

    suspend fun startGpuOpt(context: Context) {
        _isGpuLoading.value = true
        try {
            val result = TurboSpaceManager.reduceGpuLoad(context, _selectedGpuGame.value, "0.9")
            _isGpuOptActive.value = result != TurboSpaceManager.CommandOutcome.BOTH_FAILED
            TurboSpaceManager.showOutcomeToast(context, result)
        } finally { _isGpuLoading.value = false }
    }

    suspend fun startSession(context: Context): Boolean {
        val game = _selectedGame.value
        if (!TurboSpaceManager.isGamePackage(context, game)) {
            Toast.makeText(context, "Select a game first", Toast.LENGTH_SHORT).show()
            return false
        }
        _sessionBusy.value = true
        return try {
            // Capture detectable values before the first state-changing command.
            val oldMode = TurboSpaceManager.readGameMode(context, game)
            val oldLimit = TurboSpaceManager.readProcessLimit(context)
            val applied = linkedSetOf<PerformanceFeature>()
            // Persist the pre-session snapshot before changing any state, then
            // update it after each successful stateful command for crash-safe RESET.
            TurboSpaceManager.saveSessionSnapshot(context, SessionSnapshot(game, oldMode, oldLimit, emptySet()))
            _sessionActive.value = true
            _selectedFeatures.value.forEach { feature ->
                val outcome = TurboSpaceManager.executeCommandDetailed(context, feature.applyCommand(game))
                if (outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED && feature.stateful) {
                    applied += feature
                    TurboSpaceManager.saveSessionSnapshot(context, SessionSnapshot(game, oldMode, oldLimit, applied.toSet()))
                }
            }
            true
        } finally { _sessionBusy.value = false }
    }

    suspend fun runOverlayFeature(context: Context, feature: PerformanceFeature) {
        val game = _selectedGame.value
        if (feature == PerformanceFeature.GAME_PERFORMANCE && !TurboSpaceManager.isGamePackage(context, game)) return
        TurboSpaceManager.showOutcomeToast(context, TurboSpaceManager.executeCommandDetailed(context, feature.applyCommand(game)))
    }

    suspend fun resetSession(context: Context) {
        _sessionBusy.value = true
        try {
            val snapshot = TurboSpaceManager.loadSessionSnapshot(context) ?: return
            snapshot.appliedStateful.reversed().forEach { feature ->
                val command = when (feature) {
                    PerformanceFeature.GAME_PERFORMANCE -> snapshot.originalGameMode
                        ?.let { "cmd game mode $it ${snapshot.gamePackage}" }
                        ?: feature.resetCommand?.invoke(snapshot.gamePackage)
                    PerformanceFeature.PROCESS_CONTROL -> snapshot.originalProcessLimit
                        ?.let { "cmd activity set-process-limit $it" }
                        ?: feature.resetCommand?.invoke(snapshot.gamePackage)
                    else -> feature.resetCommand?.invoke(snapshot.gamePackage)
                }
                if (command != null) TurboSpaceManager.executeCommandDetailed(context, command)
            }
            TurboSpaceManager.clearSessionSnapshot(context)
            _sessionActive.value = false
            Toast.makeText(context, "Original system state restored", Toast.LENGTH_SHORT).show()
        } finally { _sessionBusy.value = false }
    }
}

// ============================================================================
// 4. HARDWARE MONITOR ENGINE
// ============================================================================
class SystemMonitorEngine(private val context: Context) {
    private var lastFrameTimeNanos = 0L
    private var frameCount = 0
    private var previousCpuIdle: Long = 0
    private var previousCpuTotal: Long = 0
    private var isFpsCallbackRegistered = false

    private val perCoreSnapshot = mutableMapOf<String, Pair<Long, Long>>()

    val currentFps = mutableStateOf(60)
    val currentCpuUsage = mutableStateOf("0%")
    val currentRamUsage = mutableStateOf("0 MB / 0 MB")
    val currentCpuCores = mutableStateOf<List<Pair<String, Int>>>(emptyList())
    val currentTemperature = mutableStateOf("N/A")

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameTimeNanos == 0L) {
                lastFrameTimeNanos = frameTimeNanos
            } else {
                val delta = frameTimeNanos - lastFrameTimeNanos
                frameCount++
                if (delta >= 1_000_000_000L) {
                    currentFps.value = frameCount
                    frameCount = 0
                    lastFrameTimeNanos = frameTimeNanos
                }
            }
            if (isFpsCallbackRegistered) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun startFpsMonitoring() {
        if (!isFpsCallbackRegistered) {
            isFpsCallbackRegistered = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun stopFpsMonitoring() {
        if (isFpsCallbackRegistered) {
            isFpsCallbackRegistered = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    suspend fun updateHardwareMetrics(includePerCore: Boolean = false) = withContext(Dispatchers.IO) {
        updateRamInfo()
        updateCpuUsageViaShizuku()
        updateTemperature()
        if (includePerCore) updatePerCoreUsage()
    }

    // Reads the hottest available thermal zone (best effort, no root needed).
    private fun updateTemperature() {
        val value = try {
            (0..9).asSequence()
                .mapNotNull { zone ->
                    try {
                        java.io.File("/sys/class/thermal/thermal_zone$zone/temp")
                            .takeIf { it.canRead() }
                            ?.readText()
                            ?.trim()
                            ?.toLongOrNull()
                    } catch (_: Exception) {
                        null
                    }
                }
                .map { raw -> if (raw > 1000) raw / 1000.0 else raw.toDouble() }
                .filter { it > 0 && it < 150 }
                .maxOrNull()
        } catch (_: Exception) {
            null
        }
        currentTemperature.value = if (value == null) "N/A" else String.format("%.1f°C", value)
    }

    // Per-core CPU load, parsed from every "cpuN" line in /proc/stat.
    private suspend fun updatePerCoreUsage() = withContext(Dispatchers.IO) {
        if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
            currentCpuCores.value = emptyList()
            return@withContext
        }
        var process: Process? = null
        try {
            process = TurboSpaceManager.newShizukuProcess(arrayOf("sh", "-c", "cat /proc/stat"), null, null)
            val lines = process.inputStream.bufferedReader().readLines()
            process.waitFor()

            val result = mutableListOf<Pair<String, Int>>()
            lines.filter { it.startsWith("cpu") && it.length > 3 && it[3].isDigit() }.forEach { line ->
                val tokens = line.trim().split("\\s+".toRegex())
                if (tokens.size >= 8) {
                    val name = tokens[0]
                    val idle = tokens[4].toLongOrNull() ?: 0L
                    val total = tokens.subList(1, 8).mapNotNull { it.toLongOrNull() }.sum()
                    val prev = perCoreSnapshot[name]
                    if (prev != null) {
                        val totalDiff = total - prev.first
                        val idleDiff = idle - prev.second
                        if (totalDiff > 0) {
                            val usage = (((totalDiff - idleDiff) * 100) / totalDiff).toInt().coerceIn(0, 100)
                            result.add(name.uppercase() to usage)
                        }
                    }
                    perCoreSnapshot[name] = total to idle
                }
            }
            if (result.isNotEmpty()) currentCpuCores.value = result
        } catch (_: Exception) {
            currentCpuCores.value = emptyList()
        } finally {
            process?.destroy()
        }
    }

    private fun updateRamInfo() {
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalMb = memInfo.totalMem / (1024 * 1024)
            val availMb = memInfo.availMem / (1024 * 1024)
            val usedMb = totalMb - availMb
            currentRamUsage.value = "$usedMb MB / $totalMb MB"
        } catch (e: Exception) {
            currentRamUsage.value = "N/A"
        }
    }

    private suspend fun updateCpuUsageViaShizuku() = withContext(Dispatchers.IO) {
        if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
            currentCpuUsage.value = "N/A"
            return@withContext
        }
        var process: Process? = null
        try {
            process = TurboSpaceManager.newShizukuProcess(arrayOf("sh", "-c", "cat /proc/stat"), null, null)
            val line = process.inputStream.bufferedReader().readLine()
            process.waitFor()

            if (line != null) {
                val tokens = line.trim().split("\\s+".toRegex())
                if (tokens.size >= 5 && tokens[0] == "cpu") {
                    val idle = tokens[4].toLong()
                    val cpuTotal = tokens.subList(1, 8).mapNotNull { it.toLongOrNull() }.sum()
                    val totalDiff = cpuTotal - previousCpuTotal
                    val idleDiff = idle - previousCpuIdle

                    if (totalDiff > 0) {
                        val usagePercentage = ((totalDiff - idleDiff) * 100 / totalDiff).toInt()
                        currentCpuUsage.value = "$usagePercentage%"
                    }

                    previousCpuTotal = cpuTotal
                    previousCpuIdle = idle
                }
            }
        } catch (_: Exception) {
            currentCpuUsage.value = "N/A"
        } finally {
            process?.destroy()
        }
    }
}

// ============================================================================
// 5. CORE MANAGER & SHIZUKU VERIFIER
// ============================================================================
object TurboSpaceManager {
    private const val PREFS_NAME = "TurboSpaceState"
    private const val COMMAND_TIMEOUT_MS = 10000L
    private const val COMPILE_TIMEOUT_MS = 120000L

    enum class CompileMode(val artFilter: String, val label: String) {
        SPACE_SAVING("space", "Space Saving"),
        FULL_SPEED("everything", "Compile Everything"),
        PROFILE_BASED("speed-profile", "Usage-Based Compile")
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class LauncherSavedState(
        val gamePackage: String,
        val features: Set<String>,
        val backgroundType: String,
        val backgroundUri: String,
        val cpuApps: Set<String>,
        val gpuGame: String
    )

    private const val KEY_GAME = "SELECTED_GAME"
    private const val KEY_FEATURES = "SELECTED_FEATURES"
    private const val KEY_BACKGROUND_TYPE = "BACKGROUND_TYPE"
    private const val KEY_BACKGROUND_URI = "BACKGROUND_URI"
    private const val KEY_CPU_APPS = "CPU_APPS"
    private const val KEY_GPU_GAME = "GPU_GAME"
    private const val KEY_SESSION_GAME = "SESSION_GAME"
    private const val KEY_SESSION_MODE = "SESSION_GAME_MODE"
    private const val KEY_SESSION_LIMIT = "SESSION_PROCESS_LIMIT"
    private const val KEY_SESSION_FEATURES = "SESSION_FEATURES"
    private const val APPS_DELIMITER = "||"

    fun saveLauncherState(
        context: Context,
        gamePackage: String,
        features: Set<String>,
        background: BackgroundChoice,
        cpuApps: Set<String>,
        gpuGame: String
    ) {
        getPrefs(context).edit()
            .putString(KEY_GAME, gamePackage)
            .putStringSet(KEY_FEATURES, features)
            .putString(KEY_BACKGROUND_TYPE, background.type.name)
            .putString(KEY_BACKGROUND_URI, background.uri)
            .putString(KEY_CPU_APPS, cpuApps.joinToString(APPS_DELIMITER))
            .putString(KEY_GPU_GAME, gpuGame)
            .apply()
    }

    fun loadLauncherState(context: Context): LauncherSavedState {
        val prefs = getPrefs(context)
        val defaultFeatures = PerformanceFeature.values().map { it.name }.toSet()
        val rawCpu = prefs.getString(KEY_CPU_APPS, "").orEmpty()
        return LauncherSavedState(
            gamePackage = prefs.getString(KEY_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
            features = prefs.getStringSet(KEY_FEATURES, defaultFeatures)?.toSet() ?: defaultFeatures,
            backgroundType = prefs.getString(KEY_BACKGROUND_TYPE, BackgroundType.DEFAULT.name) ?: BackgroundType.DEFAULT.name,
            backgroundUri = prefs.getString(KEY_BACKGROUND_URI, "").orEmpty(),
            cpuApps = if (rawCpu.isBlank()) emptySet() else rawCpu.split(APPS_DELIMITER).filter { it.isNotBlank() }.toSet(),
            gpuGame = prefs.getString(KEY_GPU_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED"
        )
    }

    fun saveSessionSnapshot(context: Context, snapshot: SessionSnapshot) {
        getPrefs(context).edit()
            .putString(KEY_SESSION_GAME, snapshot.gamePackage)
            .putString(KEY_SESSION_MODE, snapshot.originalGameMode)
            .putString(KEY_SESSION_LIMIT, snapshot.originalProcessLimit)
            .putStringSet(KEY_SESSION_FEATURES, snapshot.appliedStateful.map { it.name }.toSet())
            .apply()
    }

    fun loadSessionSnapshot(context: Context): SessionSnapshot? {
        val prefs = getPrefs(context)
        val game = prefs.getString(KEY_SESSION_GAME, null) ?: return null
        val features = prefs.getStringSet(KEY_SESSION_FEATURES, emptySet()).orEmpty()
            .mapNotNull { runCatching { PerformanceFeature.valueOf(it) }.getOrNull() }.toSet()
        return SessionSnapshot(
            gamePackage = game,
            originalGameMode = prefs.getString(KEY_SESSION_MODE, null),
            originalProcessLimit = prefs.getString(KEY_SESSION_LIMIT, null),
            appliedStateful = features
        )
    }

    fun clearSessionSnapshot(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_SESSION_GAME).remove(KEY_SESSION_MODE)
            .remove(KEY_SESSION_LIMIT).remove(KEY_SESSION_FEATURES).apply()
    }

    fun isShizukuAvailableAndGranted(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
            }
        } catch (_: Exception) {}
    }

    fun addPermissionResultListener(onResult: (granted: Boolean) -> Unit): Shizuku.OnRequestPermissionResultListener {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) {
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        return listener
    }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    fun newShizukuProcess(cmd: Array<String>, env: Array<String>?, dir: String?): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, cmd, env, dir) as Process
    }

    enum class CommandOutcome { PRIMARY_SUCCESS, FALLBACK_SUCCESS, BOTH_FAILED }

    fun combineOutcomes(vararg outcomes: CommandOutcome): CommandOutcome {
        return when {
            outcomes.any { it == CommandOutcome.BOTH_FAILED } -> CommandOutcome.BOTH_FAILED
            outcomes.any { it == CommandOutcome.FALLBACK_SUCCESS } -> CommandOutcome.FALLBACK_SUCCESS
            else -> CommandOutcome.PRIMARY_SUCCESS
        }
    }

    // Toast messages per outcome:
    // PRIMARY_SUCCESS  → "Success"        (primary cmd worked)
    // FALLBACK_SUCCESS → "Success 2"      (error-Success: primary failed, fallback worked)
    // BOTH_FAILED      → "Error - Error 2" (both commands failed)
    fun showOutcomeToast(context: Context, outcome: CommandOutcome) {
        val message = when (outcome) {
            CommandOutcome.PRIMARY_SUCCESS -> "Success"
            CommandOutcome.FALLBACK_SUCCESS -> "Success 2"
            CommandOutcome.BOTH_FAILED -> "Error - Error 2"
        }
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private suspend fun readCommandOutput(context: Context, command: String): String? = withContext(Dispatchers.IO) {
        if (!isShizukuAvailableAndGranted()) return@withContext null
        var process: Process? = null
        try {
            process = newShizukuProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().readText().trim()
            val exit = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { runInterruptible { process.waitFor() } }
            output.takeIf { exit == 0 && it.isNotBlank() }
        } catch (_: Exception) { null } finally { process?.destroy() }
    }

    suspend fun readGameMode(context: Context, packageName: String): String? {
        if (!isGamePackage(context, packageName)) return null
        val raw = readCommandOutput(context, "cmd game mode get $packageName") ?: return null
        return listOf("performance", "standard", "battery").firstOrNull { raw.contains(it, ignoreCase = true) }
    }

    suspend fun readProcessLimit(context: Context): String? {
        val raw = readCommandOutput(context, "cmd activity get-process-limit") ?: return null
        return Regex("-?\\d+").find(raw)?.value?.let { if (it == "-1") "default" else it }
    }

    suspend fun executeCommandDetailed(
        context: Context,
        primaryCmd: String,
        fallbackCmd: String = "",
        timeoutMs: Long = COMMAND_TIMEOUT_MS
    ): CommandOutcome = withContext(Dispatchers.IO) {
        if (!isShizukuAvailableAndGranted()) {
            return@withContext CommandOutcome.BOTH_FAILED
        }

        var primarySucceeded = false
        try {
            primarySucceeded = runSingleCommand(primaryCmd, timeoutMs)
        } catch (e: Exception) {
            primarySucceeded = false
        }

        if (primarySucceeded) {
            return@withContext CommandOutcome.PRIMARY_SUCCESS
        }

        var fallbackSucceeded = false
        if (fallbackCmd.isNotEmpty()) {
            try {
                fallbackSucceeded = runSingleCommand(fallbackCmd, timeoutMs)
            } catch (e: Exception) {
                fallbackSucceeded = false
            }
        }

        return@withContext if (fallbackSucceeded) CommandOutcome.FALLBACK_SUCCESS else CommandOutcome.BOTH_FAILED
    }

    suspend fun executeCommand(
        context: Context,
        primaryCmd: String,
        fallbackCmd: String = "",
        timeoutMs: Long = COMMAND_TIMEOUT_MS
    ): Boolean = executeCommandDetailed(context, primaryCmd, fallbackCmd, timeoutMs) != CommandOutcome.BOTH_FAILED

    private suspend fun runSingleCommand(cmd: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        return@withContext try {
            process = newShizukuProcess(arrayOf("sh", "-c", cmd), null, null)
            val exitCode = withTimeoutOrNull(timeoutMs) {
                runInterruptible {
                    process.waitFor()
                }
            }
            if (exitCode == null) {
                process.destroy()
                false
            } else {
                exitCode == 0
            }
        } catch (e: Exception) {
            process?.destroy()
            false
        }
    }

    suspend fun setPowerMode(context: Context, enablePerformance: Boolean): CommandOutcome {
        val primaryCmd = if (enablePerformance) "cmd power set-mode 2" else "cmd power set-mode 0"
        val fallbackCmd = if (enablePerformance) {
            "cmd power set-fixed-performance-mode-enabled true"
        } else {
            "cmd power set-fixed-performance-mode-enabled false"
        }
        val outcome = executeCommandDetailed(context, primaryCmd, fallbackCmd)
        if (outcome != CommandOutcome.BOTH_FAILED) {
            getPrefs(context).edit().putBoolean("IS_PERFORMANCE_ACTIVE", enablePerformance).apply()
        }
        return outcome
    }


    suspend fun reduceCpuLoad(context: Context, packageName: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd activity set-inactive $packageName true"
        val fallback = "cmd appops set $packageName RUN_IN_BACKGROUND deny"
        return executeCommandDetailed(context, primary, fallback)
    }

    suspend fun reduceGpuLoad(context: Context, packageName: String, ratio: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd game downscale $packageName $ratio"
        return executeCommandDetailed(context, primary)
    }

    suspend fun restoreGpu(context: Context, packageName: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd game downscale reset $packageName"
        val fallback = "cmd game downscale $packageName 1.0"
        return executeCommandDetailed(context, primary, fallback)
    }


    // --- Game Compile (ART AOT) ---
    suspend fun compileGame(context: Context, packageName: String, mode: CompileMode): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd package compile -m ${mode.artFilter} -f $packageName"
        val fallback = "pm compile -m ${mode.artFilter} -f $packageName"
        return executeCommandDetailed(context, primary, fallback, timeoutMs = COMPILE_TIMEOUT_MS)
    }

    // ------------------------------------------------------------------
    // GAMING SIDEBAR COMMANDS (Shizuku, exit-code checked)
    // ------------------------------------------------------------------

    // Smart Game Mode: 1 = balanced, 2 = performance, 3 = battery saver
    suspend fun setGameMode(context: Context, packageName: String, mode: Int): CommandOutcome {
        if (packageName.isBlank()) return CommandOutcome.BOTH_FAILED
        val primary = "cmd game mode set $mode $packageName"
        val fallback = "cmd game mode set --mode $mode --package $packageName"
        return executeCommandDetailed(context, primary, fallback)
    }

    suspend fun deleteGameMode(context: Context, packageName: String): CommandOutcome {
        if (packageName.isBlank()) return CommandOutcome.BOTH_FAILED
        val primary = "cmd game mode delete $packageName"
        val fallback = "cmd game reset $packageName"
        return executeCommandDetailed(context, primary, fallback)
    }

    // Performance Lock: pins the SoC clocks so they do not drop under heat
    suspend fun setFixedPerformanceMode(context: Context, enabled: Boolean): CommandOutcome {
        val value = if (enabled) "true" else "false"
        val primary = "cmd power set-fixed-performance-mode-enabled $value"
        val fallback = if (enabled) "cmd power set-mode 2" else "cmd power set-mode 0"
        return executeCommandDetailed(context, primary, fallback)
    }

    // Custom App Compact: squeezes the memory footprint of a background app
    suspend fun compactApp(context: Context, packageName: String): CommandOutcome {
        if (packageName.isBlank()) return CommandOutcome.BOTH_FAILED
        val primary = "cmd activity compact full $packageName"
        val fallback = "cmd activity compact $packageName"
        return executeCommandDetailed(context, primary, fallback)
    }

    // ------------------------------------------------------------------
    // NATIVE OS FEATURES (no Success/Error status shown in the sidebar)
    // ------------------------------------------------------------------

    // Native Do Not Disturb via NotificationManager. Returns the new state.
    fun toggleDoNotDisturb(context: Context, currentlyOn: Boolean): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(
                    context.applicationContext,
                    "Allow Do Not Disturb access for this app",
                    Toast.LENGTH_LONG
                ).show()
                return currentlyOn
            }
            val target = if (currentlyOn) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                NotificationManager.INTERRUPTION_FILTER_NONE
            }
            nm.setInterruptionFilter(target)
            !currentlyOn
        } catch (e: Exception) {
            Toast.makeText(context.applicationContext, "Do Not Disturb is not supported", Toast.LENGTH_SHORT).show()
            currentlyOn
        }
    }

    // ------------------------------------------------------------------
    // APP LISTS FOR THE SIDEBAR PICKERS
    // ------------------------------------------------------------------

    // Games only: Android game metadata + launchability, excluding system apps and Turbo Space.
    fun isGamePackage(context: Context, packageName: String): Boolean {
        if (packageName.isBlank() || packageName == context.packageName || packageName == "NO TARGET SELECTED") return false
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val categorized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                info.category == ApplicationInfo.CATEGORY_GAME
            } else false
            @Suppress("DEPRECATION")
            val legacy = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            !isSystem && (categorized || legacy) && pm.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) { false }
    }

    fun getGameApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { isGamePackage(context, it.packageName) }
                .sortedBy { pm.getApplicationLabel(it).toString() }
        } catch (_: Exception) { emptyList() }
    }

    // User-installed (third-party) apps only - system apps and Turbo Space
    // itself are blocked 100%. Reused by every app picker that must not
    // target this booster.
    fun getThirdPartyApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { info ->
                    (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
                        info.packageName != context.packageName
                }
                .sortedBy { pm.getApplicationLabel(it).toString() }
        } catch (e: Exception) {
            emptyList()
        }
    }


}

// ============================================================================
// 6. UNIVERSAL OVERLAY SERVICE (ANDROID 8 TO 16)
// ============================================================================
class GameSpaceOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var overlayParams: WindowManager.LayoutParams
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)

        startForegroundServiceWithNotification()

        TurboSpaceRepository.setServiceState(true)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
            setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
            setContent {
                GameSpaceInGameSidebar(
                    onDragOverlay = { dx, dy -> moveOverlayBy(dx, dy) }
                )
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val density = resources.displayMetrics.density
            y = (resources.displayMetrics.heightPixels / 2f - (43f * density)).toInt().coerceAtLeast(0)
        }

        try {
            windowManager.addView(composeView, overlayParams)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to display overlay", Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveOverlayBy(dx: Float, dy: Float) {
        if (!::composeView.isInitialized || !::overlayParams.isInitialized) return

        val displayMetrics = resources.displayMetrics
        val maxX = (displayMetrics.widthPixels - composeView.width).coerceAtLeast(0)
        val maxY = (displayMetrics.heightPixels - composeView.height).coerceAtLeast(0)

        overlayParams.x = (overlayParams.x + dx.toInt()).coerceIn(0, maxX)
        overlayParams.y = (overlayParams.y + dy.toInt()).coerceIn(0, maxY)

        try {
            windowManager.updateViewLayout(composeView, overlayParams)
        } catch (_: Exception) {
            // Ignore updates while the overlay window is being removed.
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "turbo_overlay_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Turbo Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Turbo Engine Active")
            .setContentText("Hardware Overlay is running in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TurboSpaceRepository.setServiceState(false)
        if (::composeView.isInitialized) {
            try {
                windowManager.removeView(composeView)
            } catch (e: Exception) {
                // Ignore
            }
        }
        _viewModelStore.clear()
    }
}

// ============================================================================
// 7. GAMING LAUNCHER UI
// ============================================================================
@Composable
fun TurboSpaceLandscapeHub() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("home") }
    var gamePicker by remember { mutableStateOf(false) }
    var backgroundPicker by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }

    val selectedGame by TurboSpaceRepository.selectedGame.collectAsState()
    val selectedFeatures by TurboSpaceRepository.selectedFeatures.collectAsState()
    val background by TurboSpaceRepository.background.collectAsState()
    val sessionActive by TurboSpaceRepository.sessionActive.collectAsState()
    val sessionBusy by TurboSpaceRepository.sessionBusy.collectAsState()
    val cpuApps by TurboSpaceRepository.selectedCpuApps.collectAsState()
    val gpuGame by TurboSpaceRepository.selectedGpuGame.collectAsState()
    val cpuActive by TurboSpaceRepository.isCpuOptActive.collectAsState()
    val gpuActive by TurboSpaceRepository.isGpuOptActive.collectAsState()
    val cpuLoading by TurboSpaceRepository.isCpuLoading.collectAsState()
    val gpuLoading by TurboSpaceRepository.isGpuLoading.collectAsState()
    var shizukuReady by remember { mutableStateOf(TurboSpaceManager.isShizukuAvailableAndGranted()) }

    LaunchedEffect(Unit) { TurboSpaceRepository.restoreFromPrefs(context) }
    DisposableEffect(Unit) {
        val listener = TurboSpaceManager.addPermissionResultListener { shizukuReady = it }
        onDispose { TurboSpaceManager.removePermissionResultListener(listener) }
    }

    fun launchGameAndBar() {
        val intent = context.packageManager.getLaunchIntentForPackage(selectedGame)
        if (intent == null) {
            Toast.makeText(context, "Could not launch the selected game", Toast.LENGTH_SHORT).show()
            return
        }
        ContextCompat.startForegroundService(context, Intent(context, GameSpaceOverlayService::class.java))
        context.startActivity(intent)
    }

    val overlayPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingStart && Settings.canDrawOverlays(context)) launchGameAndBar()
        pendingStart = false
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            TurboSpaceRepository.setBackground(context, BackgroundChoice(BackgroundType.IMAGE, it.toString()))
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            TurboSpaceRepository.setBackground(context, BackgroundChoice(BackgroundType.VIDEO, it.toString()))
        }
    }

    Box(Modifier.fillMaxSize()) {
        LauncherBackground(background)
        Box(Modifier.fillMaxSize().background(TurboColors.DarkBackground.copy(alpha = 0.72f)))
        if (page == "settings") {
            PerformanceSettingsScreen(
                selectedFeatures = selectedFeatures,
                cpuApps = cpuApps,
                gpuGame = gpuGame,
                cpuActive = cpuActive,
                gpuActive = gpuActive,
                cpuLoading = cpuLoading,
                gpuLoading = gpuLoading,
                enabled = shizukuReady,
                onBack = { page = "home" },
                onFeaturesChanged = { TurboSpaceRepository.setSelectedFeatures(context, it) },
                onCpuAppsChanged = { TurboSpaceRepository.setSelectedCpuApps(context, it) },
                onGpuGameChanged = { TurboSpaceRepository.setSelectedGpuGame(context, it) },
                onStartCpu = { scope.launch { TurboSpaceRepository.startCpuOpt(context) } },
                onStartGpu = { scope.launch { TurboSpaceRepository.startGpuOpt(context) } }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    LauncherButton(Icons.Filled.Settings, "Settings") { page = "settings" }
                    Spacer(Modifier.width(12.dp))
                    LauncherButton(Icons.Filled.Add, "Add Background") { backgroundPicker = true }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(0.34f), horizontalAlignment = Alignment.Start) {
                        Text("TURBO SPACE", color = TurboColors.BrightGlowRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        LauncherButton(Icons.Filled.SportsEsports, "Games") { gamePicker = true }
                    }
                    Column(Modifier.weight(0.66f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(104.dp).clip(RoundedCornerShape(16.dp))
                                .background(TurboColors.CardBackground.copy(alpha = 0.9f))
                                .border(1.dp, TurboColors.BrightGlowRed, RoundedCornerShape(16.dp))
                                .clickable { gamePicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedGame == "NO TARGET SELECTED") Icon(Icons.Filled.Add, "Select game", tint = TurboColors.TextGray, modifier = Modifier.size(42.dp))
                            else AppIconImage(selectedGame, Modifier.fillMaxSize().padding(6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (selectedGame == "NO TARGET SELECTED") "Select a game" else rememberAppLabel(selectedGame), color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(18.dp))
                        Row {
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (TurboSpaceRepository.startSession(context)) {
                                            if (Settings.canDrawOverlays(context)) launchGameAndBar()
                                            else {
                                                pendingStart = true
                                                overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                            }
                                        }
                                    }
                                },
                                enabled = shizukuReady && !sessionBusy && !sessionActive && selectedGame != "NO TARGET SELECTED"
                            ) { Icon(Icons.Filled.RocketLaunch, null); Spacer(Modifier.width(7.dp)); Text(if (sessionBusy) "STARTING…" else "START") }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { scope.launch { TurboSpaceRepository.resetSession(context) } },
                                enabled = sessionActive && !sessionBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = TurboColors.BrightRed)
                            ) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(7.dp)); Text("RESET") }
                        }
                        if (!shizukuReady) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { TurboSpaceManager.requestShizukuPermission() }) { Text("Grant Shizuku Permission") }
                        }
                    }
                }
            }
        }
    }

    if (gamePicker) GamePickerDialog({ gamePicker = false }) {
        TurboSpaceRepository.setSelectedGame(context, it); gamePicker = false
    }
    if (backgroundPicker) BackgroundPickerDialog(
        onDismiss = { backgroundPicker = false },
        onImage = { backgroundPicker = false; imagePicker.launch(arrayOf("image/*")) },
        onVideo = { backgroundPicker = false; videoPicker.launch(arrayOf("video/*")) },
        onStatic = { backgroundPicker = false; TurboSpaceRepository.setBackground(context, BackgroundChoice(BackgroundType.STATIC)) },
        onAnimated = { backgroundPicker = false; TurboSpaceRepository.setBackground(context, BackgroundChoice(BackgroundType.ANIMATED)) }
    )
}

@Composable
private fun LauncherBackground(choice: BackgroundChoice) {
    val context = LocalContext.current
    when (choice.type) {
        BackgroundType.IMAGE -> AsyncImage(Uri.parse(choice.uri), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        BackgroundType.VIDEO -> AndroidView(
            factory = { ctx -> VideoView(ctx).apply { setVideoURI(Uri.parse(choice.uri)); setOnPreparedListener { it.isLooping = true; start() } } },
            modifier = Modifier.fillMaxSize()
        )
        BackgroundType.STATIC -> Box(Modifier.fillMaxSize().background(TurboColors.CardBackground))
        BackgroundType.ANIMATED -> {
            val transition = rememberInfiniteTransition(label = "background")
            val alpha by transition.animateFloat(0.2f, 0.75f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "backgroundAlpha")
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(TurboColors.DarkBackground, TurboColors.BrightRed.copy(alpha = alpha), TurboColors.DarkBackground))))
        }
        BackgroundType.DEFAULT -> {
            val bgResId = remember { context.resources.getIdentifier("bg_main", "raw", context.packageName) }
            if (bgResId != 0) AsyncImage(Uri.parse("android.resource://${context.packageName}/$bgResId"), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(TurboColors.DarkBackground))
        }
    }
}

@Composable
private fun LauncherButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Icon(icon, null); Spacer(Modifier.width(7.dp)); Text(label) }
}

@Composable
private fun BackgroundPickerDialog(onDismiss: () -> Unit, onImage: () -> Unit, onVideo: () -> Unit, onStatic: () -> Unit, onAnimated: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Background") }, text = {
        Column {
            listOf("Image" to onImage, "Video" to onVideo, "Static background" to onStatic, "Animated background" to onAnimated).forEach { (name, action) ->
                TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(name) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PerformanceSettingsScreen(
    selectedFeatures: Set<PerformanceFeature>, cpuApps: Set<String>, gpuGame: String,
    cpuActive: Boolean, gpuActive: Boolean, cpuLoading: Boolean, gpuLoading: Boolean,
    enabled: Boolean, onBack: () -> Unit, onFeaturesChanged: (Set<PerformanceFeature>) -> Unit,
    onCpuAppsChanged: (Set<String>) -> Unit, onGpuGameChanged: (String) -> Unit,
    onStartCpu: () -> Unit, onStartGpu: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Back") }
            Text("Performance Features", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item { ExistingOptimizationRow("GPU Optimization", gpuGame != "NO TARGET SELECTED", gpuActive, gpuLoading, enabled, { onGpuGameChanged(it) }, onStartGpu, true) }
            item { CpuOptimizationRow(cpuApps, cpuActive, cpuLoading, enabled, onCpuAppsChanged, onStartCpu) }
            items(PerformanceFeature.values().toList()) { feature ->
                val checked = feature in selectedFeatures
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(TurboColors.CardBackground.copy(alpha = 0.9f))
                        .clickable { onFeaturesChanged(if (checked) selectedFeatures - feature else selectedFeatures + feature) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(featureIcon(feature), null, tint = TurboColors.BrightGlowRed)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(feature.label, color = Color.White, fontWeight = FontWeight.SemiBold); Text(if (feature.stateful) "Stateful" else "Action", color = TurboColors.TextGray, fontSize = 11.sp) }
                    Switch(checked = checked, onCheckedChange = { onFeaturesChanged(if (it) selectedFeatures + feature else selectedFeatures - feature) })
                }
            }
        }
    }
}

@Composable
private fun ExistingOptimizationRow(title: String, selected: Boolean, active: Boolean, loading: Boolean, enabled: Boolean, onSelected: (String) -> Unit, onStart: () -> Unit, gamesOnly: Boolean) {
    var picker by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(TurboColors.CardBackground).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        TextButton(onClick = { picker = true }) { Text(if (selected) "Change Game" else "Select Game") }
        Button(onClick = onStart, enabled = enabled && selected && !loading) { Text(if (loading) "Working…" else if (active) "Active" else "Apply") }
    }
    if (picker) GamePickerDialog({ picker = false }) { onSelected(it); picker = false }
}

@Composable
private fun CpuOptimizationRow(selected: Set<String>, active: Boolean, loading: Boolean, enabled: Boolean, onSelected: (Set<String>) -> Unit, onStart: () -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(TurboColors.CardBackground).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("CPU Optimization", color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        TextButton(onClick = { picker = true }) { Text(if (selected.isEmpty()) "Select Apps" else "${selected.size} Apps") }
        Button(onClick = onStart, enabled = enabled && selected.isNotEmpty() && !loading) { Text(if (loading) "Working…" else if (active) "Active" else "Apply") }
    }
    if (picker) MultiAppPickerDialog(selected, { picker = false }) { onSelected(it); picker = false }
}

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    val bitmap = remember(packageName) { runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap() }.getOrNull() }
    if (bitmap != null) Image(bitmap, null, modifier.clip(RoundedCornerShape(8.dp)))
    else Icon(Icons.Filled.SportsEsports, null, tint = TurboColors.TextGray, modifier = modifier)
}

@Composable
fun rememberAppLabel(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) { runCatching { val pm = context.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName) }
}

@Composable
private fun GamePickerDialog(onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    val games = remember { TurboSpaceManager.getGameApps(context) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Games") }, text = {
        if (games.isEmpty()) Text("No games recognized by Android were found.")
        else LazyColumn(Modifier.heightIn(max = 360.dp)) { items(games) { app ->
            Row(Modifier.fillMaxWidth().clickable { onSelected(app.packageName) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(app.packageName, Modifier.size(36.dp)); Spacer(Modifier.width(10.dp)); Text(context.packageManager.getApplicationLabel(app).toString())
            }
        } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun MultiAppPickerDialog(currentSelected: Set<String>, onDismiss: () -> Unit, onAppsSelected: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val apps = remember { TurboSpaceManager.getThirdPartyApps(context) }
    var selected by remember(currentSelected) { mutableStateOf(currentSelected) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select Apps") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) { items(apps) { app ->
            val checked = app.packageName in selected
            Row(Modifier.fillMaxWidth().clickable { selected = if (checked) selected - app.packageName else selected + app.packageName }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, { selected = if (it) selected + app.packageName else selected - app.packageName }); Spacer(Modifier.width(8.dp)); Text(context.packageManager.getApplicationLabel(app).toString())
            }
        } }
    }, confirmButton = { TextButton(onClick = { onAppsSelected(selected) }) { Text("Confirm") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun featureIcon(feature: PerformanceFeature): ImageVector = when (feature) {
    PerformanceFeature.GAME_PERFORMANCE -> Icons.Filled.SportsEsports
    PerformanceFeature.THERMAL_CONTROL -> Icons.Filled.LocalFireDepartment
    PerformanceFeature.PROCESS_CONTROL -> Icons.Filled.Memory
    PerformanceFeature.IDLE_CONTROL -> Icons.Filled.AcUnit
    PerformanceFeature.RESOURCE_CLEANUP -> Icons.Filled.Refresh
    PerformanceFeature.GFX_BOOST -> Icons.Filled.Bolt
    PerformanceFeature.APP_OPTIMIZATION -> Icons.Filled.Shield
    PerformanceFeature.INPUT_CONFIGURATION -> Icons.Filled.Settings
}

// ============================================================================
// 8. COMPACT IN-GAME GAME BAR
// ============================================================================
@Composable
fun GameSpaceInGameSidebar(onDragOverlay: (dx: Float, dy: Float) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val running by TurboSpaceRepository.isServiceRunning.collectAsState()
    val selected by TurboSpaceRepository.selectedFeatures.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val monitor = remember { SystemMonitorEngine(context) }
    var fps by remember { mutableStateOf(60) }
    var cpu by remember { mutableStateOf("0%") }
    var ram by remember { mutableStateOf("0 MB") }

    DisposableEffect(running) { if (running) monitor.startFpsMonitoring(); onDispose { monitor.stopFpsMonitoring() } }
    LaunchedEffect(running) { while (running) { monitor.updateHardwareMetrics(); fps = monitor.currentFps.value; cpu = monitor.currentCpuUsage.value; ram = monitor.currentRamUsage.value; delay(1000) } }

    AnimatedVisibility(running) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDragOverlay(drag.x, drag.y) } }
                .clip(RoundedCornerShape(8.dp)).background(TurboColors.DarkBackground.copy(alpha = 0.9f)).border(1.dp, TurboColors.BrightGlowRed, RoundedCornerShape(8.dp)).clickable { expanded = !expanded }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SportsEsports, "Game Bar", tint = TurboColors.BrightGlowRed)
            }
            AnimatedVisibility(expanded, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                Column(Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(8.dp)).background(TurboColors.DarkBackground.copy(alpha = 0.92f)).border(1.dp, TurboColors.BrightGlowRed, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TelemetryCell("FPS", "$fps", Modifier.weight(1f)); TelemetryCell("CPU", cpu, Modifier.weight(1f)); TelemetryCell("RAM", ram.substringBefore(" /"), Modifier.weight(1f))
                        TextButton(onClick = { context.stopService(Intent(context, GameSpaceOverlayService::class.java)) }) {
                            Icon(Icons.Filled.Close, null, tint = TurboColors.BrightRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("CLOSE", color = TurboColors.BrightRed, fontSize = 10.sp)
                        }
                    }
                    if (selected.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            selected.sortedBy { it.ordinal }.forEach { feature ->
                                OutlinedButton(onClick = { scope.launch { TurboSpaceRepository.runOverlayFeature(context, feature) } }) {
                                    Icon(featureIcon(feature), null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(feature.label, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(6.dp)).background(TurboColors.CardBackground).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TurboColors.TextGray, fontSize = 9.sp); Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
