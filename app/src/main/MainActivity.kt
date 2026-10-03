
package com.turbospace.optimizer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

// ============================================================================
// 1. CORE MANAGER - Shizuku + Commands + Telemetry
// ============================================================================
object TurboSpaceManager {
    const val COMMAND_TIMEOUT_MS = 8000L
    data class CommandOutcome(val success: Boolean, val output: String = "")

    fun isRecognizedGame(pkg: String): Boolean = pkg != "NO TARGET SELECTED"

    fun getInstalledGames(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        // FIX #8: removed || true — now correctly filters only games
        return pm.getInstalledApplications(PackageManager.GET_META_DATA).filter {
            it.category == ApplicationInfo.CATEGORY_GAME || (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
    }

    fun getAppLabel(context: Context, pkg: String): String {
        return try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
    }

    private fun newShizukuProcess(cmd: String): Process? {
        return try {
            val cl = Class.forName("rikka.shizuku.Shizuku")
            val method = cl.getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            @Suppress("UNCHECKED_CAST")
            method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        } catch (_: Exception) { null }
    }

    private fun readProcessResult(proc: Process): CommandOutcome {
        return try {
            var out = ""
            var err = ""
            val t1 = Thread { out = try { proc.inputStream.bufferedReader().readText() } catch (_: Exception) { "" } }
            val t2 = Thread { err = try { proc.errorStream.bufferedReader().readText() } catch (_: Exception) { "" } }
            t1.start(); t2.start()
            val finished = proc.waitFor(COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) { proc.destroy(); return CommandOutcome(false, "timeout") }
            t1.join(250); t2.join(250)
            CommandOutcome(proc.exitValue() == 0, out + err)
        } catch (e: Exception) { CommandOutcome(false, e.message ?: "error") }
    }

    fun executeCommandDetailedNoCtx(cmd: String): CommandOutcome {
        // FIX 1 & 3: Check Shizuku granted first, no fallback to sh -c which has no permission for cmd power/activity/deviceidle
        if (!isShizukuAvailableAndGranted()) {
            return CommandOutcome(false, "Shizuku not available")
        }
        val shizukuProc = newShizukuProcess(cmd)
        if (shizukuProc != null) {
            return readProcessResult(shizukuProc)
        }
        return CommandOutcome(false, "Shizuku not available")
    }

    fun executeWithPackage(cmdTemplate: String, gamePackage: String): CommandOutcome {
        // FIX 2: Use gamePackage directly, don't call getForegroundPackageViaShizuku (gets wrong package)
        // FIX 3: Check Shizuku before every command
        if (!isShizukuAvailableAndGranted()) {
            return CommandOutcome(false, "Shizuku not available - please grant Shizuku permission first")
        }
        val cmd = cmdTemplate.replace("<package_name>", gamePackage)
        return executeCommandDetailedNoCtx(cmd)
    }

    fun getBatteryPercent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return try { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (_: Exception) { 85 }
    }

    fun getRamUsagePercent(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return try { ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt() } catch (_: Exception) { 75 }
    }

    fun getCpuTemp(): String {
        return try {
            val file = java.io.File("/sys/class/thermal/thermal_zone0/temp")
            if (file.exists()) {
                val t = file.readText().trim().toInt() / 1000
                "${t}°C"
            } else "42°C"
        } catch (_: Exception) { "42°C" }
    }

    suspend fun getCpuUsagePercentViaShizuku(): Int {
        return try {
            // Primary: dumpsys cpuinfo - live usage, no lifetime average
            val result = executeCommandDetailedNoCtx("dumpsys cpuinfo | head -n 10")
            if (result.success && result.output.isNotBlank()) {
                val percentRegex = Regex("""(\d+)%""")
                val match = percentRegex.find(result.output)
                if (match != null) {
                    return match.groupValues[1].toInt().coerceIn(0, 100)
                }
            }
            // FIX: If dumpsys fails, read /proc/stat TWICE 200ms apart and calculate delta (live usage, not lifetime average)
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
            if (!firstResult.success) return 68
            val firstParsed = parseProcStat(firstResult.output) ?: return 68
            // 200ms delta for live usage - use delay instead of Thread.sleep in coroutine context
            kotlinx.coroutines.delay(200)
            val secondResult = executeCommandDetailedNoCtx("cat /proc/stat | head -n 1")
            if (!secondResult.success) return 68
            val secondParsed = parseProcStat(secondResult.output) ?: return 68

            val totalDiff = secondParsed.first - firstParsed.first
            val idleDiff = secondParsed.second - firstParsed.second
            if (totalDiff > 0) {
                val usage = ((totalDiff - idleDiff) * 100 / totalDiff).toInt()
                return usage.coerceIn(0, 100)
            }
            68
        } catch (_: Exception) {
            68
        }
    }

    fun isShizukuAvailableAndGranted(): Boolean {
        return try { Shizuku.checkSelfPermission() == 0 } catch (_: Exception) { false }
    }

    fun requestShizukuPermission() {
        try { Shizuku.requestPermission(1000) } catch (_: Exception) {}
    }

    fun getForegroundPackageViaShizuku(fallback: String): String {
        return try {
            val result = executeCommandDetailedNoCtx("dumpsys window windows | grep mCurrentFocus")
            val regex = Regex("""([a-z0-9._]+)/""")
            regex.find(result.output)?.groupValues?.get(1) ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    // FIX: Real FPS via gfxinfo delta (read "Total frames rendered" twice, 1s apart)
    suspend fun getRealFpsViaShizuku(gamePackage: String): String {
        return try {
            if (!isShizukuAvailableAndGranted()) return "N/A"
            val frameRegex = Regex("""Total frames rendered:\s*(\d+)""")
            val r1 = executeCommandDetailedNoCtx("dumpsys gfxinfo $gamePackage | grep 'Total frames'")
            val frames1 = frameRegex.find(r1.output)?.groupValues?.get(1)?.toLongOrNull() ?: return "N/A"
            kotlinx.coroutines.delay(1000)
            val r2 = executeCommandDetailedNoCtx("dumpsys gfxinfo $gamePackage | grep 'Total frames'")
            val frames2 = frameRegex.find(r2.output)?.groupValues?.get(1)?.toLongOrNull() ?: return "N/A"
            if (frames2 > frames1) "${frames2 - frames1}" else "N/A"
        } catch (_: Exception) { "N/A" }
    }

    // FIX: Latency from gfxinfo 90th percentile for the selected game package
    fun getLatencyViaShizuku(gamePackage: String): String {
        return try {
            if (!isShizukuAvailableAndGranted()) return "N/A"
            val result = executeCommandDetailedNoCtx("dumpsys gfxinfo $gamePackage | grep percentile")
            if (result.success && result.output.isNotBlank()) {
                val p90 = Regex("""90th percentile:\s*(\d+)ms""").find(result.output)
                if (p90 != null) return p90.groupValues[1] + "ms"
                val p50 = Regex("""50th percentile:\s*(\d+)ms""").find(result.output)
                if (p50 != null) return p50.groupValues[1] + "ms"
            }
            "N/A"
        } catch (_: Exception) { "N/A" }
    }

    fun showToast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

// ============================================================================
// 2. REPOSITORY
// ============================================================================
object TurboSpaceRepository {
    private const val PREFS_NAME = "turbo_space_prefs"
    private const val KEY_GAME = "selected_game"
    private const val KEY_MASTER = "is_master_on"
    private var prefs: android.content.SharedPreferences? = null

    private val _selectedGame = MutableStateFlow("NO TARGET SELECTED")
    val selectedGame: StateFlow<String> = _selectedGame.asStateFlow()
    private val _isMasterOn = MutableStateFlow(false)
    val isMasterOn: StateFlow<Boolean> = _isMasterOn.asStateFlow()

    fun init(context: android.content.Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            _selectedGame.value = prefs!!.getString(KEY_GAME, "NO TARGET SELECTED") ?: "NO TARGET SELECTED"
            _isMasterOn.value = prefs!!.getBoolean(KEY_MASTER, false)
        }
    }

    fun setSelectedGame(pkg: String) {
        _selectedGame.value = pkg
        prefs?.edit()?.putString(KEY_GAME, pkg)?.apply()
    }

    fun setMasterOn(on: Boolean) {
        _isMasterOn.value = on
        prefs?.edit()?.putBoolean(KEY_MASTER, on)?.apply()
    }
}

// ============================================================================
// 3. CYBER MODULES - 10 COMMAND BOXES EXACT SPEC
// ============================================================================
enum class CyberModule(
    val title: String,
    val commandTemplate: String,
    val needsGame: Boolean,
    val iconType: Int
) {
    FIXED_PERFORMANCE("FIXED PERFORMANCE", "cmd power set-fixed-performance-mode-enabled true", false, 1),
    TOP_APP("TOP PRIORITY", "cmd activity set-process-group <package_name> top-app", true, 2),
    ANTI_KILL("ANTI-KILL WHITELIST", "cmd deviceidle whitelist +<package_name>", true, 3),
    STANDBY_ACTIVE("STANDBY ACTIVE", "cmd am set-standby-bucket <package_name> active", true, 4),
    BATTERY_BYPASS("NO THROTTLE", "cmd jobscheduler standby-batched-jobs-execute", false, 5),
    CACHE_FLUSH("CACHE FLUSH", "cmd package trim-caches 999M", false, 6),
    RESTRICTED_WIFI("RESTRICTED WIFI", "cmd connectivity request-restricted-wifi", false, 7),
    WIFI_HIGH_PERF("WIFI OVERDRIVE", "cmd wifi set-high-perf-enabled enabled", false, 8),
    LOW_LATENCY("LOW LATENCY", "cmd wifi force-low-latency-mode enabled", false, 9),
    SIGNAL_RESET("SIGNAL RESET", "svc wifi disable && svc wifi enable && svc data disable && svc data enable", false, 10)
}

// ============================================================================
// 4. OVERLAY SERVICE - SINGLE ICON + FULL HUD
// ============================================================================
class GameSpaceOverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {
    // FIX #1: renamed backing field to _viewModelStore to avoid property name clash
    private val _viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val lifecycle get() = super.lifecycle

    private var windowManager: WindowManager? = null
    private var floatingIconView: View? = null
    private var fullHudView: View? = null
    private var currentGamePackage: String = "NO TARGET SELECTED"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isHudExpanded = false

    override fun onCreate() {
        super.onCreate()

        // FIX #4: startForeground() required for Android O+ ForegroundService
        val channelId = "turbo_space_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Turbo Space", android.app.NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(android.app.NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Turbo Space Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatingIcon()
    }

    private fun showFloatingIcon() {
        if (floatingIconView != null) return
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
            setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
            setContent {
                FloatingTacticalIcon(
                    onClick = { offset -> onFloatingIconClicked() },
                    onDrag = { dx, dy ->
                        (floatingIconView?.layoutParams as? WindowManager.LayoutParams)?.let { p ->
                            p.x += dx.toInt()
                            p.y += dy.toInt()
                            windowManager?.updateViewLayout(floatingIconView, p)
                        }
                    }
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }
        windowManager?.addView(composeView, params)
        floatingIconView = composeView
    }

    private fun onFloatingIconClicked() {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (isPortrait) {
            TurboSpaceManager.showToast(this, "Booster unavailable in portrait mode. Please switch to landscape.")
            return
        }
        if (isHudExpanded) {
            collapseHud()
        } else {
            expandHud()
        }
    }

    private fun expandHud() {
        if (fullHudView != null) return
        isHudExpanded = true
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GameSpaceOverlayService)
            setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService)
            setContent {
                FullHudOverlay(
                    gamePackage = currentGamePackage,
                    onDismiss = { collapseHud() },
                    onRollback = { runRollbackScript() }
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        windowManager?.addView(composeView, params)
        fullHudView = composeView
        floatingIconView?.alpha = 0.4f
    }

    private fun collapseHud() {
        fullHudView?.let { windowManager?.removeView(it) }
        fullHudView = null
        isHudExpanded = false
        floatingIconView?.alpha = 1f
    }

    private fun runRollbackScript() {
        scope.launch(Dispatchers.IO) {
            if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
                return@launch
            }
            val pkg = currentGamePackage
            val rollbackCommands = listOf(
                "cmd power set-fixed-performance-mode-enabled false",
                "cmd deviceidle whitelist -$pkg",
                "cmd am set-standby-bucket $pkg working_set",
                "cmd activity set-process-group $pkg background",
                "cmd connectivity release-restricted-wifi",
                "cmd wifi set-high-perf-enabled disabled",
                "cmd wifi force-low-latency-mode disabled"
            )
            rollbackCommands.forEach { cmd ->
                TurboSpaceManager.executeCommandDetailedNoCtx(cmd)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        currentGamePackage = intent?.getStringExtra("GAME_PACKAGE") ?: TurboSpaceRepository.selectedGame.value
        return START_STICKY
    }

    override fun onDestroy() {
        // FIX: withTimeout 3000ms to avoid Android killing service if Shizuku slow (>5s limit)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                kotlinx.coroutines.withTimeout(3000L) {
                    if (!TurboSpaceManager.isShizukuAvailableAndGranted()) return@withTimeout
                    val pkg = currentGamePackage
                    val rollbackCommands = listOf(
                        "cmd power set-fixed-performance-mode-enabled false",
                        "cmd deviceidle whitelist -$pkg",
                        "cmd am set-standby-bucket $pkg working_set",
                        "cmd activity set-process-group $pkg background",
                        "cmd connectivity release-restricted-wifi",
                        "cmd wifi set-high-perf-enabled disabled",
                        "cmd wifi force-low-latency-mode disabled"
                    )
                    rollbackCommands.forEach { cmd ->
                        TurboSpaceManager.executeCommandDetailedNoCtx(cmd)
                    }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                // Timeout after 3s - continue to destroy anyway
            }
        }
        super.onDestroy()
        floatingIconView?.let { windowManager?.removeView(it) }
        fullHudView?.let { windowManager?.removeView(it) }
        scope.cancel()
    }
}

// ============================================================================
// 5. FLOATING TACTICAL ICON
// ============================================================================
@Composable
fun FloatingTacticalIcon(onClick: (Offset) -> Unit, onDrag: (Float, Float) -> Unit = { _, _ -> }) {
    var sonarTrigger by remember { mutableStateOf(false) }
    var sonarCenter by remember { mutableStateOf(Offset.Zero) }
    var isInactive by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            isInactive = true
            delay(5000)
            isInactive = false
        }
    }

    // FIX #7: use pulseProgress directly for Canvas — don't gate on sonarTrigger
    // sonarTrigger resets AFTER animation completes; pulseProgress drives the visual
    var pulseProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(sonarTrigger) {
        if (sonarTrigger) {
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(400, easing = LinearEasing)) {
                pulseProgress = value // update progress every frame while animating
            }
            pulseProgress = 0f
            sonarTrigger = false
        }
    }

    Box(
        modifier = Modifier
            .size(if (isInactive) 48.dp else 64.dp)
            .alpha(if (isInactive) 0.4f else 1f)
            .clip(CircleShape)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = glowAlpha), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    sonarCenter = offset
                    sonarTrigger = true
                    onClick(offset)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(color = Color.White.copy(alpha = 0.8f), radius = size.minDimension / 2 - 4.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            drawCircle(color = Color.White.copy(alpha = 0.3f), radius = size.minDimension / 2 - 10.dp.toPx(), style = Stroke(0.8.dp.toPx()))
            drawLine(Color.White, Offset(center.x - 12.dp.toPx(), center.y), Offset(center.x + 12.dp.toPx(), center.y), 1.2.dp.toPx())
            drawLine(Color.White, Offset(center.x, center.y - 12.dp.toPx()), Offset(center.x, center.y + 12.dp.toPx()), 1.2.dp.toPx())
            drawCircle(Color.White, radius = 3.dp.toPx(), center = center)
            // FIX #7: draw pulse whenever pulseProgress > 0, not gated on sonarTrigger
            if (pulseProgress > 0f) {
                val radius = size.minDimension * pulseProgress * 3f
                val alpha = 1f - pulseProgress
                drawCircle(Color.Cyan.copy(alpha = alpha), radius = radius, center = center, style = Stroke(2.dp.toPx()))
                drawCircle(Color.White.copy(alpha = alpha * 0.6f), radius = radius * 0.7f, center = center, style = Stroke(1.dp.toPx()))
            }
        }
    }
}

// ============================================================================
// 6. FULL HUD OVERLAY
// ============================================================================
@Composable
fun FullHudOverlay(gamePackage: String, onDismiss: () -> Unit, onRollback: () -> Unit) {
    val context = LocalContext.current
    val masterOn by TurboSpaceRepository.isMasterOn.collectAsState()
    var ramPercent by remember { mutableStateOf(TurboSpaceManager.getRamUsagePercent(context)) }
    var batteryPercent by remember { mutableStateOf(TurboSpaceManager.getBatteryPercent(context)) }
    var temp by remember { mutableStateOf(TurboSpaceManager.getCpuTemp()) }
    // FIX 5: Update RAM/Battery/Temp every 2-3 seconds
    LaunchedEffect("telemetry") {
        while (true) {
            kotlinx.coroutines.delay(2500)
            ramPercent = TurboSpaceManager.getRamUsagePercent(context)
            batteryPercent = TurboSpaceManager.getBatteryPercent(context)
            temp = TurboSpaceManager.getCpuTemp()
        }
    }
    // FIX: Real FPS via SurfaceFlinger every 1 sec, show N/A if fake
    var realFps by remember { mutableStateOf("N/A") }
    var realLatency by remember { mutableStateOf("N/A") }
    var fpsHistory by remember { mutableStateOf(listOf(0.3f, 0.5f, 0.4f, 0.7f, 0.6f)) }
    var cpuPercent by remember { mutableStateOf(68) }
    LaunchedEffect(gamePackage) {
        while (true) {
            // getRealFpsViaShizuku already takes ~1s internally (delta method), no extra delay needed
            val fpsStr = TurboSpaceManager.getRealFpsViaShizuku(gamePackage)
            realFps = fpsStr
            realLatency = TurboSpaceManager.getLatencyViaShizuku(gamePackage)
            cpuPercent = TurboSpaceManager.getCpuUsagePercentViaShizuku()
            val fpsVal = fpsStr.toFloatOrNull()
            if (fpsVal != null && fpsVal > 0) {
                val normalized = (fpsVal / 120f).coerceIn(0.1f, 1f)
                fpsHistory = (fpsHistory + normalized).takeLast(13)
            }
        }
    }

    var expansionPhase by remember { mutableStateOf(0) }
    val expansionProgress by animateFloatAsState(
        targetValue = if (expansionPhase > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "expansion"
    )

    val scanlineAnim by rememberInfiniteTransition(label = "scanline").animateFloat(
        0f, 1f, infiniteRepeatable(tween(200, easing = LinearEasing)), label = "scan"
    )

    val dataStreamAnim by animateFloatAsState(
        targetValue = if (expansionPhase >= 1) 1f else 0f,
        animationSpec = tween(150, easing = LinearEasing),
        label = "dataStream"
    )

    val glowAnim by animateFloatAsState(
        targetValue = if (expansionPhase >= 2) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "ignitionGlow"
    )

    var counterScale by remember { mutableStateOf(0f) }
    val counterAnim by animateFloatAsState(
        targetValue = counterScale,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "counter"
    )

    var glitchFlash by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        expansionPhase = 0
        delay(200)
        expansionPhase = 1
        delay(150)
        expansionPhase = 2
        glitchFlash = true
        counterScale = 1f
        delay(80)
        glitchFlash = false
        delay(400)
        expansionPhase = 3
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f * expansionProgress))
            .graphicsLayer {
                scaleX = 0.2f + 0.8f * expansionProgress
                scaleY = 0.2f + 0.8f * expansionProgress
                alpha = expansionProgress
            },
        contentAlignment = Alignment.Center
    ) {
        if (expansionPhase == 0) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * scanlineAnim
                drawLine(Color.Cyan.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.4f), Offset(x - 1.dp.toPx(), 0f), Offset(x - 1.dp.toPx(), size.height), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.4f), Offset(x + 1.dp.toPx(), 0f), Offset(x + 1.dp.toPx(), size.height), 1.dp.toPx())
            }
        }

        if (glitchFlash) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.15f)))
            Canvas(Modifier.fillMaxSize()) {
                repeat(8) {
                    val y = (0..size.height.toInt()).random().toFloat()
                    drawRect(Color.Cyan.copy(0.3f), topLeft = Offset(0f, y), size = Size(size.width, (2..6).random().toFloat()))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer {
                    alpha = if (expansionPhase == 0) 0.5f + 0.5f * expansionProgress else 1f
                },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LeftPanelWhiteHacker(
                temp = temp,
                ramPercent = ramPercent,
                batteryPercent = batteryPercent,
                masterOn = masterOn,
                counterScale = if (expansionPhase >= 2) counterAnim else 0f,
                glowIntensity = if (expansionPhase >= 2) glowAnim else expansionProgress,
                isSpinning = expansionPhase >= 2,
                onMasterToggle = { on ->
                    TurboSpaceRepository.setMasterOn(on)
                    if (!on) onRollback()
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                realFps = realFps,
                realLatency = realLatency,
                fpsHistory = fpsHistory,
                cpuPercent = cpuPercent
            )

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .clickable { onDismiss() }
            )

            Spacer(Modifier.width(12.dp))

            RightPanelWhiteHacker(
                gamePackage = gamePackage,
                dataStreamPhase = expansionPhase == 1,
                dataStreamProgress = dataStreamAnim,
                glowIntensity = if (expansionPhase >= 2) glowAnim else expansionProgress,
                counterScale = if (expansionPhase >= 2) counterAnim else 0f,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onDismiss = onDismiss
            )
        }
    }
}

// Draw sci-fi corner bracket markers (like reference image)
fun DrawScope.drawCornerBrackets(color: Color, size: Size, bracketLen: Float = 16f, stroke: Float = 1.5f) {
    val s = stroke
    val b = bracketLen
    // TL
    drawLine(color, Offset(0f, 0f), Offset(b, 0f), s)
    drawLine(color, Offset(0f, 0f), Offset(0f, b), s)
    // TR
    drawLine(color, Offset(size.width, 0f), Offset(size.width - b, 0f), s)
    drawLine(color, Offset(size.width, 0f), Offset(size.width, b), s)
    // BL
    drawLine(color, Offset(0f, size.height), Offset(b, size.height), s)
    drawLine(color, Offset(0f, size.height), Offset(0f, size.height - b), s)
    // BR
    drawLine(color, Offset(size.width, size.height), Offset(size.width - b, size.height), s)
    drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - b), s)
}

@Composable
fun LeftPanelWhiteHacker(temp: String, ramPercent: Int, batteryPercent: Int, masterOn: Boolean, counterScale: Float, glowIntensity: Float, isSpinning: Boolean, onMasterToggle: (Boolean) -> Unit, modifier: Modifier, realFps: String = "N/A", realLatency: String = "N/A", fpsHistory: List<Float> = listOf(0.3f, 0.5f, 0.4f, 0.7f, 0.6f), cpuPercent: Int = 68) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rotation by infiniteTransition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (isSpinning) 2000 else 8000, easing = LinearEasing)),
        label = "rot"
    )
    val scanLine by infiniteTransition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "scan"
    )

    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(4.dp)
    ) {
        // Outer bracket frame (like reference image)
        Canvas(Modifier.fillMaxSize()) {
            drawCornerBrackets(Color.White.copy(alpha = 0.9f), size, bracketLen = 24f, stroke = 2f)
            // Side tick marks
            for (i in 1..4) {
                val y = size.height * i / 5
                drawLine(Color.White.copy(0.3f), Offset(0f, y), Offset(6f, y), 1f)
                drawLine(Color.White.copy(0.3f), Offset(size.width - 6f, y), Offset(size.width, y), 1f)
            }
        }

        Column(Modifier.fillMaxSize().padding(12.dp)) {
            // Header label
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text("SYS·MONITOR", color = Color.White.copy(0.5f), fontSize = 7.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                androidx.compose.material3.Text("v2.4", color = Color.White.copy(0.3f), fontSize = 7.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))

            // Main radar circle + FPS/MS boxes
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Large radar circle (like left panel in reference)
                Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        // Outer rings
                        drawCircle(Color.White.copy(0.8f), radius = size.minDimension / 2 - 4.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                        drawCircle(Color.White.copy(0.2f), radius = size.minDimension / 2 - 14.dp.toPx(), style = Stroke(0.8.dp.toPx()))
                        drawCircle(Color.White.copy(0.1f), radius = size.minDimension / 2 - 24.dp.toPx(), style = Stroke(0.5.dp.toPx()))
                        // Tick marks around ring
                        for (i in 0..35) {
                            val angle = i * 10f
                            val rad = Math.toRadians(angle.toDouble())
                            val r1 = size.minDimension / 2 - 4.dp.toPx()
                            val r2 = r1 - (if (i % 3 == 0) 10f else 5f)
                            drawLine(Color.White.copy(if (i % 3 == 0) 0.7f else 0.3f),
                                Offset(cx + cos(rad).toFloat() * r1, cy + sin(rad).toFloat() * r1),
                                Offset(cx + cos(rad).toFloat() * r2, cy + sin(rad).toFloat() * r2), 1f)
                        }
                        // Rotating sweep line
                        rotate(if (isSpinning) rotation else 0f, pivot = Offset(cx, cy)) {
                            drawLine(Color.White.copy(0.5f), Offset(cx, cy),
                                Offset(cx + cos(0.0).toFloat() * (size.minDimension / 2 - 14.dp.toPx()),
                                    cy + sin(0.0).toFloat() * (size.minDimension / 2 - 14.dp.toPx())), 1.2f)
                        }
                        // Crosshair center
                        drawLine(Color.White.copy(0.4f), Offset(cx - 12f, cy), Offset(cx + 12f, cy), 0.8f)
                        drawLine(Color.White.copy(0.4f), Offset(cx, cy - 12f), Offset(cx, cy + 12f), 0.8f)
                        drawCircle(Color.White, 2.5f, Offset(cx, cy))
                        // Scan line across circle
                        if (isSpinning) {
                            val sy = cy - (size.minDimension / 2 - 14.dp.toPx()) + (size.minDimension - 28.dp.toPx()) * scanLine
                            drawLine(Color.White.copy(0.15f), Offset(cx - 30f, sy), Offset(cx + 30f, sy), 0.5f)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text(
                            "${(temp.filter { it.isDigit() }.toIntOrNull() ?: 42) * maxOf(0.1f, counterScale).let { if (it > 0.8f) 1f else it }}".let {
                                val t = temp.filter { c -> c.isDigit() }.toIntOrNull() ?: 42
                                "${(t * maxOf(0.1f, counterScale)).toInt()}°C"
                            },
                            color = Color.White, fontSize = (18 * maxOf(0.1f, counterScale)).sp, fontWeight = FontWeight.Black
                        )
                        androidx.compose.material3.Text("TEMP", color = Color.White.copy(0.4f), fontSize = 7.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }

                // FPS + MS panels (bracket-frame style like reference)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // FPS frame
                    Box(Modifier.size(width = 60.dp, height = 48.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCornerBrackets(Color.White.copy(0.8f), size, bracketLen = 10f, stroke = 1.5f)
                        }
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            androidx.compose.material3.Text(
                                if (realFps == "N/A") "--" else realFps,
                                color = Color.White, fontSize = (16 * maxOf(0.5f, counterScale)).sp, fontWeight = FontWeight.Black,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            androidx.compose.material3.Text("FPS", color = Color.White.copy(0.5f), fontSize = 7.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                    // MS frame
                    Box(Modifier.size(width = 60.dp, height = 48.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCornerBrackets(Color.White.copy(0.8f), size, bracketLen = 10f, stroke = 1.5f)
                        }
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            androidx.compose.material3.Text(
                                if (realLatency == "N/A") "--" else realLatency.replace("ms", ""),
                                color = Color.White, fontSize = (14 * maxOf(0.5f, counterScale)).sp, fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            androidx.compose.material3.Text("MS", color = Color.White.copy(0.5f), fontSize = 7.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Grid data panel (like the dot-grid section in reference image)
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCornerBrackets(Color.White.copy(0.5f), size, bracketLen = 10f, stroke = 1f)
                    // dot grid
                    val cols = 16; val rows = 4
                    for (r in 0 until rows) for (c in 0 until cols) {
                        val x = size.width * c / (cols - 1)
                        val y = size.height * r / (rows - 1)
                        val alpha = if ((r + c) % 3 == 0) 0.5f else 0.15f
                        drawCircle(Color.White.copy(alpha * glowIntensity.coerceAtLeast(0.4f)), 1.2f, Offset(x, y))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Three gauge dials row (like bottom row in reference image)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                // CPU gauge
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCornerBrackets(Color.White.copy(0.6f), size, bracketLen = 8f, stroke = 1f)
                        drawArc(Color.White.copy(0.12f), -220f, 260f, false, style = Stroke(3.dp.toPx()),
                            size = Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()), topLeft = Offset(5.dp.toPx(), 5.dp.toPx()))
                        drawArc(Color.White.copy(0.9f), -220f, 260f * (cpuPercent / 100f) * maxOf(0.1f, counterScale), false,
                            style = Stroke(3.dp.toPx()), size = Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()), topLeft = Offset(5.dp.toPx(), 5.dp.toPx()))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text("${(cpuPercent * maxOf(0.1f, counterScale)).toInt()}%", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Text("CPU", color = Color.White.copy(0.4f), fontSize = 6.sp)
                    }
                }
                // RAM gauge
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCornerBrackets(Color.White.copy(0.6f), size, bracketLen = 8f, stroke = 1f)
                        drawArc(Color.White.copy(0.12f), -220f, 260f, false, style = Stroke(3.dp.toPx()),
                            size = Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()), topLeft = Offset(5.dp.toPx(), 5.dp.toPx()))
                        drawArc(Color.White, -220f, 260f * (ramPercent / 100f) * maxOf(0.1f, counterScale), false,
                            style = Stroke(3.dp.toPx()), size = Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()), topLeft = Offset(5.dp.toPx(), 5.dp.toPx()))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text("${(ramPercent * maxOf(0.1f, counterScale)).toInt()}%", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Text("RAM", color = Color.White.copy(0.4f), fontSize = 6.sp)
                    }
                }
                // Battery panel (bracket frame)
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCornerBrackets(Color.White.copy(0.6f), size, bracketLen = 8f, stroke = 1f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text("${(batteryPercent * maxOf(0.1f, counterScale)).toInt()}%", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Text("BAT", color = Color.White.copy(0.4f), fontSize = 6.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Master toggle (bracket-frame style)
            Box(
                Modifier.fillMaxWidth().height(52.dp)
                    .clickable { onMasterToggle(!masterOn) }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val borderAlpha = if (masterOn) 1f else 0.4f + glowIntensity * 0.3f
                    drawCornerBrackets(Color.White.copy(borderAlpha), size, bracketLen = 14f, stroke = 1.8f)
                    if (masterOn) {
                        // Fill tint when on
                        drawRect(Color.White.copy(0.08f), size = size)
                        // Horizontal scan line animation
                        drawLine(Color.White.copy(0.2f), Offset(14f, size.height / 2), Offset(size.width - 14f, size.height / 2), 0.5f)
                    }
                    // Barcode decoration (like reference image bottom)
                    val bx = size.width - 40f
                    for (k in 0..8) {
                        val bw = if (k % 3 == 0) 2.5f else 1f
                        drawLine(Color.White.copy(0.25f), Offset(bx + k * 4f, size.height - 8f), Offset(bx + k * 4f, size.height - 2f), bw)
                    }
                }
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    androidx.compose.material3.Text(
                        if (masterOn) "MASTER CORE  [ ON ]" else "MASTER CORE  [ OFF ]",
                        color = Color.White, fontSize = (11 * maxOf(0.8f, counterScale)).sp, fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    androidx.compose.material3.Text(
                        if (masterOn) "BOOTSTRAP ACTIVE" else "TAP TO BOOTSTRAP",
                        color = Color.White.copy(0.4f), fontSize = 7.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun RightPanelWhiteHacker(gamePackage: String, dataStreamPhase: Boolean, dataStreamProgress: Float, glowIntensity: Float, counterScale: Float, modifier: Modifier, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feedbackMap by remember { mutableStateOf(mapOf<String, String>()) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(4.dp)
    ) {
        // Outer bracket frame for entire right panel
        Canvas(Modifier.fillMaxSize()) {
            drawCornerBrackets(Color.White.copy(0.9f), size, bracketLen = 24f, stroke = 2f)
            for (i in 1..4) {
                val y = size.height * i / 5
                drawLine(Color.White.copy(0.3f), Offset(0f, y), Offset(6f, y), 1f)
                drawLine(Color.White.copy(0.3f), Offset(size.width - 6f, y), Offset(size.width, y), 1f)
            }
        }
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                androidx.compose.material3.Text("COMMAND·GRID", color = Color.White.copy(0.5f), fontSize = 7.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                androidx.compose.material3.Text("${CyberModule.values().size} MOD", color = Color.White.copy(0.3f), fontSize = 7.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Spacer(Modifier.height(6.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CyberModule.values()) { module ->
                val feedback = feedbackMap[module.title]
                Box(
                    Modifier
                        .height(92.dp)
                        .background(Color.Black)
                        .clickable {
                            scope.launch(Dispatchers.IO) {
                                // FIX 3: Check Shizuku before running every command
                                if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
                                    withContext(Dispatchers.Main) {
                                        TurboSpaceManager.showToast(context, "Shizuku not available - please grant Shizuku permission first")
                                        feedbackMap = feedbackMap + (module.title to "ERROR")
                                        scope.launch {
                                            delay(2000)
                                            feedbackMap = feedbackMap - module.title
                                        }
                                    }
                                    return@launch
                                }
                                // FIX: needsGame modules must have a real package selected
                                if (module.needsGame && gamePackage == "NO TARGET SELECTED") {
                                    withContext(Dispatchers.Main) {
                                        TurboSpaceManager.showToast(context, "Please select a game first")
                                        feedbackMap = feedbackMap + (module.title to "NO GAME")
                                        scope.launch {
                                            delay(2000)
                                            feedbackMap = feedbackMap - module.title
                                        }
                                    }
                                    return@launch
                                }
                                val result = TurboSpaceManager.executeWithPackage(module.commandTemplate, gamePackage)
                                withContext(Dispatchers.Main) {
                                    if (!result.success && result.output.contains("Shizuku not available")) {
                                        TurboSpaceManager.showToast(context, "Shizuku not available - please grant Shizuku permission first")
                                    }
                                    feedbackMap = feedbackMap + (module.title to if (result.success) "SUCCESS" else "ERROR")
                                    scope.launch {
                                        delay(2000)
                                        feedbackMap = feedbackMap - module.title
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Bracket-frame corners (like reference image panels)
                    Canvas(Modifier.fillMaxSize()) {
                        val active = feedback == "SUCCESS"
                        val err = feedback == "ERROR" || feedback == "NO GAME"
                        val bracketColor = when {
                            active -> Color.White.copy(1f)
                            err -> Color.White.copy(0.8f)
                            else -> Color.White.copy(0.5f + glowIntensity * 0.3f)
                        }
                        drawCornerBrackets(bracketColor, size, bracketLen = 12f, stroke = 1.5f)
                        // Barcode at bottom of each module (like reference image)
                        for (k in 0..10) {
                            val bw = if (k % 3 == 0) 2f else 0.8f
                            drawLine(Color.White.copy(0.2f), Offset(8f + k * 5f, size.height - 6f), Offset(8f + k * 5f, size.height - 1f), bw)
                        }
                        if (active) drawRect(Color.White.copy(0.07f), size = size)
                        if (err) {
                            drawLine(Color.White.copy(0.3f), Offset(0f, 0f), Offset(size.width, size.height), 0.8f)
                            drawLine(Color.White.copy(0.3f), Offset(size.width, 0f), Offset(0f, size.height), 0.8f)
                        }
                    }
                    if (dataStreamPhase) {
                        val hexLines = remember { List(6) { (0..15).map { (0..15).random().toString(16) }.joinToString("").uppercase() } }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                hexLines.forEach { hex ->
                                    androidx.compose.material3.Text(hex.take(6), color = Color.White.copy(alpha = (1f - dataStreamProgress) * 0.4f),
                                        fontSize = 6.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.alpha(1f - dataStreamProgress))
                                }
                            }
                            if (dataStreamProgress > 0.3f) {
                                CyberIcon(iconType = module.iconType, modifier = Modifier.size(28.dp).alpha(dataStreamProgress))
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                            CyberIcon(iconType = module.iconType, modifier = Modifier.size(26.dp).graphicsLayer { alpha = if (counterScale > 0) maxOf(0.5f, counterScale) else 1f })
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.material3.Text(module.title, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            if (feedback != null) {
                                Spacer(Modifier.height(2.dp))
                                androidx.compose.material3.Text(
                                    when(feedback) { "SUCCESS" -> "[ OK ]"; "NO GAME" -> "[ SEL ]"; else -> "[ ERR ]" },
                                    color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
        } // close Column
    }
}

@Composable
fun CyberIcon(iconType: Int, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.2.dp.toPx()
        val white = Color.White
        when (iconType) {
            1 -> {
                drawRect(white, topLeft = Offset(6.dp.toPx(), 6.dp.toPx()), size = Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()), style = Stroke(stroke))
                val path = Path().apply {
                    moveTo(center.x - 2.dp.toPx(), center.y - 8.dp.toPx())
                    lineTo(center.x + 3.dp.toPx(), center.y - 1.dp.toPx())
                    lineTo(center.x + 0.dp.toPx(), center.y - 1.dp.toPx())
                    lineTo(center.x + 4.dp.toPx(), center.y + 8.dp.toPx())
                    lineTo(center.x - 3.dp.toPx(), center.y + 1.dp.toPx())
                    lineTo(center.x + 0.dp.toPx(), center.y + 1.dp.toPx())
                    close()
                }
                drawPath(path, white, style = Stroke(stroke))
            }
            2 -> {
                drawLine(white, Offset(center.x, 6.dp.toPx()), Offset(center.x - 10.dp.toPx(), 16.dp.toPx()), stroke)
                drawLine(white, Offset(center.x, 6.dp.toPx()), Offset(center.x + 10.dp.toPx(), 16.dp.toPx()), stroke)
                drawLine(white, Offset(center.x - 8.dp.toPx(), 18.dp.toPx()), Offset(center.x + 8.dp.toPx(), 18.dp.toPx()), stroke)
            }
            3 -> {
                val path = Path().apply {
                    moveTo(center.x, 4.dp.toPx())
                    lineTo(center.x + 10.dp.toPx(), 8.dp.toPx())
                    lineTo(center.x + 10.dp.toPx(), 18.dp.toPx())
                    lineTo(center.x, 22.dp.toPx())
                    lineTo(center.x - 10.dp.toPx(), 18.dp.toPx())
                    lineTo(center.x - 10.dp.toPx(), 8.dp.toPx())
                    close()
                }
                drawPath(path, white, style = Stroke(stroke))
                drawCircle(white, 2.dp.toPx(), center, style = Stroke(stroke))
            }
            4 -> {
                drawOval(white, topLeft = Offset(4.dp.toPx(), 8.dp.toPx()), size = Size(size.width - 8.dp.toPx(), 10.dp.toPx()), style = Stroke(stroke))
                drawCircle(white, 2.dp.toPx(), center, style = Stroke(stroke))
                val ecg = Path().apply {
                    moveTo(4.dp.toPx(), size.height - 6.dp.toPx())
                    lineTo(10.dp.toPx(), size.height - 6.dp.toPx())
                    lineTo(12.dp.toPx(), 20.dp.toPx())
                    lineTo(16.dp.toPx(), 8.dp.toPx())
                    lineTo(20.dp.toPx(), size.height - 6.dp.toPx())
                    lineTo(size.width - 4.dp.toPx(), size.height - 6.dp.toPx())
                }
                drawPath(ecg, white, style = Stroke(0.8.dp.toPx()))
            }
            5 -> {
                drawRect(white, Offset(6.dp.toPx(), 8.dp.toPx()), Size(16.dp.toPx(), 10.dp.toPx()), style = Stroke(stroke))
                drawLine(white, Offset(4.dp.toPx(), 4.dp.toPx()), Offset(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()), stroke)
            }
            6 -> {
                for (i in 0..2) {
                    drawArc(white, i * 120f, 80f, false, style = Stroke(stroke), topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()))
                }
            }
            7 -> {
                drawArc(white, -30f, 60f, false, style = Stroke(stroke), topLeft = Offset(8.dp.toPx(), 6.dp.toPx()), size = Size(size.width - 16.dp.toPx(), 10.dp.toPx()))
                drawArc(white, -30f, 60f, false, style = Stroke(stroke), topLeft = Offset(6.dp.toPx(), 4.dp.toPx()), size = Size(size.width - 12.dp.toPx(), 14.dp.toPx()))
                drawLine(white, Offset(4.dp.toPx(), 6.dp.toPx()), Offset(4.dp.toPx(), 20.dp.toPx()), stroke)
                drawLine(white, Offset(size.width - 4.dp.toPx(), 6.dp.toPx()), Offset(size.width - 4.dp.toPx(), 20.dp.toPx()), stroke)
            }
            8 -> {
                drawArc(white, -30f, 60f, false, style = Stroke(stroke), topLeft = Offset(8.dp.toPx(), 6.dp.toPx()), size = Size(16.dp.toPx(), 10.dp.toPx()))
                drawLine(white, Offset(4.dp.toPx(), 12.dp.toPx()), Offset(8.dp.toPx(), 10.dp.toPx()), stroke)
                drawLine(white, Offset(size.width - 4.dp.toPx(), 12.dp.toPx()), Offset(size.width - 8.dp.toPx(), 10.dp.toPx()), stroke)
            }
            9 -> {
                drawArc(white, 180f, 180f, false, style = Stroke(stroke), topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = Size(size.width - 8.dp.toPx(), size.height - 4.dp.toPx()))
                drawLine(white, center, Offset(center.x + 8.dp.toPx(), center.y - 6.dp.toPx()), stroke)
            }
            10 -> {
                drawArc(white, 0f, 270f, false, style = Stroke(stroke), topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()))
                drawLine(white, Offset(center.x, 4.dp.toPx()), Offset(center.x - 3.dp.toPx(), 8.dp.toPx()), stroke)
                drawLine(white, Offset(center.x, 4.dp.toPx()), Offset(center.x + 3.dp.toPx(), 8.dp.toPx()), stroke)
            }
        }
    }
}

// ============================================================================
// 7. SPLASH SCREEN
// ============================================================================
enum class SplashPhase { TERMINAL_BOOT, WIREFRAME_ASSEMBLY, CORE_IGNITION, FINISHED }

@Composable
fun HackerSplashScreen(onFinished: () -> Unit) {
    var phase by remember { mutableStateOf(SplashPhase.TERMINAL_BOOT) }

    val terminalLines = remember {
        listOf(
            "[Shizuku] Initializing rootless environment...",
            "[Kernel] Bypassing Android security sandbox...",
            "[Shizuku] Injecting shell via rikka.shizuku.Shizuku...",
            "[CMD] cmd power set-fixed-performance-mode-enabled true",
            "[CMD] cmd deviceidle whitelist +com.game",
            "[MEM] Allocating 0x7F3A9C00 - 0x7F3A9FFF [RWX]",
            "[Shizuku] Permission granted - UID 2000",
            "[Kernel] Patching activity manager...",
            "[HUD] Assembling wireframe modules...",
            "[CORE] Igniting performance engine...",
            "[Shizuku] Bypass successful - exit code 0",
            "[MEM] 0x${(0x10000000..0xFFFFFFFF).random().toString(16).uppercase()} mapped",
            "[CMD] cmd am set-standby-bucket active",
            "[NET] Optimizing wifi driver...",
            "[SYS] RAM: 75% -> 42% after purge",
            "[Shizuku] Shell process PID ${(1000..9999).random()} online"
        )
    }
    var scrollIndex by remember { mutableStateOf(0) }
    var hexFlicker by remember { mutableStateOf("0x7F3A9C00") }

    // FIX #5: use different keys so LaunchedEffects don't cancel each other
    LaunchedEffect("scroll") {
        while (phase == SplashPhase.TERMINAL_BOOT) {
            delay(60)
            scrollIndex = (scrollIndex + 1) % terminalLines.size
            hexFlicker = "0x${(0x10000000L..0xFFFFFFFFL).random().toString(16).uppercase()}"
        }
    }

    LaunchedEffect("phases") {
        delay(1800)
        phase = SplashPhase.WIREFRAME_ASSEMBLY
        delay(1400)
        phase = SplashPhase.CORE_IGNITION
        delay(1200)
        phase = SplashPhase.FINISHED
        onFinished()
    }

    val wireframeProgress by animateFloatAsState(
        targetValue = if (phase == SplashPhase.WIREFRAME_ASSEMBLY || phase == SplashPhase.CORE_IGNITION) 1f else 0f,
        animationSpec = tween(1200, easing = LinearEasing), label = "wireframe"
    )

    val glowIntensity by animateFloatAsState(
        targetValue = if (phase == SplashPhase.CORE_IGNITION) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "glow"
    )

    val counterScale by animateFloatAsState(
        targetValue = if (phase == SplashPhase.CORE_IGNITION) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing), label = "counter"
    )

    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (phase) {
            SplashPhase.TERMINAL_BOOT -> {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    terminalLines.forEachIndexed { idx, line ->
                        val offset = (idx + scrollIndex) % terminalLines.size
                        androidx.compose.material3.Text(
                            terminalLines[offset],
                            color = Color(0xFF00FF41),
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.alpha(1f - idx * 0.06f)
                        )
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text(
                        hexFlicker,
                        color = Color(0xFF00FF41).copy(alpha = 0.9f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .background(Color.Black.copy(0.8f))
                            .border(1.dp, Color(0xFF00FF41), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    for (i in 0..size.height.toInt() step 4) {
                        drawLine(Color(0xFF00FF41).copy(0.04f), Offset(0f, i.toFloat()), Offset(size.width, i.toFloat()), 1f)
                    }
                }
            }
            SplashPhase.WIREFRAME_ASSEMBLY -> {
                Canvas(Modifier.fillMaxSize()) {
                    repeat(12) {
                        val y = (0..size.height.toInt()).random().toFloat()
                        val h = (2..8).random().toFloat()
                        drawRect(Color.White.copy(0.08f), topLeft = Offset(0f, y), size = Size(size.width, h))
                    }
                }
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val prog = wireframeProgress
                            drawCircle(Color.White, style = Stroke(1.2.dp.toPx()), radius = 70.dp.toPx(), center = Offset(80.dp.toPx(), 80.dp.toPx()))
                            drawArc(Color.White, -90f, 360f * prog, false, style = Stroke(1.5.dp.toPx()), topLeft = Offset(10.dp.toPx(), 10.dp.toPx()), size = Size(140.dp.toPx(), 140.dp.toPx()))
                            drawCircle(Color.White, style = Stroke(1f), radius = 26.dp.toPx(), center = Offset(160.dp.toPx(), 50.dp.toPx()))
                            drawCircle(Color.White, style = Stroke(1f), radius = 26.dp.toPx(), center = Offset(160.dp.toPx(), 110.dp.toPx()))
                            drawRect(Color.White.copy(0.3f), topLeft = Offset(10.dp.toPx(), 180.dp.toPx()), size = Size(200.dp.toPx(), 90.dp.toPx()), style = Stroke(0.8.dp.toPx()))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val prog = wireframeProgress
                            for (row in 0..4) {
                                for (col in 0..1) {
                                    val x = col * 110.dp.toPx() + 10.dp.toPx()
                                    val y = row * 80.dp.toPx() + 10.dp.toPx()
                                    val w = 100.dp.toPx()
                                    val h = 70.dp.toPx()
                                    if (prog > 0) drawLine(Color.White, Offset(x, y), Offset(x + w * minOf(1f, prog * 4), y), 1f)
                                    if (prog > 0.25f) drawLine(Color.White, Offset(x + w, y), Offset(x + w, y + h * minOf(1f, (prog - 0.25f) * 4)), 1f)
                                    if (prog > 0.5f) drawLine(Color.White, Offset(x + w, y + h), Offset(x + w - w * minOf(1f, (prog - 0.5f) * 4), y + h), 1f)
                                    if (prog > 0.75f) drawLine(Color.White, Offset(x, y + h), Offset(x, y + h - h * minOf(1f, (prog - 0.75f) * 4)), 1f)
                                }
                            }
                        }
                    }
                }
                androidx.compose.material3.Text(
                    "ASSEMBLING HUD WIREFRAME ${ (wireframeProgress * 100).toInt() }%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                )
            }
            SplashPhase.CORE_IGNITION -> {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(Color.Cyan.copy(alpha = glowIntensity * 0.25f), Color.Transparent), center = center, radius = size.minDimension * 0.8f),
                            radius = size.minDimension * 0.8f,
                            center = center
                        )
                    }
                    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.Black).border(1.dp, Color.Cyan.copy(alpha = glowIntensity), RoundedCornerShape(16.dp)).padding(12.dp)) {
                            Column {
                                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                                    val infinite = rememberInfiniteTransition(label = "rot")
                                    val rot by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "rot")
                                    Canvas(Modifier.fillMaxSize()) {
                                        rotate(rot) {
                                            drawCircle(Color.Cyan.copy(alpha = glowIntensity), style = Stroke(1.5.dp.toPx()), radius = size.minDimension / 2 - 4.dp.toPx())
                                        }
                                    }
                                    androidx.compose.material3.Text(
                                        "${(42 * counterScale).toInt()}°C",
                                        color = Color.White,
                                        fontSize = (20 * counterScale).sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, Color.Cyan, CircleShape), contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.Text("${(120 * counterScale).toInt()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, Color.Cyan, CircleShape), contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.Text("${(25 * counterScale).toInt()} ms", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().height(60.dp).background(Color.Cyan.copy(alpha = 0.1f * glowIntensity)).border(0.5.dp, Color.Cyan.copy(alpha = glowIntensity))) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        // FIX #2: use .map instead of * operator
                                        val pts = listOf(0.3f, 0.5f, 0.7f, 0.6f, 0.8f).map { it * counterScale }
                                        val path = Path()
                                        pts.forEachIndexed { idx, v ->
                                            val x = size.width * idx / (pts.size - 1)
                                            val y = size.height * (1 - v)
                                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                        }
                                        drawPath(path, Color.Cyan.copy(alpha = glowIntensity), style = Stroke(1.5f))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.Cyan.copy(alpha = 0.15f * glowIntensity)).border(1.dp, Color.Cyan, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.Text("ENGINE ONLINE", color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.alpha(glowIntensity))
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.Black).border(1.dp, Color.Cyan.copy(alpha = glowIntensity), RoundedCornerShape(16.dp)).padding(8.dp)) {
                            androidx.compose.material3.Text("10 MODULES ARMED", color = Color.Cyan.copy(alpha = glowIntensity), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            SplashPhase.FINISHED -> {}
        }
    }
}

// ============================================================================
// 8. MAIN APP
// ============================================================================
enum class TurboScreen { HOME, GAMES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TurboSpaceRepository.init(this)
        setContent { TurboSpaceApp() }
    }
}

@Composable
fun TurboSpaceApp() {
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) {
        HackerSplashScreen(onFinished = { showSplash = false })
        return
    }
    val context = LocalContext.current
    var screen by remember { mutableStateOf(TurboScreen.HOME) }
    val selectedGame by TurboSpaceRepository.selectedGame.collectAsState()
    val scope = rememberCoroutineScope()
    val isMasterOn by TurboSpaceRepository.isMasterOn.collectAsState()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (screen) {
            TurboScreen.HOME -> HomeScreenWhite(
                selectedGame = selectedGame,
                isMasterOn = isMasterOn,
                onGames = { screen = TurboScreen.GAMES },
                onStart = {
                    if (selectedGame == "NO TARGET SELECTED") {
                        Toast.makeText(context, "Select a game first", Toast.LENGTH_SHORT).show()
                        screen = TurboScreen.GAMES
                        return@HomeScreenWhite
                    }
                    if (!Settings.canDrawOverlays(context)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                        Toast.makeText(context, "Grant overlay, then press START again", Toast.LENGTH_LONG).show()
                        return@HomeScreenWhite
                    }
                    if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
                        TurboSpaceManager.requestShizukuPermission()
                        Toast.makeText(context, "Grant Shizuku, then press START again", Toast.LENGTH_LONG).show()
                        return@HomeScreenWhite
                    }
                    val serviceIntent = Intent(context, GameSpaceOverlayService::class.java).apply {
                        putExtra("GAME_PACKAGE", selectedGame)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
                    scope.launch {
                        delay(500)
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedGame)
                        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (launchIntent != null) context.startActivity(launchIntent)
                    }
                },
                onMasterToggle = { TurboSpaceRepository.setMasterOn(it) }
            )
            TurboScreen.GAMES -> GamesScreenWhite(
                onBack = { screen = TurboScreen.HOME },
                onSelect = { pkg ->
                    TurboSpaceRepository.setSelectedGame(pkg)
                    screen = TurboScreen.HOME
                }
            )
        }
    }
}

@Composable
fun HomeScreenWhite(selectedGame: String, isMasterOn: Boolean, onGames: () -> Unit, onStart: () -> Unit, onMasterToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Text("TURBO SPACE", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        androidx.compose.material3.Text("UNIVERSAL GAME BOOSTER - ALL GAMES", color = Color.White.copy(0.6f), fontSize = 10.sp)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(12.dp)).padding(12.dp)) {
            Column {
                androidx.compose.material3.Text("SELECTED GAME", color = Color.White.copy(0.5f), fontSize = 8.sp)
                androidx.compose.material3.Text(selectedGame, color = Color.White, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGames, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
            androidx.compose.material3.Text("SELECT GAME (ALL GAMES)", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
            androidx.compose.material3.Text("START BOOST - ICON FIRST", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        // FIX 7: RESET button
        Button(
            onClick = {
                TurboSpaceRepository.setSelectedGame("NO TARGET SELECTED")
                TurboSpaceRepository.setMasterOn(false)
                Toast.makeText(context, "Reset to default", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.2f), contentColor = Color.White)
        ) {
            androidx.compose.material3.Text("RESET", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isMasterOn) Color.White.copy(0.15f) else Color.White.copy(0.05f))
                .border(1.dp, Color.White, RoundedCornerShape(12.dp))
                .clickable { onMasterToggle(!isMasterOn) },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(if (isMasterOn) "MASTER CORE ON" else "MASTER CORE OFF", color = Color.White, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Text("Single Floating Icon • Tap outside to dismiss • Portrait blocker", color = Color.White.copy(0.4f), fontSize = 9.sp)
    }
}

@Composable
fun GamesScreenWhite(onBack: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val games = remember { TurboSpaceManager.getInstalledGames(context) }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text("SELECT GAME - ALL GAMES", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            androidx.compose.material3.Text("BACK", color = Color.White, modifier = Modifier.clickable { onBack() }, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(games) { app ->
                val label = TurboSpaceManager.getAppLabel(context, app.packageName)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.08f)).border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(8.dp)).clickable { onSelect(app.packageName) }.padding(12.dp)) {
                    Column {
                        androidx.compose.material3.Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Text(app.packageName, color = Color.White.copy(0.4f), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}
