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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
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

    // Futuristic in-game HUD palette.
    val HudObsidian = Color(0xFF0B0C10)
    val HudCyan = Color(0xFF00F0FF)
    val HudPurple = Color(0xFFA020F0)
    val HudCrimson = Color(0xFFFF003C)
    val HudGreen = Color(0xFF39FF6A)
}

// ============================================================================
// 2b. GAMING SIDEBAR SHARED TYPES
// ============================================================================
// Status shown next to the three Shizuku-driven sidebar buttons only.
enum class SidebarStatus { IDLE, SUCCESS, ERROR }

// Smart Game Mode cycle: balanced -> performance -> battery saver
enum class GameModeState(val cmdValue: Int, val label: String) {
    BALANCED(1, "Balanced"),
    PERFORMANCE(2, "Turbo"),
    BATTERY(3, "Cool")
}

// ============================================================================
// 3. REPOSITORY & STATE MANAGEMENT
// ============================================================================
object TurboSpaceRepository {
    // --- Per-feature app selections ---
    // Network/CPU can each target MULTIPLE user-installed apps at once (a
    // Set, not a single package). GPU, Compile, and the overlay launcher
    // stay single-select and games-only (enforced by the picker dialog).
    private val _selectedNetworkApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedNetworkApps: StateFlow<Set<String>> = _selectedNetworkApps.asStateFlow()

    private val _selectedCpuApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedCpuApps: StateFlow<Set<String>> = _selectedCpuApps.asStateFlow()

    private val _selectedGpuGame = MutableStateFlow("NO TARGET SELECTED")
    val selectedGpuGame: StateFlow<String> = _selectedGpuGame.asStateFlow()

    private val _selectedOverlayGame = MutableStateFlow("NO TARGET SELECTED")
    val selectedOverlayGame: StateFlow<String> = _selectedOverlayGame.asStateFlow()

    private val _isNetOptActive = MutableStateFlow(false)
    val isNetOptActive: StateFlow<Boolean> = _isNetOptActive.asStateFlow()

    private val _isCpuOptActive = MutableStateFlow(false)
    val isCpuOptActive: StateFlow<Boolean> = _isCpuOptActive.asStateFlow()

    private val _isGpuOptActive = MutableStateFlow(false)
    val isGpuOptActive: StateFlow<Boolean> = _isGpuOptActive.asStateFlow()

    // Balance (default) vs Performance - selected from the in-game overlay's
    // rocket icon menu now, not a standalone card on the main hub.
    private val _isPerformanceActive = MutableStateFlow(false)
    val isPerformanceActive: StateFlow<Boolean> = _isPerformanceActive.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // Per-feature loading state, so each card can show a spinner and lock
    // its own "Start" button while its shell command is actually in flight.
    private val _isNetLoading = MutableStateFlow(false)
    val isNetLoading: StateFlow<Boolean> = _isNetLoading.asStateFlow()

    private val _isCpuLoading = MutableStateFlow(false)
    val isCpuLoading: StateFlow<Boolean> = _isCpuLoading.asStateFlow()

    private val _isGpuLoading = MutableStateFlow(false)
    val isGpuLoading: StateFlow<Boolean> = _isGpuLoading.asStateFlow()

    private val _isPerfLoading = MutableStateFlow(false)
    val isPerfLoading: StateFlow<Boolean> = _isPerfLoading.asStateFlow()

    private val _isResetting = MutableStateFlow(false)
    val isResetting: StateFlow<Boolean> = _isResetting.asStateFlow()

    // --- Gaming Sidebar state (overlay only) ---
    // Button 1: Smart Game Mode cycle, bound to one game package
    private val _boundGamePackage = MutableStateFlow("")
    val boundGamePackage: StateFlow<String> = _boundGamePackage.asStateFlow()

    private val _gameModeState = MutableStateFlow(GameModeState.BALANCED)
    val gameModeState: StateFlow<GameModeState> = _gameModeState.asStateFlow()

    private val _gameModeStatus = MutableStateFlow(SidebarStatus.IDLE)
    val gameModeStatus: StateFlow<SidebarStatus> = _gameModeStatus.asStateFlow()

    private val _isGameModeBusy = MutableStateFlow(false)
    val isGameModeBusy: StateFlow<Boolean> = _isGameModeBusy.asStateFlow()

    // Button 2: Performance Lock (fixed performance mode)
    private val _isPerfLockOn = MutableStateFlow(false)
    val isPerfLockOn: StateFlow<Boolean> = _isPerfLockOn.asStateFlow()

    private val _perfLockStatus = MutableStateFlow(SidebarStatus.IDLE)
    val perfLockStatus: StateFlow<SidebarStatus> = _perfLockStatus.asStateFlow()

    private val _isPerfLockBusy = MutableStateFlow(false)
    val isPerfLockBusy: StateFlow<Boolean> = _isPerfLockBusy.asStateFlow()

    // Button 3: Custom App Compact
    private val _compactStatus = MutableStateFlow(SidebarStatus.IDLE)
    val compactStatus: StateFlow<SidebarStatus> = _compactStatus.asStateFlow()

    private val _isCompacting = MutableStateFlow(false)
    val isCompacting: StateFlow<Boolean> = _isCompacting.asStateFlow()

    // Native Do Not Disturb
    private val _isDndOn = MutableStateFlow(false)
    val isDndOn: StateFlow<Boolean> = _isDndOn.asStateFlow()

    // --- Compile feature state ---
    private val _selectedCompileMode = MutableStateFlow(TurboSpaceManager.CompileMode.PROFILE_BASED)
    val selectedCompileMode: StateFlow<TurboSpaceManager.CompileMode> = _selectedCompileMode.asStateFlow()

    private val _isCompiling = MutableStateFlow(false)
    val isCompiling: StateFlow<Boolean> = _isCompiling.asStateFlow()

    private val _lastCompileSucceeded = MutableStateFlow<Boolean?>(null)
    val lastCompileSucceeded: StateFlow<Boolean?> = _lastCompileSucceeded.asStateFlow()

    // --- Persistence ---
    fun restoreFromPrefs(context: Context) {
        val saved = TurboSpaceManager.loadAppState(context)
        // Never allow Turbo Space itself to become an optimization target,
        // including stale selections restored from an older app version.
        val ownPackage = context.packageName
        _selectedNetworkApps.value = saved.networkApps.filterNot { it == ownPackage }.toSet()
        _selectedCpuApps.value = saved.cpuApps.filterNot { it == ownPackage }.toSet()
        _selectedGpuGame.value = if (saved.gpuGame == ownPackage) "NO TARGET SELECTED" else saved.gpuGame
        _selectedOverlayGame.value = if (saved.overlayGame == ownPackage) "NO TARGET SELECTED" else saved.overlayGame
        _isNetOptActive.value = saved.netActive
        _isCpuOptActive.value = saved.cpuActive
        _isGpuOptActive.value = saved.gpuActive
        _isPerformanceActive.value = saved.perfActive
        _selectedCompileMode.value = try {
            TurboSpaceManager.CompileMode.valueOf(saved.compileMode)
        } catch (e: Exception) {
            TurboSpaceManager.CompileMode.PROFILE_BASED
        }
    }

    private fun persistState(context: Context) {
        TurboSpaceManager.saveAppState(
            context = context,
            networkApps = _selectedNetworkApps.value,
            cpuApps = _selectedCpuApps.value,
            gpuGame = _selectedGpuGame.value,
            overlayGame = _selectedOverlayGame.value,
            netActive = _isNetOptActive.value,
            cpuActive = _isCpuOptActive.value,
            gpuActive = _isGpuOptActive.value,
            perfActive = _isPerformanceActive.value,
            compileMode = _selectedCompileMode.value.name
        )
    }

    fun setSelectedNetworkApps(context: Context, apps: Set<String>) {
        _selectedNetworkApps.value = apps
        persistState(context)
    }

    fun setSelectedCpuApps(context: Context, apps: Set<String>) {
        _selectedCpuApps.value = apps
        persistState(context)
    }

    fun setSelectedGpuGame(context: Context, pkg: String) {
        _selectedGpuGame.value = pkg
        persistState(context)
    }

    fun setSelectedOverlayGame(context: Context, pkg: String) {
        _selectedOverlayGame.value = pkg
        persistState(context)
    }

    fun setServiceState(running: Boolean) {
        _isServiceRunning.value = running
    }

    // --- Network Optimization: apply to ALL selected apps ---
    suspend fun startNetworkOpt(context: Context) {
        _isNetLoading.value = true
        try {
            val apps = _selectedNetworkApps.value
            if (apps.isEmpty()) {
                TurboSpaceManager.showOutcomeToast(context, TurboSpaceManager.CommandOutcome.BOTH_FAILED)
                return
            }
            val outcomes = apps.map { pkg -> TurboSpaceManager.optimizeNetwork(context, pkg) }
            val overall = TurboSpaceManager.combineOutcomes(*outcomes.toTypedArray())
            TurboSpaceManager.showOutcomeToast(context, overall)
            _isNetOptActive.value = overall != TurboSpaceManager.CommandOutcome.BOTH_FAILED
            persistState(context)
        } finally {
            _isNetLoading.value = false
        }
    }

    // --- CPU Optimization: apply to ALL selected apps ---
    suspend fun startCpuOpt(context: Context) {
        _isCpuLoading.value = true
        try {
            val apps = _selectedCpuApps.value
            if (apps.isEmpty()) {
                TurboSpaceManager.showOutcomeToast(context, TurboSpaceManager.CommandOutcome.BOTH_FAILED)
                return
            }
            val outcomes = apps.map { pkg -> TurboSpaceManager.reduceCpuLoad(context, pkg) }
            val overall = TurboSpaceManager.combineOutcomes(*outcomes.toTypedArray())
            TurboSpaceManager.showOutcomeToast(context, overall)
            _isCpuOptActive.value = overall != TurboSpaceManager.CommandOutcome.BOTH_FAILED
            persistState(context)
        } finally {
            _isCpuLoading.value = false
        }
    }

    // --- GPU Optimization: apply to the selected game ---
    suspend fun startGpuOpt(context: Context) {
        _isGpuLoading.value = true
        try {
            val outcome = TurboSpaceManager.reduceGpuLoad(context, _selectedGpuGame.value, "0.9")
            TurboSpaceManager.showOutcomeToast(context, outcome)
            _isGpuOptActive.value = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
            persistState(context)
        } finally {
            _isGpuLoading.value = false
        }
    }

    // Balance Mode (default) vs Performance Mode
    suspend fun setPowerMode(context: Context, enablePerformance: Boolean) {
        _isPerfLoading.value = true
        try {
            val outcome = TurboSpaceManager.setPowerMode(context, enablePerformance)
            TurboSpaceManager.showOutcomeToast(context, outcome)
            _isPerformanceActive.value = if (outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED) enablePerformance else false
            persistState(context)
        } finally {
            _isPerfLoading.value = false
        }
    }

    // ------------------------------------------------------------------
    // GAMING SIDEBAR ACTIONS (overlay only, main hub untouched)
    // ------------------------------------------------------------------

    fun restoreSidebarFromPrefs(context: Context) {
        _boundGamePackage.value = TurboSpaceManager.loadSidebarGame(context)
        _gameModeState.value = try {
            GameModeState.valueOf(TurboSpaceManager.loadSidebarMode(context))
        } catch (e: Exception) {
            GameModeState.BALANCED
        }
        _isPerfLockOn.value = TurboSpaceManager.loadSidebarPerfLock(context)
    }

    private fun persistSidebar(context: Context) {
        TurboSpaceManager.saveSidebarState(
            context,
            _boundGamePackage.value,
            _gameModeState.value.name,
            _isPerfLockOn.value
        )
    }

    fun setBoundGame(context: Context, packageName: String) {
        _boundGamePackage.value = packageName
        persistSidebar(context)
    }

    // Button 1 - Smart Game Mode (cycle toggle, status checked)
    suspend fun applyGameMode(context: Context, mode: GameModeState) {
        _isGameModeBusy.value = true
        try {
            val outcome = TurboSpaceManager.setGameMode(context, _boundGamePackage.value, mode.cmdValue)
            val ok = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
            _gameModeStatus.value = if (ok) SidebarStatus.SUCCESS else SidebarStatus.ERROR
            if (ok) _gameModeState.value = mode
            persistSidebar(context)
        } finally {
            _isGameModeBusy.value = false
        }
    }

    suspend fun cycleGameMode(context: Context) {
        val next = when (_gameModeState.value) {
            GameModeState.BALANCED -> GameModeState.PERFORMANCE
            GameModeState.PERFORMANCE -> GameModeState.BATTERY
            GameModeState.BATTERY -> GameModeState.BALANCED
        }
        applyGameMode(context, next)
    }

    // Button 2 - Performance Lock toggle (status checked)
    suspend fun togglePerformanceLock(context: Context) {
        _isPerfLockBusy.value = true
        try {
            val target = !_isPerfLockOn.value
            val outcome = TurboSpaceManager.setFixedPerformanceMode(context, target)
            val ok = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
            _perfLockStatus.value = if (ok) SidebarStatus.SUCCESS else SidebarStatus.ERROR
            if (ok) _isPerfLockOn.value = target
            persistSidebar(context)
        } finally {
            _isPerfLockBusy.value = false
        }
    }

    // Button 3 - Custom App Compact (status checked)
    suspend fun compactApps(context: Context, packages: Set<String>) {
        if (packages.isEmpty()) {
            _compactStatus.value = SidebarStatus.ERROR
            return
        }
        _isCompacting.value = true
        try {
            val outcomes = packages.map { TurboSpaceManager.compactApp(context, it) }
            val overall = TurboSpaceManager.combineOutcomes(*outcomes.toTypedArray())
            _compactStatus.value =
                if (overall != TurboSpaceManager.CommandOutcome.BOTH_FAILED) SidebarStatus.SUCCESS
                else SidebarStatus.ERROR
        } finally {
            _isCompacting.value = false
        }
    }

    // Native Do Not Disturb (no status indicator)
    fun toggleDoNotDisturb(context: Context) {
        _isDndOn.value = TurboSpaceManager.toggleDoNotDisturb(context, _isDndOn.value)
    }

    // Emergency Reset - clears every sidebar setting and the bound game
    suspend fun emergencyReset(context: Context) {
        val bound = _boundGamePackage.value
        if (bound.isNotEmpty()) {
            TurboSpaceManager.deleteGameMode(context, bound)
        }
        TurboSpaceManager.setFixedPerformanceMode(context, false)
        _boundGamePackage.value = ""
        _gameModeState.value = GameModeState.BALANCED
        _isPerfLockOn.value = false
        _gameModeStatus.value = SidebarStatus.IDLE
        _perfLockStatus.value = SidebarStatus.IDLE
        _compactStatus.value = SidebarStatus.IDLE
        persistSidebar(context)
    }

    // --- Game Compile (ART AOT) ---
    fun setCompileMode(mode: TurboSpaceManager.CompileMode) {
        _selectedCompileMode.value = mode
    }

    suspend fun runCompile(context: Context, packageName: String) {
        _isCompiling.value = true
        _lastCompileSucceeded.value = null
        val outcome = TurboSpaceManager.compileGame(
            context,
            packageName,
            _selectedCompileMode.value
        )
        TurboSpaceManager.showOutcomeToast(context, outcome)
        _lastCompileSucceeded.value = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
        _isCompiling.value = false
    }

    // Full reset - main hub only. Restores all Network + CPU apps, GPU game, and power mode.
    suspend fun resetAllRestrictions(context: Context) {
        _isResetting.value = true
        try {
            val netOutcomes = _selectedNetworkApps.value.map { TurboSpaceManager.restoreApps(context, it) }
            val cpuOutcomes = _selectedCpuApps.value.map { TurboSpaceManager.restoreApps(context, it) }
            val gpuOutcome = TurboSpaceManager.restoreGpu(context, _selectedGpuGame.value)
            val powerOutcome = TurboSpaceManager.setPowerMode(context, false)
            val overall = TurboSpaceManager.combineOutcomes(
                *(netOutcomes + cpuOutcomes + listOf(gpuOutcome, powerOutcome)).toTypedArray()
            )
            TurboSpaceManager.showOutcomeToast(context, overall)
            _isNetOptActive.value = false
            _isCpuOptActive.value = false
            _isGpuOptActive.value = false
            _isPerformanceActive.value = false
            persistState(context)
        } finally {
            _isResetting.value = false
        }
    }

    // Safe reset - overlay only. Restores Network + CPU only, never touches GPU resolution.
    suspend fun resetNetworkAndCpuOnly(context: Context) {
        val netOutcomes = _selectedNetworkApps.value.map { TurboSpaceManager.restoreApps(context, it) }
        val cpuOutcomes = _selectedCpuApps.value.map { TurboSpaceManager.restoreApps(context, it) }
        val powerOutcome = TurboSpaceManager.setPowerMode(context, false)
        val overall = TurboSpaceManager.combineOutcomes(
            *(netOutcomes + cpuOutcomes + listOf(powerOutcome)).toTypedArray()
        )
        TurboSpaceManager.showOutcomeToast(context, overall)
        _isNetOptActive.value = false
        _isCpuOptActive.value = false
        _isPerformanceActive.value = false
        persistState(context)
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

    data class SavedAppState(
        val networkApps: Set<String>,
        val cpuApps: Set<String>,
        val gpuGame: String,
        val overlayGame: String,
        val netActive: Boolean,
        val cpuActive: Boolean,
        val gpuActive: Boolean,
        val perfActive: Boolean,
        val compileMode: String
    )

    private const val KEY_NETWORK_APPS = "NETWORK_APPS"
    private const val KEY_CPU_APPS = "CPU_APPS"
    private const val KEY_GPU_GAME = "GPU_GAME"
    private const val KEY_OVERLAY_GAME = "OVERLAY_GAME"
    private const val KEY_NET_ACTIVE = "NET_ACTIVE"
    private const val KEY_CPU_ACTIVE = "CPU_ACTIVE"
    private const val KEY_GPU_ACTIVE = "GPU_ACTIVE"
    private const val KEY_COMPILE_MODE = "COMPILE_MODE"
    private const val APPS_DELIMITER = "||"

    fun saveAppState(
        context: Context,
        networkApps: Set<String>,
        cpuApps: Set<String>,
        gpuGame: String,
        overlayGame: String,
        netActive: Boolean,
        cpuActive: Boolean,
        gpuActive: Boolean,
        perfActive: Boolean,
        compileMode: String
    ) {
        getPrefs(context).edit()
            .putString(KEY_NETWORK_APPS, networkApps.joinToString(APPS_DELIMITER))
            .putString(KEY_CPU_APPS, cpuApps.joinToString(APPS_DELIMITER))
            .putString(KEY_GPU_GAME, gpuGame)
            .putString(KEY_OVERLAY_GAME, overlayGame)
            .putBoolean(KEY_NET_ACTIVE, netActive)
            .putBoolean(KEY_CPU_ACTIVE, cpuActive)
            .putBoolean(KEY_GPU_ACTIVE, gpuActive)
            .putBoolean("IS_PERFORMANCE_ACTIVE", perfActive)
            .putString(KEY_COMPILE_MODE, compileMode)
            .apply()
    }

    fun loadAppState(context: Context): SavedAppState {
        val prefs = getPrefs(context)
        fun readApps(key: String): Set<String> {
            val raw = prefs.getString(key, "") ?: ""
            return if (raw.isBlank()) emptySet() else raw.split(APPS_DELIMITER).filter { it.isNotBlank() }.toSet()
        }
        return SavedAppState(
            networkApps = readApps(KEY_NETWORK_APPS),
            cpuApps = readApps(KEY_CPU_APPS),
            gpuGame = prefs.getString(KEY_GPU_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
            overlayGame = prefs.getString(KEY_OVERLAY_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
            netActive = prefs.getBoolean(KEY_NET_ACTIVE, false),
            cpuActive = prefs.getBoolean(KEY_CPU_ACTIVE, false),
            gpuActive = prefs.getBoolean(KEY_GPU_ACTIVE, false),
            perfActive = prefs.getBoolean("IS_PERFORMANCE_ACTIVE", false),
            compileMode = prefs.getString(KEY_COMPILE_MODE, CompileMode.PROFILE_BASED.name) ?: CompileMode.PROFILE_BASED.name
        )
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

    suspend fun optimizeNetwork(context: Context, packageName: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd netpolicy add restrict-background-whitelist $packageName"
        return executeCommandDetailed(context, primary)
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

    suspend fun restoreApps(context: Context, packageName: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val res1 = executeCommandDetailed(context, "cmd activity set-inactive $packageName false", "cmd appops set $packageName RUN_IN_BACKGROUND allow")
        val res2 = executeCommandDetailed(context, "cmd netpolicy remove restrict-background-whitelist $packageName", "appops set $packageName RUN_IN_BACKGROUND default")
        return combineOutcomes(res1, res2)
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

    // Games only - ApplicationInfo.CATEGORY_GAME (no regular or system apps)
    fun getGameApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { info ->
                    val isGameCategory =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            info.category == ApplicationInfo.CATEGORY_GAME
                        } else {
                            @Suppress("DEPRECATION")
                            (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                        }
                    isGameCategory && pm.getLaunchIntentForPackage(info.packageName) != null
                }
                .sortedBy { pm.getApplicationLabel(it).toString() }
        } catch (e: Exception) {
            emptyList()
        }
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

    // ------------------------------------------------------------------
    // SIDEBAR PERSISTENCE (kept separate from the main hub state)
    // ------------------------------------------------------------------
    private const val KEY_SIDEBAR_GAME = "SIDEBAR_GAME"
    private const val KEY_SIDEBAR_MODE = "SIDEBAR_GAME_MODE"
    private const val KEY_SIDEBAR_PERF_LOCK = "SIDEBAR_PERF_LOCK"

    fun saveSidebarState(context: Context, gamePackage: String, modeName: String, perfLock: Boolean) {
        getPrefs(context).edit()
            .putString(KEY_SIDEBAR_GAME, gamePackage)
            .putString(KEY_SIDEBAR_MODE, modeName)
            .putBoolean(KEY_SIDEBAR_PERF_LOCK, perfLock)
            .apply()
    }

    fun loadSidebarGame(context: Context): String =
        getPrefs(context).getString(KEY_SIDEBAR_GAME, "") ?: ""

    fun loadSidebarMode(context: Context): String =
        getPrefs(context).getString(KEY_SIDEBAR_MODE, GameModeState.BALANCED.name) ?: GameModeState.BALANCED.name

    fun loadSidebarPerfLock(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SIDEBAR_PERF_LOCK, false)
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
        TurboSpaceRepository.restoreSidebarFromPrefs(this)
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
            val barWidthPx = (350f * density).toInt()
            val barHeightPx = (124f * density).toInt()
            x = ((resources.displayMetrics.widthPixels - barWidthPx) / 2f).toInt().coerceAtLeast(0)
            y = ((resources.displayMetrics.heightPixels - barHeightPx) / 2f).toInt().coerceAtLeast(0)
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
// 7. MAIN HUB LANDSCAPE UI
// ============================================================================
@Composable
fun TurboSpaceLandscapeHub() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isShizukuReady by remember { mutableStateOf(TurboSpaceManager.isShizukuAvailableAndGranted()) }
    var isResetConfirmOpen by remember { mutableStateOf(false) }
    var isOverlayGamePickerOpen by remember { mutableStateOf(false) }
    var isCompileGamePickerOpen by remember { mutableStateOf(false) }
    var isCompileModeDialogOpen by remember { mutableStateOf(false) }
    var compileTargetGame by remember { mutableStateOf("NO TARGET SELECTED") }

    LaunchedEffect(Unit) {
        TurboSpaceRepository.restoreFromPrefs(context)
        TurboSpaceRepository.restoreSidebarFromPrefs(context)
    }

    DisposableEffect(Unit) {
        val listener = TurboSpaceManager.addPermissionResultListener { granted ->
            isShizukuReady = granted
        }
        onDispose { TurboSpaceManager.removePermissionResultListener(listener) }
    }

    // --- FIX: Use plural (Set<String>) for Network and CPU ---
    val selectedNetworkApps by TurboSpaceRepository.selectedNetworkApps.collectAsState()
    val selectedCpuApps by TurboSpaceRepository.selectedCpuApps.collectAsState()
    val selectedGpuGame by TurboSpaceRepository.selectedGpuGame.collectAsState()
    val selectedOverlayGame by TurboSpaceRepository.selectedOverlayGame.collectAsState()

    val isNetActive by TurboSpaceRepository.isNetOptActive.collectAsState()
    val isCpuActive by TurboSpaceRepository.isCpuOptActive.collectAsState()
    val isGpuActive by TurboSpaceRepository.isGpuOptActive.collectAsState()
    val isServiceRunning by TurboSpaceRepository.isServiceRunning.collectAsState()

    val isNetLoading by TurboSpaceRepository.isNetLoading.collectAsState()
    val isCpuLoading by TurboSpaceRepository.isCpuLoading.collectAsState()
    val isGpuLoading by TurboSpaceRepository.isGpuLoading.collectAsState()
    val isResetting by TurboSpaceRepository.isResetting.collectAsState()

    val selectedCompileMode by TurboSpaceRepository.selectedCompileMode.collectAsState()
    val isCompiling by TurboSpaceRepository.isCompiling.collectAsState()

    var pendingOverlayStart by remember { mutableStateOf(false) }

    fun hasDoNotDisturbAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.isNotificationPolicyAccessGranted
    }

    fun startGameAndOverlayNow() {
        if (selectedOverlayGame != "NO TARGET SELECTED") {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedOverlayGame)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "Could not launch the selected game", Toast.LENGTH_SHORT).show()
                return
            }
        }
        ContextCompat.startForegroundService(context, Intent(context, GameSpaceOverlayService::class.java))
    }

    val dndPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingOverlayStart) {
            pendingOverlayStart = false
            if (hasDoNotDisturbAccess()) {
                startGameAndOverlayNow()
            } else {
                Toast.makeText(
                    context,
                    "Do Not Disturb access is required to start Game Overlay",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun requestDoNotDisturbThenStart() {
        if (!hasDoNotDisturbAccess()) {
            pendingOverlayStart = true
            dndPermissionLauncher.launch(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            )
        } else {
            pendingOverlayStart = false
            startGameAndOverlayNow()
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            requestDoNotDisturbThenStart()
        } else {
            pendingOverlayStart = false
            Toast.makeText(
                context,
                "Overlay permission is required to show the in-game sidebar",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun launchGameAndOverlay() {
        pendingOverlayStart = true
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }
        requestDoNotDisturbThenStart()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bgResId = remember {
            context.resources.getIdentifier("bg_main", "raw", context.packageName)
        }

        if (bgResId != 0) {
            val gifImageLoader = remember {
                ImageLoader.Builder(context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.parse("android.resource://${context.packageName}/$bgResId"))
                    .build(),
                imageLoader = gifImageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TurboColors.DarkBackground.copy(alpha = 0.72f))
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // ---------------- LEFT PANEL: overlay launcher + status ----------------
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = "TURBO SPACE",
                    color = TurboColors.BrightRed,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isShizukuReady) "Shizuku: Connected" else "Shizuku: Not Connected",
                    color = if (isShizukuReady) TurboColors.BrightGlowRed else TurboColors.TextGray,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(16.dp))

                if (!isShizukuReady) {
                    OutlinedButton(
                        onClick = { TurboSpaceManager.requestShizukuPermission() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Grant Shizuku Permission")
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (selectedOverlayGame != "NO TARGET SELECTED") {
                    Text(
                        "Change game: tap the game cover once",
                        color = TurboColors.TextGray,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TurboColors.CardBackground)
                        .border(1.dp, TurboColors.BorderGray, RoundedCornerShape(14.dp))
                        .clickable { isOverlayGamePickerOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedOverlayGame != "NO TARGET SELECTED") {
                        AppIconImage(
                            packageName = selectedOverlayGame,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = "Select game", tint = TurboColors.TextGray)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { launchGameAndOverlay() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isShizukuReady && !isServiceRunning && selectedOverlayGame != "NO TARGET SELECTED"
                ) {
                    Icon(Icons.Filled.RocketLaunch, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isServiceRunning) "Overlay Running" else "Start Game Overlay")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        context.stopService(Intent(context, GameSpaceOverlayService::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isServiceRunning
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop Overlay")
                }
            }

            Spacer(Modifier.width(16.dp))

            // ---------------- RIGHT PANEL: optimization features ----------------
            LazyColumn(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // FIX: Network uses MultiAppActionCard (Set<String>, select all supported)
                item {
                    MultiAppActionCard(
                        title = "Network Optimization",
                        subtitle = "Whitelists selected apps from background data limits",
                        selectedApps = selectedNetworkApps,
                        isActive = isNetActive,
                        isLoading = isNetLoading,
                        enabled = isShizukuReady,
                        onAppsSelected = { TurboSpaceRepository.setSelectedNetworkApps(context, it) },
                        onStart = { coroutineScope.launch { TurboSpaceRepository.startNetworkOpt(context) } }
                    )
                }
                // FIX: CPU uses MultiAppActionCard (Set<String>, select all supported)
                item {
                    MultiAppActionCard(
                        title = "CPU Optimization",
                        subtitle = "Idles selected apps to free up CPU",
                        selectedApps = selectedCpuApps,
                        isActive = isCpuActive,
                        isLoading = isCpuLoading,
                        enabled = isShizukuReady,
                        onAppsSelected = { TurboSpaceRepository.setSelectedCpuApps(context, it) },
                        onStart = { coroutineScope.launch { TurboSpaceRepository.startCpuOpt(context) } }
                    )
                }
                // GPU stays as single-select (games only) - no changes requested
                item {
                    AppActionCard(
                        title = "GPU Optimization",
                        subtitle = "Downscales render resolution for the selected game",
                        selectedApp = selectedGpuGame,
                        gamesOnly = true,
                        isActive = isGpuActive,
                        isLoading = isGpuLoading,
                        enabled = isShizukuReady,
                        onAppSelected = { TurboSpaceRepository.setSelectedGpuGame(context, it) },
                        onStart = { coroutineScope.launch { TurboSpaceRepository.startGpuOpt(context) } }
                    )
                }
                // Game Compile - no changes requested
                item {
                    CompileActionCard(
                        isCompiling = isCompiling,
                        enabled = isShizukuReady,
                        onPickGame = { isCompileGamePickerOpen = true }
                    )
                }
                item {
                    Button(
                        onClick = { isResetConfirmOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isResetting,
                        colors = ButtonDefaults.buttonColors(containerColor = TurboColors.EngineStopGray)
                    ) {
                        if (isResetting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Resetting...")
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reset All")
                        }
                    }
                }
            }
        }
    }

    if (isOverlayGamePickerOpen) {
        AppPickerDialog(
            gamesOnly = true,
            onDismiss = { isOverlayGamePickerOpen = false },
            onAppSelected = {
                TurboSpaceRepository.setSelectedOverlayGame(context, it)
                isOverlayGamePickerOpen = false
            }
        )
    }

    if (isCompileGamePickerOpen) {
        AppPickerDialog(
            gamesOnly = true,
            onDismiss = { isCompileGamePickerOpen = false },
            onAppSelected = {
                compileTargetGame = it
                isCompileGamePickerOpen = false
                isCompileModeDialogOpen = true
            }
        )
    }

    if (isCompileModeDialogOpen) {
        CompileModeDialog(
            packageName = compileTargetGame,
            selectedMode = selectedCompileMode,
            onModeSelected = { TurboSpaceRepository.setCompileMode(it) },
            onConfirm = {
                isCompileModeDialogOpen = false
                coroutineScope.launch { TurboSpaceRepository.runCompile(context, compileTargetGame) }
            },
            onCancel = { isCompileModeDialogOpen = false }
        )
    }

    if (isResetConfirmOpen) {
        AlertDialog(
            onDismissRequest = { isResetConfirmOpen = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = TurboColors.BrightRed) },
            title = { Text("Reset everything?") },
            text = {
                Text(
                    "This will restore network, CPU, GPU resolution, and power " +
                        "mode for every app you've selected back to normal. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    isResetConfirmOpen = false
                    coroutineScope.launch { TurboSpaceRepository.resetAllRestrictions(context) }
                }) { Text("Reset", color = TurboColors.BrightRed) }
            },
            dismissButton = {
                TextButton(onClick = { isResetConfirmOpen = false }) { Text("Cancel") }
            }
        )
    }
}

// ============================================================================
// 8. REUSABLE UI COMPONENTS
// ============================================================================

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
    } else {
        Icon(
            Icons.Filled.SportsEsports,
            contentDescription = null,
            tint = TurboColors.TextGray,
            modifier = modifier
        )
    }
}

@Composable
fun rememberAppLabel(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName == "NO TARGET SELECTED") {
            packageName
        } else {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }
        }
    }
}

// ============================================================================
// MULTI-SELECT CARD: Network Optimization & CPU Optimization
// Supports selecting multiple apps at once, with "Select All" in the picker.
// ============================================================================
@Composable
fun MultiAppActionCard(
    title: String,
    subtitle: String,
    selectedApps: Set<String>,
    isActive: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    onAppsSelected: (Set<String>) -> Unit,
    onStart: () -> Unit
) {
    var isPickerOpen by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isActive) TurboColors.BrightRed else TurboColors.BorderGray,
        animationSpec = tween(300),
        label = "borderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isActive) TurboColors.ActiveRedBg else TurboColors.CardBackground,
        animationSpec = tween(300),
        label = "bgColor"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(3.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                TurboColors.BrightRed.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TurboColors.TextGray, fontSize = 12.sp)

                Spacer(Modifier.height(10.dp))

                // App selection row - shows selected app icons or a prompt
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TurboColors.DarkBackground.copy(alpha = 0.4f))
                        .clickable { isPickerOpen = true }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedApps.isEmpty()) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = TurboColors.TextGray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Select apps...",
                            color = TurboColors.TextGray,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Show first 3 app icons side by side
                        val displayList = selectedApps.toList().take(3)
                        displayList.forEach { pkg ->
                            AppIconImage(pkg, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (selectedApps.size == 1) "1 app selected"
                            else "${selectedApps.size} apps selected",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled && !isLoading && selectedApps.isNotEmpty()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Starting...")
                    } else {
                        Text("Start")
                    }
                }
            }
        }
    }

    if (isPickerOpen) {
        MultiAppPickerDialog(
            currentSelected = selectedApps,
            onDismiss = { isPickerOpen = false },
            onAppsSelected = {
                onAppsSelected(it)
                isPickerOpen = false
            }
        )
    }
}

// Multi-select app picker dialog with "Select All" checkbox at the top.
// Used by Network Optimization and CPU Optimization only.
@Composable
fun MultiAppPickerDialog(
    currentSelected: Set<String>,
    onDismiss: () -> Unit,
    onAppsSelected: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    // Initialize temp selection from current, refreshes if dialog reopens with different selection
    var tempSelected by remember(currentSelected, context.packageName) {
        mutableStateOf(currentSelected.filterNot { it == context.packageName }.toSet())
    }

    // Intentionally exclude Turbo Space itself so the optimizer can never
    // target its own process, even through Select All.
    val allApps = remember {
        TurboSpaceManager.getThirdPartyApps(context)
    }

    val filteredApps = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) allApps
        else {
            val pm = context.packageManager
            allApps.filter {
                pm.getApplicationLabel(it).toString().contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // "Select All" reflects whether all currently-visible (filtered) apps are selected
    val allFilteredSelected = filteredApps.isNotEmpty() && filteredApps.all { it.packageName in tempSelected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Apps") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps...") },
                    singleLine = true
                )

                Spacer(Modifier.height(6.dp))

                // --- Select All row ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            tempSelected = if (allFilteredSelected) {
                                // Deselect all filtered apps
                                tempSelected - filteredApps.map { it.packageName }.toSet()
                            } else {
                                // Select all filtered apps
                                tempSelected + filteredApps.map { it.packageName }.toSet()
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allFilteredSelected,
                        onCheckedChange = { checked ->
                            tempSelected = if (checked) {
                                tempSelected + filteredApps.map { it.packageName }.toSet()
                            } else {
                                tempSelected - filteredApps.map { it.packageName }.toSet()
                            }
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "Select All" else "Select All Results",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                HorizontalDivider(color = TurboColors.BorderGray)
                Spacer(Modifier.height(4.dp))

                when {
                    allApps.isEmpty() -> {
                        Text(
                            "No user-installed apps found on this device.",
                            color = TurboColors.TextGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                    filteredApps.isEmpty() -> {
                        Text(
                            "No matches for \"$searchQuery\".",
                            color = TurboColors.TextGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                            items(filteredApps) { appInfo ->
                                val pm = context.packageManager
                                val isChecked = appInfo.packageName in tempSelected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            tempSelected = if (isChecked) {
                                                tempSelected - appInfo.packageName
                                            } else {
                                                tempSelected + appInfo.packageName
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            tempSelected = if (checked) {
                                                tempSelected + appInfo.packageName
                                            } else {
                                                tempSelected - appInfo.packageName
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    AppIconImage(appInfo.packageName, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        pm.getApplicationLabel(appInfo).toString(),
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAppsSelected(tempSelected) }) {
                Text("Confirm (${tempSelected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Single-select card - used for GPU Optimization (game-only, single target)
@Composable
fun AppActionCard(
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
    var isPickerOpen by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isActive) TurboColors.BrightRed else TurboColors.BorderGray,
        animationSpec = tween(300),
        label = "borderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isActive) TurboColors.ActiveRedBg else TurboColors.CardBackground,
        animationSpec = tween(300),
        label = "bgColor"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(3.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                TurboColors.BrightRed.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TurboColors.TextGray, fontSize = 12.sp)

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TurboColors.DarkBackground.copy(alpha = 0.4f))
                        .clickable { isPickerOpen = true }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedApp != "NO TARGET SELECTED") {
                        AppIconImage(selectedApp, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            rememberAppLabel(selectedApp),
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = TurboColors.TextGray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (gamesOnly) "Select a game..." else "Select an app...",
                            color = TurboColors.TextGray,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled && !isLoading && selectedApp != "NO TARGET SELECTED"
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Starting...")
                    } else {
                        Text("Start")
                    }
                }
            }
        }
    }

    if (isPickerOpen) {
        AppPickerDialog(
            gamesOnly = gamesOnly,
            onDismiss = { isPickerOpen = false },
            onAppSelected = {
                onAppSelected(it)
                isPickerOpen = false
            }
        )
    }
}

// Game Compile card - no changes requested
@Composable
fun CompileActionCard(
    isCompiling: Boolean,
    enabled: Boolean,
    onPickGame: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TurboColors.CardBackground),
        border = BorderStroke(1.dp, TurboColors.BorderGray)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FlashOn, contentDescription = null, tint = TurboColors.BrightGlowRed)
                Spacer(Modifier.width(8.dp))
                Text("Game Compile (ART AOT)", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(
                "One-shot action. Pre-compiles the selected game for faster launch times.",
                color = TurboColors.TextGray,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onPickGame,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && !isCompiling
            ) {
                if (isCompiling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Compiling...")
                } else {
                    Icon(Icons.Filled.Shield, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Select Game to Compile")
                }
            }
        }
    }
}

// Compile mode dialog - no changes requested
@Composable
fun CompileModeDialog(
    packageName: String,
    selectedMode: TurboSpaceManager.CompileMode,
    onModeSelected: (TurboSpaceManager.CompileMode) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(packageName, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(rememberAppLabel(packageName))
            }
        },
        text = {
            Column {
                TurboSpaceManager.CompileMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == selectedMode, onClick = { onModeSelected(mode) })
                        Spacer(Modifier.width(6.dp))
                        Text(mode.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}

// Single-select app picker - used for GPU, Compile, and Overlay launcher
@Composable
fun AppPickerDialog(
    gamesOnly: Boolean,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val allApps = remember(gamesOnly) {
        val pm = context.packageManager
        TurboSpaceManager.getThirdPartyApps(context)
            .filter { appInfo ->
                if (!gamesOnly) return@filter true
                val isCategorizedAsGame = appInfo.category == ApplicationInfo.CATEGORY_GAME
                @Suppress("DEPRECATION")
                val hasLegacyGameFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                isCategorizedAsGame || hasLegacyGameFlag
            }
            .sortedBy { pm.getApplicationLabel(it).toString() }
    }

    val filteredApps = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) {
            allApps
        } else {
            val pm = context.packageManager
            allApps.filter {
                pm.getApplicationLabel(it).toString().contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text(if (gamesOnly) "Select Game" else "Select App") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (gamesOnly) "Search your games..." else "Search your apps...") },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                if (allApps.isEmpty()) {
                    Text(
                        if (gamesOnly) {
                            "No games detected on this device yet. Android only " +
                                "lists an app here once it's tagged as a game - " +
                                "some older or unusual game APKs aren't tagged " +
                                "that way and won't show up."
                        } else {
                            "No user-installed apps found on this device."
                        },
                        color = TurboColors.TextGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else if (filteredApps.isEmpty()) {
                    Text(
                        "No matches for \"$searchQuery\".",
                        color = TurboColors.TextGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filteredApps) { appInfo ->
                            val pm = context.packageManager
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAppSelected(appInfo.packageName) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconImage(appInfo.packageName, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(pm.getApplicationLabel(appInfo).toString())
                            }
                        }
                    }
                }
            }
        }
    )
}

// ============================================================================
// 9. IN-GAME OVERLAY HUD (rendered by GameSpaceOverlayService)
//
// Futuristic horizontal Game Booster bar inspired by the requested blueprint:
//   Top    : 3 equal telemetry modules (FPS / CPU / RAM)
//   Center : large circular Turbo Core master switch
//   Bottom : 4 compact quick-access feature slots
//
// Important interaction rules:
//   CLOSE  -> hides/stops ONLY the overlay service; it does not reset state.
//   RESET  -> restores the overlay/game settings handled by the repository.
//   Drag   -> use the small GAME header area to move the bar around the screen.
// ============================================================================
@Composable
private fun HudTelemetryCell(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(CutCornerShape(7.dp))
            .background(TurboColors.HudObsidian.copy(alpha = 0.86f))
            .border(
                1.dp,
                accent.copy(alpha = 0.78f),
                CutCornerShape(7.dp)
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            color = accent,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )
        Text(
            value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            detail,
            color = TurboColors.TextGray,
            fontSize = 7.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun HudQuickSlot(
    icon: ImageVector,
    label: String,
    accent: Color,
    active: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = if (active) accent else accent.copy(alpha = 0.58f)
    val bgColor = if (active) accent.copy(alpha = 0.16f) else TurboColors.HudObsidian.copy(alpha = 0.82f)

    Column(
        modifier = Modifier
            .weight(1f)
            .height(27.dp)
            .clip(CutCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, CutCornerShape(6.dp))
            .clickable(enabled = enabled && !busy) { onClick() }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = accent
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    label,
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Inline picker panel (a Compose Dialog cannot be shown from a Service window,
// so the game/app picker stays embedded in the overlay window).
@Composable
private fun HudPickerPanel(
    title: String,
    apps: List<ApplicationInfo>,
    multiSelect: Boolean,
    onCancel: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var selected by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CutCornerShape(7.dp))
            .background(TurboColors.HudObsidian.copy(alpha = 0.98f))
            .border(1.dp, TurboColors.HudCyan.copy(alpha = 0.75f), CutCornerShape(7.dp))
            .padding(6.dp)
    ) {
        Text(
            title,
            color = TurboColors.HudCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))

        if (apps.isEmpty()) {
            Text("No apps found", color = TurboColors.TextGray, fontSize = 8.sp)
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                items(apps) { app ->
                    val pkg = app.packageName
                    val checked = pkg in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (multiSelect) {
                                    if (checked) selected - pkg else selected + pkg
                                } else {
                                    setOf(pkg)
                                }
                            }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (checked) TurboColors.HudGreen else TurboColors.TextGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(pkg),
                            color = Color.White,
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "CANCEL",
                color = TurboColors.TextGray,
                fontSize = 8.sp,
                modifier = Modifier
                    .clickable { onCancel() }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
            Text(
                "APPLY",
                color = if (selected.isEmpty()) TurboColors.EngineStopGray else TurboColors.HudGreen,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = selected.isNotEmpty()) { onConfirm(selected) }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun GameSpaceInGameSidebar(
    onDragOverlay: (dx: Float, dy: Float) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val isServiceRunning by TurboSpaceRepository.isServiceRunning.collectAsState()
    val selectedOverlayGame by TurboSpaceRepository.selectedOverlayGame.collectAsState()

    // Existing sidebar actions remain wired to the new compact HUD.
    val boundGame by TurboSpaceRepository.boundGamePackage.collectAsState()
    val gameMode by TurboSpaceRepository.gameModeState.collectAsState()
    val isGameModeBusy by TurboSpaceRepository.isGameModeBusy.collectAsState()
    val isPerfLockOn by TurboSpaceRepository.isPerfLockOn.collectAsState()
    val isPerfLockBusy by TurboSpaceRepository.isPerfLockBusy.collectAsState()
    val isCompacting by TurboSpaceRepository.isCompacting.collectAsState()
    val isDndOn by TurboSpaceRepository.isDndOn.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var isResetting by remember { mutableStateOf(false) }
    var showGamePicker by remember { mutableStateOf(false) }
    var showCompactPicker by remember { mutableStateOf(false) }

    val monitor = remember { SystemMonitorEngine(context) }
    var fps by remember { mutableStateOf(monitor.currentFps.value) }
    var cpu by remember { mutableStateOf(monitor.currentCpuUsage.value) }
    var ram by remember { mutableStateOf(monitor.currentRamUsage.value) }
    var temperature by remember { mutableStateOf(monitor.currentTemperature.value) }

    DisposableEffect(isServiceRunning) {
        if (isServiceRunning) monitor.startFpsMonitoring()
        onDispose { monitor.stopFpsMonitoring() }
    }

    LaunchedEffect(isServiceRunning) {
        while (isServiceRunning) {
            monitor.updateHardwareMetrics(includePerCore = false)
            fps = monitor.currentFps.value
            cpu = monitor.currentCpuUsage.value
            ram = monitor.currentRamUsage.value
            temperature = monitor.currentTemperature.value
            delay(1000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hudCorePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hudCorePulseAlpha"
    )

    val gameApps = remember { TurboSpaceManager.getGameApps(context) }
    val thirdPartyApps = remember { TurboSpaceManager.getThirdPartyApps(context) }
    val gameName = remember(selectedOverlayGame) {
        if (selectedOverlayGame == "NO TARGET SELECTED") {
            "NO GAME"
        } else {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(selectedOverlayGame, 0)).toString()
            }.getOrDefault(selectedOverlayGame)
        }
    }

    AnimatedVisibility(
        visible = isServiceRunning,
        enter = expandHorizontally(),
        exit = shrinkHorizontally()
    ) {
        Box(
            modifier = Modifier
                .width(350.dp)
                .wrapContentHeight()
                .clip(CutCornerShape(10.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            TurboColors.HudObsidian.copy(alpha = 0.96f),
                            Color(0xFF11131A).copy(alpha = 0.94f)
                        )
                    )
                )
                .border(1.dp, TurboColors.HudCyan.copy(alpha = 0.7f), CutCornerShape(10.dp))
        ) {
            // Outer neon glow.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                TurboColors.HudPurple.copy(alpha = pulseAlpha * 0.45f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header / drag area. Clicking CLOSE here only hides the overlay.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDragOverlay(dragAmount.x, dragAmount.y)
                                    }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedOverlayGame != "NO TARGET SELECTED") {
                            AppIconImage(
                                packageName = selectedOverlayGame,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            "TURBO SPACE // ${gameName.uppercase()}",
                            color = TurboColors.HudCyan,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Text(
                        "RESET",
                        color = TurboColors.HudCrimson,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(enabled = !isResetting) {
                                coroutineScope.launch {
                                    isResetting = true
                                    TurboSpaceRepository.emergencyReset(context)
                                    isResetting = false
                                }
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )

                    Text(
                        "CLOSE",
                        color = TurboColors.TextGray,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                context.stopService(Intent(context, GameSpaceOverlayService::class.java))
                            }
                            .padding(start = 5.dp, end = 1.dp, top = 2.dp, bottom = 2.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ── TOP LAYER: 3 live telemetry modules ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HudTelemetryCell(
                        title = "FRAME RATE",
                        value = "$fps FPS",
                        detail = "LIVE DISPLAY",
                        accent = TurboColors.HudCyan,
                        modifier = Modifier.weight(1f)
                    )
                    HudTelemetryCell(
                        title = "CPU LOAD",
                        value = cpu,
                        detail = "SYSTEM TOTAL",
                        accent = TurboColors.HudPurple,
                        modifier = Modifier.weight(1f)
                    )
                    HudTelemetryCell(
                        title = "MEMORY",
                        value = ram.substringBefore(" /").ifBlank { ram },
                        detail = "TEMP $temperature",
                        accent = TurboColors.HudCrimson,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ── CENTER CORE: primary master switch ──
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        (if (isPerfLockOn) TurboColors.HudCrimson else TurboColors.HudCyan).copy(alpha = pulseAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(37.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isPerfLockOn) TurboColors.HudCrimson.copy(alpha = 0.25f)
                                else TurboColors.HudObsidian.copy(alpha = 0.94f)
                            )
                            .border(
                                1.5.dp,
                                if (isPerfLockOn) TurboColors.HudCrimson else TurboColors.HudCyan,
                                RoundedCornerShape(50)
                            )
                            .clickable(enabled = !isPerfLockBusy) {
                                coroutineScope.launch {
                                    TurboSpaceRepository.togglePerformanceLock(context)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = "Turbo Core",
                                tint = if (isPerfLockOn) TurboColors.HudCrimson else TurboColors.HudCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                if (isPerfLockOn) "ON" else "CORE",
                                color = Color.White,
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ── PICKERS: opened inline so the service window remains self-contained ──
                if (showGamePicker) {
                    Spacer(Modifier.height(3.dp))
                    HudPickerPanel(
                        title = "SELECT GAME MODE TARGET",
                        apps = gameApps,
                        multiSelect = false,
                        onCancel = { showGamePicker = false },
                        onConfirm = { picked ->
                            showGamePicker = false
                            picked.firstOrNull()?.let { pkg ->
                                TurboSpaceRepository.setBoundGame(context, pkg)
                                coroutineScope.launch {
                                    TurboSpaceRepository.applyGameMode(context, GameModeState.BALANCED)
                                }
                            }
                        }
                    )
                }

                if (showCompactPicker) {
                    Spacer(Modifier.height(3.dp))
                    HudPickerPanel(
                        title = "SELECT APPS FOR COMPACT RAM",
                        apps = thirdPartyApps,
                        multiSelect = true,
                        onCancel = { showCompactPicker = false },
                        onConfirm = { picked ->
                            showCompactPicker = false
                            coroutineScope.launch {
                                TurboSpaceRepository.compactApps(context, picked)
                            }
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ── BOTTOM LAYER: 4 symmetrical quick-access slots ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(27.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HudQuickSlot(
                        icon = when (gameMode) {
                            GameModeState.BALANCED -> Icons.Filled.LocalFlorist
                            GameModeState.PERFORMANCE -> Icons.Filled.LocalFireDepartment
                            GameModeState.BATTERY -> Icons.Filled.AcUnit
                        },
                        label = "MODE ${gameMode.label.uppercase()}",
                        accent = TurboColors.HudCyan,
                        active = boundGame.isNotEmpty() && gameMode == GameModeState.PERFORMANCE,
                        enabled = !isGameModeBusy,
                        busy = isGameModeBusy,
                        onClick = {
                            if (boundGame.isEmpty()) {
                                showGamePicker = true
                            } else {
                                coroutineScope.launch {
                                    TurboSpaceRepository.cycleGameMode(context)
                                }
                            }
                        }
                    )
                    HudQuickSlot(
                        icon = Icons.Filled.Memory,
                        label = "COMPACT",
                        accent = TurboColors.HudPurple,
                        active = isCompacting,
                        enabled = true,
                        busy = isCompacting,
                        onClick = { showCompactPicker = !showCompactPicker }
                    )
                    HudQuickSlot(
                        icon = Icons.Filled.DoNotDisturbOn,
                        label = "DND",
                        accent = if (isDndOn) TurboColors.HudCrimson else TurboColors.HudCyan,
                        active = isDndOn,
                        onClick = { TurboSpaceRepository.toggleDoNotDisturb(context) }
                    )
                    HudQuickSlot(
                        icon = Icons.Filled.Refresh,
                        label = if (isResetting) "RESET..." else "RESET",
                        accent = TurboColors.HudCrimson,
                        active = false,
                        enabled = !isResetting,
                        busy = isResetting,
                        onClick = {
                            coroutineScope.launch {
                                isResetting = true
                                TurboSpaceRepository.emergencyReset(context)
                                isResetting = false
                            }
                        }
                    )
                }
            }
        }
    }
}

