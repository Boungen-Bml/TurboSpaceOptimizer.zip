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
import android.view.Gravity
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

// ============================================================================
// 1. MAIN ENTRY POINT
// ============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurboSpaceApp() }
    }
}

// ============================================================================
// 2. THEME
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

    val HudObsidian = Color(0xFF0B0C10)
    val HudCyan = Color(0xFF00F0FF)
    val HudPurple = Color(0xFFA020F0)
    val HudCrimson = Color(0xFFFF003C)
    val HudGreen = Color(0xFF39FF6A)
}

// ============================================================================
// 3. PERFORMANCE FEATURE DEFINITIONS
// ============================================================================
enum class PerformanceFeature(
    val id: String,
    val icon: String,
    val title: String,
    val stateful: Boolean,
    val description: String,
    val command: String,
    val resetCommand: String?
) {
    GAME_PERFORMANCE(
        "game_performance", "🎮", "Game Performance", true,
        "Sets the selected game to Android Performance Game Mode during the Game Session.",
        "cmd game mode performance <package_name>",
        "Restore the game's previous Game Mode"
    ),
    THERMAL_CONTROL(
        "thermal_control", "🔥", "Thermal Control", true,
        "Applies a thermal-service override for the Game Session.",
        "cmd thermalservice override-status 0",
        "cmd thermalservice reset (then restore the detected original override)"
    ),
    PROCESS_CONTROL(
        "process_control", "📱", "Process Control", true,
        "Limits the system process count to 2 during the Game Session.",
        "cmd activity set-process-limit 2",
        "cmd activity set-process-limit default (or the detected original limit)"
    ),
    IDLE_CONTROL(
        "idle_control", "🌙", "Idle Control", true,
        "Forces light idle mode during the Game Session.",
        "cmd deviceidle force-idle light",
        "cmd deviceidle unforce (or restore the detected original idle state)"
    ),
    RESOURCE_CLEANUP(
        "resource_cleanup", "🧹", "Resource Cleanup", false,
        "Purges process resources once to reclaim runtime resources.",
        "cmd activity purge-process-resources",
        null
    ),
    APP_OPTIMIZATION(
        "app_optimization", "🧠", "App Optimization", false,
        "Runs Android background dex optimization as a one-time action.",
        "cmd package bg-dexopt-job",
        null
    ),
    INPUT_CONFIGURATION(
        "input_configuration", "⚙️", "Input Configuration", false,
        "Reloads the InputFlinger configuration as a one-time action.",
        "cmd inputflinger reload-config",
        null
    ),
    TRIM_CACHE(
        "trim_cache", "🗑️", "Trim Cache", false,
        "Requests Android to trim cached storage aggressively as an action.",
        "pm trim-caches 999G",
        null
    ),
    COMPILE_OPTIMIZATION(
        "compile_optimization", "⚡", "Compile Optimization", false,
        "Compiles the selected game with one of three ART compilation profiles.",
        "cmd package compile -m <space|speed-profile|speed> -f <package_name>",
        "cmd package compile --reset <package_name>"
    )
}

private fun Set<String>.toPerformanceFeatures(): Set<PerformanceFeature> = mapNotNull { id ->
    PerformanceFeature.values().firstOrNull { it.id == id }
}.toSet()

private fun Set<PerformanceFeature>.toIds(): Set<String> = map { it.id }.toSet()

enum class CompileMode(val key: String, val title: String, val description: String, val artFilter: String) {
    ECO_SPEED("eco_speed", "Eco-Speed Mode", "Space-optimized compilation profile.", "space"),
    SMART_ADAPTIVE("smart_adaptive", "Smart-Adaptive Mode", "Profile-guided speed optimization.", "speed-profile"),
    MAX_PERFORMANCE("max_performance", "Max-Performance Mode", "Maximum speed-oriented compilation profile.", "speed")
}

// ============================================================================
// 4. SESSION SNAPSHOT
// ============================================================================
data class GameSessionSnapshot(
    val gamePackage: String,
    val previousGameMode: String?,
    val previousThermalOverride: Int?,
    val previousProcessLimit: String?,
    val previousIdleForced: Boolean?
)

data class RestoreResult(
    val success: Boolean,
    val warnings: List<String> = emptyList(),
    val failedFeatures: List<String> = emptyList()
)

// ============================================================================
// 5. REPOSITORY & APP STATE
// ============================================================================
object TurboSpaceRepository {
    private val _selectedGame = MutableStateFlow("NO TARGET SELECTED")
    val selectedGame: StateFlow<String> = _selectedGame.asStateFlow()

    private val _selectedFeatures = MutableStateFlow<Set<PerformanceFeature>>(emptySet())
    val selectedFeatures: StateFlow<Set<PerformanceFeature>> = _selectedFeatures.asStateFlow()

    private val _selectedCpuApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedCpuApps: StateFlow<Set<String>> = _selectedCpuApps.asStateFlow()

    private val _selectedGpuGame = MutableStateFlow("NO TARGET SELECTED")
    val selectedGpuGame: StateFlow<String> = _selectedGpuGame.asStateFlow()

    private val _selectedCompileMode = MutableStateFlow(CompileMode.SMART_ADAPTIVE)
    val selectedCompileMode: StateFlow<CompileMode> = _selectedCompileMode.asStateFlow()

    private val _isCpuActive = MutableStateFlow(false)
    val isCpuActive: StateFlow<Boolean> = _isCpuActive.asStateFlow()

    private val _isGpuActive = MutableStateFlow(false)
    val isGpuActive: StateFlow<Boolean> = _isGpuActive.asStateFlow()

    private val _isCpuLoading = MutableStateFlow(false)
    val isCpuLoading: StateFlow<Boolean> = _isCpuLoading.asStateFlow()

    private val _isGpuLoading = MutableStateFlow(false)
    val isGpuLoading: StateFlow<Boolean> = _isGpuLoading.asStateFlow()

    private val _isSessionStarting = MutableStateFlow(false)
    val isSessionStarting: StateFlow<Boolean> = _isSessionStarting.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isResetting = MutableStateFlow(false)
    val isResetting: StateFlow<Boolean> = _isResetting.asStateFlow()

    // Every Settings switch is backed by a real command execution. While the
    // command is running, its switch is replaced by a spinner and cannot lie.
    private val _busyFeatures = MutableStateFlow<Set<PerformanceFeature>>(emptySet())
    val busyFeatures: StateFlow<Set<PerformanceFeature>> = _busyFeatures.asStateFlow()

    // START waits for the overlay service to report that its window is really
    // attached before launching the game, eliminating the race that could hide
    // the Game Bar after returning from Android's overlay permission screen.
    private val _overlayReady = MutableStateFlow(false)
    val overlayReady: StateFlow<Boolean> = _overlayReady.asStateFlow()

    fun setOverlayReady(ready: Boolean) {
        _overlayReady.value = ready
    }

    fun restoreFromPrefs(context: Context) {
        val ownPackage = context.packageName
        val saved = TurboSpaceManager.loadAppState(context)

        _selectedGame.value = if (saved.selectedGame == ownPackage) {
            "NO TARGET SELECTED"
        } else {
            saved.selectedGame
        }

        _selectedFeatures.value = saved.selectedFeatureIds.toPerformanceFeatures()

        _selectedCpuApps.value = saved.cpuApps
            .filterNot { it == ownPackage }
            .toSet()

        _selectedGpuGame.value = if (saved.gpuGame == ownPackage) {
            "NO TARGET SELECTED"
        } else {
            saved.gpuGame
        }
        _selectedCompileMode.value = saved.compileMode

        _isCpuActive.value = saved.cpuActive
        _isGpuActive.value = saved.gpuActive
        _isSessionActive.value = TurboSpaceManager.hasSavedSession(context)
    }

    fun setSelectedGame(context: Context, packageName: String) {
        if (!TurboSpaceManager.isRecognizedGame(context, packageName)) {
            Toast.makeText(context, "Only recognized games can be selected", Toast.LENGTH_SHORT).show()
            return
        }
        val current = _selectedGame.value
        if (TurboSpaceManager.hasSavedSession(context) && current != packageName) {
            TurboSpaceManager.showToast(context, "Reset the current Game Session before changing the game")
            return
        }
        _selectedGame.value = packageName
        persist(context)
    }

    fun setFeatureSelected(context: Context, feature: PerformanceFeature, selected: Boolean) {
        _selectedFeatures.value = if (selected) {
            _selectedFeatures.value + feature
        } else {
            _selectedFeatures.value - feature
        }
        persist(context)
    }

    suspend fun setFeatureEnabledVerified(context: Context, feature: PerformanceFeature, enabled: Boolean): Boolean {
        if (feature == PerformanceFeature.APP_OPTIMIZATION || feature == PerformanceFeature.COMPILE_OPTIMIZATION) {
            return false
        }

        if (!enabled) {
            // Action features have no OS undo command, so turning the switch off
            // only removes them from the saved feature list. RESET ALL remains the
            // only place where a true reset is attempted.
            if (!feature.stateful) {
                setFeatureSelected(context, feature, false)
                return true
            }

            _busyFeatures.value = _busyFeatures.value + feature
            return try {
                val ok = TurboSpaceManager.restoreSingleStatefulFeature(context, feature)
                setFeatureSelected(context, feature, false)
                TurboSpaceManager.showToast(
                    context,
                    if (ok) "SUCCESS — ${feature.title}" else "ERROR — ${feature.title}"
                )
                ok
            } finally {
                _busyFeatures.value = _busyFeatures.value - feature
            }
        }

        if (feature.stateful && !TurboSpaceManager.isRecognizedGame(context, _selectedGame.value)) {
            TurboSpaceManager.showToast(context, "ERROR — Select a recognized game first")
            return false
        }

        _busyFeatures.value = _busyFeatures.value + feature
        return try {
            if (feature.stateful) {
                TurboSpaceManager.captureAndSaveSessionSnapshot(context, _selectedGame.value, setOf(feature))
            }

            val ok = if (feature.stateful) {
                TurboSpaceManager.applySelectedSessionFeatures(context, _selectedGame.value, setOf(feature)).contains(feature)
            } else {
                TurboSpaceManager.runActionFeature(context, feature, _selectedGame.value)
            }

            if (ok) {
                setFeatureSelected(context, feature, true)
                TurboSpaceManager.showToast(context, "SUCCESS — ${feature.title}")
            } else {
                setFeatureSelected(context, feature, false)
                TurboSpaceManager.showToast(context, "ERROR — ${feature.title}")
            }
            ok
        } finally {
            _busyFeatures.value = _busyFeatures.value - feature
        }
    }

    fun setSelectedCpuApps(context: Context, apps: Set<String>) {
        val allowed = TurboSpaceManager.getThirdPartyApps(context)
            .asSequence()
            .map { it.packageName }
            .toSet()
        _selectedCpuApps.value = apps.filter { it in allowed }.toSet()
        persist(context)
    }

    fun setSelectedCompileMode(context: Context, mode: CompileMode) {
        _selectedCompileMode.value = mode
        persist(context)
    }

    fun setSelectedGpuGame(context: Context, packageName: String) {
        if (!TurboSpaceManager.isRecognizedGame(context, packageName)) {
            Toast.makeText(context, "GPU Optimization only accepts games", Toast.LENGTH_SHORT).show()
            return
        }
        _selectedGpuGame.value = packageName
        persist(context)
    }

    suspend fun startCpuOpt(context: Context) {
        _isCpuLoading.value = true
        try {
            val apps = _selectedCpuApps.value
            if (apps.isEmpty()) {
                TurboSpaceManager.showToast(context, "Select at least one app for CPU Optimization")
                return
            }
            val outcomes = apps.map { pkg -> TurboSpaceManager.reduceCpuLoad(context, pkg) }
            val ok = outcomes.none { it == TurboSpaceManager.CommandOutcome.BOTH_FAILED }
            _isCpuActive.value = ok
            TurboSpaceManager.showToast(context, if (ok) "CPU Optimization applied" else "CPU Optimization failed")
            persist(context)
        } finally {
            _isCpuLoading.value = false
        }
    }

    suspend fun startGpuOpt(context: Context) {
        _isGpuLoading.value = true
        try {
            val pkg = _selectedGpuGame.value
            if (!TurboSpaceManager.isRecognizedGame(context, pkg)) {
                TurboSpaceManager.showToast(context, "Select a game for GPU Optimization")
                return
            }
            val outcome = TurboSpaceManager.reduceGpuLoad(context, pkg, "0.9")
            val ok = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
            _isGpuActive.value = ok
            TurboSpaceManager.showToast(context, if (ok) "GPU Optimization applied" else "GPU Optimization failed")
            persist(context)
        } finally {
            _isGpuLoading.value = false
        }
    }

    suspend fun resetCpuOpt(context: Context) {
        val outcomes = _selectedCpuApps.value.map { TurboSpaceManager.restoreCpuLoad(context, it) }
        val ok = outcomes.isEmpty() || outcomes.none { it == TurboSpaceManager.CommandOutcome.BOTH_FAILED }
        _isCpuActive.value = false
        TurboSpaceManager.showToast(context, if (ok) "CPU Optimization reset" else "CPU Optimization reset completed with errors")
        persist(context)
    }

    suspend fun resetGpuOpt(context: Context) {
        val pkg = _selectedGpuGame.value
        if (!TurboSpaceManager.isRecognizedGame(context, pkg)) {
            TurboSpaceManager.showToast(context, "No recognized game selected for GPU reset")
            return
        }
        val outcome = TurboSpaceManager.restoreGpu(context, pkg)
        val ok = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
        _isGpuActive.value = false
        TurboSpaceManager.showToast(context, if (ok) "GPU Optimization reset" else "GPU Optimization reset failed")
        persist(context)
    }

    suspend fun startGameSession(context: Context): Boolean {
        if (_selectedGame.value == "NO TARGET SELECTED" ||
            !TurboSpaceManager.isRecognizedGame(context, _selectedGame.value)
        ) {
            TurboSpaceManager.showToast(context, "Select a supported game first")
            return false
        }

        if (_isSessionActive.value) {
            TurboSpaceManager.showToast(context, "A Game Session is already active; press RESET first")
            return false
        }

        _isSessionStarting.value = true
        try {
            val game = _selectedGame.value
            val statefulFeatures = _selectedFeatures.value.filter { it.stateful }.toSet()

            // Settings switches are already verified against the real Android
            // command. START must not blindly rerun one-shot actions (for example
            // dexopt/trim/compile) just because their switches are on. If this is a
            // fresh stateful session, create/restore the real snapshot and apply only
            // the stateful commands.
            val alreadySaved = TurboSpaceManager.hasSavedSession(context)
            if (!alreadySaved && statefulFeatures.isNotEmpty()) {
                TurboSpaceManager.captureAndSaveSessionSnapshot(context, game, statefulFeatures)
                val successful = TurboSpaceManager.applySelectedSessionFeatures(context, game, statefulFeatures)
                val failed = statefulFeatures - successful
                if (failed.isNotEmpty()) {
                    // Restore the snapshot we just captured so prefs are not left in a half-applied
                    // state. This prevents KEY_SESSION_ACTIVE from staying true when no real session
                    // was started, which would block every subsequent START attempt.
                    TurboSpaceManager.restoreSavedSession(context)
                    _selectedFeatures.value = _selectedFeatures.value - failed
                    persist(context)
                    failed.forEach { feature ->
                        TurboSpaceManager.showToast(context, "ERROR — ${feature.title}")
                    }
                    return false
                }
            }

            _isSessionActive.value = true
            TurboSpaceManager.showToast(context, "SUCCESS — Game Session ready")
            return true
        } finally {
            _isSessionStarting.value = false
        }
    }

    suspend fun resetSession(context: Context): RestoreResult {
        _isResetting.value = true
        return try {
            val result = TurboSpaceManager.restoreSavedSession(context)
            _isSessionActive.value = false
            TurboSpaceManager.showToast(
                context,
                if (result.success) "SUCCESS — Original game session state restored"
                else "ERROR — Reset completed with warnings"
            )
            result
        } finally {
            _isResetting.value = false
        }
    }

    suspend fun resetAll(context: Context): Boolean {
        _isResetting.value = true
        return try {
            var allOk = true

            // Restore real session state first. Even if a state cannot be read/restored,
            // the UI switches are still cleared so the user never sees a false "enabled" state.
            val session = TurboSpaceManager.restoreSavedSession(context)
            if (!session.success) allOk = false
            session.failedFeatures.forEach { name ->
                TurboSpaceManager.showToast(context, "ERROR — $name")
            }
            if (session.success && session.failedFeatures.isEmpty()) {
                TurboSpaceManager.showToast(context, "SUCCESS — Game Session")
            }

            if (_isCpuActive.value) {
                val outcomes = _selectedCpuApps.value.map {
                    TurboSpaceManager.restoreCpuLoad(context, it)
                }
                if (outcomes.any { it == TurboSpaceManager.CommandOutcome.BOTH_FAILED }) {
                    allOk = false
                    TurboSpaceManager.showToast(context, "ERROR — CPU Optimization")
                } else {
                    TurboSpaceManager.showToast(context, "SUCCESS — CPU Optimization")
                }
            }

            if (_isGpuActive.value) {
                val pkg = _selectedGpuGame.value
                if (!TurboSpaceManager.isRecognizedGame(context, pkg)) {
                    allOk = false
                    TurboSpaceManager.showToast(context, "ERROR — GPU Optimization")
                } else {
                    val outcome = TurboSpaceManager.restoreGpu(context, pkg)
                    if (outcome == TurboSpaceManager.CommandOutcome.BOTH_FAILED) {
                        allOk = false
                        TurboSpaceManager.showToast(context, "ERROR — GPU Optimization")
                    } else {
                        TurboSpaceManager.showToast(context, "SUCCESS — GPU Optimization")
                    }
                }
            }

            // Compile Optimization has a real Android reset command. Wait for the actual
            // compiler/reset process to finish instead of treating it as an instant toggle.
            if (PerformanceFeature.COMPILE_OPTIMIZATION in _selectedFeatures.value) {
                val pkg = TurboSpaceManager.getSavedCompileTarget(context)
                if (pkg.isNullOrBlank() || !TurboSpaceManager.isRecognizedGame(context, pkg)) {
                    allOk = false
                    TurboSpaceManager.showToast(context, "ERROR — Compile Optimization")
                } else {
                    val outcome = TurboSpaceManager.resetCompileGame(context, pkg)
                    if (outcome == TurboSpaceManager.CommandOutcome.BOTH_FAILED) {
                        allOk = false
                        TurboSpaceManager.showToast(context, "ERROR — Compile Optimization")
                    } else {
                        TurboSpaceManager.showToast(context, "SUCCESS — Compile Optimization")
                    }
                }
            }

            // One-shot action features without an OS undo command are not faked. Reset All only clears their
            // enabled state in the app UI; the already-completed system action remains completed.
            _selectedFeatures.value = emptySet()
            _isCpuActive.value = false
            _isGpuActive.value = false
            TurboSpaceManager.clearSavedCompileTarget(context)
            _selectedCompileMode.value = CompileMode.SMART_ADAPTIVE
            _isSessionActive.value = false
            persist(context)

            TurboSpaceManager.showToast(
                context,
                if (allOk) "SUCCESS — RESET ALL" else "ERROR — RESET ALL completed with errors"
            )
            allOk
        } finally {
            _isResetting.value = false
        }
    }

    suspend fun runFeatureFromOverlay(context: Context, feature: PerformanceFeature) {
        if (feature.stateful) {
            TurboSpaceManager.showToast(context, "${feature.title} is controlled by the current Game Session")
            return
        }
        val ok = TurboSpaceManager.runActionFeature(context, feature, TurboSpaceRepository.selectedGame.value)
        TurboSpaceManager.showToast(context, if (ok) "${feature.title} applied" else "${feature.title} failed")
    }

    private fun persist(context: Context) {
        TurboSpaceManager.saveAppState(
            context = context,
            selectedGame = _selectedGame.value,
            selectedFeatureIds = _selectedFeatures.value.toIds(),
            cpuApps = _selectedCpuApps.value,
            gpuGame = _selectedGpuGame.value,
            compileMode = _selectedCompileMode.value,
            cpuActive = _isCpuActive.value,
            gpuActive = _isGpuActive.value
        )
    }
}

// ============================================================================
// 6. HARDWARE MONITOR FOR HUD
// ============================================================================
class SystemMonitorEngine(private val context: Context) {
    val currentCpuUsage = mutableStateOf("N/A")
    val currentRamUsage = mutableStateOf("N/A")
    val currentTemperature = mutableStateOf("N/A")

    // Previous /proc/stat snapshot for delta-based CPU % calculation.
    // A single read gives cumulative totals since boot, which is not useful as
    // a live %. We store the last sample and compare against the new one.
    private var prevCpuTotal = 0L
    private var prevCpuIdle = 0L

    suspend fun update() = withContext(Dispatchers.IO) {
        updateRam()
        updateCpu()
        updateTemperature()
    }

    private fun updateRam() {
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(mem)
            val total = mem.totalMem / (1024 * 1024)
            val avail = mem.availMem / (1024 * 1024)
            currentRamUsage.value = "${total - avail} MB / $total MB"
        } catch (_: Exception) {
            currentRamUsage.value = "N/A"
        }
    }

    private suspend fun updateCpu() = withContext(Dispatchers.IO) {
        try {
            // Read /proc/stat directly — no Shizuku needed, it is world-readable.
            val line = java.io.File("/proc/stat")
                .bufferedReader()
                .useLines { lines -> lines.firstOrNull { it.startsWith("cpu ") } }
            if (line == null) {
                currentCpuUsage.value = "N/A"
                return@withContext
            }
            val values = line.trim().split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 4) {
                currentCpuUsage.value = "N/A"
                return@withContext
            }
            val total = values.sum()
            // fields: user nice system idle iowait irq softirq steal …
            // idle = idle + iowait (index 3 and 4)
            val idle = values[3] + (values.getOrElse(4) { 0L })

            val deltaTotal = total - prevCpuTotal
            val deltaIdle = idle - prevCpuIdle

            // Update snapshots for the next call before computing the display value.
            val isFirstSample = prevCpuTotal == 0L
            prevCpuTotal = total
            prevCpuIdle = idle

            currentCpuUsage.value = if (isFirstSample || deltaTotal <= 0L) {
                // First sample — no previous snapshot yet; show placeholder.
                "0%"
            } else {
                val usagePct = ((deltaTotal - deltaIdle) * 100L / deltaTotal).coerceIn(0L, 100L)
                "$usagePct%"
            }
        } catch (_: Exception) {
            currentCpuUsage.value = "N/A"
        }
    }

    private fun updateTemperature() {
        val temp = try {
            (0..9).asSequence()
                .mapNotNull { zone ->
                    try {
                        java.io.File("/sys/class/thermal/thermal_zone$zone/temp")
                            .takeIf { it.canRead() }
                            ?.readText()
                            ?.trim()
                            ?.toLongOrNull()
                    } catch (_: Exception) { null }
                }
                .map { raw -> if (raw > 1000) raw / 1000.0 else raw.toDouble() }
                .filter { it > 0 && it < 150 }
                .maxOrNull()
        } catch (_: Exception) { null }
        currentTemperature.value = if (temp == null) "N/A" else String.format("%.1f°C", temp)
    }
}

// ============================================================================
// 7. SHIZUKU / COMMAND MANAGER
// ============================================================================
object TurboSpaceManager {
    private const val PREFS_NAME = "TurboSpaceState"
    private const val COMMAND_TIMEOUT_MS = 10000L
    private const val APPS_DELIMITER = "||"
    private const val FEATURE_DELIMITER = "|"

    private const val KEY_SELECTED_GAME = "SELECTED_GAME"
    private const val KEY_SELECTED_FEATURES = "SELECTED_FEATURES"
    private const val KEY_CPU_APPS = "CPU_APPS"
    private const val KEY_GPU_GAME = "GPU_GAME"
    private const val KEY_CPU_ACTIVE = "CPU_ACTIVE"
    private const val KEY_GPU_ACTIVE = "GPU_ACTIVE"
    private const val KEY_COMPILE_MODE = "COMPILE_MODE"
    private const val KEY_COMPILE_GAME = "COMPILE_GAME"
    private const val KEY_PENDING_OVERLAY_START = "PENDING_OVERLAY_START"

    private const val KEY_BG_URI = "HOME_BG_URI"
    private const val KEY_BG_KIND = "HOME_BG_KIND"

    private const val KEY_SESSION_ACTIVE = "SESSION_ACTIVE"
    private const val KEY_SESSION_GAME = "SESSION_GAME"
    private const val KEY_SESSION_GAME_MODE = "SESSION_GAME_MODE"
    private const val KEY_SESSION_THERMAL = "SESSION_THERMAL"
    private const val KEY_SESSION_PROCESS = "SESSION_PROCESS"
    private const val KEY_SESSION_IDLE = "SESSION_IDLE"
    private const val KEY_SESSION_STATEFUL_FEATURES = "SESSION_STATEFUL_FEATURES"

    data class SavedAppState(
        val selectedGame: String,
        val selectedFeatureIds: Set<String>,
        val cpuApps: Set<String>,
        val gpuGame: String,
        val compileMode: CompileMode,
        val cpuActive: Boolean,
        val gpuActive: Boolean
    )

    data class SavedBackground(val uri: String?, val kind: String?)

    enum class CommandOutcome { PRIMARY_SUCCESS, FALLBACK_SUCCESS, BOTH_FAILED }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAppState(context: Context): SavedAppState {
        val p = prefs(context)
        fun readApps(key: String): Set<String> {
            val raw = p.getString(key, "") ?: ""
            return if (raw.isBlank()) emptySet()
            else raw.split(APPS_DELIMITER).filter { it.isNotBlank() }.toSet()
        }
        val rawFeatures = p.getString(KEY_SELECTED_FEATURES, "") ?: ""
        val featureIds = if (rawFeatures.isBlank()) emptySet() else rawFeatures.split(FEATURE_DELIMITER).toSet()
        return SavedAppState(
            selectedGame = p.getString(KEY_SELECTED_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
            selectedFeatureIds = featureIds,
            cpuApps = readApps(KEY_CPU_APPS),
            gpuGame = p.getString(KEY_GPU_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
            compileMode = CompileMode.values().firstOrNull { it.key == p.getString(KEY_COMPILE_MODE, CompileMode.SMART_ADAPTIVE.key) }
                ?: CompileMode.SMART_ADAPTIVE,
            cpuActive = p.getBoolean(KEY_CPU_ACTIVE, false),
            gpuActive = p.getBoolean(KEY_GPU_ACTIVE, false)
        )
    }

    fun saveAppState(
        context: Context,
        selectedGame: String,
        selectedFeatureIds: Set<String>,
        cpuApps: Set<String>,
        gpuGame: String,
        compileMode: CompileMode,
        cpuActive: Boolean,
        gpuActive: Boolean
    ) {
        prefs(context).edit()
            .putString(KEY_SELECTED_GAME, selectedGame)
            .putString(KEY_SELECTED_FEATURES, selectedFeatureIds.joinToString(FEATURE_DELIMITER))
            .putString(KEY_CPU_APPS, cpuApps.joinToString(APPS_DELIMITER))
            .putString(KEY_GPU_GAME, gpuGame)
            .putString(KEY_COMPILE_MODE, compileMode.key)
            .putBoolean(KEY_CPU_ACTIVE, cpuActive)
            .putBoolean(KEY_GPU_ACTIVE, gpuActive)
            .apply()
    }

    fun setPendingOverlayStart(context: Context, pending: Boolean) {
        prefs(context).edit().putBoolean(KEY_PENDING_OVERLAY_START, pending).apply()
    }

    fun hasPendingOverlayStart(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PENDING_OVERLAY_START, false)

    fun saveCompileTarget(context: Context, packageName: String?) {
        prefs(context).edit().putString(KEY_COMPILE_GAME, packageName).apply()
    }

    fun getSavedCompileTarget(context: Context): String? =
        prefs(context).getString(KEY_COMPILE_GAME, null)

    fun clearSavedCompileTarget(context: Context) {
        prefs(context).edit().remove(KEY_COMPILE_GAME).apply()
    }

    fun saveBackground(context: Context, uri: Uri?, kind: String?) {
        prefs(context).edit()
            .putString(KEY_BG_URI, uri?.toString())
            .putString(KEY_BG_KIND, kind)
            .apply()
    }

    fun loadBackground(context: Context): SavedBackground = SavedBackground(
        uri = prefs(context).getString(KEY_BG_URI, null),
        kind = prefs(context).getString(KEY_BG_KIND, null)
    )

    fun hasSavedSession(context: Context): Boolean = prefs(context).getBoolean(KEY_SESSION_ACTIVE, false)

    fun isShizukuAvailableAndGranted(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
            }
        } catch (_: Exception) { }
    }

    fun addPermissionResultListener(
        onResult: (granted: Boolean) -> Unit
    ): Shizuku.OnRequestPermissionResultListener {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) onResult(grantResult == PackageManager.PERMISSION_GRANTED)
        }
        Shizuku.addRequestPermissionResultListener(listener)
        return listener
    }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    fun newShizukuProcess(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, cmd, env, dir) as Process
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    suspend fun executeCommandDetailed(
        context: Context,
        primaryCmd: String,
        fallbackCmd: String = "",
        timeoutMs: Long? = COMMAND_TIMEOUT_MS
    ): CommandOutcome = withContext(Dispatchers.IO) {
        if (!isShizukuAvailableAndGranted()) return@withContext CommandOutcome.BOTH_FAILED
        if (runSingleCommand(primaryCmd, timeoutMs)) return@withContext CommandOutcome.PRIMARY_SUCCESS
        if (fallbackCmd.isNotBlank() && runSingleCommand(fallbackCmd, timeoutMs)) {
            return@withContext CommandOutcome.FALLBACK_SUCCESS
        }
        CommandOutcome.BOTH_FAILED
    }

    suspend fun executeCommandCapture(context: Context, command: String): String =
        withContext(Dispatchers.IO) {
            if (!isShizukuAvailableAndGranted()) return@withContext ""
            runSingleCommandCapture(command)
        }

    private suspend fun runSingleCommand(cmd: String, timeoutMs: Long?): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            val startedProcess = newShizukuProcess(arrayOf("sh", "-c", cmd))
            process = startedProcess
            val code = if (timeoutMs == null) {
                // No artificial timer: wait for the real system command to finish.
                runInterruptible { startedProcess.waitFor() }
            } else {
                withTimeoutOrNull(timeoutMs) {
                    runInterruptible { startedProcess.waitFor() }
                }
            }
            if (code == null) {
                startedProcess.destroy()
                false
            } else {
                code == 0
            }
        } catch (_: Exception) {
            process?.destroy()
            false
        }
    }

    private suspend fun runSingleCommandCapture(cmd: String): String = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = newShizukuProcess(arrayOf("sh", "-c", cmd))
            val out = process.inputStream.bufferedReader().readText()
            runInterruptible { process.waitFor() }
            out
        } catch (_: Exception) {
            ""
        } finally {
            process?.destroy()
        }
    }

    // ------------------------------------------------------------------
    // GAME IDENTIFICATION
    // ------------------------------------------------------------------
    fun isRecognizedGame(context: Context, packageName: String): Boolean {
        if (packageName.isBlank() || packageName == "NO TARGET SELECTED" || packageName == context.packageName) return false
        return getGameApps(context).any { it.packageName == packageName }
    }

    fun getGameApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { info ->
                    if (info.packageName == context.packageName) return@filter false
                    if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@filter false
                    if ((info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return@filter false

                    val gameCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        info.category == ApplicationInfo.CATEGORY_GAME
                    } else {
                        @Suppress("DEPRECATION")
                        (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                    }
                    gameCategory && pm.getLaunchIntentForPackage(info.packageName) != null
                }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getThirdPartyApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { info ->
                    info.packageName != context.packageName &&
                        (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAppLabel(context: Context, packageName: String): String {
        return try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    // ------------------------------------------------------------------
    // CPU / GPU IMPLEMENTATIONS KEPT FROM THE EXISTING APP
    // ------------------------------------------------------------------
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

    suspend fun restoreCpuLoad(context: Context, packageName: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd activity set-inactive $packageName false"
        val fallback = "cmd appops set $packageName RUN_IN_BACKGROUND allow"
        return executeCommandDetailed(context, primary, fallback)
    }

    suspend fun restoreGpu(context: Context, packageName: String): CommandOutcome {
        if (!isRecognizedGame(context, packageName)) return CommandOutcome.BOTH_FAILED
        val primary = "cmd game downscale reset $packageName"
        val fallback = "cmd game downscale $packageName 1.0"
        return executeCommandDetailed(context, primary, fallback)
    }

    suspend fun restoreSingleStatefulFeature(context: Context, feature: PerformanceFeature): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_SESSION_ACTIVE, false)) return true

        val ids = (p.getString(KEY_SESSION_STATEFUL_FEATURES, "") ?: "")
            .split(FEATURE_DELIMITER)
            .filter { it.isNotBlank() }
            .toMutableSet()
        if (feature.id !in ids) return true

        val gamePackage = p.getString(KEY_SESSION_GAME, "") ?: ""
        val previousGameMode = p.getString(KEY_SESSION_GAME_MODE, null)
        val thermalRaw = p.getInt(KEY_SESSION_THERMAL, Int.MIN_VALUE)
        val previousThermal = thermalRaw.takeUnless { it == Int.MIN_VALUE }
        val previousProcess = p.getString(KEY_SESSION_PROCESS, null)
        val previousIdle = when ((p.getString(KEY_SESSION_IDLE, null) ?: "").lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }

        val ok = when (feature) {
            PerformanceFeature.GAME_PERFORMANCE -> previousGameMode != null && gamePackage.isNotBlank() &&
                executeCommandDetailed(context, "cmd game mode $previousGameMode $gamePackage") != CommandOutcome.BOTH_FAILED
            PerformanceFeature.THERMAL_CONTROL -> {
                val resetOk = executeCommandDetailed(context, "cmd thermalservice reset") != CommandOutcome.BOTH_FAILED
                if (!resetOk) false
                else if (previousThermal != null && previousThermal >= 0) {
                    executeCommandDetailed(context, "cmd thermalservice override-status $previousThermal") != CommandOutcome.BOTH_FAILED
                } else true
            }
            PerformanceFeature.PROCESS_CONTROL -> previousProcess != null &&
                executeCommandDetailed(
                    context,
                    if (previousProcess.equals("default", true)) "cmd activity set-process-limit default"
                    else "cmd activity set-process-limit $previousProcess"
                ) != CommandOutcome.BOTH_FAILED
            PerformanceFeature.IDLE_CONTROL -> previousIdle != null &&
                executeCommandDetailed(
                    context,
                    if (previousIdle) "cmd deviceidle force-idle light" else "cmd deviceidle unforce"
                ) != CommandOutcome.BOTH_FAILED
            else -> true
        }

        if (!ok) return false

        ids.remove(feature.id)
        val editor = p.edit().putString(KEY_SESSION_STATEFUL_FEATURES, ids.joinToString(FEATURE_DELIMITER))
        if (ids.isEmpty()) {
            editor.putBoolean(KEY_SESSION_ACTIVE, false)
                .remove(KEY_SESSION_GAME)
                .remove(KEY_SESSION_STATEFUL_FEATURES)
                .remove(KEY_SESSION_GAME_MODE)
                .remove(KEY_SESSION_THERMAL)
                .remove(KEY_SESSION_PROCESS)
                .remove(KEY_SESSION_IDLE)
        }
        editor.apply()
        return true
    }

    // --- Game Compile / ART optimization ---
    suspend fun compileGame(context: Context, packageName: String, mode: CompileMode): CommandOutcome {
        if (!isRecognizedGame(context, packageName)) return CommandOutcome.BOTH_FAILED
        val primary = "cmd package compile -m ${mode.artFilter} -f $packageName"
        val fallback = "pm compile -m ${mode.artFilter} -f $packageName"
        val outcome = executeCommandDetailed(context, primary, fallback, timeoutMs = null)
        if (outcome != CommandOutcome.BOTH_FAILED) saveCompileTarget(context, packageName)
        return outcome
    }

    suspend fun resetCompileGame(context: Context, packageName: String): CommandOutcome {
        if (!isRecognizedGame(context, packageName)) return CommandOutcome.BOTH_FAILED
        val primary = "cmd package compile --reset $packageName"
        val fallback = "pm compile --reset $packageName"
        val outcome = executeCommandDetailed(context, primary, fallback, timeoutMs = null)
        if (outcome != CommandOutcome.BOTH_FAILED) clearSavedCompileTarget(context)
        return outcome
    }

    // ------------------------------------------------------------------
    // NEW SESSION FEATURE COMMANDS
    // ------------------------------------------------------------------
    suspend fun applySelectedSessionFeatures(
        context: Context,
        gamePackage: String,
        features: Set<PerformanceFeature>
    ): Set<PerformanceFeature> {
        val successful = mutableSetOf<PerformanceFeature>()
        features.forEach { feature ->
            val ok = when (feature) {
                PerformanceFeature.GAME_PERFORMANCE ->
                    executeCommandDetailed(context, "cmd game mode performance $gamePackage") != CommandOutcome.BOTH_FAILED
                PerformanceFeature.THERMAL_CONTROL ->
                    executeCommandDetailed(context, "cmd thermalservice override-status 0") != CommandOutcome.BOTH_FAILED
                PerformanceFeature.PROCESS_CONTROL ->
                    executeCommandDetailed(context, "cmd activity set-process-limit 2") != CommandOutcome.BOTH_FAILED
                PerformanceFeature.IDLE_CONTROL ->
                    executeCommandDetailed(context, "cmd deviceidle force-idle light") != CommandOutcome.BOTH_FAILED
                else -> runActionFeature(context, feature, gamePackage)
            }
            if (ok) successful += feature
        }
        return successful
    }

    suspend fun runGfxBoost(context: Context): CommandOutcome =
        executeCommandDetailed(context, "cmd activity boost-gfx")

    suspend fun runActionFeature(context: Context, feature: PerformanceFeature, gamePackage: String? = null): Boolean {
        val command = when (feature) {
            PerformanceFeature.RESOURCE_CLEANUP -> "cmd activity purge-process-resources"
            PerformanceFeature.APP_OPTIMIZATION -> "cmd package bg-dexopt-job"
            PerformanceFeature.INPUT_CONFIGURATION -> "cmd inputflinger reload-config"
            PerformanceFeature.TRIM_CACHE -> "pm trim-caches 999G"
            else -> null
        }
        if (feature == PerformanceFeature.COMPILE_OPTIMIZATION) {
            val pkg = gamePackage?.takeIf { isRecognizedGame(context, it) } ?: return false
            val mode = TurboSpaceRepository.selectedCompileMode.value
            return compileGame(context, pkg, mode) != CommandOutcome.BOTH_FAILED
        }
        if (feature == PerformanceFeature.APP_OPTIMIZATION || feature == PerformanceFeature.TRIM_CACHE) {
            // These may genuinely take longer than the short command timeout. Wait for
            // the real shell process to exit; no artificial sleep and no forced cutoff.
            return command?.let {
                executeCommandDetailed(context, it, timeoutMs = null) != CommandOutcome.BOTH_FAILED
            } ?: false
        }
        return command?.let { executeCommandDetailed(context, it) != CommandOutcome.BOTH_FAILED } ?: false
    }

    // ------------------------------------------------------------------
    // ORIGINAL STATE CAPTURE / RESTORE
    // ------------------------------------------------------------------
    suspend fun captureAndSaveSessionSnapshot(
        context: Context,
        gamePackage: String,
        features: Set<PerformanceFeature>
    ) {
        val p = prefs(context)
        val existingActive = p.getBoolean(KEY_SESSION_ACTIVE, false)
        val existingIds = if (existingActive) {
            (p.getString(KEY_SESSION_STATEFUL_FEATURES, "") ?: "")
                .split(FEATURE_DELIMITER)
                .filter { it.isNotBlank() }
                .toMutableSet()
        } else mutableSetOf()

        val requestedStateful = features.filter { it.stateful }.toSet()
        val requestedIds = requestedStateful.toIds()
        if (requestedIds.isEmpty() && existingIds.isEmpty()) {
            return
        }

        // Never overwrite an already-captured original value. This is essential when
        // the user enables several stateful switches one after another.
        val savedGamePackage = p.getString(KEY_SESSION_GAME, null)
        val sessionGame = savedGamePackage?.takeIf { it.isNotBlank() } ?: gamePackage

        var previousGameMode = p.getString(KEY_SESSION_GAME_MODE, null)
        var previousThermal: Int? = p.getInt(KEY_SESSION_THERMAL, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
        var previousProcess = p.getString(KEY_SESSION_PROCESS, null)
        var previousIdle: Boolean? = when ((p.getString(KEY_SESSION_IDLE, null) ?: "").lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }

        if (PerformanceFeature.GAME_PERFORMANCE in requestedStateful && PerformanceFeature.GAME_PERFORMANCE.id !in existingIds) {
            previousGameMode = queryGameMode(context, gamePackage)
            existingIds += PerformanceFeature.GAME_PERFORMANCE.id
        }
        if (PerformanceFeature.THERMAL_CONTROL in requestedStateful && PerformanceFeature.THERMAL_CONTROL.id !in existingIds) {
            previousThermal = queryThermalOverride(context)
            existingIds += PerformanceFeature.THERMAL_CONTROL.id
        }
        if (PerformanceFeature.PROCESS_CONTROL in requestedStateful && PerformanceFeature.PROCESS_CONTROL.id !in existingIds) {
            previousProcess = queryProcessLimit(context)
            existingIds += PerformanceFeature.PROCESS_CONTROL.id
        }
        if (PerformanceFeature.IDLE_CONTROL in requestedStateful && PerformanceFeature.IDLE_CONTROL.id !in existingIds) {
            previousIdle = queryForcedIdle(context)
            existingIds += PerformanceFeature.IDLE_CONTROL.id
        }

        p.edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putString(KEY_SESSION_GAME, sessionGame)
            .putString(KEY_SESSION_STATEFUL_FEATURES, existingIds.joinToString(FEATURE_DELIMITER))
            .putString(KEY_SESSION_GAME_MODE, previousGameMode)
            .putInt(KEY_SESSION_THERMAL, previousThermal ?: Int.MIN_VALUE)
            .putString(KEY_SESSION_PROCESS, previousProcess)
            .putString(KEY_SESSION_IDLE, previousIdle?.toString())
            .apply()
    }

    suspend fun restoreSavedSession(context: Context): RestoreResult {
        val p = prefs(context)
        if (!p.getBoolean(KEY_SESSION_ACTIVE, false)) {
            return RestoreResult(success = true)
        }

        val gamePackage = p.getString(KEY_SESSION_GAME, "") ?: ""
        val statefulFeatureIds = (p.getString(KEY_SESSION_STATEFUL_FEATURES, "") ?: "")
            .split(FEATURE_DELIMITER)
            .filter { it.isNotBlank() }
            .toSet()
        val statefulFeatures = statefulFeatureIds.toPerformanceFeatures()
        if (statefulFeatures.isEmpty()) {
            p.edit()
                .remove(KEY_SESSION_GAME)
                .remove(KEY_SESSION_STATEFUL_FEATURES)
                .remove(KEY_SESSION_GAME_MODE)
                .remove(KEY_SESSION_THERMAL)
                .remove(KEY_SESSION_PROCESS)
                .remove(KEY_SESSION_IDLE)
                .putBoolean(KEY_SESSION_ACTIVE, false)
                .apply()
            return RestoreResult(success = true)
        }

        val previousGameMode = p.getString(KEY_SESSION_GAME_MODE, null)
        val thermalRaw = p.getInt(KEY_SESSION_THERMAL, Int.MIN_VALUE)
        val previousThermal = thermalRaw.takeUnless { it == Int.MIN_VALUE }
        val previousProcess = p.getString(KEY_SESSION_PROCESS, null)
        val previousIdle = when ((p.getString(KEY_SESSION_IDLE, null) ?: "").lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }

        var allOk = true
        val warnings = mutableListOf<String>()
        val failedFeatures = mutableListOf<String>()

        if (PerformanceFeature.GAME_PERFORMANCE in statefulFeatures && gamePackage.isNotBlank() && previousGameMode != null) {
            val ok = executeCommandDetailed(
                context,
                "cmd game mode $previousGameMode $gamePackage"
            ) != CommandOutcome.BOTH_FAILED
            if (!ok) {
                allOk = false
                failedFeatures += "Game Performance"
            }
        } else if (PerformanceFeature.GAME_PERFORMANCE in statefulFeatures && gamePackage.isNotBlank() && previousGameMode == null) {
            allOk = false
            failedFeatures += "Game Performance"
            warnings += "Previous Game Mode could not be detected; it was left unchanged."
        }

        if (PerformanceFeature.THERMAL_CONTROL in statefulFeatures && previousThermal != null) {
            val resetOk = executeCommandDetailed(context, "cmd thermalservice reset") != CommandOutcome.BOTH_FAILED
            if (!resetOk) {
                allOk = false
                failedFeatures += "Thermal Control"
            } else if (previousThermal >= 0) {
                val reapplyOk = executeCommandDetailed(
                    context,
                    "cmd thermalservice override-status $previousThermal"
                ) != CommandOutcome.BOTH_FAILED
                if (!reapplyOk) {
                    allOk = false
                    failedFeatures += "Thermal Control"
                }
            }
        } else if (PerformanceFeature.THERMAL_CONTROL in statefulFeatures && p.contains(KEY_SESSION_THERMAL)) {
            // The requested reset command is used only when the original query was unavailable.
            val ok = executeCommandDetailed(context, "cmd thermalservice reset") != CommandOutcome.BOTH_FAILED
            if (!ok) {
                allOk = false
                failedFeatures += "Thermal Control"
            }
            warnings += "Original Thermal Override state was not readable; the system reset command was used."
        }

        if (PerformanceFeature.PROCESS_CONTROL in statefulFeatures && previousProcess != null) {
            val cmd = if (previousProcess.equals("default", ignoreCase = true)) {
                "cmd activity set-process-limit default"
            } else {
                "cmd activity set-process-limit $previousProcess"
            }
            val ok = executeCommandDetailed(context, cmd) != CommandOutcome.BOTH_FAILED
            if (!ok) {
                allOk = false
                failedFeatures += "Process Control"
            }
        } else if (PerformanceFeature.PROCESS_CONTROL in statefulFeatures) {
            allOk = false
            failedFeatures += "Process Control"
            warnings += "Original Process Limit was not readable; no blind replacement value was applied."
        }

        if (PerformanceFeature.IDLE_CONTROL in statefulFeatures && previousIdle != null) {
            val cmd = if (previousIdle) {
                "cmd deviceidle force-idle light"
            } else {
                "cmd deviceidle unforce"
            }
            val ok = executeCommandDetailed(context, cmd) != CommandOutcome.BOTH_FAILED
            if (!ok) {
                allOk = false
                failedFeatures += "Idle Control"
            }
        } else if (PerformanceFeature.IDLE_CONTROL in statefulFeatures) {
            allOk = false
            failedFeatures += "Idle Control"
            warnings += "Original Idle state was not readable; no blind replacement state was applied."
        }

        p.edit()
            .remove(KEY_SESSION_GAME)
            .remove(KEY_SESSION_STATEFUL_FEATURES)
            .remove(KEY_SESSION_GAME_MODE)
            .remove(KEY_SESSION_THERMAL)
            .remove(KEY_SESSION_PROCESS)
            .remove(KEY_SESSION_IDLE)
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .apply()

        return RestoreResult(allOk, warnings, failedFeatures.distinct())
    }

    private suspend fun queryGameMode(context: Context, packageName: String): String? {
        val output = executeCommandCapture(context, "cmd game mode get $packageName")
        val lower = output.lowercase()
        return when {
            Regex("current[^\\n]*(performance)", RegexOption.IGNORE_CASE).containsMatchIn(output) -> "performance"
            Regex("current[^\\n]*(battery)", RegexOption.IGNORE_CASE).containsMatchIn(output) -> "battery"
            Regex("current[^\\n]*(standard|balanced)", RegexOption.IGNORE_CASE).containsMatchIn(output) -> "standard"
            lower.trim() == "performance" -> "performance"
            lower.trim() == "battery" -> "battery"
            lower.trim() == "standard" || lower.trim() == "balanced" -> "standard"
            else -> null
        }
    }

    private suspend fun queryProcessLimit(context: Context): String? {
        val output = executeCommandCapture(context, "cmd activity get-process-limit")
        val match = Regex("(?i)(?:process.?limit|limit)\\s*[:=]\\s*(default|-?\\d+)").find(output)
        if (match != null) return match.groupValues[1]
        val trimmed = output.trim()
        if (trimmed.equals("default", true)) return "default"
        return Regex("(?:^|\\s)(\\d+)(?:\\s|$)").find(trimmed)?.groupValues?.get(1)
    }

    private suspend fun queryThermalOverride(context: Context): Int? {
        val output = executeCommandCapture(context, "dumpsys thermalservice")
        val match = Regex("(?i)mOverrideStatus\\s*[:=]\\s*(-?\\d+)").find(output)
            ?: Regex("(?i)override.?status\\s*[:=]\\s*(-?\\d+)").find(output)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private suspend fun queryForcedIdle(context: Context): Boolean? {
        val output = executeCommandCapture(context, "dumpsys deviceidle")
        Regex("(?i)mForceIdle\\s*[=:]\\s*(true|false)").find(output)?.let {
            return it.groupValues[1].equals("true", true)
        }
        Regex("(?i)mForceLevel\\s*[=:]\\s*(\\d+)").find(output)?.let {
            return it.groupValues[1].toIntOrNull()?.let { value -> value != 0 }
        }
        return null
    }
}

// ============================================================================
// 8. OVERLAY SERVICE
// ============================================================================
class GameSpaceOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var overlayParams: WindowManager.LayoutParams
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)

        // startForeground must succeed before we touch WindowManager.
        // Wrap in try/catch so a foreground-service rejection on any API level
        // is caught and reported instead of silently skipping addView.
        try {
            startForegroundServiceWithNotification()
        } catch (e: Exception) {
            TurboSpaceRepository.setOverlayReady(false)
            Toast.makeText(this, "ERROR — Game Bar foreground service failed", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        composeView = ComposeView(this).apply {
            // Hardware acceleration is required for Compose to render inside a
            // TYPE_APPLICATION_OVERLAY window; without it the view stays blank.
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
            setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
            setContent {
                TurboSpaceGameBar(
                    onDragOverlay = { dx, dy -> moveOverlayBy(dx, dy) },
                    onClose = { stopSelf() },
                    onReset = {
                        // RESET restores state; CLOSE never does.
                        // Use lifecycleScope (from LifecycleService) to avoid MainScope leaks.
                        lifecycleScope.launch {
                            TurboSpaceRepository.resetAll(this@GameSpaceOverlayService)
                            stopSelf()
                        }
                    }
                )
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val wmFlags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            wmFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            val targetWidth = (minOf(dm.widthPixels * 0.92f, 1080f)).toInt()
            width = targetWidth
            x = ((dm.widthPixels - targetWidth) / 2f).toInt().coerceAtLeast(0)
            y = (dm.heightPixels * 0.12f).toInt()
        }

        try {
            windowManager.addView(composeView, overlayParams)
            TurboSpaceRepository.setOverlayReady(true)
        } catch (e: Exception) {
            TurboSpaceRepository.setOverlayReady(false)
            Toast.makeText(this, "ERROR — Game Bar could not be displayed", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun moveOverlayBy(dx: Float, dy: Float) {
        if (!::composeView.isInitialized || !::overlayParams.isInitialized) return
        val dm = resources.displayMetrics
        val maxX = (dm.widthPixels - composeView.width).coerceAtLeast(0)
        val maxY = (dm.heightPixels - composeView.height).coerceAtLeast(0)
        overlayParams.x = (overlayParams.x + dx.toInt()).coerceIn(0, maxX)
        overlayParams.y = (overlayParams.y + dy.toInt()).coerceIn(0, maxY)
        try { windowManager.updateViewLayout(composeView, overlayParams) } catch (_: Exception) { }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "turbo_overlay_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Turbo Space Game Bar", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Turbo Space Game Session")
            .setContentText("Game Bar is active")
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
        TurboSpaceRepository.setOverlayReady(false)
        try {
            if (::composeView.isInitialized) windowManager.removeView(composeView)
        } catch (_: Exception) { }
        _viewModelStore.clear()
        super.onDestroy()
    }
}

// ============================================================================
// 9. ROOT APP / SCREEN NAVIGATION
// ============================================================================
private enum class TurboScreen { HOME, GAMES, SETTINGS }

@Composable
fun TurboSpaceApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(TurboScreen.HOME) }
    var isShizukuReady by remember { mutableStateOf(TurboSpaceManager.isShizukuAvailableAndGranted()) }
    var backgroundRefreshKey by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        TurboSpaceRepository.restoreFromPrefs(context)
        if (TurboSpaceManager.hasPendingOverlayStart(context) && Settings.canDrawOverlays(context)) {
            TurboSpaceManager.setPendingOverlayStart(context, false)
            coroutineScope.launch {
                startSessionAndLaunch(
                    context = context,
                    isShizukuReady = isShizukuReady,
                    onSessionStarted = {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, GameSpaceOverlayService::class.java)
                        )
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = TurboSpaceManager.addPermissionResultListener { granted ->
            isShizukuReady = granted
        }
        onDispose { TurboSpaceManager.removePermissionResultListener(listener) }
    }

    val selectedGame by TurboSpaceRepository.selectedGame.collectAsState()
    val isSessionStarting by TurboSpaceRepository.isSessionStarting.collectAsState()
    val isSessionActive by TurboSpaceRepository.isSessionActive.collectAsState()
    val isResetting by TurboSpaceRepository.isResetting.collectAsState()


    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val mime = context.contentResolver.getType(uri).orEmpty()
            val kind = when {
                mime.startsWith("video/") -> "video"
                mime.equals("image/gif", true) -> "animated"
                else -> "image"
            }
            TurboSpaceManager.saveBackground(context, uri, kind)
            backgroundRefreshKey++
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context) && TurboSpaceManager.hasPendingOverlayStart(context)) {
            TurboSpaceManager.setPendingOverlayStart(context, false)
            // The system Settings screen can recreate the Activity. Reload the persisted
            // game synchronously before attempting START so the selected game cannot be lost.
            TurboSpaceRepository.restoreFromPrefs(context)
            coroutineScope.launch {
                startSessionAndLaunch(
                    context = context,
                    isShizukuReady = isShizukuReady,
                    onSessionStarted = {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, GameSpaceOverlayService::class.java)
                        )
                    }
                )
            }
        } else if (!Settings.canDrawOverlays(context)) {
            TurboSpaceManager.setPendingOverlayStart(context, false)
            Toast.makeText(context, "Overlay permission is required to show the Game Bar", Toast.LENGTH_LONG).show()
        }
    }

    fun requestStart() {
        // Read directly from the repository because the Compose collector may still be catching up
        // after returning from Android Settings.
        val currentSelectedGame = TurboSpaceRepository.selectedGame.value
        if (currentSelectedGame == "NO TARGET SELECTED") {
            Toast.makeText(context, "Select a game first", Toast.LENGTH_SHORT).show()
            screen = TurboScreen.GAMES
            return
        }
        if (!isShizukuReady) {
            Toast.makeText(context, "Shizuku permission is required", Toast.LENGTH_LONG).show()
            TurboSpaceManager.requestShizukuPermission()
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            TurboSpaceManager.setPendingOverlayStart(context, true)
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
            return
        }
        coroutineScope.launch {
            startSessionAndLaunch(
                context = context,
                isShizukuReady = isShizukuReady,
                onSessionStarted = {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, GameSpaceOverlayService::class.java)
                    )
                }
            )
        }
    }

    fun resetNow() {
        coroutineScope.launch {
            TurboSpaceRepository.resetAll(context)
            context.stopService(Intent(context, GameSpaceOverlayService::class.java))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TurboColors.DarkBackground)) {
        HomeBackground(backgroundRefreshKey)

        when (screen) {
            TurboScreen.HOME -> HomeScreen(
                selectedGame = selectedGame,
                isShizukuReady = isShizukuReady,
                isSessionStarting = isSessionStarting,
                isSessionActive = isSessionActive,
                isResetting = isResetting,
                onSettings = { screen = TurboScreen.SETTINGS },
                onGames = { screen = TurboScreen.GAMES },
                onAddBackground = { backgroundPicker.launch(arrayOf("image/*", "video/*")) },
                onStart = ::requestStart,
                onReset = ::resetNow,
                onShizuku = { TurboSpaceManager.requestShizukuPermission() }
            )

            TurboScreen.GAMES -> GameSelectionScreen(
                selectedGame = selectedGame,
                onBack = { screen = TurboScreen.HOME },
                onGameSelected = {
                    TurboSpaceRepository.setSelectedGame(context, it)
                    screen = TurboScreen.HOME
                }
            )

            TurboScreen.SETTINGS -> PerformanceSettingsScreen(
                isShizukuReady = isShizukuReady,
                onBack = { screen = TurboScreen.HOME },
                onResetSession = { resetNow() }
            )
        }
    }
}

private suspend fun startSessionAndLaunch(
    context: Context,
    isShizukuReady: Boolean,
    onSessionStarted: () -> Unit
) {
    if (!isShizukuReady) {
        TurboSpaceManager.showToast(context, "Shizuku permission is required")
        return
    }

    val pkg = TurboSpaceRepository.selectedGame.value
    if (!TurboSpaceManager.isRecognizedGame(context, pkg)) {
        TurboSpaceManager.showToast(context, "ERROR — No recognized game selected")
        return
    }

    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
    if (launchIntent == null) {
        TurboSpaceManager.showToast(context, "ERROR — Could not launch the selected game")
        return
    }

    // Reset the ready flag BEFORE starting the service so we can't miss the signal.
    // Then start the service and wait for it to confirm its overlay window is attached.
    // StateFlow always replays the last value, so collecting after setOverlayReady(true)
    // is called will still see true — no race window.
    TurboSpaceRepository.setOverlayReady(false)
    try {
        onSessionStarted()
    } catch (_: Exception) {
        TurboSpaceManager.showToast(context, "ERROR — Could not start Game Bar")
        return
    }

    val ready = withTimeoutOrNull(6000L) {
        // StateFlow.filter { }.first() correctly handles the case where the value is
        // already true when collection starts (StateFlow replays current value).
        TurboSpaceRepository.overlayReady.filter { it }.first()
        true
    } ?: false

    if (!ready) {
        context.stopService(Intent(context, GameSpaceOverlayService::class.java))
        TurboSpaceManager.showToast(context, "ERROR — Game Bar was not ready, game launch cancelled")
        return
    }

    val started = TurboSpaceRepository.startGameSession(context)
    if (!started) {
        context.stopService(Intent(context, GameSpaceOverlayService::class.java))
        return
    }

    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
}

// ============================================================================
// 10. HOME SCREEN
// ============================================================================
@Composable
private fun HomeScreen(
    selectedGame: String,
    isShizukuReady: Boolean,
    isSessionStarting: Boolean,
    isSessionActive: Boolean,
    isResetting: Boolean,
    onSettings: () -> Unit,
    onGames: () -> Unit,
    onAddBackground: () -> Unit,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onShizuku: () -> Unit
) {
    val context = LocalContext.current
    val label = remember(selectedGame) {
        if (selectedGame == "NO TARGET SELECTED") "NO GAME SELECTED"
        else TurboSpaceManager.getAppLabel(context, selectedGame)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Minimal glass layer keeps the selected background visible and sharp.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TurboColors.DarkBackground.copy(alpha = 0.07f))
        )

        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GamingTopButton(
                    text = "🎮 Games",
                    onClick = onGames,
                    modifier = Modifier.width(120.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "TURBO SPACE",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(8.dp))
                GamingTopButton("⚙️ Settings", onSettings, Modifier.width(132.dp))
                Spacer(Modifier.width(6.dp))
                GamingTopButton("＋ Add Background", onAddBackground, Modifier.width(145.dp))
            }

            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                // Left-side control deck: compact so most of the background remains visible.
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.40f)
                        .wrapContentHeight(),
                    color = TurboColors.HudObsidian.copy(alpha = 0.56f),
                    shape = CutCornerShape(16.dp),
                    border = BorderStroke(1.dp, TurboColors.HudCyan.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("GAME SESSION", color = TurboColors.HudCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedGame != "NO TARGET SELECTED") {
                                AppIconImage(selectedGame, Modifier.size(48.dp))
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                Text(
                                    if (isSessionActive) "SESSION ACTIVE" else if (isShizukuReady) "READY" else "SHIZUKU REQUIRED",
                                    color = if (isSessionActive) TurboColors.HudGreen else TurboColors.TextGray,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (!isShizukuReady) {
                            OutlinedButton(onClick = onShizuku, modifier = Modifier.fillMaxWidth()) {
                                Text("Grant Shizuku Permission")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            GamingMainButton(
                                text = if (isSessionStarting) "STARTING..." else "▶ START",
                                accent = TurboColors.HudCyan,
                                enabled = isShizukuReady && !isSessionStarting && !isSessionActive && selectedGame != "NO TARGET SELECTED",
                                modifier = Modifier.weight(1f),
                                onClick = onStart
                            )
                            GamingMainButton(
                                text = if (isResetting) "RESETTING..." else "↻ RESET ALL",
                                accent = TurboColors.HudCrimson,
                                enabled = !isResetting,
                                modifier = Modifier.weight(1f),
                                onClick = onReset
                            )
                        }
                    }
                }
            }
        }

        if (isSessionStarting || isResetting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.padding(28.dp),
                    color = TurboColors.HudObsidian.copy(alpha = 0.97f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isResetting) TurboColors.HudCrimson else TurboColors.HudCyan)
                ) {
                    Column(
                        modifier = Modifier.padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = if (isResetting) TurboColors.HudCrimson else TurboColors.HudCyan
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (isResetting) "RESETTING..." else "STARTING...",
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (isResetting)
                                "Waiting for the real system reset commands to finish."
                            else
                                "Waiting for the real system commands to finish.",
                            color = TurboColors.TextGray,
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GamingTopButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = CutCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TurboColors.HudObsidian.copy(alpha = 0.92f),
            contentColor = Color.White
        )
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun GamingMainButton(
    text: String,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = CutCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.82f),
            contentColor = Color.White,
            disabledContainerColor = TurboColors.EngineStopGray,
            disabledContentColor = Color.White
        )
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Black) }
}

// ============================================================================
// 11. SETTINGS SCREEN
// ============================================================================
@Composable
private fun PerformanceSettingsScreen(
    isShizukuReady: Boolean,
    onBack: () -> Unit,
    onResetSession: () -> Unit
) {
    val context = LocalContext.current
    val selectedFeatures by TurboSpaceRepository.selectedFeatures.collectAsState()
    val selectedGame by TurboSpaceRepository.selectedGame.collectAsState()
    val selectedCpuApps by TurboSpaceRepository.selectedCpuApps.collectAsState()
    val selectedGpuGame by TurboSpaceRepository.selectedGpuGame.collectAsState()
    val selectedCompileMode by TurboSpaceRepository.selectedCompileMode.collectAsState()
    val cpuActive by TurboSpaceRepository.isCpuActive.collectAsState()
    val gpuActive by TurboSpaceRepository.isGpuActive.collectAsState()
    val cpuLoading by TurboSpaceRepository.isCpuLoading.collectAsState()
    val gpuLoading by TurboSpaceRepository.isGpuLoading.collectAsState()
    val isSessionActive by TurboSpaceRepository.isSessionActive.collectAsState()
    val isResetting by TurboSpaceRepository.isResetting.collectAsState()
    val busyFeatures by TurboSpaceRepository.busyFeatures.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var gamePerformancePickerOpen by remember { mutableStateOf(false) }
    var compileGamePickerOpen by remember { mutableStateOf(false) }
    var compileModePickerOpen by remember { mutableStateOf(false) }
    var appOptimizationConfirmOpen by remember { mutableStateOf(false) }
    var appOptimizationRunning by remember { mutableStateOf(false) }
    var compileConfirmOpen by remember { mutableStateOf(false) }
    var compileRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TurboColors.DarkBackground.copy(alpha = 0.96f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Performance Features", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Switches execute real Android commands and reflect the command result.", color = TurboColors.TextGray, fontSize = 11.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            if (!isShizukuReady) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = TurboColors.ActiveRedBg.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TurboColors.BrightRed.copy(alpha = 0.7f))
                ) {
                    Text(
                        "Shizuku permission is required to execute performance commands.",
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            Text("SESSION FEATURES", color = TurboColors.HudCyan, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                "Stateful features are restored by RESET. Action features run once and have no fake reset.",
                color = TurboColors.TextGray,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))

            GamePerformanceConfigRow(
                selectedGame = selectedGame,
                checked = PerformanceFeature.GAME_PERFORMANCE in selectedFeatures,
                enabled = isShizukuReady && PerformanceFeature.GAME_PERFORMANCE !in busyFeatures,
                busy = PerformanceFeature.GAME_PERFORMANCE in busyFeatures,
                onSelectGame = { gamePerformancePickerOpen = true },
                onCheckedChange = { checked ->
                    coroutineScope.launch {
                        TurboSpaceRepository.setFeatureEnabledVerified(context, PerformanceFeature.GAME_PERFORMANCE, checked)
                    }
                }
            )
            Spacer(Modifier.height(8.dp))

            PerformanceFeature.values()
                .filter {
                    it != PerformanceFeature.GAME_PERFORMANCE &&
                        it != PerformanceFeature.COMPILE_OPTIMIZATION &&
                        it != PerformanceFeature.APP_OPTIMIZATION
                }
                .forEach { feature ->
                    FeatureSwitchRow(
                        feature = feature,
                        checked = feature in selectedFeatures,
                        enabled = isShizukuReady && feature !in busyFeatures,
                        busy = feature in busyFeatures,
                        onCheckedChange = { checked ->
                            coroutineScope.launch {
                                TurboSpaceRepository.setFeatureEnabledVerified(context, feature, checked)
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

            // App Optimization is a real blocking system operation. Turning it on requires
            // confirmation and runs the actual bg-dexopt-job until the system command exits.
            FeatureSwitchRow(
                feature = PerformanceFeature.APP_OPTIMIZATION,
                checked = PerformanceFeature.APP_OPTIMIZATION in selectedFeatures,
                enabled = isShizukuReady && !appOptimizationRunning && PerformanceFeature.APP_OPTIMIZATION !in busyFeatures,
                busy = appOptimizationRunning,
                onCheckedChange = { checked ->
                    if (checked) {
                        appOptimizationConfirmOpen = true
                    } else {
                        TurboSpaceRepository.setFeatureSelected(context, PerformanceFeature.APP_OPTIMIZATION, false)
                    }
                }
            )
            Spacer(Modifier.height(8.dp))

            CompileFeatureRow(
                selectedGame = selectedGame,
                selectedMode = selectedCompileMode,
                checked = PerformanceFeature.COMPILE_OPTIMIZATION in selectedFeatures,
                enabled = isShizukuReady && !compileRunning,
                onConfigure = { compileGamePickerOpen = true },
                onCheckedChange = { checked ->
                    if (!checked) {
                        TurboSpaceRepository.setFeatureSelected(context, PerformanceFeature.COMPILE_OPTIMIZATION, false)
                    } else if (!TurboSpaceManager.isRecognizedGame(context, selectedGame)) {
                        compileGamePickerOpen = true
                    } else {
                        compileConfirmOpen = true
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = TurboColors.BorderGray)
            Spacer(Modifier.height(14.dp))

            Text("EXISTING OPTIMIZATIONS", color = TurboColors.HudPurple, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                "CPU and GPU Optimization remain separate from the session command list.",
                color = TurboColors.TextGray,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))

            MultiAppActionCard(
                title = "CPU Optimization",
                subtitle = "Choose only user-installed apps. This keeps the existing CPU implementation intact.",
                selectedApps = selectedCpuApps,
                isActive = cpuActive,
                isLoading = cpuLoading,
                enabled = isShizukuReady,
                onAppsSelected = { TurboSpaceRepository.setSelectedCpuApps(context, it) },
                onStart = { coroutineScope.launch { TurboSpaceRepository.startCpuOpt(context) } }
            )
            Text("COMMAND: cmd activity set-inactive <package_name> true", color = TurboColors.HudCyan, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
            Text("RESET: cmd activity set-inactive <package_name> false  |  fallback: cmd appops set <package_name> RUN_IN_BACKGROUND allow", color = TurboColors.HudGreen, fontSize = 8.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { coroutineScope.launch { TurboSpaceRepository.resetCpuOpt(context) } },
                enabled = isShizukuReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("↻ RESET CPU OPTIMIZATION", color = TurboColors.HudGreen)
            }

            Spacer(Modifier.height(10.dp))

            AppActionCard(
                title = "GPU Optimization",
                subtitle = "Choose one recognized game only. This keeps the existing GPU implementation intact.",
                selectedApp = selectedGpuGame,
                gamesOnly = true,
                isActive = gpuActive,
                isLoading = gpuLoading,
                enabled = isShizukuReady,
                onAppSelected = { TurboSpaceRepository.setSelectedGpuGame(context, it) },
                onStart = { coroutineScope.launch { TurboSpaceRepository.startGpuOpt(context) } }
            )
            Text("COMMAND: cmd game downscale <package_name> 0.9", color = TurboColors.HudCyan, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
            Text("RESET: cmd game downscale reset <package_name>  |  fallback: cmd game downscale <package_name> 1.0", color = TurboColors.HudGreen, fontSize = 8.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { coroutineScope.launch { TurboSpaceRepository.resetGpuOpt(context) } },
                enabled = isShizukuReady && selectedGpuGame != "NO TARGET SELECTED",
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("↻ RESET GPU OPTIMIZATION", color = TurboColors.HudGreen)
            }

            Spacer(Modifier.height(16.dp))
            Text("RESET COMMANDS", color = TurboColors.HudCrimson, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                "RESET restores detected original state, and uses real Android reset commands where available.",
                color = TurboColors.TextGray,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            PerformanceFeature.values().filter { it.resetCommand != null }.forEach { feature ->
                ResetCommandRow(feature)
                Spacer(Modifier.height(6.dp))
            }

            Button(
                onClick = onResetSession,
                enabled = !isResetting && busyFeatures.isEmpty() && !appOptimizationRunning && !compileRunning,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TurboColors.HudCrimson)
            ) {
                Text("↻ RESET ALL FEATURES", fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(22.dp))
        }
    }

    if (gamePerformancePickerOpen) {
        AppPickerDialog(
            gamesOnly = true,
            onDismiss = { gamePerformancePickerOpen = false },
            onAppSelected = {
                TurboSpaceRepository.setSelectedGame(context, it)
                gamePerformancePickerOpen = false
            }
        )
    }

    if (compileGamePickerOpen) {
        AppPickerDialog(
            gamesOnly = true,
            onDismiss = { compileGamePickerOpen = false },
            onAppSelected = {
                TurboSpaceRepository.setSelectedGame(context, it)
                compileGamePickerOpen = false
                compileModePickerOpen = true
            }
        )
    }

    if (compileModePickerOpen) {
        CompileModeDialog(
            selectedMode = selectedCompileMode,
            onDismiss = { compileModePickerOpen = false },
            onSelected = { mode ->
                TurboSpaceRepository.setSelectedCompileMode(context, mode)
                compileModePickerOpen = false
                if (TurboSpaceManager.isRecognizedGame(context, TurboSpaceRepository.selectedGame.value)) {
                    compileConfirmOpen = true
                }
            }
        )
    }

    if (appOptimizationConfirmOpen) {
        AlertDialog(
            onDismissRequest = { appOptimizationConfirmOpen = false },
            title = { Text("App Optimization") },
            text = {
                Text(
                    "Do you want to use this service?\n\nThis system optimization may take a long time. Turbo Space will wait for the real system process to finish and will not use an artificial delay.",
                    color = Color.White
                )
            },
            dismissButton = {
                TextButton(onClick = { appOptimizationConfirmOpen = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appOptimizationConfirmOpen = false
                        appOptimizationRunning = true
                        coroutineScope.launch {
                            val ok = TurboSpaceManager.runActionFeature(
                                context,
                                PerformanceFeature.APP_OPTIMIZATION,
                                selectedGame
                            )
                            appOptimizationRunning = false
                            if (ok) {
                                TurboSpaceRepository.setFeatureSelected(
                                    context,
                                    PerformanceFeature.APP_OPTIMIZATION,
                                    true
                                )
                                TurboSpaceManager.showToast(context, "SUCCESS — App Optimization")
                            } else {
                                TurboSpaceRepository.setFeatureSelected(
                                    context,
                                    PerformanceFeature.APP_OPTIMIZATION,
                                    false
                                )
                                TurboSpaceManager.showToast(context, "ERROR — App Optimization")
                            }
                        }
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    if (compileConfirmOpen) {
        AlertDialog(
            onDismissRequest = { compileConfirmOpen = false },
            title = { Text("Compile Optimization") },
            text = {
                Text(
                    "Do you want to use this service?\n\nThis ART compilation can take a long time. Turbo Space will wait for the real Android compiler process to finish and will not use an artificial delay.\n\nMode: ${selectedCompileMode.title}",
                    color = Color.White
                )
            },
            dismissButton = {
                TextButton(onClick = { compileConfirmOpen = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    compileConfirmOpen = false
                    compileRunning = true
                    coroutineScope.launch {
                        val pkg = TurboSpaceRepository.selectedGame.value
                        val ok = if (TurboSpaceManager.isRecognizedGame(context, pkg)) {
                            TurboSpaceManager.compileGame(context, pkg, selectedCompileMode) != TurboSpaceManager.CommandOutcome.BOTH_FAILED
                        } else false
                        if (ok) {
                            TurboSpaceRepository.setFeatureSelected(context, PerformanceFeature.COMPILE_OPTIMIZATION, true)
                            TurboSpaceManager.showToast(context, "SUCCESS — Compile Optimization")
                        } else {
                            TurboSpaceRepository.setFeatureSelected(context, PerformanceFeature.COMPILE_OPTIMIZATION, false)
                            TurboSpaceManager.showToast(context, "ERROR — Compile Optimization")
                        }
                        compileRunning = false
                    }
                }) { Text("OK") }
            }
        )
    }

    if (compileRunning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.64f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.padding(28.dp),
                color = TurboColors.HudObsidian.copy(alpha = 0.97f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TurboColors.HudCrimson.copy(alpha = 0.75f))
            ) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TurboColors.HudCrimson)
                    Spacer(Modifier.height(14.dp))
                    Text("COMPILING...", color = Color.White, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Waiting for the real Android compiler process to finish.",
                        color = TurboColors.TextGray,
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (appOptimizationRunning) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.padding(28.dp),
                color = TurboColors.HudObsidian.copy(alpha = 0.97f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TurboColors.HudCyan.copy(alpha = 0.65f))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = TurboColors.HudCyan)
                    Spacer(Modifier.height(14.dp))
                    Text("OPTIMIZING...", color = Color.White, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Waiting for the system optimization process to finish.",
                        color = TurboColors.TextGray,
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GamePerformanceConfigRow(
    selectedGame: String,
    checked: Boolean,
    enabled: Boolean,
    busy: Boolean = false,
    onSelectGame: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val label = if (selectedGame == "NO TARGET SELECTED") "Tap + to choose a game" else TurboSpaceManager.getAppLabel(context, selectedGame)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (checked) TurboColors.ActiveRedBg else TurboColors.CardBackground,
        border = BorderStroke(1.dp, if (checked) TurboColors.BrightRed else TurboColors.BorderGray)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎮", fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Game Performance", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Games only • current target: $label", color = TurboColors.TextGray, fontSize = 11.sp)
                Text("cmd game mode performance <package_name>", color = TurboColors.HudCyan, fontSize = 9.sp)
                Text("RESET: Restore the game's previous Game Mode", color = TurboColors.HudGreen, fontSize = 9.sp)
            }
            OutlinedButton(onClick = onSelectGame, enabled = enabled && !busy) { Text("＋") }
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = TurboColors.HudCyan)
            } else {
                Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
            }
        }
    }
}

@Composable
private fun CompileFeatureRow(
    selectedGame: String,
    selectedMode: CompileMode,
    checked: Boolean,
    enabled: Boolean,
    onConfigure: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val gameLabel = if (selectedGame == "NO TARGET SELECTED") "No game selected" else TurboSpaceManager.getAppLabel(context, selectedGame)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (checked) TurboColors.ActiveRedBg.copy(alpha = 0.92f) else TurboColors.CardBackground,
        border = BorderStroke(1.dp, if (checked) TurboColors.BrightRed else TurboColors.HudCrimson.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = "Compile Optimization",
                tint = TurboColors.HudCrimson,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Compile Optimization", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Games only • $gameLabel • ${selectedMode.title}", color = TurboColors.TextGray, fontSize = 11.sp)
                Text("1. Eco-Speed: cmd package compile -m space -f <package_name>", color = TurboColors.HudCyan, fontSize = 8.sp)
                Text("2. Smart-Adaptive: cmd package compile -m speed-profile -f <package_name>", color = TurboColors.HudCyan, fontSize = 8.sp)
                Text("3. Max-Performance: cmd package compile -m speed -f <package_name>", color = TurboColors.HudCyan, fontSize = 8.sp)
            }
            OutlinedButton(onClick = onConfigure, enabled = enabled) {
                Icon(Icons.Filled.Bolt, contentDescription = "Configure Compile", tint = TurboColors.HudCrimson)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun CompileModeDialog(
    selectedMode: CompileMode,
    onDismiss: () -> Unit,
    onSelected: (CompileMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚡ Compile Mode") },
        text = {
            Column {
                CompileMode.values().forEach { mode ->
                    val selected = mode == selectedMode
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelected(mode) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) TurboColors.ActiveRedBg else Color.Transparent,
                        border = BorderStroke(1.dp, if (selected) TurboColors.HudCrimson else TurboColors.BorderGray)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = { onSelected(mode) })
                                Spacer(Modifier.width(6.dp))
                                Text(mode.title, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Text(mode.description, color = TurboColors.TextGray, fontSize = 10.sp, modifier = Modifier.padding(start = 50.dp))
                            Text("cmd package compile -m ${mode.artFilter} -f <package_name>", color = TurboColors.HudCyan, fontSize = 8.sp, modifier = Modifier.padding(start = 50.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        confirmButton = {}
    )
}

@Composable
private fun FeatureSwitchRow(
    feature: PerformanceFeature,
    checked: Boolean,
    enabled: Boolean,
    busy: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (checked) TurboColors.ActiveRedBg else TurboColors.CardBackground,
        border = BorderStroke(1.dp, if (checked) TurboColors.BrightRed else TurboColors.BorderGray)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(feature.icon, fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(feature.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(feature.description, color = TurboColors.TextGray, fontSize = 10.sp)
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = TurboColors.HudCyan)
                } else {
                    Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("COMMAND  ${feature.command}", color = TurboColors.HudCyan, fontSize = 8.sp)
            Text(
                "RESET  ${feature.resetCommand ?: "None — action feature"}",
                color = if (feature.resetCommand == null) TurboColors.TextGray else TurboColors.HudGreen,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun ResetCommandRow(feature: PerformanceFeature) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TurboColors.CardBackground.copy(alpha = 0.88f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, TurboColors.BorderGray)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("${feature.icon} ${feature.title}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("RESET: ${feature.resetCommand ?: "None"}", color = TurboColors.HudGreen, fontSize = 8.sp)
        }
    }
}

// ============================================================================
// 12. GAMES SCREEN
// ============================================================================
@Composable
private fun GameSelectionScreen(
    selectedGame: String,
    onBack: () -> Unit,
    onGameSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val games = remember { TurboSpaceManager.getGameApps(context) }
    val filtered = remember(searchQuery, games) {
        if (searchQuery.isBlank()) games
        else games.filter {
            TurboSpaceManager.getAppLabel(context, it.packageName)
                .contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TurboColors.DarkBackground.copy(alpha = 0.94f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(12.dp))
            Text("🎮 Games", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search recognized games...") }
        )
        Spacer(Modifier.height(10.dp))

        if (games.isEmpty()) {
            Text(
                "No recognized games were found. The list only includes launchable packages identified as games by Android application metadata.",
                color = TurboColors.TextGray,
                modifier = Modifier.padding(12.dp)
            )
        } else if (filtered.isEmpty()) {
            Text("No matching games.", color = TurboColors.TextGray, modifier = Modifier.padding(12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.packageName }) { appInfo ->
                    val isSelected = appInfo.packageName == selectedGame
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onGameSelected(appInfo.packageName) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) TurboColors.ActiveRedBg else TurboColors.CardBackground,
                        border = BorderStroke(1.dp, if (isSelected) TurboColors.BrightRed else TurboColors.BorderGray)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(appInfo.packageName, Modifier.size(48.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(TurboSpaceManager.getAppLabel(context, appInfo.packageName), color = Color.White, fontWeight = FontWeight.Bold)
                                Text(appInfo.packageName, color = TurboColors.TextGray, fontSize = 10.sp)
                            }
                            if (isSelected) Text("SELECTED", color = TurboColors.HudGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 13. REUSABLE CPU / GPU UI
// ============================================================================
@Composable
private fun MultiAppActionCard(
    title: String,
    subtitle: String,
    selectedApps: Set<String>,
    isActive: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    onAppsSelected: (Set<String>) -> Unit,
    onStart: () -> Unit
) {
    var pickerOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) TurboColors.ActiveRedBg else TurboColors.CardBackground,
        border = BorderStroke(1.dp, if (isActive) TurboColors.BrightRed else TurboColors.BorderGray)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TurboColors.TextGray, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TurboColors.DarkBackground.copy(alpha = 0.55f))
                    .clickable(enabled = enabled) { pickerOpen = true }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedApps.isEmpty()) {
                    Text("＋ Select apps...", color = TurboColors.TextGray)
                } else {
                    selectedApps.take(3).forEach { pkg ->
                        AppIconImage(pkg, Modifier.size(30.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (selectedApps.size == 1) "1 app selected" else "${selectedApps.size} apps selected",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onStart,
                enabled = enabled && !isLoading && selectedApps.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Starting..." else "Start CPU Optimization")
            }
        }
    }

    if (pickerOpen) {
        MultiAppPickerDialog(
            currentSelected = selectedApps,
            onDismiss = { pickerOpen = false },
            onAppsSelected = {
                onAppsSelected(it)
                pickerOpen = false
            }
        )
    }
}

@Composable
private fun AppActionCard(
    title: String,
    subtitle: String,
    selectedApp: String,
    gamesOnly: Boolean,
    isActive: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    onAppSelected: (String) -> Unit,
    onStart: () -> Unit
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedLabel = if (selectedApp == "NO TARGET SELECTED") {
        if (gamesOnly) "Select a game..." else "Select an app..."
    } else {
        TurboSpaceManager.getAppLabel(context, selectedApp)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) TurboColors.ActiveRedBg else TurboColors.CardBackground,
        border = BorderStroke(1.dp, if (isActive) TurboColors.BrightRed else TurboColors.BorderGray)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TurboColors.TextGray, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TurboColors.DarkBackground.copy(alpha = 0.55f))
                    .clickable(enabled = enabled) { pickerOpen = true }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedApp != "NO TARGET SELECTED") {
                    AppIconImage(selectedApp, Modifier.size(34.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(selectedLabel, color = if (selectedApp == "NO TARGET SELECTED") TurboColors.TextGray else Color.White, modifier = Modifier.weight(1f))
                Text("›", color = TurboColors.TextGray, fontSize = 22.sp)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onStart,
                enabled = enabled && !isLoading && selectedApp != "NO TARGET SELECTED",
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Starting..." else "Start GPU Optimization")
            }
        }
    }

    if (pickerOpen) {
        AppPickerDialog(
            gamesOnly = gamesOnly,
            onDismiss = { pickerOpen = false },
            onAppSelected = {
                onAppSelected(it)
                pickerOpen = false
            }
        )
    }
}

@Composable
private fun MultiAppPickerDialog(
    currentSelected: Set<String>,
    onDismiss: () -> Unit,
    onAppsSelected: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selected by remember(currentSelected) { mutableStateOf(currentSelected.toSet()) }
    val allApps = remember { TurboSpaceManager.getThirdPartyApps(context) }
    val filtered = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter {
            TurboSpaceManager.getAppLabel(context, it.packageName).contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Apps") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search apps...") }
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = filtered.isNotEmpty() && filtered.all { it.packageName in selected },
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + filtered.map { it.packageName }
                            else selected - filtered.map { it.packageName }
                        }
                    )
                    Text("Select All Results", color = Color.White)
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(filtered, key = { it.packageName }) { appInfo ->
                        val checked = appInfo.packageName in selected
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = if (checked) selected - appInfo.packageName else selected + appInfo.packageName
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { value ->
                                selected = if (value) selected + appInfo.packageName else selected - appInfo.packageName
                            })
                            Spacer(Modifier.width(6.dp))
                            AppIconImage(appInfo.packageName, Modifier.size(34.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(TurboSpaceManager.getAppLabel(context, appInfo.packageName), color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAppsSelected(selected) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AppPickerDialog(
    gamesOnly: Boolean,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val allApps = remember(gamesOnly) {
        if (gamesOnly) TurboSpaceManager.getGameApps(context) else TurboSpaceManager.getThirdPartyApps(context)
    }
    val filtered = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter {
            TurboSpaceManager.getAppLabel(context, it.packageName).contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (gamesOnly) "Select Game" else "Select App") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(if (gamesOnly) "Search recognized games..." else "Search apps...") }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.packageName }) { appInfo ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onAppSelected(appInfo.packageName) }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(appInfo.packageName, Modifier.size(40.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(TurboSpaceManager.getAppLabel(context, appInfo.packageName), color = Color.White)
                                Text(appInfo.packageName, color = TurboColors.TextGray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {}
    )
}

@Composable
private fun AppIconImage(packageName: String, modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(10.dp)))
    } else {
        Text("🎮", modifier = modifier, textAlign = TextAlign.Center)
    }
}

// ============================================================================
// 14. HOME BACKGROUND
// ============================================================================
@Composable
private fun HomeBackground(refreshKey: Int) {
    val context = LocalContext.current
    val saved = remember(refreshKey) { TurboSpaceManager.loadBackground(context) }
    val rawUri = saved.uri ?: return
    val uri = remember(rawUri) { Uri.parse(rawUri) }

    Box(Modifier.fillMaxSize()) {
        if (saved.kind == "video") {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setVideoURI(uri)
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            start()
                        }
                    }
                }
            )
        } else {
            val loader = remember {
                ImageLoader.Builder(context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                        else add(GifDecoder.Factory())
                    }
                    .build()
            }
            AsyncImage(
                model = ImageRequest.Builder(context).data(uri).build(),
                imageLoader = loader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.03f),
                        Color.Black.copy(alpha = 0.10f)
                    )
                )
            )
        )
    }
}

// ============================================================================
// 15. FUTURISTIC GAME BAR / HUD
// ============================================================================
@Composable
private fun TurboSpaceGameBar(
    onDragOverlay: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedFeatures by TurboSpaceRepository.selectedFeatures.collectAsState()

    val cpuText = remember { mutableStateOf("N/A") }
    val ramText = remember { mutableStateOf("N/A") }
    val tempText = remember { mutableStateOf("N/A") }
    val monitor = remember { SystemMonitorEngine(context) }
    var gfxBusy by remember { mutableStateOf(false) }
    var gfxSuccess by remember { mutableStateOf<Boolean?>(null) }
    var coreOn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            monitor.update()
            cpuText.value = monitor.currentCpuUsage.value
            ramText.value = monitor.currentRamUsage.value
            tempText.value = monitor.currentTemperature.value
            kotlinx.coroutines.delay(1200)
        }
    }

    val quickFeatures = selectedFeatures.take(3)

    Surface(
        modifier = Modifier
            .width(350.dp)
            .height(124.dp),
        color = TurboColors.HudObsidian.copy(alpha = 0.85f),
        shape = CutCornerShape(14.dp),
        border = BorderStroke(1.dp, TurboColors.HudCyan.copy(alpha = 0.72f))
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 6.dp)) {
                // TOP LAYER: 3 equal live telemetry modules.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                onDragOverlay(dragAmount.x, dragAmount.y)
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HudTelemetryCell("CPU", cpuText.value, TurboColors.HudPurple, Modifier.weight(1f))
                    HudTelemetryCell("RAM", ramText.value.substringBefore(" /"), TurboColors.HudCyan, Modifier.weight(1f))
                    HudTelemetryCell("TEMP", tempText.value, TurboColors.HudCrimson, Modifier.weight(1f))
                }

                Spacer(Modifier.height(4.dp))

                // CENTER CORE: dedicated visual master switch.
                Box(
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp).clickable(enabled = !gfxBusy) { coreOn = !coreOn },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (coreOn) TurboColors.HudCrimson.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.35f),
                            border = BorderStroke(2.dp, if (coreOn) TurboColors.HudCrimson else TurboColors.BorderGray)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("⚡", color = if (coreOn) TurboColors.HudCrimson else TurboColors.TextGray, fontSize = 14.sp)
                                    Text("CORE", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("TURBO SPACE", color = TurboColors.HudCyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            Text(
                                if (coreOn) "MASTER ACTIVE" else "MASTER OFF",
                                color = if (coreOn) TurboColors.HudGreen else TurboColors.TextGray,
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Text("RESET", color = TurboColors.HudCrimson, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Text("CLOSE", color = TurboColors.TextGray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // BOTTOM LAYER: exactly four symmetric quick-access slots.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HudFeatureSlot(
                        icon = "⚡",
                        label = if (gfxBusy) "BOOSTING" else if (gfxSuccess == true) "GFX OK" else "GFX BOOST",
                        accent = if (gfxSuccess == false) TurboColors.HudCrimson else TurboColors.HudCyan,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (coreOn && !gfxBusy) {
                                gfxBusy = true
                                coroutineScope.launch {
                                    val outcome = TurboSpaceManager.runGfxBoost(context)
                                    gfxSuccess = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
                                    gfxBusy = false
                                    TurboSpaceManager.showToast(
                                        context,
                                        if (gfxSuccess == true) "SUCCESS — GFX Boost" else "ERROR — GFX Boost"
                                    )
                                }
                            }
                        }
                    )
                    quickFeatures.forEach { feature ->
                        HudFeatureSlot(
                            icon = feature.icon,
                            label = feature.title,
                            accent = if (coreOn) TurboColors.HudPurple else TurboColors.TextGray,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (coreOn) {
                                    coroutineScope.launch {
                                        TurboSpaceRepository.runFeatureFromOverlay(context, feature)
                                    }
                                }
                            }
                        )
                    }
                    repeat(3 - quickFeatures.size) {
                        HudFeatureSlot(
                            icon = "•",
                            label = "EMPTY",
                            accent = TurboColors.BorderGray,
                            modifier = Modifier.weight(1f),
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudTelemetryCell(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(CutCornerShape(5.dp))
            .background(accent.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.35f), CutCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, color = accent, fontSize = 5.sp, fontWeight = FontWeight.Bold)
                Text(value, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HudFeatureSlot(
    icon: String,
    label: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(31.dp)
            .clip(CutCornerShape(6.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.45f), CutCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(icon, fontSize = 11.sp)
            Spacer(Modifier.width(3.dp))
            Text(
                label.uppercase(),
                color = Color.White,
                fontSize = 5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
