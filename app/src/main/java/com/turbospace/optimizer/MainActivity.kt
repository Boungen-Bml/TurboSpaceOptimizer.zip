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
import android.view.View
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.atomic.AtomicBoolean
import rikka.shizuku.Shizuku

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.material3.LocalTextStyle


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

    // v2.3 Neon Wireframe Style - Lotus Evija Inspired
    val PureBlack = Color(0xFF000000)
    val NeonWhite = Color(0xFFFFFFFF)
    val NeonWhiteGlow = Color(0xFFB3E5FC)
    val NeonCyanGlow = Color(0xFF00E5FF)
    val WireframeStroke = Color(0xFFFFFFFF)
    val WireframeBorder = Color(0xFF000000)
    val HologramBlue = Color(0xFFE1F5FE)
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
        "cmd game mode reset <package_name>"
    ),
    THERMAL_CONTROL(
        "thermal_control", "🔥", "Thermal Control", true,
        "Applies a thermal-service override for the Game Session.",
        "cmd thermalservice override-status 0",
        "cmd thermalservice reset"
    ),
    PROCESS_CONTROL(
        "process_control", "📱", "Process Control", true,
        "Limits the system process count to 2 during the Game Session.",
        "cmd activity set-process-limit 2",
        "cmd activity set-process-limit default"
    ),
    IDLE_CONTROL(
        "idle_control", "🌙", "Idle Control", true,
        "Forces light idle mode during the Game Session.",
        "cmd deviceidle force-idle light",
        "cmd deviceidle unforce"
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
        "cmd package compile -m speed-profile -f <package_name>",
        "cmd package compile --reset <package_name>"
    )
}

// Fallback top-level constants for any legacy bare references
const val APP_OPTIMIZATION = "app_optimization"
const val GAME_PERFORMANCE = "game_performance"
const val TRIM_CACHE = "trim_cache"


private fun Set<String>.toPerformanceFeatures(): Set<PerformanceFeature> = mapNotNull { id ->
    PerformanceFeature.values().firstOrNull { it.id == id }
}.toSet()

private fun Set<PerformanceFeature>.toIds(): Set<String> = map { it.id }.toSet()

const val TURBO_APP_VERSION = "1.8"

enum class CyberCommandState { IDLE, INJECTING, SUCCESS, ERROR, UNSUPPORTED }

data class CyberCommandResult(
    val state: CyberCommandState,
    val exitCode: Int? = null,
    val output: String = ""
)

enum class CyberModule(
    val title: String,
    val icon: String,
    val commandTemplate: String,
    val needsGame: Boolean,
    val isReset: Boolean = false,
    val description: String
) {
    CORE_OVERDRIVE("CORE OVERDRIVE", "⚡", "cmd power set-fixed-performance-mode-enabled true", false, false, "Lock CPU/GPU max performance"),
    ANTI_KILL_SHIELD("ANTI-KILL SHIELD", "🛡️", "cmd deviceidle whitelist +<package_name>", true, false, "Whitelist game from Doze kill"),
    STANDBY_LOCKER("STANDBY LOCKER", "🔒", "cmd am set-standby-bucket <package_name> active", true, false, "Lock bucket to active"),
    SLEEP_BREAKER("SLEEP BREAKER", "🌙", "cmd activity set-inactive <package_name> false", true, false, "Prevent auto inactive - keep awake"),
    TOP_IGNITION("TOP PRIORITY", "🚀", "cmd activity set-process-group <package_name> top-app", true, false, "Move the active game to the top-app process group"),
    WIFI_OVERDRIVE("WIFI OVERDRIVE", "📶", "cmd wifi set-high-perf-enabled enabled", false, false, "WiFi high performance mode"),
    ULTRA_LATENCY("ULTRA LATENCY", "🎯", "cmd wifi force-low-latency-mode enabled", false, false, "WiFi low latency mode"),
    BANDWIDTH_LOCK("BANDWIDTH LOCK", "🔗", "cmd connectivity request-restricted-wifi", false, false, "Request restricted wifi - lock bandwidth"),
    CACHE_NOVA("CACHE FLUSH", "✨", "cmd package trim-caches 999G", false, false, "Trim system caches 999G"),
    BATTERY_BYPASS("NO THROTTLE", "🔋", "cmd jobscheduler standby-batched-jobs-execute", false, false, "Execute standby jobs immediately"),
    SIGNAL_RESET("SIGNAL RESET", "↻", "cmd connectivity factory-reset", false, false, "Reset connectivity signal state"),
    RAGNA_PURGE("RAGNA PURGE", "💀", "cmd activity kill-all", false, false, "Kill all background apps"),
    PING_SLASH("PING SLASH", "✈️", "cmd connectivity airplane-mode enable && sleep 1 && cmd connectivity airplane-mode disable", false, false, "Airplane toggle - clear ping spike"),
    SYSTEM_RESTORE("SYSTEM RESTORE", "↻", "cmd power set-fixed-performance-mode-enabled false && cmd deviceidle whitelist -<package_name>", true, true, "Reset all optimizations - restore defaults")
}



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
        if (!TurboSpaceManager.isRecognizedGame( packageName)) {
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

        if (feature.stateful && !TurboSpaceManager.isRecognizedGame( _selectedGame.value)) {
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
        if (!TurboSpaceManager.isRecognizedGame( packageName)) {
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
            if (!TurboSpaceManager.isRecognizedGame(pkg)) {
                TurboSpaceManager.showToast(context, "Select a game for GPU Optimization")
                return
            }
            val outcome = TurboSpaceManager.reduceGpuLoad(context, pkg)
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
        if (!TurboSpaceManager.isRecognizedGame(pkg)) {
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
            !TurboSpaceManager.isRecognizedGame( _selectedGame.value)
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
                if (!TurboSpaceManager.isRecognizedGame(pkg)) {
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
                if (pkg.isNullOrBlank() || !TurboSpaceManager.isRecognizedGame( pkg)) {
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
    const val COMMAND_TIMEOUT_MS = 10000L
    private const val APPS_DELIMITER = "||"
    const val FEATURE_DELIMITER = "|"

    const val KEY_SELECTED_GAME = "SELECTED_GAME"
    const val KEY_SELECTED_FEATURES = "SELECTED_FEATURES"
    const val KEY_CPU_APPS = "CPU_APPS"
    const val KEY_GPU_GAME = "GPU_GAME"
    const val KEY_CPU_ACTIVE = "CPU_ACTIVE"
    const val KEY_GPU_ACTIVE = "GPU_ACTIVE"
    const val KEY_COMPILE_MODE = "COMPILE_MODE"
    const val KEY_COMPILE_GAME = "COMPILE_GAME"
    const val KEY_PENDING_OVERLAY_START = "PENDING_OVERLAY_START"
    const val KEY_BG_URI = "HOME_BG_URI"
    const val KEY_BG_KIND = "HOME_BG_KIND"
    const val KEY_SESSION_ACTIVE = "SESSION_ACTIVE"
    const val KEY_SESSION_GAME = "SESSION_GAME"
    const val KEY_SESSION_GAME_MODE = "SESSION_GAME_MODE"
    const val KEY_SESSION_THERMAL = "SESSION_THERMAL"
    const val KEY_SESSION_PROCESS = "SESSION_PROCESS"
    const val KEY_SESSION_IDLE = "SESSION_IDLE"
    const val KEY_SESSION_STATEFUL_FEATURES = "SESSION_STATEFUL_FEATURES"
    private const val KEY_CYBER_ACTIVE_MODULES = "CYBER_ACTIVE_MODULES"
    private const val KEY_CYBER_ACTIVE_GAME = "CYBER_ACTIVE_GAME_PACKAGE"

    const val STABILIZER_PRO = "STABILIZER_PRO"
    const val PERFORMANCE_INJECT = "PERFORMANCE_INJECT"
    const val BOTH_FAILED = "BOTH_FAILED"

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
    data class CommandOutcomeDetailed(val success: Boolean, val output: String)
    enum class CommandOutcome { PRIMARY_SUCCESS, FALLBACK_SUCCESS, BOTH_FAILED }
    @Volatile
    var currentActiveGamePackage: String? = null
    var cyberActiveModules: MutableSet<String> = mutableSetOf()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // === App State ===
    fun loadAppState(context: Context): SavedAppState {
        val p = prefs(context)
        fun readApps(key: String): Set<String> {
            val raw = p.getString(key, "") ?: ""
            return if (raw.isBlank()) emptySet() else raw.split(APPS_DELIMITER).filter { it.isNotBlank() }.toSet()
        }
        val rawFeatures = p.getString(KEY_SELECTED_FEATURES, "") ?: ""
        val featureIds = if (rawFeatures.isBlank()) emptySet() else rawFeatures.split(FEATURE_DELIMITER).toSet()
        return SavedAppState(
            selectedGame = p.getString(KEY_SELECTED_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
            selectedFeatureIds = featureIds,
            cpuApps = readApps(KEY_CPU_APPS),
            gpuGame = p.getString(KEY_GPU_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
            compileMode = CompileMode.values().firstOrNull { it.key == p.getString(KEY_COMPILE_MODE, CompileMode.SMART_ADAPTIVE.key) } ?: CompileMode.SMART_ADAPTIVE,
            cpuActive = p.getBoolean(KEY_CPU_ACTIVE, false),
            gpuActive = p.getBoolean(KEY_GPU_ACTIVE, false)
        )
    }

    fun saveAppState(context: Context, selectedGame: String, selectedFeatureIds: Set<String>, cpuApps: Set<String>, gpuGame: String, compileMode: CompileMode, cpuActive: Boolean, gpuActive: Boolean) {
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

    fun setPendingOverlayStart(context: Context, pending: Boolean) { prefs(context).edit().putBoolean(KEY_PENDING_OVERLAY_START, pending).apply() }
    fun hasPendingOverlayStart(context: Context): Boolean = prefs(context).getBoolean(KEY_PENDING_OVERLAY_START, false)
    fun saveCompileTarget(context: Context, packageName: String?) { prefs(context).edit().putString(KEY_COMPILE_GAME, packageName).apply() }
    fun getSavedCompileTarget(context: Context): String? = prefs(context).getString(KEY_COMPILE_GAME, null)
    fun clearSavedCompileTarget(context: Context) { prefs(context).edit().remove(KEY_COMPILE_GAME).apply() }
    fun saveBackground(context: Context, uri: Uri?, kind: String?) { prefs(context).edit().putString(KEY_BG_URI, uri?.toString()).putString(KEY_BG_KIND, kind).apply() }
    fun loadBackground(context: Context): SavedBackground = SavedBackground(uri = prefs(context).getString(KEY_BG_URI, null), kind = prefs(context).getString(KEY_BG_KIND, null))
    fun hasSavedSession(context: Context): Boolean = prefs(context).getBoolean(KEY_SESSION_ACTIVE, false)

    // === Shizuku ===
    fun isShizukuAvailableAndGranted(): Boolean {
        return try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
    }
    fun requestShizukuPermission() {
        try { if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { Shizuku.requestPermission(1001) } } catch (_: Exception) { }
    }
    fun addPermissionResultListener(onResult: (granted: Boolean) -> Unit): Shizuku.OnRequestPermissionResultListener {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult -> if (requestCode == 1001) onResult(grantResult == PackageManager.PERMISSION_GRANTED) }
        Shizuku.addRequestPermissionResultListener(listener)
        return listener
    }
    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) { Shizuku.removeRequestPermissionResultListener(listener) }

    // === Game detection ===
        fun getGameApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        val all = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return all.filter { info ->
            if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@filter false
            if (!isRecognizedGame(info.packageName)) return@filter false
            val label = try { pm.getApplicationLabel(info).toString().lowercase() } catch (_: Exception) { "" }
            val pkg = info.packageName.lowercase()
            val gameKeywords = listOf("game", "roblox", "minecraft", "pubg", "free fire", "mlbb", "mobile legends", "cod", "call of duty", "genshin", "valorant", "rov", "arena", "legends", "clash", "brawl", "among", "fortnite", "efootball", "fifa", "8 ball", "pool", "ludo", "chess", "carrom", "racing", "shooter")
            val isGameCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { info.category == ApplicationInfo.CATEGORY_GAME } catch (_: Exception) { false }
            } else false
            isGameCategory || gameKeywords.any { label.contains(it) || pkg.contains(it.replace(" ", "")) }
        }.sortedBy { try { pm.getApplicationLabel(it).toString() } catch (_: Exception) { it.packageName } }
    }

    fun sanitizePackageName(pkg: String): String {
        if (pkg.isBlank() || pkg == "NO TARGET SELECTED") return "NO TARGET SELECTED"
        // Safety: only allow valid package name chars, prevent injection
        val sanitized = pkg.replace(Regex("[^a-zA-Z0-9._]"), "")
        // Block system packages
        val blocked = listOf("android", "system", "com.android", "com.google.android", "com.samsung", "miui")
        if (blocked.any { sanitized.startsWith(it) }) return "NO TARGET SELECTED"
        return sanitized.ifBlank { "NO TARGET SELECTED" }
    }



    fun getInstalledGamesOnly(context: Context): List<ApplicationInfo> = getGameApps(context)

    fun getThirdPartyApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA).filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }.sortedBy { getAppLabel(context, it.packageName) }
    }

    fun getThirdPartyApps(): List<String> {
        val res = executeCommandDetailedNoCtx("pm list packages -3")
        if (!res.success) return emptyList()
        return res.output.lines().mapNotNull { it.substringAfter("package:", "").trim().takeIf { it.isNotEmpty() } }
    }

    fun getAppLabel(context: Context, packageName: String): String {
        return try { val pm = context.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() } catch (_: Exception) { packageName }
    }

    fun getAppIcon(context: Context, packageName: String): android.graphics.drawable.Drawable? {
        return try { context.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }
    }

    fun showToast(context: Context, msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    // === Package name helpers ===
    private fun safePackageNameInternal(packageName: String): String? = packageName.takeIf { it.matches(Regex("^[A-Za-z0-9_.]+$")) }
    fun safePackageName(packageName: String): String? = safePackageNameInternal(packageName)
    fun safePackageName(): String? {
        val pkg = currentActiveGamePackage ?: return null
        if (pkg.isBlank() || pkg == "NO TARGET SELECTED") return null
        return if (pkg.matches(Regex("^[a-zA-Z0-9._]+$"))) pkg else null
    }

    // === Recognized game ===
    fun isRecognizedGame(packageName: String): Boolean {
        if (packageName.isBlank() || packageName == "NO TARGET SELECTED") return false
        if (packageName == "com.turbospace.optimizer") return false
        val low = packageName.lowercase()
        val blocked = listOf("facebook", "instagram", "whatsapp", "twitter", "tiktok", "snapchat", "discord", "telegram", "messenger", "youtube", "gmail", "chrome", "system", "android", "google", "settings", "launcher", "keyboard", "inputmethod", "com.android", "com.sec", "com.samsung", "com.miui", "com.coloros")
        if (blocked.any { low.contains(it) }) return false
        // Only allow if not system app and looks like game
        return true
    }
    fun isRecognizedGame(context: Context, packageName: String): Boolean = isRecognizedGame(packageName)

    // === Cyber modules ===
    private fun getCyberActiveModulesSet(context: Context): MutableSet<String> = prefs(context).getStringSet(KEY_CYBER_ACTIVE_MODULES, emptySet())?.toMutableSet() ?: mutableSetOf()
    private fun saveCyberActiveModules(context: Context, modules: Set<String>) { prefs(context).edit().putStringSet(KEY_CYBER_ACTIVE_MODULES, modules).apply() }
    fun saveCyberActiveModules() {
        try {
            val ctx = getAppContextViaReflection()
            ctx?.getSharedPreferences(PREFS_NAME, 0)?.edit()?.putStringSet("CYBER_ACTIVE_MODULES", cyberActiveModules)?.apply()
        } catch (_: Exception) {}
    }
    fun logActiveGamePackage(context: Context, packageName: String?) {
        val safe = packageName?.let { safePackageName(it) }
        currentActiveGamePackage = safe
        prefs(context).edit().putString(KEY_CYBER_ACTIVE_GAME, safe).apply()
    }
    fun logActiveGamePackage() { android.util.Log.i("TurboSpaceManager", "Active: $currentActiveGamePackage Modules: $cyberActiveModules") }
    fun restoreActiveGamePackage(context: Context) { currentActiveGamePackage = prefs(context).getString(KEY_CYBER_ACTIVE_GAME, null) }

    // === Command execution - FIXED for Shizuku API private access (FULLY FIXED) ===
    // Keep the existing app command path unchanged. Overlay commands use the
    // dedicated Shizuku path below so their status reflects the privileged process.
    // Fix: Shizuku.newProcess became private in new API, use ONLY reflection to avoid compile error
    fun newShizukuProcess(cmd: String): Process? {
        if (!isShizukuAvailableAndGranted()) return null
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
        } catch (_: Exception) {
            null
        }
    }

    fun executeCommandDetailedNoCtx(cmd: String): CommandOutcomeDetailed {
        val proc = try { Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)) } catch (_: Exception) { null }
            ?: return CommandOutcomeDetailed(false, "Command process unavailable")
        return readProcessResult(proc)
    }

    private fun executeOverlayCommandDetailed(cmd: String): CommandOutcomeDetailed {
        val proc = newShizukuProcess(cmd)
            ?: return CommandOutcomeDetailed(false, "Shizuku unavailable or permission denied")
        return readProcessResult(proc)
    }

    private fun readProcessResult(proc: Process): CommandOutcomeDetailed {
        return try {
            var stdout = ""
            var stderr = ""
            val stdoutReader = kotlin.concurrent.thread(start = true, name = "command-stdout") {
                stdout = runCatching { proc.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            }
            val stderrReader = kotlin.concurrent.thread(start = true, name = "command-stderr") {
                stderr = runCatching { proc.errorStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            }
            val finished = proc.waitFor(COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroy()
                stdoutReader.join(250)
                stderrReader.join(250)
                return CommandOutcomeDetailed(false, "Command timed out")
            }
            stdoutReader.join(500)
            stderrReader.join(500)
            val output = listOf(stdout.trim(), stderr.trim()).filter { it.isNotEmpty() }.joinToString("\n")
            CommandOutcomeDetailed(proc.exitValue() == 0, output)
        } catch (e: Exception) {
            runCatching { proc.destroy() }
            CommandOutcomeDetailed(false, e.message ?: "Command failed")
        }
    }

    fun resolveFocusedGamePackage(context: Context, preferred: String? = null): String? {
        val focused = executeOverlayCommandDetailed(
            "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -n 1"
        ).output.let { output ->
            Regex("([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+")
                .find(output)?.groupValues?.getOrNull(1)
        }
        val candidates = listOf(focused, currentActiveGamePackage, preferred, TurboSpaceRepository.selectedGame.value)
        val resolved = candidates.asSequence()
            .mapNotNull { it?.takeIf { value -> value.isNotBlank() && value != "NO TARGET SELECTED" } }
            .mapNotNull { safePackageName(it) }
            .firstOrNull { isRecognizedGame(it) }
        if (resolved != null) logActiveGamePackage(context, resolved)
        return resolved
    }

    fun getActiveModules(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_CYBER_ACTIVE_MODULES, emptySet()) ?: emptySet()
    }

    suspend fun executeCyberModule(context: Context, module: CyberModule, gamePackage: String): CyberCommandResult =
        withContext(Dispatchers.IO) {
            if (!isShizukuAvailableAndGranted()) {
                return@withContext CyberCommandResult(CyberCommandState.ERROR, output = "Shizuku permission required")
            }
            val targetPkg = resolveFocusedGamePackage(context, gamePackage)
            if (module.isReset) {
                if (targetPkg == null) {
                    return@withContext CyberCommandResult(CyberCommandState.ERROR, output = "No active game detected")
                }
                val rollback = listOf(
                    "cmd power set-fixed-performance-mode-enabled false",
                    "cmd deviceidle whitelist -$targetPkg",
                    "cmd am set-standby-bucket $targetPkg rare",
                    "cmd activity set-process-group $targetPkg background",
                    "cmd connectivity release-restricted-wifi",
                    "cmd wifi set-high-perf-enabled disabled",
                    "cmd wifi force-low-latency-mode disabled"
                )
                val results = rollback.map { executeOverlayCommandDetailed(it) }
                prefs(context).edit().remove(KEY_CYBER_ACTIVE_MODULES).remove(KEY_CYBER_ACTIVE_GAME).remove(KEY_SESSION_ACTIVE).apply()
                cyberActiveModules.clear()
                return@withContext CyberCommandResult(
                    if (results.all { it.success }) CyberCommandState.SUCCESS else CyberCommandState.ERROR,
                    output = results.filterNot { it.success }.joinToString("\n") { it.output }
                )
            }
            if (module.needsGame && targetPkg == null) {
                return@withContext CyberCommandResult(CyberCommandState.ERROR, output = "No active game detected")
            }
            val command = module.commandTemplate.replace("<package_name>", targetPkg.orEmpty())
            val result = executeOverlayCommandDetailed(command)
            if (result.success) {
                cyberActiveModules.add(module.name)
                saveCyberActiveModules(context, cyberActiveModules)
            }
            CyberCommandResult(
                state = if (result.success) CyberCommandState.SUCCESS else CyberCommandState.ERROR,
                exitCode = if (result.success) 0 else 1,
                output = result.output
            )
        }



    fun executeCommandDetailed(cmd: String): CommandOutcomeDetailed = executeCommandDetailedNoCtx(cmd)

    suspend fun executeCommandCapture(context: Context, cmd: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                val out = proc.inputStream.bufferedReader().readText()
                runInterruptible { proc.waitFor() }
                out
            } catch (_: Exception) { "" }
        }
    }

    suspend fun executeCommandDetailed(context: Context, primary: String, fallback: String? = null, timeoutMs: Long? = COMMAND_TIMEOUT_MS): CommandOutcome {
        val res = executeCommandDetailedNoCtx(primary)
        if (res.success) return CommandOutcome.PRIMARY_SUCCESS
        if (fallback != null) {
            val res2 = executeCommandDetailedNoCtx(fallback)
            if (res2.success) return CommandOutcome.FALLBACK_SUCCESS
        }
        return CommandOutcome.BOTH_FAILED
    }

    private fun getAppContextViaReflection(): Context? {
        return try {
            val at = Class.forName("android.app.ActivityThread")
            val app = at.getMethod("currentApplication").invoke(null)
            app as? Context
        } catch (_: Exception) { null }
    }

    // === Stateful features ===
    fun restoreSingleStatefulFeature(feature: String): Boolean {
        val pkg = safePackageName()
        val cmd = when (feature) {
            "CORE_OVERCLOCK", "CORE_OVERDRIVE", STABILIZER_PRO -> "cmd power set-fixed-performance-mode-enabled false"
            PERFORMANCE_INJECT -> if (pkg != null) "cmd game mode $pkg 1" else null
            "ANTI_KILL_SHIELD" -> if (pkg != null) "cmd deviceidle whitelist -$pkg" else null
            "STANDBY_LOCKER" -> if (pkg != null) "cmd am set-standby-bucket $pkg working_set" else null
            "ANTI_SLEEP_CORE" -> if (pkg != null) "cmd activity set-inactive $pkg true" else null
            "WIFI_OVERDRIVE" -> "cmd wifi set-high-perf-enabled disabled"
            "ULTRA_LOW_LATENCY" -> "cmd wifi force-low-latency-mode disabled"
            else -> null
        }
        return cmd?.let { executeCommandDetailedNoCtx(it).success } ?: false
    }

    suspend fun restoreSingleStatefulFeature(context: Context, feature: PerformanceFeature): Boolean {
        val id = feature.id
        val pkg = prefs(context).getString(KEY_SESSION_GAME, null) ?: currentActiveGamePackage
        val cmd = when (id) {
            "GAME_PERFORMANCE" -> if (pkg != null) "cmd game mode ${prefs(context).getString(KEY_SESSION_GAME_MODE, "standard")} $pkg" else null
            "THERMAL_CONTROL" -> "cmd thermalservice reset"
            "PROCESS_CONTROL" -> "cmd activity set-process-limit default"
            "IDLE_CONTROL" -> "cmd deviceidle unforce"
            else -> null
        }
        return if (cmd != null) executeCommandDetailed(context, cmd) != CommandOutcome.BOTH_FAILED else {
            val p = prefs(context)
            val ids = (p.getString(KEY_SESSION_STATEFUL_FEATURES, "") ?: "").split(FEATURE_DELIMITER).filter { it.isNotBlank() }.toMutableSet()
            ids.remove(id)
            val editor = p.edit().putString(KEY_SESSION_STATEFUL_FEATURES, ids.joinToString(FEATURE_DELIMITER))
            if (ids.isEmpty()) { editor.putBoolean(KEY_SESSION_ACTIVE, false).remove(KEY_SESSION_GAME).remove(KEY_SESSION_STATEFUL_FEATURES).remove(KEY_SESSION_GAME_MODE).remove(KEY_SESSION_THERMAL).remove(KEY_SESSION_PROCESS).remove(KEY_SESSION_IDLE) }
            editor.apply()
            true
        }
    }

    fun captureAndSaveSessionSnapshot() {
        val game = safePackageName() ?: return
        executeCommandDetailedNoCtx("cmd game mode $game")
        saveCyberActiveModules()
        logActiveGamePackage()
    }

    suspend fun captureAndSaveSessionSnapshot(context: Context, gamePackage: String, features: Set<PerformanceFeature>) {
        val p = prefs(context)
        val existingActive = p.getBoolean(KEY_SESSION_ACTIVE, false)
        val existingIds = if (existingActive) (p.getString(KEY_SESSION_STATEFUL_FEATURES, "") ?: "").split(FEATURE_DELIMITER).filter { it.isNotBlank() }.toMutableSet() else mutableSetOf()
        val requestedStateful = features.filter { it.stateful }.toSet()
        val requestedIds = requestedStateful.map { it.id }.toSet()
        if (requestedIds.isEmpty() && existingIds.isEmpty()) return
        val savedGamePackage = p.getString(KEY_SESSION_GAME, null)
        val sessionGame = savedGamePackage?.takeIf { it.isNotBlank() } ?: gamePackage
        var previousGameMode = p.getString(KEY_SESSION_GAME_MODE, null)
        var previousThermal: Int? = p.getInt(KEY_SESSION_THERMAL, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
        var previousProcess = p.getString(KEY_SESSION_PROCESS, null)
        var previousIdle: Boolean? = when ((p.getString(KEY_SESSION_IDLE, null) ?: "").lowercase()) { "true" -> true; "false" -> false; else -> null }
        if (PerformanceFeature.GAME_PERFORMANCE in requestedStateful && PerformanceFeature.GAME_PERFORMANCE.id !in existingIds) { previousGameMode = queryGameMode(context, gamePackage); existingIds += PerformanceFeature.GAME_PERFORMANCE.id }
        if (PerformanceFeature.THERMAL_CONTROL in requestedStateful && PerformanceFeature.THERMAL_CONTROL.id !in existingIds) { previousThermal = queryThermalOverride(context); existingIds += PerformanceFeature.THERMAL_CONTROL.id }
        if (PerformanceFeature.PROCESS_CONTROL in requestedStateful && PerformanceFeature.PROCESS_CONTROL.id !in existingIds) { previousProcess = queryProcessLimit(context); existingIds += PerformanceFeature.PROCESS_CONTROL.id }
        if (PerformanceFeature.IDLE_CONTROL in requestedStateful && PerformanceFeature.IDLE_CONTROL.id !in existingIds) { previousIdle = queryForcedIdle(context); existingIds += PerformanceFeature.IDLE_CONTROL.id }
        val editor = p.edit().putBoolean(KEY_SESSION_ACTIVE, true).putString(KEY_SESSION_GAME, sessionGame).putString(KEY_SESSION_STATEFUL_FEATURES, (existingIds + requestedIds).joinToString(FEATURE_DELIMITER))
        if (previousGameMode != null) editor.putString(KEY_SESSION_GAME_MODE, previousGameMode)
        if (previousThermal != null) editor.putInt(KEY_SESSION_THERMAL, previousThermal)
        if (previousProcess != null) editor.putString(KEY_SESSION_PROCESS, previousProcess)
        if (previousIdle != null) editor.putString(KEY_SESSION_IDLE, previousIdle.toString())
        editor.apply()
    }

    fun applySelectedSessionFeatures(features: Set<String>): Set<String> {
        val ok = mutableSetOf<String>()
        val pkg = safePackageName()
        for (f in features) {
            val cmd = when (f) {
                "CORE_OVERCLOCK", "CORE_OVERDRIVE" -> "cmd power set-fixed-performance-mode-enabled true"
                PERFORMANCE_INJECT -> if (pkg != null) "cmd game mode performance $pkg" else null
                "ANTI_KILL_SHIELD" -> if (pkg != null) "cmd deviceidle whitelist +$pkg" else null
                "STANDBY_LOCKER", STABILIZER_PRO -> if (pkg != null) "cmd am set-standby-bucket $pkg active" else null
                "ANTI_SLEEP_CORE" -> if (pkg != null) "cmd activity set-inactive $pkg false" else null
                "TOP_APP_IGNITION" -> if (pkg != null) "cmd activity set-scheduler-group $pkg top-app" else null
                "BATTERY_BYPASS" -> "cmd jobscheduler standby-batched-jobs-execute"
                "RAGNA_PURGE" -> "cmd activity kill-all"
                "CACHE_NOVA" -> "cmd package trim-caches 999G"
                "BANDWIDTH_LOCK" -> "cmd connectivity request-restricted-wifi"
                "PING_SLASH" -> "cmd connectivity airplane-mode enable && sleep 1 && cmd connectivity airplane-mode disable"
                "WIFI_OVERDRIVE" -> "cmd wifi set-high-perf-enabled enabled"
                "ULTRA_LOW_LATENCY" -> "cmd wifi force-low-latency-mode enabled"
                else -> f
            }
            if (cmd != null && executeCommandDetailedNoCtx(cmd).success) { ok.add(f); cyberActiveModules.add(f) }
        }
        saveCyberActiveModules()
        return ok
    }

    suspend fun applySelectedSessionFeatures(context: Context, gamePackage: String, features: Set<PerformanceFeature>): Set<PerformanceFeature> {
        val successful = mutableSetOf<PerformanceFeature>()
        features.forEach { feature ->
            val ok = when (feature) {
                PerformanceFeature.GAME_PERFORMANCE -> executeCommandDetailed(context, "cmd game mode performance $gamePackage") != CommandOutcome.BOTH_FAILED
                PerformanceFeature.THERMAL_CONTROL -> executeCommandDetailed(context, "cmd thermalservice override-status 0") != CommandOutcome.BOTH_FAILED
                PerformanceFeature.PROCESS_CONTROL -> executeCommandDetailed(context, "cmd activity set-process-limit 2") != CommandOutcome.BOTH_FAILED
                PerformanceFeature.IDLE_CONTROL -> executeCommandDetailed(context, "cmd deviceidle force-idle light") != CommandOutcome.BOTH_FAILED
                else -> runActionFeature(context, feature, gamePackage)
            }
            if (ok) successful += feature
        }
        return successful
    }

    fun runActionFeature(feature: String): Boolean {
        val cmd = when (feature) {
            "RAGNA_PURGE" -> "cmd activity kill-all"
            "CACHE_NOVA" -> "cmd package trim-caches 999G"
            "BANDWIDTH_LOCK" -> "cmd connectivity request-restricted-wifi"
            "PING_SLASH" -> "cmd connectivity airplane-mode enable && sleep 1 && cmd connectivity airplane-mode disable"
            else -> feature
        }
        val res = executeCommandDetailedNoCtx(cmd)
        if (res.success) { cyberActiveModules.add(feature); saveCyberActiveModules() }
        return res.success
    }

    suspend fun runGfxBoost(context: Context): CommandOutcome = executeCommandDetailed(context, "cmd activity boost-gfx")
    suspend fun runActionFeature(context: Context, feature: PerformanceFeature, gamePackage: String? = null): Boolean {
        val command = when (feature) {
            PerformanceFeature.RESOURCE_CLEANUP -> "cmd activity purge-process-resources"
            PerformanceFeature.APP_OPTIMIZATION -> "cmd package bg-dexopt-job"
            PerformanceFeature.INPUT_CONFIGURATION -> "cmd inputflinger reload-config"
            PerformanceFeature.TRIM_CACHE -> "pm trim-caches 999G"
            else -> null
        }
        if (feature == PerformanceFeature.COMPILE_OPTIMIZATION) {
            val pkg = gamePackage?.takeIf { isRecognizedGame(it) } ?: return false
            val mode = TurboSpaceRepository.selectedCompileMode.value
            return compileGame(context, pkg, mode) != CommandOutcome.BOTH_FAILED
        }
        if (feature == PerformanceFeature.APP_OPTIMIZATION || feature == PerformanceFeature.TRIM_CACHE) {
            return command?.let { executeCommandDetailed(context, it, timeoutMs = null) != CommandOutcome.BOTH_FAILED } ?: false
        }
        return command?.let { executeCommandDetailed(context, it) != CommandOutcome.BOTH_FAILED } ?: false
    }

    // Compile
    suspend fun compileGame(context: Context, packageName: String, mode: CompileMode): CommandOutcome {
        if (!isRecognizedGame(packageName)) return CommandOutcome.BOTH_FAILED
        val primary = "cmd package compile -m ${mode.artFilter} -f $packageName"
        val fallback = "pm compile -m ${mode.artFilter} -f $packageName"
        val outcome = executeCommandDetailed(context, primary, fallback, timeoutMs = null)
        if (outcome != CommandOutcome.BOTH_FAILED) saveCompileTarget(context, packageName)
        return outcome
    }
    suspend fun resetCompileGame(context: Context, packageName: String): CommandOutcome {
        if (!isRecognizedGame(packageName)) return CommandOutcome.BOTH_FAILED
        val primary = "cmd package compile --reset $packageName"
        val fallback = "pm compile --reset $packageName"
        val outcome = executeCommandDetailed(context, primary, fallback, timeoutMs = null)
        if (outcome != CommandOutcome.BOTH_FAILED) clearSavedCompileTarget(context)
        return outcome
    }
    fun resetCompileGame(packageName: String): CommandOutcomeDetailed = executeCommandDetailedNoCtx("cmd package compile --reset $packageName")

    // CPU/GPU
    fun reduceCpuLoad(packageName: String): CommandOutcomeDetailed {
        currentActiveGamePackage = packageName
        val res = executeCommandDetailedNoCtx("cmd power set-fixed-performance-mode-enabled true")
        if (res.success) { cyberActiveModules.add("CORE_OVERCLOCK"); saveCyberActiveModules() }
        return res
    }
    suspend fun reduceCpuLoad(context: Context, packageName: String): CommandOutcome {
        currentActiveGamePackage = packageName
        return executeCommandDetailed(context, "cmd power set-fixed-performance-mode-enabled true")
    }
    fun reduceGpuLoad(packageName: String): CommandOutcomeDetailed {
        currentActiveGamePackage = packageName
        val res = executeCommandDetailedNoCtx("cmd game mode performance $packageName")
        if (res.success) { cyberActiveModules.add(PERFORMANCE_INJECT); saveCyberActiveModules() }
        return res
    }
    suspend fun reduceGpuLoad(context: Context, packageName: String): CommandOutcome {
        currentActiveGamePackage = packageName
        return executeCommandDetailed(context, "cmd game mode performance $packageName")
    }
    fun restoreCpuLoad(packageName: String): CommandOutcomeDetailed {
        val res = executeCommandDetailedNoCtx("cmd power set-fixed-performance-mode-enabled false")
        cyberActiveModules.remove("CORE_OVERCLOCK"); saveCyberActiveModules()
        return res
    }
    suspend fun restoreCpuLoad(context: Context, packageName: String): CommandOutcome {
        return executeCommandDetailed(context, "cmd power set-fixed-performance-mode-enabled false")
    }
    fun restoreGpu(packageName: String): CommandOutcomeDetailed {
        val res = executeCommandDetailedNoCtx("cmd game mode $packageName 1")
        cyberActiveModules.remove(PERFORMANCE_INJECT); saveCyberActiveModules()
        return res
    }
    suspend fun restoreGpu(context: Context, packageName: String): CommandOutcome {
        return executeCommandDetailed(context, "cmd game mode $packageName 1")
    }

    // Restore session
    fun restoreSavedSession(features: Set<String>): Boolean {
        val pkg = safePackageName() ?: currentActiveGamePackage
        val rollback = if (pkg != null) listOf(
            "cmd power set-fixed-performance-mode-enabled false",
            "cmd deviceidle whitelist -$pkg",
            "cmd am set-standby-bucket $pkg working_set",
            "cmd activity set-inactive $pkg true",
            "cmd wifi set-high-perf-enabled disabled",
            "cmd wifi force-low-latency-mode disabled"
        ) else listOf(
            "cmd power set-fixed-performance-mode-enabled false",
            "cmd wifi set-high-perf-enabled disabled",
            "cmd wifi force-low-latency-mode disabled"
        )
        var allOk = true
        for (c in rollback) if (!executeCommandDetailedNoCtx(c).success) allOk = false
        cyberActiveModules.clear(); saveCyberActiveModules()
        return allOk
    }
    suspend fun restoreSavedSession(context: Context): RestoreResult {
        val p = prefs(context)
        val isActive = p.getBoolean(KEY_SESSION_ACTIVE, false)
        if (!isActive) return RestoreResult(true, emptyList(), emptyList())
        val gamePackage = p.getString(KEY_SESSION_GAME, null) ?: ""
        val raw = p.getString(KEY_SESSION_STATEFUL_FEATURES, "") ?: ""
        val statefulFeatures = raw.split(FEATURE_DELIMITER).filter { it.isNotBlank() }.mapNotNull { id -> PerformanceFeature.values().firstOrNull { it.id == id } }.toSet()
        val previousGameMode = p.getString(KEY_SESSION_GAME_MODE, null)
        val previousThermal = p.getInt(KEY_SESSION_THERMAL, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
        val previousProcess = p.getString(KEY_SESSION_PROCESS, null)
        val previousIdle = when ((p.getString(KEY_SESSION_IDLE, null) ?: "").lowercase()) { "true" -> true; "false" -> false; else -> null }
        var allOk = true
        val warnings = mutableListOf<String>()
        val failedFeatures = mutableListOf<String>()
        if (PerformanceFeature.GAME_PERFORMANCE in statefulFeatures && gamePackage.isNotBlank() && previousGameMode != null) {
            val ok = executeCommandDetailed(context, "cmd game mode $previousGameMode $gamePackage") != CommandOutcome.BOTH_FAILED
            if (!ok) { allOk = false; failedFeatures += "Game Performance" }
        } else if (PerformanceFeature.GAME_PERFORMANCE in statefulFeatures && gamePackage.isNotBlank() && previousGameMode == null) {
            allOk = false; failedFeatures += "Game Performance"; warnings += "Previous Game Mode could not be detected; it was left unchanged."
        }
        if (PerformanceFeature.THERMAL_CONTROL in statefulFeatures && previousThermal != null) {
            val resetOk = executeCommandDetailed(context, "cmd thermalservice reset") != CommandOutcome.BOTH_FAILED
            if (!resetOk) { allOk = false; failedFeatures += "Thermal Control" } else if (previousThermal >= 0) {
                val reapplyOk = executeCommandDetailed(context, "cmd thermalservice override-status $previousThermal") != CommandOutcome.BOTH_FAILED
                if (!reapplyOk) { allOk = false; failedFeatures += "Thermal Control" }
            }
        } else if (PerformanceFeature.THERMAL_CONTROL in statefulFeatures && p.contains(KEY_SESSION_THERMAL)) {
            val ok = executeCommandDetailed(context, "cmd thermalservice reset") != CommandOutcome.BOTH_FAILED
            if (!ok) { allOk = false; failedFeatures += "Thermal Control" }
            warnings += "Original Thermal Override state was not readable; the system reset command was used."
        }
        if (PerformanceFeature.PROCESS_CONTROL in statefulFeatures && previousProcess != null) {
            val cmd = if (previousProcess.equals("default", ignoreCase = true)) "cmd activity set-process-limit default" else "cmd activity set-process-limit $previousProcess"
            val ok = executeCommandDetailed(context, cmd) != CommandOutcome.BOTH_FAILED
            if (!ok) { allOk = false; failedFeatures += "Process Control" }
        } else if (PerformanceFeature.PROCESS_CONTROL in statefulFeatures) {
            allOk = false; failedFeatures += "Process Control"; warnings += "Original Process Limit was not readable; no blind replacement value was applied."
        }
        if (PerformanceFeature.IDLE_CONTROL in statefulFeatures && previousIdle != null) {
            val cmd = if (previousIdle) "cmd deviceidle force-idle light" else "cmd deviceidle unforce"
            val ok = executeCommandDetailed(context, cmd) != CommandOutcome.BOTH_FAILED
            if (!ok) { allOk = false; failedFeatures += "Idle Control" }
        } else if (PerformanceFeature.IDLE_CONTROL in statefulFeatures) {
            allOk = false; failedFeatures += "Idle Control"; warnings += "Original Idle state was not readable; no blind replacement state was applied."
        }
        p.edit().remove(KEY_SESSION_GAME).remove(KEY_SESSION_STATEFUL_FEATURES).remove(KEY_SESSION_GAME_MODE).remove(KEY_SESSION_THERMAL).remove(KEY_SESSION_PROCESS).remove(KEY_SESSION_IDLE).putBoolean(KEY_SESSION_ACTIVE, false).apply()
        return RestoreResult(allOk, warnings, failedFeatures.distinct())
    }


    suspend fun executeShizukuCommand(context: Context, cmd: String): String {
        return executeCommandCapture(context, cmd)
    }

    fun executeShizukuCommand(cmd: String): Boolean {
        return executeCommandDetailedNoCtx(cmd).success
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
        val match = Regex("(?i)mOverrideStatus\\s*[:=]\\s*(-?\\d+)").find(output) ?: Regex("(?i)override.?status\\s*[:=]\\s*(-?\\d+)").find(output)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
    private suspend fun queryForcedIdle(context: Context): Boolean? {
        val output = executeCommandCapture(context, "dumpsys deviceidle")
        Regex("(?i)mForceIdle\\s*[=:]\\s*(true|false)").find(output)?.let { return it.groupValues[1].equals("true", true) }
        Regex("(?i)mForceLevel\\s*[=:]\\s*(\\d+)").find(output)?.let { return it.groupValues[1].toIntOrNull()?.let { value -> value != 0 } }
        return null
    }
}


// ============================================================================
// 8. OVERLAY SERVICE - DUAL BARS LEFT/RIGHT INDEPENDENT (FIXED TOUCH BUG)
// ============================================================================
class GameSpaceOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    // THREE fully independent windows: left panel pinned to left edge, right panel pinned to right edge, collapsed bubble
    private lateinit var leftView: ComposeView
    private lateinit var rightView: ComposeView
    private lateinit var bubbleView: ComposeView
    private lateinit var leftParams: WindowManager.LayoutParams
    private lateinit var rightParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()
    private val shuttingDown = AtomicBoolean(false)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private fun makeView(content: @Composable () -> Unit) = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
        setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
        setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
        setContent { content() }
    }

    private fun baseParams(type: Int, w: Int, h: Int, g: Int) = WindowManager.LayoutParams(
        w, h, type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = g; x = 0; y = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    override fun onCreate() {
        super.onCreate()
        TurboSpaceRepository.setOverlayReady(false)
        try { startForegroundImmediately() } catch (_: Exception) { stopSelf(); return }
        savedStateRegistryController.performRestore(null)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "ERROR — Overlay permission missing", Toast.LENGTH_LONG).show()
            stopSelf(); return
        }
        HudController.reset()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val dm = resources.displayMetrics
        val landscapeW = maxOf(dm.widthPixels, dm.heightPixels)

        leftView = makeView {
            HudLeftPanel(
                onCollapse = { setExpanded(false) },
                onClose = ::rollbackAndClose,
                onMasterOff = ::rollbackAndClose
            )
        }
        rightView = makeView { HudRightPanel() }
        bubbleView = makeView {
            HudCollapsedIcon(onDrag = ::moveBy, onDragFinished = ::snapToEdge, onExpand = { setExpanded(true) })
        }
        leftParams = baseParams(type, (landscapeW * 0.34f).toInt(), WindowManager.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.START)
        rightParams = baseParams(type, (landscapeW * 0.37f).toInt(), WindowManager.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.END)
        bubbleParams = baseParams(type, dpToPx(58), dpToPx(58), Gravity.TOP or Gravity.START).apply { y = dpToPx(80) }
        try {
            windowManager.addView(leftView, leftParams)
            windowManager.addView(rightView, rightParams)
            windowManager.addView(bubbleView, bubbleParams)
            setExpanded(true)
            TurboSpaceRepository.setOverlayReady(true)
            monitorGameTermination()
        } catch (e: Exception) {
            Toast.makeText(this, "ERROR — HUD could not be displayed: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun startForegroundImmediately() {
        val channelId = "turbo_overlay_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(
            NotificationChannel(channelId, "Turbo Space HUD", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Turbo Space HUD").setContentText("Universal game booster active")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1001, notification)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density.coerceAtLeast(1f)).toInt().coerceAtLeast(1)

    private fun setExpanded(expanded: Boolean) {
        HudController.expanded.value = expanded
        HudController.inactive.value = false
        if (::leftView.isInitialized) leftView.visibility = if (expanded) View.VISIBLE else View.GONE
        if (::rightView.isInitialized) rightView.visibility = if (expanded) View.VISIBLE else View.GONE
        if (::bubbleView.isInitialized) bubbleView.visibility = if (expanded) View.GONE else View.VISIBLE
    }

    private fun moveBy(dx: Float, dy: Float) {
        if (!::bubbleParams.isInitialized) return
        val dm = resources.displayMetrics
        bubbleParams.x = (bubbleParams.x + dx.toInt()).coerceIn(-dpToPx(29), (dm.widthPixels - dpToPx(29)).coerceAtLeast(0))
        bubbleParams.y = (bubbleParams.y + dy.toInt()).coerceIn(0, (dm.heightPixels - dpToPx(58)).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
    }

    // Snaps halfway into the nearest screen border (spec: 50% hidden after 3s idle)
    private fun snapToEdge() {
        if (!::bubbleParams.isInitialized) return
        val dm = resources.displayMetrics
        bubbleParams.x = if (bubbleParams.x + dpToPx(29) < dm.widthPixels / 2) -dpToPx(29) else dm.widthPixels - dpToPx(29)
        runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
    }

    private fun monitorGameTermination() {
        lifecycleScope.launch(Dispatchers.IO) {
            var misses = 0
            while (!shuttingDown.get()) {
                delay(3000)
                val pkg = TurboSpaceManager.currentActiveGamePackage ?: continue
                val alive = TurboSpaceManager.executeShizukuCommand("pidof $pkg")
                misses = if (alive) 0 else misses + 1
                if (misses >= 3) { rollbackAndClose(); break }
            }
        }
    }

    private fun rollbackAndClose() {
        if (!shuttingDown.compareAndSet(false, true)) return
        lifecycleScope.launch(Dispatchers.IO) {
            TurboSpaceManager.executeCyberModule(this@GameSpaceOverlayService, CyberModule.SYSTEM_RESTORE,
                TurboSpaceManager.currentActiveGamePackage.orEmpty())
            withContext(Dispatchers.Main) { stopSelf() }
        }
    }

    override fun onDestroy() {
        TurboSpaceRepository.setOverlayReady(false)
        if (!shuttingDown.get() && TurboSpaceManager.currentActiveGamePackage != null) {
            val pkg = TurboSpaceManager.currentActiveGamePackage.orEmpty()
            kotlin.concurrent.thread(start = true, name = "overlay-rollback") {
                val commands = listOf(
                    "cmd power set-fixed-performance-mode-enabled false",
                    "cmd deviceidle whitelist -$pkg",
                    "cmd am set-standby-bucket $pkg rare",
                    "cmd activity set-process-group $pkg background",
                    "cmd connectivity release-restricted-wifi",
                    "cmd wifi set-high-perf-enabled disabled",
                    "cmd wifi force-low-latency-mode disabled"
                )
                commands.forEach { TurboSpaceManager.executeShizukuCommand(it) }
            }
        }
        runCatching { if (::leftView.isInitialized) windowManager.removeView(leftView) }
        runCatching { if (::rightView.isInitialized) windowManager.removeView(rightView) }
        runCatching { if (::bubbleView.isInitialized) windowManager.removeView(bubbleView) }
        _viewModelStore.clear()
        super.onDestroy()
    }
}


private enum class TurboScreen { HOME, GAMES, SETTINGS }


// ============================================================================
// v2.3 - NEON WIREFRAME STYLE - SPLASH SCREEN
// Style: Lotus Evija Wireframe - Pure Black + White Neon Glowing Outline 2D
// ============================================================================

@Composable
fun NeonWireframeSLogo(
    modifier: Modifier = Modifier,
    glowAlpha: Float = 1f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // Create Paint with Blur for glow effect (BlurMaskFilter equivalent)
        val glowPaint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = w * 0.06f
            color = android.graphics.Color.WHITE
            maskFilter = BlurMaskFilter(w * 0.08f, BlurMaskFilter.Blur.NORMAL)
            alpha = (180 * glowAlpha).toInt()
        }
        
        val borderPaint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = w * 0.09f
            color = android.graphics.Color.BLACK
            alpha = (255 * glowAlpha).toInt()
        }
        
        val corePaint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = w * 0.045f
            color = android.graphics.Color.WHITE
            alpha = (255 * glowAlpha).toInt()
        }
        
        // S Path - wireframe style S
        val path = android.graphics.Path().apply {
            val cx = w / 2f
            val cy = h / 2f
            val scale = w * 0.38f
            
            // Draw S as wireframe - using bezier curves
            moveTo(cx + scale * 0.8f, cy - scale * 0.9f)
            cubicTo(cx + scale * 0.2f, cy - scale * 1.1f, cx - scale * 0.8f, cy - scale * 0.7f, cx - scale * 0.3f, cy - scale * 0.2f)
            cubicTo(cx + scale * 0.2f, cy + scale * 0.1f, cx + scale * 0.9f, cy + scale * 0.1f, cx + scale * 0.5f, cy + scale * 0.6f)
            cubicTo(cx + scale * 0.1f, cy + scale * 1.1f, cx - scale * 0.7f, cy + scale * 1.0f, cx - scale * 0.8f, cy + scale * 0.5f)
        }
        
        drawIntoCanvas { canvas ->
            // Draw border (black) first
            canvas.nativeCanvas.drawPath(path, borderPaint)
            // Draw glow
            canvas.nativeCanvas.drawPath(path, glowPaint)
            // Draw core white neon
            canvas.nativeCanvas.drawPath(path, corePaint)
            
            // Second inner glow for hologram effect
            val innerGlow = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = w * 0.02f
                color = android.graphics.Color.parseColor("#B3E5FC")
                maskFilter = BlurMaskFilter(w * 0.04f, BlurMaskFilter.Blur.NORMAL)
                alpha = (120 * glowAlpha).toInt()
            }
            canvas.nativeCanvas.drawPath(path, innerGlow)
        }
    }
}


@Composable
fun TurboSpaceSplashScreen(
    onFinished: () -> Unit
) {
    // Design like example image: Pure black, white particles, hexagon, S glowing, TURBO SPACE spaced
    val sScale = remember { androidx.compose.animation.core.Animatable(0.2f) }
    val sAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val orbit = remember { androidx.compose.animation.core.Animatable(0f) }
    val textAlpha = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { sScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        sAlpha.animateTo(1f, tween(400))
        launch {
            while (true) {
                orbit.animateTo(360f, tween(3000, easing = androidx.compose.animation.core.LinearEasing))
                orbit.snapTo(0f)
            }
        }
        delay(400)
        textAlpha.animateTo(1f, tween(600))
        delay(2500)
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // Built-in background particles + hexagon - same as HomeBackground
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val time = orbit.value
            // Particles
            for (i in 0..30) {
                val x = (w * 0.1f + (i * 83 % w)) + kotlin.math.sin((time + i*20)*0.01f)*12f
                val y = (h * 0.1f + (i * 127 % h)) + (time * 10 + i*5) % h * 0.3f
                drawCircle(Color.White.copy(alpha = 0.22f + (i % 3)*0.08f), radius = 1.6f + (i % 4), center = Offset(x % w, y % h))
            }
            // Hexagon
            val cx = w/2f; val cy = h/2f; val r = w*0.38f
            val path = Path().apply {
                for (j in 0..5) {
                    val ang = Math.toRadians((60*j -30).toDouble())
                    val hx = cx + r * kotlin.math.cos(ang).toFloat()
                    val hy = cy + r * kotlin.math.sin(ang).toFloat()
                    if (j==0) moveTo(hx,hy) else lineTo(hx,hy)
                }
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.04f), style = Stroke(0.5f))
            // Corner brackets
            val bl = 18.dp.toPx(); val st = 0.8.dp.toPx(); val c = Color.White.copy(0.2f)
            drawLine(c, Offset(14.dp.toPx(), h-14.dp.toPx()), Offset(14.dp.toPx()+bl, h-14.dp.toPx()), st)
            drawLine(c, Offset(14.dp.toPx(), h-14.dp.toPx()), Offset(14.dp.toPx(), h-14.dp.toPx()-bl), st)
            drawLine(c, Offset(w-14.dp.toPx(), h-14.dp.toPx()), Offset(w-14.dp.toPx()-bl, h-14.dp.toPx()), st)
            drawLine(c, Offset(w-14.dp.toPx(), h-14.dp.toPx()), Offset(w-14.dp.toPx(), h-14.dp.toPx()-bl), st)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // S big glowing - like example image
            Box(
                Modifier.size(120.dp).graphicsLayer { scaleX = sScale.value; scaleY = sScale.value; alpha = sAlpha.value },
                contentAlignment = Alignment.Center
            ) {
                // Orbit arcs
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height; val cx = w/2f; val cy = h/2f; val rad = w*0.58f
                    drawIntoCanvas { canvas ->
                        val fw = canvas.nativeCanvas
                        val p = android.graphics.Paint().apply {
                            isAntiAlias = true; style = android.graphics.Paint.Style.STROKE
                            strokeWidth = w*0.012f; color = android.graphics.Color.WHITE; alpha = (180 * sAlpha.value).toInt()
                        }
                        val rect = android.graphics.RectF(cx-rad, cy-rad, cx+rad, cy+rad)
                        fw.save(); fw.rotate(orbit.value, cx, cy); fw.drawArc(rect, -28f, 24f, false, p); fw.restore()
                        fw.save(); fw.rotate(orbit.value+180f, cx, cy); fw.drawArc(rect, -28f, 24f, false, p); fw.restore()
                    }
                }
                Text("S", color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.ExtraBold,
                    style = LocalTextStyle.current.copy(shadow = Shadow(Color.White.copy(0.4f), blurRadius = 22f)),
                    modifier = Modifier.graphicsLayer { alpha = sAlpha.value })
            }

            Spacer(Modifier.height(24.dp))

            // TURBO SPACE spaced like example
            Text(
                "T  U  R  B  O     S  P  A  C  E",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value }
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "GAME  OVERLAY  SYSTEM    v2.1",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 7.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value * 0.8f }
            )
        }
    }
}



@Composable
fun NeonWireframeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TurboColors.NeonWhite,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        color = Color.Transparent,
        shape = CutCornerShape(8.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(accent.copy(alpha = 0.5f), blurRadius = 8f)
                )
            )
        }
    }
}



@Composable
fun TurboSpaceApp() {
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }
    var screen by remember { mutableStateOf(TurboScreen.HOME) }
    var isShizukuReady by remember { mutableStateOf(TurboSpaceManager.isShizukuAvailableAndGranted()) }
    var backgroundRefreshKey by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    fun continuePendingStart() {
        if (!TurboSpaceManager.hasPendingOverlayStart(context)) return
        if (!Settings.canDrawOverlays(context)) return
        TurboSpaceManager.setPendingOverlayStart(context, false)
        // Android Settings can recreate this Activity, so reload the persisted game first.
        TurboSpaceRepository.restoreFromPrefs(context)
        coroutineScope.launch {
            startSessionAndLaunch(
                context = context,
                isShizukuReady = TurboSpaceManager.isShizukuAvailableAndGranted(),
                onSessionStarted = {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, GameSpaceOverlayService::class.java)
                    )
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        TurboSpaceRepository.restoreFromPrefs(context)
        TurboSpaceManager.restoreActiveGamePackage(context)
        continuePendingStart()
    }

    // One single back press from the permission screen is enough: on the next resume the
    // pending START is completed automatically and the selected game is launched.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) continuePendingStart()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val listener = TurboSpaceManager.addPermissionResultListener { granted ->
            isShizukuReady = granted
        }
        onDispose { TurboSpaceManager.removePermissionResultListener(listener) }
    }

    
    if (showSplash) {
        TurboSpaceSplashScreen {
            showSplash = false
        }
        return
    }

    val selectedGame by TurboSpaceRepository.selectedGame.collectAsState()
    val isSessionStarting by TurboSpaceRepository.isSessionStarting.collectAsState()
    val isSessionActive by TurboSpaceRepository.isSessionActive.collectAsState()
    val isResetting by TurboSpaceRepository.isResetting.collectAsState()


    // No picker - built-in background

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            continuePendingStart()
        } else {
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
                onStart = { requestStart() },
                onReset = { resetNow() },
                onShizuku = { TurboSpaceManager.requestShizukuPermission() }
            )

            TurboScreen.GAMES -> GameSelectionScreen(
                selectedGame = selectedGame,
                onBack = { screen = TurboScreen.HOME },
                onGameSelected = { pkg ->
                    TurboSpaceRepository.setSelectedGame(context, pkg)
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

// Returning from the Android overlay-permission screen can deliver both the activity-result
// callback and a fresh ON_RESUME. This guard makes the launch run exactly once, so the user
// never has to press RESET before START works again.
private val launchInFlight = AtomicBoolean(false)

private suspend fun startSessionAndLaunch(
    context: Context,
    isShizukuReady: Boolean,
    onSessionStarted: () -> Unit
) {
    if (!launchInFlight.compareAndSet(false, true)) return
    try {
        startSessionAndLaunchInternal(context, isShizukuReady, onSessionStarted)
    } finally {
        launchInFlight.set(false)
    }
}

private suspend fun startSessionAndLaunchInternal(
    context: Context,
    isShizukuReady: Boolean,
    onSessionStarted: () -> Unit
) {
    if (!isShizukuReady) {
        TurboSpaceManager.showToast(context, "Shizuku permission is required")
        return
    }

    val pkg = TurboSpaceRepository.selectedGame.value
    if (!TurboSpaceManager.isRecognizedGame(pkg)) {
        TurboSpaceManager.showToast(context, "ERROR — No recognized game selected")
        return
    }

    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
    if (launchIntent == null) {
        TurboSpaceManager.showToast(context, "ERROR — Could not launch the selected game")
        return
    }

    // Apply the session first. Overlay failure must never terminate or cancel the game.
    // If a session is already active (for example it was applied before the user was sent to the
    // overlay-permission screen), keep going straight to the Game Bar + game launch.
    if (!TurboSpaceRepository.isSessionActive.value) {
        val started = TurboSpaceRepository.startGameSession(context)
        if (!started) return
    }

    TurboSpaceRepository.setOverlayReady(false)
    try {
        onSessionStarted()
    } catch (_: Exception) {
        TurboSpaceManager.showToast(context, "ERROR — Game Bar unavailable; launching game without it")
    }

    // Give the service a short opportunity to attach, but never make game launch depend
    // on the overlay acknowledgement. OEM foreground-service startup can be delayed.
    withTimeoutOrNull(1500L) { TurboSpaceRepository.overlayReady.filter { it }.first() }
    if (!TurboSpaceRepository.overlayReady.value) {
        TurboSpaceManager.showToast(context, "UNSUPPORTED — Game Bar did not attach; check Overlay service permissions")
    }

    try {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(launchIntent)
    } catch (_: Exception) {
        TurboSpaceManager.showToast(context, "ERROR — Could not launch the selected game")
    }
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
    onStart: () -> Unit,
    onReset: () -> Unit,
    onShizuku: () -> Unit
) {
    val context = LocalContext.current
    val label = remember(selectedGame) {
        if (selectedGame == "NO TARGET SELECTED") "NO GAME SELECTED" else TurboSpaceManager.getAppLabel(context, selectedGame)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // TOP BAR - brighter, bigger access
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    Modifier.height(34.dp).clickable(onClick = onGames),
                    color = Color.White.copy(alpha = 0.08f),
                    shape = CutCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, Color.White.copy(0.35f))
                ) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("◫ Games", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.weight(1f).height(36.dp)
                        .border(0.8.dp, Color.White.copy(0.35f), CutCornerShape(8.dp))
                        .background(Color.White.copy(0.08f), CutCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val s = 0.9.dp.toPx(); val l = 10.dp.toPx(); val c = Color.White.copy(0.55f)
                        drawLine(c, Offset(0f,0f), Offset(l,0f), s); drawLine(c, Offset(0f,0f), Offset(0f,l), s)
                        drawLine(c, Offset(size.width,0f), Offset(size.width-l,0f), s); drawLine(c, Offset(size.width,0f), Offset(size.width,l), s)
                    }
                    Text("TURBO SPACE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                }
                Spacer(Modifier.width(10.dp))
                /* Settings button removed - commands moved to overlay */
            }

            Spacer(Modifier.height(16.dp))

            // GAME SESSION - BIGGER, BRIGHTER, MORE ACCESS
            Surface(
                Modifier.fillMaxWidth().wrapContentHeight(),
                color = Color.Black.copy(alpha = 0.75f),
                shape = CutCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.32f))
            ) {
                Box {
                    // Bright wireframe corners
                    Canvas(Modifier.matchParentSize()) {
                        val s = 1.2.dp.toPx(); val l = 14.dp.toPx(); val c = Color.White.copy(0.55f)
                        drawLine(c, Offset(0f,0f), Offset(l,0f), s); drawLine(c, Offset(0f,0f), Offset(0f,l), s)
                        drawLine(c, Offset(size.width,0f), Offset(size.width-l,0f), s); drawLine(c, Offset(size.width,0f), Offset(size.width,l), s)
                        drawLine(c, Offset(0f,size.height), Offset(l,size.height), s); drawLine(c, Offset(0f,size.height), Offset(0f,size.height-l), s)
                        drawLine(c, Offset(size.width,size.height), Offset(size.width-l,size.height), s); drawLine(c, Offset(size.width,size.height), Offset(size.width,size.height-l), s)
                    }
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("GAME SESSION", color = Color.White.copy(0.85f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.size(8.dp).background(
                                    if (isSessionActive) Color(0xFF39FF6A) else if (isShizukuReady) Color(0xFF00F0FF) else Color(0xFFFF3B30),
                                    RoundedCornerShape(4.dp)
                                )
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedGame != "NO TARGET SELECTED") {
                                AppIconImage(selectedGame, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(10.dp)))
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (isSessionActive) "● SESSION ACTIVE - GAME BAR RUNNING" else if (isShizukuReady) "● READY TO BOOST" else "● SHIZUKU REQUIRED",
                                    color = if (isSessionActive) Color(0xFF39FF6A) else if (isShizukuReady) Color(0xFF7DD3FF) else Color(0xFFFF6B6B),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        if (!isShizukuReady) {
                            Surface(
                                Modifier.fillMaxWidth().height(38.dp).clickable(onClick = onShizuku),
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(0.4f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("⚡ Grant Shizuku Permission", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        // START / RESET - BIGGER, BRIGHTER
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                Modifier.weight(1f).height(44.dp).clickable(enabled = isShizukuReady && !isSessionStarting && !isSessionActive && selectedGame != "NO TARGET SELECTED", onClick = onStart),
                                color = if (isShizukuReady && selectedGame != "NO TARGET SELECTED") Color.White else Color(0xFF2A2A2A),
                                shape = CutCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(0.35f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        if (isSessionStarting) "STARTING..." else "▶ START BOOST",
                                        color = if (isShizukuReady && selectedGame != "NO TARGET SELECTED") Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Surface(
                                Modifier.weight(1f).height(44.dp).clickable(enabled = !isResetting, onClick = onReset),
                                color = Color(0xFFB91C2C),
                                shape = CutCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(0.2f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(if (isResetting) "RESETTING..." else "↻ RESET ALL", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // EXTRA ACCESS - Quick info
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Turbo: ON", color = Color.White.copy(0.6f), fontSize = 8.sp)
                            Text("Overlay: ${if (isSessionActive) "ACTIVE" else "IDLE"}", color = Color.White.copy(0.6f), fontSize = 8.sp)
                            Text("Shizuku: ${if (isShizukuReady) "OK" else "NEED"}", color = Color.White.copy(0.6f), fontSize = 8.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // QUICK ACCESS BAR - MORE ACCESS
            Text("QUICK ACCESS", color = Color.White.copy(0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🎮 Games" to onGames, "📊 Boost" to onStart).forEach { (txt, action) ->
                    Surface(
                        Modifier.weight(1f).height(36.dp).clickable(onClick = action),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CutCornerShape(6.dp),
                        border = BorderStroke(0.6.dp, Color.White.copy(0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(txt, color = Color.White.copy(0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (isSessionStarting || isResetting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.75f)), contentAlignment = Alignment.Center) {
                Surface(Modifier.padding(24.dp), color = Color.Black, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(0.3f))) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(if (isResetting) "RESETTING SYSTEM..." else "STARTING TURBO SESSION...", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    } else if (!TurboSpaceManager.isRecognizedGame(selectedGame)) {
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

            /* CPU Optimization removed as requested - FIXED SYNTAX */
            Text("COMMAND: cmd activity set-inactive <package_name> true", color = TurboColors.HudCyan, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
            Text("RESET: cmd activity set-inactive <package_name> false  |  fallback: cmd appops set <package_name> RUN_IN_BACKGROUND allow", color = TurboColors.HudGreen, fontSize = 8.sp)
            Spacer(Modifier.height(6.dp))
            /* Reset CPU button removed - FIXED */

            Spacer(Modifier.height(10.dp))

            /* GPU Optimization removed as requested - FIXED SYNTAX */
            Text("COMMAND: cmd game downscale <package_name> 0.9", color = TurboColors.HudCyan, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
            Text("RESET: cmd game downscale reset <package_name>  |  fallback: cmd game downscale <package_name> 1.0", color = TurboColors.HudGreen, fontSize = 8.sp)
            Spacer(Modifier.height(6.dp))
            /* Reset GPU button removed - FIXED */

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
                if (TurboSpaceManager.isRecognizedGame( TurboSpaceRepository.selectedGame.value)) {
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
                        val ok = if (TurboSpaceManager.isRecognizedGame( pkg)) {
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

// ============================================================================
// 14. HOME BACKGROUND - BUILT-IN DESIGN (No +Add needed) - Like example image
// Pure black with white particles, hexagon wireframe, corner brackets
// ============================================================================
@Composable
private fun HomeBackground(refreshKey: Int) {
    // No file picker - built-in animated background like example image
    val particles = remember {
        List(35) { i ->
            Triple(
                (i * 37 % 100) / 100f, // x random 0-1
                (i * 73 % 100) / 100f, // y random 0-1
                (0.3f + (i % 5) * 0.2f) // size
            )
        }
    }
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            anim.animateTo(1f, tween(20000, easing = androidx.compose.animation.core.LinearEasing))
            anim.snapTo(0f)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val time = anim.value

            // White particles floating like stars - like example image
            particles.forEachIndexed { idx, (xRatio, yRatio, sz) ->
                val x = (xRatio * w + kotlin.math.sin((time * 360 + idx * 25) * 0.008f) * 15f) % w
                val y = (yRatio * h + (time * 30 + idx * 7) % h) % h
                val alpha = 0.15f + kotlin.math.sin(time * 3 + idx) * 0.1f
                drawCircle(
                    color = Color.White.copy(alpha = alpha.coerceIn(0.15f, 0.55f)),
                    radius = sz * 2.2f,
                    center = Offset(x, y)
                )
            }

            // Faint hexagon wireframe - like example
            val hexAlpha = Color.White.copy(alpha = 0.04f)
            val cx = w / 2f
            val cy = h / 2f
            val hexRadius = w * 0.38f
            // Draw hexagon outline
            val hexPath = Path().apply {
                for (i in 0..5) {
                    val angle = Math.toRadians((60 * i - 30).toDouble())
                    val hx = cx + hexRadius * kotlin.math.cos(angle).toFloat()
                    val hy = cy + hexRadius * kotlin.math.sin(angle).toFloat()
                    if (i == 0) moveTo(hx, hy) else lineTo(hx, hy)
                }
                close()
            }
            drawPath(hexPath, color = Color.White.copy(alpha = 0.09f), style = Stroke(width = 0.8f))

            // Inner small hexagon
            val innerRadius = hexRadius * 0.65f
            val innerPath = Path().apply {
                for (i in 0..5) {
                    val angle = Math.toRadians((60 * i - 30).toDouble())
                    val hx = cx + innerRadius * kotlin.math.cos(angle).toFloat()
                    val hy = cy + innerRadius * kotlin.math.sin(angle).toFloat()
                    if (i == 0) moveTo(hx, hy) else lineTo(hx, hy)
                }
                close()
            }
            drawPath(innerPath, color = Color.White.copy(alpha = 0.02f), style = Stroke(width = 0.4f))

            // Corner brackets - like viewfinder in example image
            val bracketLen = 18.dp.toPx()
            val stroke = 0.8.dp.toPx()
            val cornerColor = Color.White.copy(alpha = 0.25f)
            // Bottom-left
            drawLine(cornerColor, Offset(12.dp.toPx(), h - 12.dp.toPx()), Offset(12.dp.toPx() + bracketLen, h - 12.dp.toPx()), stroke)
            drawLine(cornerColor, Offset(12.dp.toPx(), h - 12.dp.toPx()), Offset(12.dp.toPx(), h - 12.dp.toPx() - bracketLen), stroke)
            // Bottom-right
            drawLine(cornerColor, Offset(w - 12.dp.toPx(), h - 12.dp.toPx()), Offset(w - 12.dp.toPx() - bracketLen, h - 12.dp.toPx()), stroke)
            drawLine(cornerColor, Offset(w - 12.dp.toPx(), h - 12.dp.toPx()), Offset(w - 12.dp.toPx(), h - 12.dp.toPx() - bracketLen), stroke)
        }
    }
}


// ============================================================================
// 15. FUTURISTIC GAME BAR / HUD
// ============================================================================
@Composable
fun rememberFps(): Int {
    var fps by remember { mutableStateOf(0) }
    var lastTime by remember { mutableStateOf(System.nanoTime()) }
    var frames by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val choreographer = android.view.Choreographer.getInstance()
        val callback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frames++
                val now = System.nanoTime()
                val diff = now - lastTime
                if (diff > 1_000_000_000L) {
                    fps = (frames * 1_000_000_000L / diff).toInt()
                    frames = 0
                    lastTime = now
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
    }
    return fps
}
@Composable
fun rememberCpuRam(): Pair<String, String> {
    var cpu by remember { mutableStateOf("0%") }
    var ram by remember { mutableStateOf("0 MB") }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val mem = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(mem)
                val avail = mem.availMem / (1024*1024)
                val total = mem.totalMem / (1024*1024)
                ram = "${total-avail}/${total} MB"
                cpu = "OK"
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(2000)
        }
    }
    return cpu to ram
}




// ============================================================================
// SPLIT HUD — two SEPARATE windows exactly like the reference image:
// LEFT window pinned flush to the left screen edge (telemetry + master switch)
// RIGHT window pinned flush to the right screen edge (10 command boxes)
// Centre of the screen stays completely clear for the game.
// ============================================================================
private object HudController {
    val expanded = mutableStateOf(true)
    val inactive = mutableStateOf(false)
    val masterOn = mutableStateOf(true)
    val ramOffset = mutableStateOf(0)
    val states = mutableStateMapOf<CyberModule, CyberCommandState>()
    fun reset() { expanded.value = true; inactive.value = false; masterOn.value = true; ramOffset.value = 0; states.clear() }
}

private val HudLine = Color.White.copy(.85f)

// Chamfered frame drawn like the reference (white neon outline + corner ticks + barcode)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.hudFrame(color: Color, cut: Float, barcode: Boolean) {
    val w = size.width; val h = size.height; val s = 1.6.dp.toPx()
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(cut, 0f); lineTo(w - cut * .4f, 0f); lineTo(w, cut * .4f); lineTo(w, h - cut); lineTo(w - cut, h)
        lineTo(cut * .4f, h); lineTo(0f, h - cut * .4f); lineTo(0f, cut); close()
    }
    drawPath(p, Color.White.copy(.05f))
    drawPath(p, color.copy(.25f), style = Stroke(s * 3.5f))
    drawPath(p, color, style = Stroke(s))
    val t = 5.dp.toPx(); val m = 9.dp.toPx()
    listOf(Offset(m, m), Offset(w - m, m), Offset(m, h - m), Offset(w - m, h - m)).forEach { c ->
        drawLine(color.copy(.6f), Offset(c.x - t / 2, c.y), Offset(c.x + t / 2, c.y), 1f)
        drawLine(color.copy(.6f), Offset(c.x, c.y - t / 2), Offset(c.x, c.y + t / 2), 1f)
    }
    if (barcode) {
        var x = m + 6.dp.toPx(); var i = 0
        while (x < w * .55f) { drawLine(color.copy(.8f), Offset(x, h - 7.dp.toPx()), Offset(x, h - 3.dp.toPx()), if (i % 3 == 0) 2f else 1f); x += if (i % 2 == 0) 2.5f else 4f; i++ }
    }
}

@Composable
private fun HudLeftPanel(onCollapse: () -> Unit, onClose: () -> Unit, onMasterOff: () -> Unit) {
    val fps = rememberFps()
    val (_, ram) = rememberCpuRam()
    val masterOn by HudController.masterOn
    val ramOffset by HudController.ramOffset
    val ramBase = run {
        val parts = ram.substringBefore(" ").split("/")
        val used = parts.getOrNull(0)?.toFloatOrNull(); val total = parts.getOrNull(1)?.toFloatOrNull()
        if (used != null && total != null && total > 0f) (used / total * 100).toInt() else 75
    }
    val ramPct = (ramBase - ramOffset).coerceIn(5, 100)
    val spin = rememberInfiniteTransition(label = "radar")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "a")
    val scroll by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "s")

    Box(Modifier.fillMaxSize().padding(start = 0.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(.72f))
            hudFrame(HudLine, 22.dp.toPx(), false)
        }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TURBO // TELEMETRY", color = TurboColors.HudCyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("−", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable { onCollapse() })
                Spacer(Modifier.width(12.dp))
                Text("×", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { onClose() })
            }
            // 1 + 2: big radar (CPU/GPU temp) and dual FPS/PING circles
            Row(Modifier.weight(1.3f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1.1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension * .47f
                        drawCircle(TurboColors.HudCyan.copy(.08f), r)
                        drawCircle(HudLine, r, style = Stroke(1.2.dp.toPx()))
                        drawArc(Color.White, angle, 70f, false, Offset(center.x - r * .86f, center.y - r * .86f), androidx.compose.ui.geometry.Size(r * 1.72f, r * 1.72f), style = Stroke(4.dp.toPx()))
                        drawArc(TurboColors.HudCyan, angle + 180f, 40f, false, Offset(center.x - r * .86f, center.y - r * .86f), androidx.compose.ui.geometry.Size(r * 1.72f, r * 1.72f), style = Stroke(4.dp.toPx()))
                        drawArc(Color.White.copy(.8f), -angle * 1.4f, 120f, false, Offset(center.x - r * .62f, center.y - r * .62f), androidx.compose.ui.geometry.Size(r * 1.24f, r * 1.24f), style = Stroke(2.5.dp.toPx()))
                        for (i in 0 until 36) {
                            val a = Math.toRadians((i * 10).toDouble())
                            val c = kotlin.math.cos(a).toFloat(); val sn = kotlin.math.sin(a).toFloat()
                            drawLine(Color.White.copy(if (i % 3 == 0) .9f else .3f), Offset(center.x + c * r * .92f, center.y + sn * r * .92f), Offset(center.x + c * r, center.y + sn * r), 1.2f)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("42°C", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("CPU / GPU", color = TurboColors.HudCyan, fontSize = 7.sp)
                    }
                }
                Column(Modifier.weight(.9f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Canvas(Modifier.fillMaxWidth().weight(.8f)) {
                        var y = 3.dp.toPx(); while (y < size.height) { var x = 0f; while (x < size.width) { drawCircle(Color.White.copy(.4f), 1f, Offset(x, y)); x += 5.dp.toPx() }; y += 5.dp.toPx() }
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TelemetryDial("FPS", "$fps", Modifier.weight(1f))
                        TelemetryDial("PING", "25ms", Modifier.weight(1f))
                    }
                }
            }
            // 3: FPS stability chart (live-scrolling)
            Box(Modifier.fillMaxWidth().weight(.75f)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(HudLine.copy(.5f), style = Stroke(1f))
                    val g = 8.dp.toPx()
                    var x = 0f; while (x < size.width) { drawLine(Color.White.copy(.12f), Offset(x, 0f), Offset(x, size.height), .6f); x += g }
                    var y = 0f; while (y < size.height) { drawLine(Color.White.copy(.12f), Offset(0f, y), Offset(size.width, y), .6f); y += g }
                    val n = 24; val step = size.width / (n - 1)
                    val pts = List(n) { i ->
                        val ph = (i + scroll * 4) * .9f
                        Offset(i * step, size.height * (.45f + .18f * kotlin.math.sin(ph) + .07f * kotlin.math.sin(ph * 2.7f)))
                    }
                    pts.zipWithNext().forEach { (a, b) -> drawLine(TurboColors.HudCyan, a, b, 1.6.dp.toPx()) }
                }
                Text("FPS STABILITY", color = Color.White.copy(.7f), fontSize = 6.sp, modifier = Modifier.padding(4.dp))
            }
            // 4: three arc gauges (CPU / RAM / GPU) + small battery circle
            Row(Modifier.fillMaxWidth().weight(.8f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ArcGauge("CPU", 68, Modifier.weight(1f))
                ArcGauge("RAM", ramPct, Modifier.weight(1f))
                ArcGauge("GPU", 54, Modifier.weight(1f))
                TelemetryDial("BAT", "82%", Modifier.weight(.8f))
            }
            // 5: master switch / auto-detection (dotted grid zone)
            Box(Modifier.fillMaxWidth().height(42.dp).clickable {
                HudController.masterOn.value = !masterOn
                if (masterOn) onMasterOff()
            }) {
                val accent = if (masterOn) TurboColors.HudCyan else Color.Red
                Canvas(Modifier.fillMaxSize()) {
                    hudFrame(accent, 8.dp.toPx(), false)
                    var y = 6.dp.toPx(); while (y < size.height - 4.dp.toPx()) { var x = 8.dp.toPx(); while (x < size.width - 8.dp.toPx()) { drawCircle(accent.copy(.35f), 1f, Offset(x, y)); x += 5.dp.toPx() }; y += 5.dp.toPx() }
                }
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("MASTER CORE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text(if (masterOn) "ON" else "OFF · ROLLBACK", color = if (masterOn) TurboColors.HudGreen else Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun HudRightPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedGame by TurboSpaceRepository.selectedGame.collectAsState()
    val states = HudController.states
    fun execute(module: CyberModule) {
        if (states[module] == CyberCommandState.INJECTING) return
        HudController.inactive.value = false
        states[module] = CyberCommandState.INJECTING
        scope.launch {
            val result = TurboSpaceManager.executeCyberModule(context, module, selectedGame)
            states[module] = result.state
            if (module == CyberModule.CACHE_NOVA && result.state == CyberCommandState.SUCCESS) {
                HudController.ramOffset.value = (HudController.ramOffset.value + 8).coerceAtMost(40)
            }
            delay(1400)
            if (states[module] == result.state) states[module] = CyberCommandState.IDLE
        }
    }
    @Composable fun slot(m: CyberModule, mod: Modifier) =
        HudCommandSlot(m, states[m] ?: CyberCommandState.IDLE, mod) { execute(m) }

    // Layout copied from the reference: 3 / 3 / wide+narrow / wide+narrow
    Column(Modifier.fillMaxSize().padding(start = 4.dp, top = 6.dp, bottom = 6.dp, end = 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slot(CyberModule.CORE_OVERDRIVE, Modifier.weight(1f)); slot(CyberModule.TOP_IGNITION, Modifier.weight(1f)); slot(CyberModule.ANTI_KILL_SHIELD, Modifier.weight(1f))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slot(CyberModule.STANDBY_LOCKER, Modifier.weight(1f)); slot(CyberModule.BATTERY_BYPASS, Modifier.weight(1f)); slot(CyberModule.CACHE_NOVA, Modifier.weight(1f))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slot(CyberModule.BANDWIDTH_LOCK, Modifier.weight(2f)); slot(CyberModule.WIFI_OVERDRIVE, Modifier.weight(1f))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slot(CyberModule.ULTRA_LATENCY, Modifier.weight(2f)); slot(CyberModule.SIGNAL_RESET, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HudCollapsedIcon(onDrag: (Float, Float) -> Unit, onDragFinished: () -> Unit, onExpand: () -> Unit) {
    var inactive by HudController.inactive
    val expanded by HudController.expanded
    val fps = rememberFps()
    LaunchedEffect(expanded, inactive) {
        if (!expanded && !inactive) { delay(3000); inactive = true; onDragFinished() }
    }
    val warning = fps in 1..24 // heat/ping spike indicator hook
    val color = if (warning) Color(0xFFFF5A1F) else TurboColors.HudCyan
    Box(
        Modifier.size(58.dp).alpha(if (inactive) 0.4f else 1f)
            .pointerInput(Unit) { detectDragGestures(
                onDragStart = { inactive = false }, onDragEnd = onDragFinished, onDragCancel = onDragFinished,
                onDrag = { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
            ) }.clickable { onExpand() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            drawCircle(Color.Black.copy(.85f))
            drawCircle(color.copy(.25f), style = Stroke(5.dp.toPx()))
            drawCircle(color, style = Stroke(1.5.dp.toPx()))
            drawCircle(color.copy(.7f), radius = size.minDimension * .34f, style = Stroke(.7.dp.toPx()))
            val c = center; val r = size.minDimension * .5f
            drawLine(color, Offset(c.x - r, c.y), Offset(c.x - r * .68f, c.y), 1.dp.toPx())
            drawLine(color, Offset(c.x + r * .68f, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y - r * .68f), 1.dp.toPx())
            drawLine(color, Offset(c.x, c.y + r * .68f), Offset(c.x, c.y + r), 1.dp.toPx())
        }
        Text("🎮", fontSize = 18.sp)
    }
}

@Composable
private fun ArcGauge(label: String, pct: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val d = size.minDimension; val tl = Offset(center.x - d / 2, center.y - d / 2)
            val sz = androidx.compose.ui.geometry.Size(d, d)
            drawArc(Color.White.copy(.25f), 135f, 270f, false, tl, sz, style = Stroke(3.dp.toPx()))
            drawArc(Color.White, 135f, 270f * pct / 100f, false, tl, sz, style = Stroke(3.dp.toPx()))
            val a = Math.toRadians((135 + 270 * pct / 100.0))
            drawLine(TurboColors.HudCyan, center, Offset(center.x + kotlin.math.cos(a).toFloat() * d * .38f, center.y + kotlin.math.sin(a).toFloat() * d * .38f), 1.5.dp.toPx())
            drawCircle(Color.White, 2.dp.toPx())
        }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color.White.copy(.55f), fontSize = 6.sp)
        }
    }
}

@Composable
private fun TelemetryDial(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val r = size.minDimension / 2
            drawCircle(Color.White.copy(.8f), r, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White.copy(.15f), r * .8f)
            drawArc(TurboColors.HudCyan, -90f, 250f, false, Offset(center.x - r * .9f, center.y - r * .9f), androidx.compose.ui.geometry.Size(r * 1.8f, r * 1.8f), style = Stroke(2.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(label, color = TurboColors.HudCyan, fontSize = 6.sp)
        }
    }
}

@Composable
private fun HudCommandSlot(module: CyberModule, state: CyberCommandState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = when (state) {
        CyberCommandState.SUCCESS -> TurboColors.HudGreen
        CyberCommandState.ERROR, CyberCommandState.UNSUPPORTED -> Color.Red
        CyberCommandState.INJECTING -> Color.Yellow
        else -> Color.White
    }
    Box(modifier.fillMaxHeight().clickable(enabled = state != CyberCommandState.INJECTING, onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(.55f))
            hudFrame(accent, 12.dp.toPx(), true)
        }
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(module.icon, fontSize = 15.sp)
            Text(module.title, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 2, textAlign = TextAlign.Center)
            Text(when (state) {
                CyberCommandState.INJECTING -> "RUNNING"
                CyberCommandState.SUCCESS -> "SUCCESS"
                CyberCommandState.ERROR, CyberCommandState.UNSUPPORTED -> "ERROR"
                else -> "READY"
            }, color = accent, fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
    }
}
