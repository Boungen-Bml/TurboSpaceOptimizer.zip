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
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
    private val _selectedGamePackage = MutableStateFlow("NO TARGET SELECTED")
    val selectedGamePackage: StateFlow<String> = _selectedGamePackage.asStateFlow()

    private val _isNetOptActive = MutableStateFlow(false)
    val isNetOptActive: StateFlow<Boolean> = _isNetOptActive.asStateFlow()

    private val _isCpuOptActive = MutableStateFlow(false)
    val isCpuOptActive: StateFlow<Boolean> = _isCpuOptActive.asStateFlow()

    private val _isGpuOptActive = MutableStateFlow(false)
    val isGpuOptActive: StateFlow<Boolean> = _isGpuOptActive.asStateFlow()

    private val _isPerformanceActive = MutableStateFlow(false)
    val isPerformanceActive: StateFlow<Boolean> = _isPerformanceActive.asStateFlow()

    private val _isMonitorActive = MutableStateFlow(false)
    val isMonitorActive: StateFlow<Boolean> = _isMonitorActive.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // --- NEW: per-toggle loading state, so the UI can show a spinner and
    // disable the switch while its shell command is actually in flight,
    // instead of flipping instantly with no feedback while the user waits.
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

    // --- NEW: Compile feature state (5th optimization feature) ---
    private val _selectedCompileMode = MutableStateFlow(TurboSpaceManager.CompileMode.PROFILE_BASED)
    val selectedCompileMode: StateFlow<TurboSpaceManager.CompileMode> = _selectedCompileMode.asStateFlow()

    private val _isCompiling = MutableStateFlow(false)
    val isCompiling: StateFlow<Boolean> = _isCompiling.asStateFlow()

    private val _lastCompileSucceeded = MutableStateFlow<Boolean?>(null)
    val lastCompileSucceeded: StateFlow<Boolean?> = _lastCompileSucceeded.asStateFlow()

    // --- NEW: Persistence ---
    // Reads the last-saved target game, toggle states, and compile mode from
    // SharedPreferences and applies them to the in-memory state flows above.
    // Call this once, on app start, before the UI is shown - otherwise every
    // restart of the app forgets the selected game and every toggle's
    // on/off state even though the underlying Shizuku commands are still
    // in effect at the OS level.
    fun restoreFromPrefs(context: Context) {
        val saved = TurboSpaceManager.loadAppState(context)
        _selectedGamePackage.value = saved.packageName
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
            packageName = _selectedGamePackage.value,
            netActive = _isNetOptActive.value,
            cpuActive = _isCpuOptActive.value,
            gpuActive = _isGpuOptActive.value,
            perfActive = _isPerformanceActive.value,
            compileMode = _selectedCompileMode.value.name
        )
    }

    fun setSelectedGame(context: Context, pkg: String) {
        _selectedGamePackage.value = pkg
        persistState(context)
    }

    fun setServiceState(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun toggleSystemMonitor() {
        _isMonitorActive.value = !_isMonitorActive.value
    }

    suspend fun toggleNetworkOpt(context: Context) {
        val nextState = !_isNetOptActive.value
        _isNetLoading.value = true
        try {
            val outcome = TurboSpaceManager.optimizeNetwork(context, _selectedGamePackage.value)
            TurboSpaceManager.showOutcomeToast(context, outcome)
            if (outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED) {
                _isNetOptActive.value = nextState
                persistState(context)
            }
        } finally {
            _isNetLoading.value = false
        }
    }

    suspend fun toggleCpuOpt(context: Context) {
        val nextState = !_isCpuOptActive.value
        _isCpuLoading.value = true
        try {
            val outcome = TurboSpaceManager.reduceCpuLoad(context, _selectedGamePackage.value)
            TurboSpaceManager.showOutcomeToast(context, outcome)
            if (outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED) {
                _isCpuOptActive.value = nextState
                persistState(context)
            }
        } finally {
            _isCpuLoading.value = false
        }
    }

    suspend fun toggleGpuOpt(context: Context) {
        val nextState = !_isGpuOptActive.value
        _isGpuLoading.value = true
        try {
            val outcome = TurboSpaceManager.reduceGpuLoad(context, _selectedGamePackage.value, "0.9")
            TurboSpaceManager.showOutcomeToast(context, outcome)
            if (outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED) {
                _isGpuOptActive.value = nextState
                persistState(context)
            }
        } finally {
            _isGpuLoading.value = false
        }
    }

    suspend fun togglePerformanceMode(context: Context) {
        val targetState = !_isPerformanceActive.value
        _isPerfLoading.value = true
        try {
            val outcome = TurboSpaceManager.setPowerMode(context, targetState)
            TurboSpaceManager.showOutcomeToast(context, outcome)
            _isPerformanceActive.value = if (outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED) targetState else false
            persistState(context)
        } finally {
            _isPerfLoading.value = false
        }
    }

    // --- NEW: Feature 5 - Game Compile (ART AOT compilation) ---
    fun setCompileMode(context: Context, mode: TurboSpaceManager.CompileMode) {
        _selectedCompileMode.value = mode
        persistState(context)
    }

    suspend fun runCompile(context: Context) {
        _isCompiling.value = true
        _lastCompileSucceeded.value = null
        val outcome = TurboSpaceManager.compileGame(
            context,
            _selectedGamePackage.value,
            _selectedCompileMode.value
        )
        TurboSpaceManager.showOutcomeToast(context, outcome)
        _lastCompileSucceeded.value = outcome != TurboSpaceManager.CommandOutcome.BOTH_FAILED
        _isCompiling.value = false
    }

    // Full reset - used by the main hub's "Reset All" button ONLY. Restores
    // network, CPU, power mode, AND GPU/resolution. Safe to run here because
    // the player is on the main hub screen, not mid-match, so a slow or
    // failed resolution-restore command is easy to notice and retry.
    // Shows ONE aggregated Success/Success 2/Error-Error 2 toast covering
    // every sub-command this reset ran, rather than one toast per command.
    suspend fun resetAllRestrictions(context: Context) {
        _isResetting.value = true
        try {
            val appsOutcome = TurboSpaceManager.restoreApps(context, _selectedGamePackage.value)
            val gpuOutcome = TurboSpaceManager.restoreGpu(context, _selectedGamePackage.value)
            val powerOutcome = TurboSpaceManager.setPowerMode(context, false)
            val overall = TurboSpaceManager.combineOutcomes(appsOutcome, gpuOutcome, powerOutcome)
            TurboSpaceManager.showOutcomeToast(context, overall)
            _isNetOptActive.value = false
            _isCpuOptActive.value = false
            _isGpuOptActive.value = false
            _isPerformanceActive.value = false
            _isMonitorActive.value = false
            persistState(context)
            // Note: compilation is a one-shot action, not a restriction that needs
            // to be reverted. Compiled code is automatically replaced next time the
            // target app is updated, so Reset All intentionally leaves it untouched.
        } finally {
            _isResetting.value = false
        }
    }

    // Safe reset - used by the in-game overlay's reset button ONLY. Restores
    // network and CPU restrictions but deliberately NEVER touches GPU
    // resolution: if that command failed while the player is actively
    // mid-match, it could leave the screen glitched or frozen with no easy
    // way to recover without leaving the game entirely. _isGpuOptActive is
    // intentionally left untouched too, since resolution was not restored.
    suspend fun resetNetworkAndCpuOnly(context: Context) {
        val appsOutcome = TurboSpaceManager.restoreApps(context, _selectedGamePackage.value)
        val powerOutcome = TurboSpaceManager.setPowerMode(context, false)
        val overall = TurboSpaceManager.combineOutcomes(appsOutcome, powerOutcome)
        TurboSpaceManager.showOutcomeToast(context, overall)
        _isNetOptActive.value = false
        _isCpuOptActive.value = false
        _isPerformanceActive.value = false
        _isMonitorActive.value = false
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

    // --- NEW: Persistence ---
    // Holds everything the UI needs to restore itself to how the user left
    // it. Keeping these as plain SharedPreferences keys (rather than e.g.
    // DataStore) matches what this file already used for
    // IS_PERFORMANCE_ACTIVE, so there's only one persistence mechanism in
    // the whole app.
    data class SavedAppState(
        val packageName: String,
        val netActive: Boolean,
        val cpuActive: Boolean,
        val gpuActive: Boolean,
        val perfActive: Boolean,
        val compileMode: String
    )

    private const val KEY_SELECTED_GAME = "SELECTED_GAME"
    private const val KEY_NET_ACTIVE = "NET_ACTIVE"
    private const val KEY_CPU_ACTIVE = "CPU_ACTIVE"
    private const val KEY_GPU_ACTIVE = "GPU_ACTIVE"
    private const val KEY_COMPILE_MODE = "COMPILE_MODE"

    fun saveAppState(
        context: Context,
        packageName: String,
        netActive: Boolean,
        cpuActive: Boolean,
        gpuActive: Boolean,
        perfActive: Boolean,
        compileMode: String
    ) {
        getPrefs(context).edit()
            .putString(KEY_SELECTED_GAME, packageName)
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
            packageName = prefs.getString(KEY_SELECTED_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED",
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
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isAppPickerOpen by remember { mutableStateOf(false) }
    var isCompileMenuOpen by remember { mutableStateOf(false) } // NEW: dropdown for compile mode
    var isResetConfirmOpen by remember { mutableStateOf(false) } // NEW: confirm before Reset All

    // Loads the previously saved target game, toggle states, and compile
    // mode once when the hub first appears, so relaunching the app doesn't
    // forget everything the user had set up.
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

    val selectedGame by TurboSpaceRepository.selectedGamePackage.collectAsState()
    val isNetActive by TurboSpaceRepository.isNetOptActive.collectAsState()
    val isCpuActive by TurboSpaceRepository.isCpuOptActive.collectAsState()
    val isGpuActive by TurboSpaceRepository.isGpuOptActive.collectAsState()
    val isServiceRunning by TurboSpaceRepository.isServiceRunning.collectAsState()

    // --- NEW: per-toggle loading indicators ---
    val isNetLoading by TurboSpaceRepository.isNetLoading.collectAsState()
    val isCpuLoading by TurboSpaceRepository.isCpuLoading.collectAsState()
    val isGpuLoading by TurboSpaceRepository.isGpuLoading.collectAsState()
    val isPerfLoading by TurboSpaceRepository.isPerfLoading.collectAsState()
    val isResetting by TurboSpaceRepository.isResetting.collectAsState()

    // --- NEW: Compile feature UI state ---
    val selectedCompileMode by TurboSpaceRepository.selectedCompileMode.collectAsState()
    val isCompiling by TurboSpaceRepository.isCompiling.collectAsState()
    val lastCompileSucceeded by TurboSpaceRepository.lastCompileSucceeded.collectAsState()

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Overlay permission is required to show the in-game sidebar", Toast.LENGTH_LONG).show()
        }
    }

    fun launchOverlayServiceIfPermitted() {
        if (Settings.canDrawOverlays(context)) {
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
        // NOT "drawable" - raw keeps the file's bytes untouched, which the
        // GIF decoder needs) and it will show automatically.
        //
        // Fix for: "Unresolved reference 'raw'". R.raw.bg_main is a
        // COMPILE-TIME reference - if res/raw/ has no bg_main file (or no
        // files at all), Android's resource compiler never generates an
        // R.raw class, so referencing R.raw.bg_main directly is a hard
        // compile error, not something try/catch can rescue at runtime.
        // The fix is to look the resource up by NAME at runtime instead via
        // getIdentifier(), which returns 0 if it's missing instead of
        // failing to compile. That is what bgResId below does - this file
        // now compiles fine whether or not you've added the GIF yet, and
        // just shows a plain dark background until you do.
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
        // Dark scrim over the GIF so text and cards stay readable no matter
        // how bright/busy the animation is. Raise the alpha (closer to 1f)
        // for a darker, more legible look, or lower it (closer to 0f) to
        // let more of the animation show through. When there's no GIF at
        // all (bgResId == 0 above), this alone still gives the screen its
        // normal dark background.
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
        // ---------------- LEFT PANEL: target + status ----------------
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAppPickerOpen = true },
                colors = CardDefaults.cardColors(containerColor = TurboColors.CardBackground),
                border = BorderStroke(1.dp, TurboColors.BorderGray)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Target Game", color = TurboColors.TextGray, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(selectedGame, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!isShizukuReady) {
                OutlinedButton(
                    onClick = {
                        // Result arrives later via the DisposableEffect listener above,
                        // not synchronously here.
                        TurboSpaceManager.requestShizukuPermission()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Grant Shizuku Permission")
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { launchOverlayServiceIfPermitted() },
                modifier = Modifier.fillMaxWidth(),
                enabled = isShizukuReady && !isServiceRunning
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

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { isSettingsOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.SettingsIcon,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text("Settings")
            }
        }

        Spacer(Modifier.width(16.dp))

        // ---------------- RIGHT PANEL: optimization toggles ----------------
        LazyColumn(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OptimizationToggleCard(
                    title = "Network Optimization",
                    subtitle = "Blocks background data for other apps",
                    isActive = isNetActive,
                    isLoading = isNetLoading,
                    enabled = isShizukuReady && selectedGame != "NO TARGET SELECTED",
                    onToggle = { coroutineScope.launch { TurboSpaceRepository.toggleNetworkOpt(context) } }
                )
            }
            item {
                OptimizationToggleCard(
                    title = "CPU Optimization",
                    subtitle = "Idles other apps to free up CPU",
                    isActive = isCpuActive,
                    isLoading = isCpuLoading,
                    enabled = isShizukuReady && selectedGame != "NO TARGET SELECTED",
                    onToggle = { coroutineScope.launch { TurboSpaceRepository.toggleCpuOpt(context) } }
                )
            }
            item {
                OptimizationToggleCard(
                    title = "GPU Optimization",
                    subtitle = "Downscales render resolution for the target game",
                    isActive = isGpuActive,
                    isLoading = isGpuLoading,
                    enabled = isShizukuReady && selectedGame != "NO TARGET SELECTED",
                    onToggle = { coroutineScope.launch { TurboSpaceRepository.toggleGpuOpt(context) } }
                )
            }
            item {
                val isPerfActive by TurboSpaceRepository.isPerformanceActive.collectAsState()
                OptimizationToggleCard(
                    title = "Performance Mode",
                    subtitle = "Disables system power saving",
                    isActive = isPerfActive,
                    isLoading = isPerfLoading,
                    enabled = isShizukuReady,
                    onToggle = { coroutineScope.launch { TurboSpaceRepository.togglePerformanceMode(context) } }
                )
            }
            item {
                CompileGameCard(
                    selectedMode = selectedCompileMode,
                    isCompiling = isCompiling,
                    lastResult = lastCompileSucceeded,
                    isMenuOpen = isCompileMenuOpen,
                    onMenuToggle = { isCompileMenuOpen = it },
                    onModeSelected = { TurboSpaceRepository.setCompileMode(context, it) },
                    onRunClicked = { coroutineScope.launch { TurboSpaceRepository.runCompile(context) } },
                    enabled = isShizukuReady && selectedGame != "NO TARGET SELECTED"
                )
            }
            item {
                val isMonitorActive by TurboSpaceRepository.isMonitorActive.collectAsState()
                OptimizationToggleCard(
                    title = "System Monitor",
                    subtitle = "Shows live FPS / CPU / RAM overlay",
                    isActive = isMonitorActive,
                    isLoading = false,
                    enabled = true,
                    onToggle = { TurboSpaceRepository.toggleSystemMonitor() }
                )
            }
            item {
                Button(
                    onClick = { isResetConfirmOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isResetting && selectedGame != "NO TARGET SELECTED",
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

    if (isAppPickerOpen) {
        AppPickerDialog(
            onDismiss = { isAppPickerOpen = false },
            onAppSelected = {
                TurboSpaceRepository.setSelectedGame(context, it)
                isAppPickerOpen = false
            }
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
                        "mode for \"$selectedGame\" back to normal. This cannot be undone."
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

    if (isSettingsOpen) {
        AlertDialog(
            onDismissRequest = { isSettingsOpen = false },
            confirmButton = {
                TextButton(onClick = { isSettingsOpen = false }) { Text("Close") }
            },
            title = { Text("Settings") },
            text = { Text("TurboSpace Optimizer requires an active Shizuku session to run privileged commands. This app does not run as root.") }
        )
    }
}

// ============================================================================
// 8. REUSABLE UI COMPONENTS
// ============================================================================
@Composable
fun OptimizationToggleCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    // Visual polish only - no shell commands involved below, just how the
    // existing isActive state is rendered. Smoothly animates the card's
    // color instead of snapping instantly, and adds a soft pulsing red glow
    // behind the card while its toggle is active for a "powered on" feel.
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = TurboColors.TextGray, fontSize = 12.sp)
                }
                // Bug/UX fix: the switch used to flip the instant it was tapped,
                // with no feedback while the shell command it triggers actually
                // runs in the background (which can take a noticeable moment).
                // A spinner now shows in its place while the command is in
                // flight, and the switch is disabled so a slow command can't be
                // tapped again mid-flight.
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = TurboColors.BrightGlowRed
                    )
                } else {
                    Switch(
                        checked = isActive,
                        onCheckedChange = { onToggle() },
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
fun CompileGameCard(
    selectedMode: TurboSpaceManager.CompileMode,
    isCompiling: Boolean,
    lastResult: Boolean?,
    isMenuOpen: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onModeSelected: (TurboSpaceManager.CompileMode) -> Unit,
    onRunClicked: () -> Unit,
    enabled: Boolean
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
                "One-shot action. Pre-compiles the target game for faster launch times.",
                color = TurboColors.TextGray,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            Box {
                OutlinedButton(
                    onClick = { onMenuToggle(true) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled && !isCompiling
                ) {
                    Text(selectedMode.label)
                }
                DropdownMenu(expanded = isMenuOpen, onDismissRequest = { onMenuToggle(false) }) {
                    TurboSpaceManager.CompileMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                onModeSelected(mode)
                                onMenuToggle(false)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onRunClicked,
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
                    Text("Run Compile")
                }
            }

            // Per your request: removed the inline "Compilation finished." /
            // "Compilation failed." text here - it repeated the same result
            // the Success / Success 2 / Error - Error 2 Toast already shows,
            // just phrased differently, which read as two separate systems
            // disagreeing with each other. The Toast is now the only result
            // indicator for this action.
        }
    }
}

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    // Per your request: this list is now GAMES ONLY. "cmd game downscale"
    // (the GPU feature) is a real Android GameManager subcommand that only
    // recognizes packages the OS itself flags as games - it's a no-op on
    // anything else. Restricting the picker to games keeps every feature
    // (not just GPU) pointed at something the whole app makes sense for, and
    // makes it impossible to accidentally target something like a video or
    // social app that this app was never meant to touch.
    //
    // Detection uses two signals since manufacturers/ROMs vary in which one
    // they set: the modern ApplicationInfo.category (API 26+, matches this
    // app's minSdk) and the older FLAG_IS_GAME bit some legacy-targeting
    // game APKs still carry.
    val allGames = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { appInfo ->
                val isCategorizedAsGame = appInfo.category == ApplicationInfo.CATEGORY_GAME
                @Suppress("DEPRECATION")
                val hasLegacyGameFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                isCategorizedAsGame || hasLegacyGameFlag
            }
            .sortedBy { pm.getApplicationLabel(it).toString() }
    }

    val filteredGames = remember(searchQuery, allGames) {
        if (searchQuery.isBlank()) {
            allGames
        } else {
            val pm = context.packageManager
            allGames.filter {
                pm.getApplicationLabel(it).toString().contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Select Target Game") },
        text = {
            Column {
                // Search only narrows the already-games-only list above -
                // per your request, there is no way to widen this back out
                // to all apps from here.
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your games...") },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                if (allGames.isEmpty()) {
                    Text(
                        "No games detected on this device yet. Android only " +
                            "lists an app here once it's tagged as a game - " +
                            "some older or unusual game APKs aren't tagged " +
                            "that way and won't show up.",
                        color = TurboColors.TextGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else if (filteredGames.isEmpty()) {
                    Text(
                        "No games match \"$searchQuery\".",
                        color = TurboColors.TextGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filteredGames) { appInfo ->
                            val pm = context.packageManager
                            Text(
                                text = pm.getApplicationLabel(appInfo).toString(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAppSelected(appInfo.packageName) }
                                    .padding(vertical = 10.dp)
                            )
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
    val isMonitorActive by TurboSpaceRepository.isMonitorActive.collectAsState()
    val isServiceRunning by TurboSpaceRepository.isServiceRunning.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isResetting by remember { mutableStateOf(false) }

    // Bug fix: SystemMonitorEngine previously existed but was never created
    // or started, so the sidebar always showed "--" for FPS. It is now
    // created once per composition and its FPS callback is stopped in
    // onDispose to avoid leaking a Choreographer callback after the
    // overlay view is torn down.
    val monitor = remember { SystemMonitorEngine(context) }
    var fps by remember { mutableStateOf(monitor.currentFps.value) }
    var cpu by remember { mutableStateOf(monitor.currentCpuUsage.value) }
    var ram by remember { mutableStateOf(monitor.currentRamUsage.value) }

    DisposableEffect(isMonitorActive) {
        if (isMonitorActive) {
            monitor.startFpsMonitoring()
        }
        onDispose {
            monitor.stopFpsMonitoring()
        }
    }

    LaunchedEffect(isMonitorActive) {
        while (isMonitorActive) {
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
                        shape = RoundedCornerShape(16.dp)
                    )
            )
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                TurboColors.CardBackground.copy(alpha = 0.95f),
                                TurboColors.DarkBackground.copy(alpha = 0.95f)
                            )
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .border(1.dp, TurboColors.BrightRed.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(10.dp),
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
                Spacer(Modifier.height(8.dp))
                if (isMonitorActive) {
                    Text("FPS", color = TurboColors.TextGray, fontSize = 10.sp)
                    Text("$fps", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                    Box(
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .width(36.dp)
                            .height(1.dp)
                            .background(TurboColors.BrightRed.copy(alpha = 0.4f))
                    )

                    Text("CPU", color = TurboColors.TextGray, fontSize = 10.sp)
                    Text(cpu, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Box(
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .width(36.dp)
                            .height(1.dp)
                            .background(TurboColors.BrightRed.copy(alpha = 0.4f))
                    )

                    Text("RAM", color = TurboColors.TextGray, fontSize = 10.sp)
                    Text(ram, color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(10.dp))

                // Quick access to a SAFE reset right from the in-game overlay -
                // network + CPU only. GPU/resolution reset is intentionally
                // excluded here and only available from the main hub's Reset
                // All button, since a failed resolution restore mid-match could
                // glitch or freeze the screen with no easy way to recover.
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
                            contentDescription = "Reset All",
                            tint = TurboColors.BrightGlowRed
                        )
                    }
                }
            }
        }
    }
}
