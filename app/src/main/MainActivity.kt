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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
// 3. REPOSITORY & STATE MANAGEMENT
// ============================================================================
object TurboSpaceRepository {
    // --- Per-feature app selections ---
    // Each optimization feature now targets its OWN chosen app, instead of
    // one shared "Target Game" used everywhere. Network/CPU can target any
    // user-installed app; GPU, Compile, and the overlay launcher are
    // games-only (enforced by the picker dialog, not here).
    private val _selectedNetworkApp = MutableStateFlow("NO TARGET SELECTED")
    val selectedNetworkApp: StateFlow<String> = _selectedNetworkApp.asStateFlow()

    private val _selectedCpuApp = MutableStateFlow("NO TARGET SELECTED")
    val selectedCpuApp: StateFlow<String> = _selectedCpuApp.asStateFlow()

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

    // --- Compile feature state ---
    private val _selectedCompileMode = MutableStateFlow(TurboSpaceManager.CompileMode.PROFILE_BASED)
    val selectedCompileMode: StateFlow<TurboSpaceManager.CompileMode> = _selectedCompileMode.asStateFlow()

    private val _isCompiling = MutableStateFlow(false)
    val isCompiling: StateFlow<Boolean> = _isCompiling.asStateFlow()

    private val _lastCompileSucceeded = MutableStateFlow<Boolean?>(null)
    val lastCompileSucceeded: StateFlow<Boolean?> = _lastCompileSucceeded.asStateFlow()

    // --- Persistence ---
    // Reads everything back from SharedPreferences on app start, so
    // relaunching the app doesn't forget every per-feature app selection
    // even though the underlying Shizuku commands are still in effect at
    // the OS level.
    fun restoreFromPrefs(context: Context) {
        val saved = TurboSpaceManager.loadAppState(context)
        _selectedNetworkApp.value = saved.networkApp
        _selectedCpuApp.value = saved.cpuApp
        _selectedGpuGame.value = saved.gpuGame
        _selectedOverlayGame.value = saved.overlayGame
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
            networkApp = _selectedNetworkApp.value,
            cpuApp = _selectedCpuApp.value,
            gpuGame = _selectedGpuGame.value,
            overlayGame = _selectedOverlayGame.value,
            netActive = _isNetOptActive.value,
            cpuActive = _isCpuOptActive.value,
            gpuActive = _isGpuOptActive.value,
            perfActive = _isPerformanceActive.value,
            compileMode = _selectedCompileMode.value.name
        )
    }

    fun setSelectedNetworkApp(context: Context, pkg: String) {
        _selectedNetworkApp.value = pkg
        persistState(context)
    }

    fun setSelectedCpuApp(context: Context, pkg: String) {
        _selectedCpuApp.value = pkg
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

    // --- Network Optimization: press "Start" to apply to the selected app ---
    suspend fun startNetworkOpt(context: Context) {
        _isNetLoading.value = true
        try {
            val outcome = TurboSpaceManager.optimizeNetwork(context, _selectedNetworkApp.value)
            TurboSpaceManager.showOutcomeToast(context, outcome)
            _isNetOptActive.value = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
            persistState(context)
        } finally {
            _isNetLoading.value = false
        }
    }

    // --- CPU Optimization: press "Start" to apply to the selected app ---
    suspend fun startCpuOpt(context: Context) {
        _isCpuLoading.value = true
        try {
            val outcome = TurboSpaceManager.reduceCpuLoad(context, _selectedCpuApp.value)
            TurboSpaceManager.showOutcomeToast(context, outcome)
            _isCpuOptActive.value = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
            persistState(context)
        } finally {
            _isCpuLoading.value = false
        }
    }

    // --- GPU Optimization: press "Start" to apply to the selected game ---
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

    // Balance Mode (default, off) vs Performance Mode - now chosen from the
    // in-game overlay's rocket-icon menu.
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

    // --- Game Compile (ART AOT compilation) ---
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

    // Full reset - used by the main hub's "Reset All" button ONLY. Restores
    // whichever app was targeted by Network, CPU, and GPU (they can each be
    // a DIFFERENT app now), plus power mode. Safe to run here because the
    // player is on the main hub screen, not mid-match, so a slow or failed
    // resolution-restore command is easy to notice and retry.
    suspend fun resetAllRestrictions(context: Context) {
        _isResetting.value = true
        try {
            val netOutcome = TurboSpaceManager.restoreApps(context, _selectedNetworkApp.value)
            val cpuOutcome = TurboSpaceManager.restoreApps(context, _selectedCpuApp.value)
            val gpuOutcome = TurboSpaceManager.restoreGpu(context, _selectedGpuGame.value)
            val powerOutcome = TurboSpaceManager.setPowerMode(context, false)
            val overall = TurboSpaceManager.combineOutcomes(netOutcome, cpuOutcome, gpuOutcome, powerOutcome)
            TurboSpaceManager.showOutcomeToast(context, overall)
            _isNetOptActive.value = false
            _isCpuOptActive.value = false
            _isGpuOptActive.value = false
            _isPerformanceActive.value = false
            persistState(context)
            // Note: compilation is a one-shot action, not a restriction that needs
            // to be reverted. Compiled code is automatically replaced next time the
            // target app is updated, so Reset All intentionally leaves it untouched.
        } finally {
            _isResetting.value = false
        }
    }

    // Safe reset - used by the in-game overlay's reset button ONLY. Restores
    // whichever app was targeted by Network and CPU (each can be a
    // different app) but deliberately NEVER touches GPU resolution: if that
    // command failed while the player is actively mid-match, it could leave
    // the screen glitched or frozen with no easy way to recover.
    suspend fun resetNetworkAndCpuOnly(context: Context) {
        val netOutcome = TurboSpaceManager.restoreApps(context, _selectedNetworkApp.value)
        val cpuOutcome = TurboSpaceManager.restoreApps(context, _selectedCpuApp.value)
        val powerOutcome = TurboSpaceManager.setPowerMode(context, false)
        val overall = TurboSpaceManager.combineOutcomes(netOutcome, cpuOutcome, powerOutcome)
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

    val currentFps = mutableStateOf(60)
    val currentCpuUsage = mutableStateOf("0%")
    val currentRamUsage = mutableStateOf("0 MB / 0 MB")

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

    suspend fun updateHardwareMetrics() = withContext(Dispatchers.IO) {
        updateRamInfo()
        updateCpuUsageViaShizuku()
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

    // Bug fix: direct RandomAccessFile("/proc/stat") reads are blocked by
    // SELinux on Android 8+ for normal (non-privileged) apps, so this always
    // reported "RESTRICTED". Reading the same file through a Shizuku shell
    // process runs with the elevated ADB-shell context Shizuku grants, which
    // is allowed to read /proc/stat, so real percentages come back instead.
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
    // Game compile jobs (ART AOT, especially "everything" mode) routinely run
    // far longer than a normal toggle command. The old shared 10s timeout
    // killed the shell process mid-compile before it could finish, so
    // compileGame() now passes this longer, dedicated timeout instead of
    // reusing COMMAND_TIMEOUT_MS.
    private const val COMPILE_TIMEOUT_MS = 120000L

    // --- NEW: Compile modes backed by real ART compiler filters ---
    // SPACE_SAVING     -> "space"         fastest to compile, smallest storage footprint, mid-tier speed
    // FULL_SPEED       -> "everything"    compiles every method (including debug info),
    //                                      the most exhaustive filter ART offers, best possible
    //                                      performance, largest storage footprint and longest compile time
    // PROFILE_BASED    -> "speed-profile" compiles only hot code paths from collected usage profiles
    enum class CompileMode(val artFilter: String, val label: String) {
        SPACE_SAVING("space", "Space Saving"),
        FULL_SPEED("everything", "Compile Everything"),
        PROFILE_BASED("speed-profile", "Usage-Based Compile")
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Persistence ---
    // Holds everything the UI needs to restore itself to how the user left
    // it. Keeping these as plain SharedPreferences keys (rather than e.g.
    // DataStore) matches what this file already used for
    // IS_PERFORMANCE_ACTIVE, so there's only one persistence mechanism in
    // the whole app. Each optimization feature now has its OWN selected
    // app/game, since Network/CPU/GPU can each target a different one.
    data class SavedAppState(
        val networkApp: String,
        val cpuApp: String,
        val gpuGame: String,
        val overlayGame: String,
        val netActive: Boolean,
        val cpuActive: Boolean,
        val gpuActive: Boolean,
        val perfActive: Boolean,
        val compileMode: String
    )

    private const val KEY_NETWORK_APP = "NETWORK_APP"
    private const val KEY_CPU_APP = "CPU_APP"
    private const val KEY_GPU_GAME = "GPU_GAME"
    private const val KEY_OVERLAY_GAME = "OVERLAY_GAME"
    private const val KEY_NET_ACTIVE = "NET_ACTIVE"
    private const val KEY_CPU_ACTIVE = "CPU_ACTIVE"
    private const val KEY_GPU_ACTIVE = "GPU_ACTIVE"
    private const val KEY_COMPILE_MODE = "COMPILE_MODE"

    fun saveAppState(
        context: Context,
        networkApp: String,
        cpuApp: String,
        gpuGame: String,
        overlayGame: String,
        netActive: Boolean,
        cpuActive: Boolean,
        gpuActive: Boolean,
        perfActive: Boolean,
        compileMode: String
    ) {
        getPrefs(context).edit()
            .putString(KEY_NETWORK_APP, networkApp)
            .putString(KEY_CPU_APP, cpuApp)
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
        return SavedAppState(
            networkApp = prefs.getString(KEY_NETWORK_APP, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
            cpuApp = prefs.getString(KEY_CPU_APP, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
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

    // Shizuku.requestPermission() is asynchronous - the result only arrives
    // through this listener once the user answers the system dialog.
    // Checking isShizukuAvailableAndGranted() immediately after calling
    // requestPermission() will almost always read the old (denied) state.
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

    // Fix for: "Cannot access 'newProcess' because it is private in
    // 'Shizuku'". Shizuku.newProcess() is intentionally hidden from the
    // library's public API surface - it exists on the class but isn't
    // meant to be called directly. The documented, widely-used workaround
    // (used by Shizuku-based apps in general) is to invoke it via
    // reflection instead. This is the ONLY change needed to fix the error;
    // the shell commands passed into it are untouched.
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

    // Bug fix: the previous one-liner `runSingleCommand(primaryCmd) ||
    // (fallbackCmd.isNotEmpty() && runSingleCommand(fallbackCmd))` relied on
    // Kotlin's `||` short-circuit to decide success, which is correct in
    // principle, but folded both attempts into a single boolean with no way
    // to know afterward *which* command actually ran - and no isolation
    // between the two attempts, so a slow/hanging primary call could affect
    // the fallback's own try-catch scope. Each attempt now runs in its own
    // explicit try-catch block, the result is tracked in its own local
    // variable, and the Toast only fires once both attempts are confirmed
    // failed - never when the fallback quietly succeeded.
    //
    // Per your request: callers now get back WHICH of the two commands
    // actually worked (or that both failed), not just a plain true/false, so
    // the UI can show "Success" / "Success 2" / "Error - Error 2" instead of
    // a single generic pass/fail message.
    enum class CommandOutcome { PRIMARY_SUCCESS, FALLBACK_SUCCESS, BOTH_FAILED }

    // Combines several CommandOutcomes (e.g. the 2-3 sub-commands a single
    // Reset action runs) into one overall result: only PRIMARY_SUCCESS if
    // every single one hit its primary command; FALLBACK_SUCCESS if none
    // fully failed but at least one needed its backup; BOTH_FAILED if any
    // single one failed outright.
    fun combineOutcomes(vararg outcomes: CommandOutcome): CommandOutcome {
        return when {
            outcomes.any { it == CommandOutcome.BOTH_FAILED } -> CommandOutcome.BOTH_FAILED
            outcomes.any { it == CommandOutcome.FALLBACK_SUCCESS } -> CommandOutcome.FALLBACK_SUCCESS
            else -> CommandOutcome.PRIMARY_SUCCESS
        }
    }

    fun showOutcomeToast(context: Context, outcome: CommandOutcome) {
        val message = when (outcome) {
            CommandOutcome.PRIMARY_SUCCESS -> "Success"
            CommandOutcome.FALLBACK_SUCCESS -> "Success 2"
            CommandOutcome.BOTH_FAILED -> "Error - Error 2"
        }
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    // The detection here is never guessed or assumed - isShizukuAvailableAndGranted()
    // and each command's real process exit code (in runSingleCommand) are the
    // only things that decide PRIMARY_SUCCESS / FALLBACK_SUCCESS / BOTH_FAILED.
    // If Android/Shizuku refuses the command outright, that is reported as
    // BOTH_FAILED immediately, the same as if both attempts had actually run
    // and failed.
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

    // Boolean convenience wrapper, kept for any call site that only cares
    // whether SOMETHING succeeded and doesn't need the Success/Success 2
    // distinction.
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

    // Fix per your request: fallback changed from the Settings.Global write
    // to the real "cmd power set-fixed-performance-mode-enabled" shell
    // command, which forces sustained performance mode directly through the
    // PowerManager service instead of toggling a settings key. Disabling
    // (enablePerformance = false) uses the same command pair with "false" /
    // mode 0, so Reset All can cleanly undo whichever one actually took
    // effect.
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

    // Fix per your request: this was backwards - it added the SELECTED GAME
    // itself to the background-data blacklist, cutting the game's own
    // network instead of protecting it. It now whitelists the target game
    // so Android's data saver / background restrictions leave it alone.
    // Per your latest request, the appops fallback has been removed
    // entirely - restrict-background-whitelist either works or it doesn't,
    // with no generic second-best command to fall back to.
    suspend fun optimizeNetwork(context: Context, packageName: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd netpolicy add restrict-background-whitelist $packageName"
        return executeCommandDetailed(context, primary)
    }

    // Fix per your request: the fallback now uses the standard modern
    // "cmd appops set ... RUN_IN_BACKGROUND deny" form instead of the older
    // "am make-uid-idle" call. "am make-uid-idle" forces the process straight
    // into the idle bucket, which can fight with the game's own foreground
    // lifecycle while it's actively being played; "appops set ... deny" only
    // restricts background execution permission and does not touch a
    // currently foregrounded app's running state.
    suspend fun reduceCpuLoad(context: Context, packageName: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd activity set-inactive $packageName true"
        val fallback = "cmd appops set $packageName RUN_IN_BACKGROUND deny"
        return executeCommandDetailed(context, primary, fallback)
    }

    suspend fun reduceGpuLoad(context: Context, packageName: String, ratio: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd game downscale $packageName $ratio"
        // NOTE: the previous fallback "cmd display density 280" was removed.
        // It rescaled the ENTIRE system UI (every app's icons/text), had no
        // stored "original density" to restore from, and was not reverted by
        // Reset All - a failed density change could leave the whole device
        // stuck at the wrong scale until a manual "wm density reset" or a
        // reboot. There is no safe device-wide fallback for this action, so
        // if "cmd game downscale" fails, the GPU toggle now simply reports
        // failure instead of taking a risky whole-system action.
        return executeCommandDetailed(context, primary)
    }

    // Restores native resolution using Android's real "downscale reset"
    // subcommand, falling back to explicitly setting the ratio back to 1.0.
    // Deliberately kept as its OWN function, separate from restoreApps()
    // below: it is only ever called from the main hub's full Reset All flow,
    // never from the in-game overlay's reset button, so a failed resolution
    // restore can't glitch or freeze the screen while the player is
    // mid-match with no easy way back to the main hub.
    suspend fun restoreGpu(context: Context, packageName: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd game downscale reset $packageName"
        val fallback = "cmd game downscale $packageName 1.0"
        return executeCommandDetailed(context, primary, fallback)
    }

    // Reverts network and CPU restrictions only - intentionally does NOT
    // touch GPU/resolution. Safe to call from anywhere, including while a
    // game is actively running (the in-game overlay's reset button uses
    // this).
    suspend fun restoreApps(context: Context, packageName: String): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val res1 = executeCommandDetailed(context, "cmd activity set-inactive $packageName false", "cmd appops set $packageName RUN_IN_BACKGROUND allow")
        // Updated to remove from the whitelist (matching optimizeNetwork's
        // new "add ... whitelist" above) instead of the old blacklist -
        // removing from the wrong list would leave the whitelist entry
        // stuck forever and Reset All would silently do nothing here.
        val res2 = executeCommandDetailed(context, "cmd netpolicy remove restrict-background-whitelist $packageName", "appops set $packageName RUN_IN_BACKGROUND default")
        return combineOutcomes(res1, res2)
    }

    // --- NEW: Feature 5 - Game Compile (ART AOT compilation) ---
    // Pre-compiles the target game's bytecode to native code so it launches
    // faster and runs with less jank early on. This is a one-shot action,
    // not a toggle: there is no "on/off" state to track or revert.
    suspend fun compileGame(context: Context, packageName: String, mode: CompileMode): CommandOutcome {
        if (packageName == "NO TARGET SELECTED") return CommandOutcome.BOTH_FAILED
        val primary = "cmd package compile -m ${mode.artFilter} -f $packageName"
        val fallback = "pm compile -m ${mode.artFilter} -f $packageName"
        // Fix: compile jobs (especially "everything" mode) can run well past
        // the 10s default used by every other toggle, so this now passes the
        // dedicated, longer COMPILE_TIMEOUT_MS instead of timing out mid-job.
        return executeCommandDetailed(context, primary, fallback, timeoutMs = COMPILE_TIMEOUT_MS)
    }
}

// ============================================================================
// 6. UNIVERSAL OVERLAY SERVICE (ANDROID 8 TO 16)
// ============================================================================
class GameSpaceOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // Fix for: "Unresolved reference 'viewModelStore'". A plain Service (and
    // LifecycleService) does not implement ViewModelStoreOwner the way
    // ComponentActivity does, so that property never existed here at all -
    // it has to be provided explicitly. This is a real ViewModelStore
    // instance, cleared in onDestroy() below so it doesn't leak.
    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        
        // Handles Foreground Service across Android 8 to 16
        startForegroundServiceWithNotification()
        
        TurboSpaceRepository.setServiceState(true)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
            setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
            setContent { GameSpaceInGameSidebar() }
        }

        // WindowManager Flags for Android 8+
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        
        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to display overlay", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "turbo_overlay_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Required for Android 8 (API 26) and above
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

        // Universal Foreground Service Check (Android 14+ / API 34+ requirement)
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
                // Ignore window manager removal errors
            }
        }
        // Clears the ViewModelStore we now own (see the ViewModelStoreOwner
        // fix above) so any ViewModels created under it aren't leaked.
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

    // Loads every per-feature app selection back from SharedPreferences once
    // when the hub first appears, so relaunching the app doesn't forget
    // what you picked for Network/CPU/GPU/Overlay.
    LaunchedEffect(Unit) {
        TurboSpaceRepository.restoreFromPrefs(context)
    }

    // Registers the async Shizuku permission callback once per composition
    // and cleans it up on dispose, so isShizukuReady reflects the real
    // grant result instead of being read before the user answers the dialog.
    DisposableEffect(Unit) {
        val listener = TurboSpaceManager.addPermissionResultListener { granted ->
            isShizukuReady = granted
        }
        onDispose { TurboSpaceManager.removePermissionResultListener(listener) }
    }

    val selectedNetworkApp by TurboSpaceRepository.selectedNetworkApp.collectAsState()
    val selectedCpuApp by TurboSpaceRepository.selectedCpuApp.collectAsState()
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

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Overlay permission is required to show the in-game sidebar", Toast.LENGTH_LONG).show()
        }
    }

    // Per your request: "Start Game Overlay" now launches the SELECTED GAME
    // itself (via its normal launch intent) in addition to starting the
    // floating overlay service - not just showing the overlay over
    // whatever happened to be on screen.
    fun launchGameAndOverlay() {
        if (Settings.canDrawOverlays(context)) {
            if (selectedOverlayGame != "NO TARGET SELECTED") {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedOverlayGame)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                } else {
                    Toast.makeText(context, "Could not launch the selected game", Toast.LENGTH_SHORT).show()
                }
            }
            ContextCompat.startForegroundService(context, Intent(context, GameSpaceOverlayService::class.java))
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated GIF background for the main hub - fully OPTIONAL. Drop a
        // GIF file at app/src/main/res/raw/bg_main.gif (the "raw" folder,
        // NOT "drawable") and it will show automatically. Looked up by name
        // at runtime (not R.raw.bg_main directly) so the app still compiles
        // and runs fine with a plain dark background if you never add one.
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

                // Per your request: "+" picker for the game to actually
                // launch when "Start Game Overlay" is pressed. Tapping the
                // game cover once (after one is picked) reopens the picker
                // to change it - the hint text above only appears once a
                // game is selected, since before that there's nothing to
                // "change" yet.
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
                item {
                    AppActionCard(
                        title = "Network Optimization",
                        subtitle = "Whitelists the selected app from background data limits",
                        selectedApp = selectedNetworkApp,
                        gamesOnly = false,
                        isActive = isNetActive,
                        isLoading = isNetLoading,
                        enabled = isShizukuReady,
                        onAppSelected = { TurboSpaceRepository.setSelectedNetworkApp(context, it) },
                        onStart = { coroutineScope.launch { TurboSpaceRepository.startNetworkOpt(context) } }
                    )
                }
                item {
                    AppActionCard(
                        title = "CPU Optimization",
                        subtitle = "Idles the selected app to free up CPU",
                        selectedApp = selectedCpuApp,
                        gamesOnly = false,
                        isActive = isCpuActive,
                        isLoading = isCpuLoading,
                        enabled = isShizukuReady,
                        onAppSelected = { TurboSpaceRepository.setSelectedCpuApp(context, it) },
                        onStart = { coroutineScope.launch { TurboSpaceRepository.startCpuOpt(context) } }
                    )
                }
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

// Loads and shows an app's real icon (falls back to a generic controller
// icon if the icon can't be loaded for some reason).
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

// One card per feature (Network / CPU / GPU): tap the app row to pick a
// target via AppPickerDialog, then press "Start" to actually apply the
// command to whichever app is currently selected.
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

// The Compile card: tap "Select Game to Compile" to pick a game, which then
// opens CompileModeDialog to choose one of the 3 ART filters before running.
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

// Shown right after picking a game for Compile - lets the user choose which
// of the 3 ART filters to run, with OK/Cancel instead of a dropdown menu.
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

// Generic app picker used everywhere an app/game needs to be chosen.
// gamesOnly=true restricts the list to apps Android itself tags as games
// (used by GPU, Compile, and the overlay launcher); gamesOnly=false shows
// every user-installed app excluding system apps (used by Network/CPU).
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
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
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
// 9. IN-GAME OVERLAY SIDEBAR (rendered by GameSpaceOverlayService)
// ============================================================================
@Composable
fun GameSpaceInGameSidebar() {
    val context = LocalContext.current
    val isServiceRunning by TurboSpaceRepository.isServiceRunning.collectAsState()
    val isPerformanceActive by TurboSpaceRepository.isPerformanceActive.collectAsState()
    val isPerfLoading by TurboSpaceRepository.isPerfLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isResetting by remember { mutableStateOf(false) }
    var isModeMenuOpen by remember { mutableStateOf(false) }

    // Bug fix: SystemMonitorEngine previously existed but was never created
    // or started, so the sidebar always showed "--" for FPS. It is now
    // created once per composition and its FPS callback is stopped in
    // onDispose to avoid leaking a Choreographer callback after the
    // overlay view is torn down. Stats now run automatically whenever the
    // overlay is visible - there's no separate "System Monitor" toggle on
    // the main hub anymore.
    val monitor = remember { SystemMonitorEngine(context) }
    var fps by remember { mutableStateOf(monitor.currentFps.value) }
    var cpu by remember { mutableStateOf(monitor.currentCpuUsage.value) }
    var ram by remember { mutableStateOf(monitor.currentRamUsage.value) }

    DisposableEffect(isServiceRunning) {
        if (isServiceRunning) {
            monitor.startFpsMonitoring()
        }
        onDispose {
            monitor.stopFpsMonitoring()
        }
    }

    LaunchedEffect(isServiceRunning) {
        while (isServiceRunning) {
            monitor.updateHardwareMetrics()
            fps = monitor.currentFps.value
            cpu = monitor.currentCpuUsage.value
            ram = monitor.currentRamUsage.value
            delay(1000L)
        }
    }

    // Visual polish only - no optimization commands touched below. A slow
    // pulsing red glow behind the whole bar and a gentle "breathing" scale
    // on the shield icon give the overlay a powered-on neon look instead of
    // a flat static card.
    val infiniteTransition = rememberInfiniteTransition(label = "sidebarPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sidebarGlowAlpha"
    )
    val shieldScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldScale"
    )

    AnimatedVisibility(
        visible = isServiceRunning,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                TurboColors.BrightRed.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
            )
            // Widened from the original 76dp to feel closer to the size of
            // OPPO Game Space's in-game side panel, now that it carries the
            // power-mode picker plus fps/cpu/ram/gpu stats and reset.
            Column(
                modifier = Modifier
                    .width(112.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                TurboColors.CardBackground.copy(alpha = 0.95f),
                                TurboColors.DarkBackground.copy(alpha = 0.95f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, TurboColors.BrightRed.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = TurboColors.BrightGlowRed,
                    modifier = Modifier
                        .size(22.dp)
                        .scale(shieldScale)
                )

                Spacer(Modifier.height(10.dp))

                // Per your request: a rocket icon labelled "Balance Mode"
                // (the default). Tapping it opens a 2-option picker -
                // Balance Mode vs Performance Mode - replacing the old
                // standalone "Performance Mode" card on the main hub.
                Box {
                    IconButton(
                        onClick = { isModeMenuOpen = true },
                        modifier = Modifier.size(32.dp),
                        enabled = !isPerfLoading
                    ) {
                        if (isPerfLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TurboColors.BrightGlowRed
                            )
                        } else {
                            Icon(
                                Icons.Filled.RocketLaunch,
                                contentDescription = "Power mode",
                                tint = if (isPerformanceActive) TurboColors.BrightRed else TurboColors.TextGray
                            )
                        }
                    }
                    DropdownMenu(expanded = isModeMenuOpen, onDismissRequest = { isModeMenuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = !isPerformanceActive, onClick = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Balance Mode")
                                }
                            },
                            onClick = {
                                isModeMenuOpen = false
                                coroutineScope.launch { TurboSpaceRepository.setPowerMode(context, false) }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = isPerformanceActive, onClick = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Performance Mode")
                                }
                            },
                            onClick = {
                                isModeMenuOpen = false
                                coroutineScope.launch { TurboSpaceRepository.setPowerMode(context, true) }
                            }
                        )
                    }
                }
                Text(
                    if (isPerformanceActive) "Performance" else "Balance Mode",
                    color = TurboColors.TextGray,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(48.dp)
                        .height(1.dp)
                        .background(TurboColors.BrightRed.copy(alpha = 0.4f))
                )

                Text("FPS", color = TurboColors.TextGray, fontSize = 10.sp)
                Text("$fps", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                Box(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .width(48.dp)
                        .height(1.dp)
                        .background(TurboColors.BrightRed.copy(alpha = 0.4f))
                )

                Text("CPU", color = TurboColors.TextGray, fontSize = 10.sp)
                Text(cpu, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Box(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .width(48.dp)
                        .height(1.dp)
                        .background(TurboColors.BrightRed.copy(alpha = 0.4f))
                )

                Text("RAM", color = TurboColors.TextGray, fontSize = 10.sp)
                Text(ram, color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)

                Box(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .width(48.dp)
                        .height(1.dp)
                        .background(TurboColors.BrightRed.copy(alpha = 0.4f))
                )

                // Honest placeholder: Android has no shell command this app
                // can use to read real GPU usage, so this always shows
                // "N/A" rather than a made-up number.
                Text("GPU", color = TurboColors.TextGray, fontSize = 10.sp)
                Text("N/A", color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)

                Spacer(Modifier.height(10.dp))

                // Quick access to a SAFE reset right from the in-game overlay -
                // restores whichever apps were targeted by Network + CPU
                // Optimization back to normal. GPU/resolution reset is
                // intentionally excluded here and only available from the
                // main hub's Reset All button, since a failed resolution
                // restore mid-match could glitch or freeze the screen.
                IconButton(
                    onClick = {
                        if (!isResetting) {
                            coroutineScope.launch {
                                isResetting = true
                                TurboSpaceRepository.resetNetworkAndCpuOnly(context)
                                isResetting = false
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = TurboColors.BrightGlowRed
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reset Network + CPU",
                            tint = TurboColors.BrightGlowRed
                        )
                    }
                }
            }
        }
    }
}
