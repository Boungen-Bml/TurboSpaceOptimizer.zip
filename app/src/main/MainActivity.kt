package com.turbospace.optimizer

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

// -----------------------------------------------------------------------------
// ANDROID 12 MATERIAL 3 LIGHT THEME - EMERALD GREEN
// -----------------------------------------------------------------------------
val LaunchDashboardBackground = Color(0xFFF7FBF7) // Soft green-white background
val LaunchDashboardText = Color(0xFF1A1C19)       // Deep green-black text
val LaunchDashboardCyan = Color(0xFF386A20)       // Emerald Green primary accent
val LaunchDashboardSurface = Color(0xFFFFFFFF)    // Clean white cards
private val MutedText = Color(0xFF6B7A5E)         // Muted green-gray
private val OutlineColor = Color(0xFFD7E8CD)      // Soft green divider
private val SuccessColor = Color(0xFF386A20)      // Deep green status
private val SystemGreen = Color(0xFF4CAF50)       // System graph green
private val ErrorColor = Color(0xFFB3261E)        // Deep red error

// -----------------------------------------------------------------------------
// REPOSITORY - selected target and persistent app preferences
// -----------------------------------------------------------------------------
object TurboSpaceRepository {
    private const val PREFS = "turbospace_prefs"
    private val _selectedGame = MutableStateFlow("")
    val selectedGame: StateFlow<String> = _selectedGame

    fun setSelectedGame(pkg: String, context: Context? = null) {
        _selectedGame.value = pkg
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("selected_target_package", pkg)?.apply()
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _selectedGame.value = prefs.getString("selected_target_package", "").orEmpty()
    }

    fun appPrefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// -----------------------------------------------------------------------------
// SHIZUKU COMMAND RUNNER
// All shell commands are routed through executeCommandDetailedNoCtx().
// Callers dispatch this blocking method on Dispatchers.IO.
// -----------------------------------------------------------------------------
object TurboSpaceManager {
    data class CommandResult(
        val success: Boolean,
        val output: String,
        val exitCode: Int,
        val error: String
    )

    fun isShizukuAvailableAndGranted(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.getVersion() >= 11 &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestShizukuPermission() {
        try {
            Shizuku.requestPermission(1000)
        } catch (_: Throwable) {
            // Permission UI will remain available for retry from HOME.
        }
    }

    fun executeCommandDetailedNoCtx(cmd: String): CommandResult {
        if (!isShizukuAvailableAndGranted()) {
            return CommandResult(false, "", -1, "Shizuku permission not granted")
        }
        return try {
            val process = try {
                Shizuku::class.java.getMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            } catch (_: NoSuchMethodException) {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            }

            var stdout = ""
            var stderr = ""
            val outThread = Thread { stdout = process.inputStream.bufferedReader().use { it.readText() } }
            val errThread = Thread { stderr = process.errorStream.bufferedReader().use { it.readText() } }
            outThread.start()
            errThread.start()
            val exit = process.waitFor()
            outThread.join(2_000)
            errThread.join(2_000)
            CommandResult(exit == 0, stdout, exit, stderr)
        } catch (t: Throwable) {
            CommandResult(false, "", -1, t.message ?: "Shell execution failed")
        }
    }

    fun getInstalledApps(context: Context): List<ApplicationInfo> = try {
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .sortedBy { context.packageManager.getApplicationLabel(it).toString().lowercase() }
    } catch (_: Throwable) {
        emptyList()
    }

    /**
     * Returns true only for ordinary third-party app packages that may be stopped.
     * Core Android, vendor, Google, and this app's own packages are protected.
     */
    fun isSafeToKill(pkg: String): Boolean {
        val normalized = pkg.substringBefore(':').trim()
        if (!normalized.matches(Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$"))) return false
        if (normalized == "com.turbospace.optimizer" || normalized == "android") return false
        val protectedPrefixes = listOf(
            "com.android.", "com.google.", "com.qualcomm.", "com.mediatek.",
            "com.oplus.", "com.coloros.", "com.realme.", "com.vivo.",
            "com.miui.", "com.xiaomi.", "com.samsung.", "com.huawei.",
            "com.hihonor.", "com.transsion."
        )
        return protectedPrefixes.none { normalized.startsWith(it, ignoreCase = true) }
    }

    fun getBatteryPercent(context: Context): Int = try {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
    } catch (_: Throwable) {
        0
    }
}

/**
 * Restores the small set of device settings changed by this app.
 * Every command is executed through the existing Shizuku shell runner.
 */
fun performFullReset(context: Context): Boolean {
    val prefs = context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
    val resetCommands = mutableListOf(
        // 1. Reset Global Settings
        "cmd device_config put activity_manager performance.mode_extreme_enabled false",
        "cmd device_config delete activity_manager high_fps_config",
        "cmd device_config delete activity_manager fpsbooster_enable",
        "settings put global wifi_scan_always_enabled 1"
    )

    // 2. Reset Per-App Settings (Only for apps that were actually modified)
    val installedApps = TurboSpaceManager.getInstalledApps(context)
    for (app in installedApps) {
        val pkg = app.packageName
        var appModified = false
        if (prefs.contains("game_performance_$pkg") || prefs.contains("game_battery_$pkg") ||
            prefs.contains("gpu_performance_$pkg") || prefs.contains("app_optimized_$pkg") ||
            prefs.contains("fps_max_$pkg")) {
            appModified = true
        }

        if (appModified) {
            resetCommands.add("cmd game mode set standard $pkg")
            resetCommands.add("cmd game downscale disable $pkg")
        }
    }

    // 3. Execute all reset commands
    var allSucceeded = true
    for (command in resetCommands) {
        val result = try {
            TurboSpaceManager.executeCommandDetailedNoCtx(command)
        } catch (_: Throwable) {
            null
        }
        if (result == null || !result.success || result.exitCode != 0) allSucceeded = false
    }
    return allSucceeded
}

// -----------------------------------------------------------------------------
// REFRESH RATE SUPPORT - refresh-rate keys are only used by FPS Max Optimizer.
// -----------------------------------------------------------------------------
fun detectMaxSupportedRefreshRateHz(context: Context): Int {
    return try {
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
        val maxRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            display?.supportedModes?.maxOfOrNull { it.refreshRate }
                ?: display?.refreshRate ?: 60f
        } else {
            display?.refreshRate ?: 60f
        }
        maxRate.roundToInt().coerceIn(60, 240)
    } catch (_: Throwable) {
        60
    }
}

private fun supportedFpsChoices(context: Context): List<Int> {
    val display: Display? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display
        else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
    } catch (_: Throwable) { null }
    val rates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        display?.supportedModes?.map { it.refreshRate } ?: emptyList()
    } else emptyList()
    val max = detectMaxSupportedRefreshRateHz(context)
    return listOf(60, 90, 120).filter { candidate ->
        candidate <= max && (rates.isEmpty() || rates.any { kotlin.math.abs(it - candidate) < 1.5f })
    }.ifEmpty { listOf(60) }
}

// -----------------------------------------------------------------------------
// CANVAS ICONS - no emoji or external icon glyphs in the UI.
// -----------------------------------------------------------------------------
@Composable
fun MonetCanvasIcon(
    kind: String,
    modifier: Modifier = Modifier,
    tint: Color = LaunchDashboardCyan
) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val m = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val sw = (m * 0.075f).coerceAtLeast(1.5f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)
        when (kind.lowercase()) {
            "speedometer" -> {
                drawArc(tint, 135f, 270f, false,
                    Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.76f), style = Stroke(sw * 1.25f, cap = StrokeCap.Round))
                for (i in 0..6) {
                    val angle = Math.toRadians((135 + i * 45).toDouble())
                    val inner = m * 0.27f
                    val outer = m * 0.35f
                    drawLine(tint,
                        Offset(cx + kotlin.math.cos(angle).toFloat() * inner, cy + kotlin.math.sin(angle).toFloat() * inner),
                        Offset(cx + kotlin.math.cos(angle).toFloat() * outer, cy + kotlin.math.sin(angle).toFloat() * outer), sw * 0.8f)
                }
                rotate(-42f, Offset(cx, cy)) {
                    drawLine(tint, Offset(cx, cy), Offset(cx + m * 0.29f, cy), sw * 1.5f, cap = StrokeCap.Round)
                }
                drawCircle(tint, m * 0.055f, Offset(cx, cy))
                val bolt = Path().apply {
                    moveTo(w * 0.56f, h * 0.39f)
                    lineTo(w * 0.43f, h * 0.58f)
                    lineTo(w * 0.53f, h * 0.58f)
                    lineTo(w * 0.46f, h * 0.75f)
                    lineTo(w * 0.67f, h * 0.51f)
                    lineTo(w * 0.56f, h * 0.51f)
                    close()
                }
                drawPath(bolt, tint)
            }
            "gpu", "fps" -> {
                val label = if (kind.lowercase() == "gpu") "GPU" else "FPS"
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = tint.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = m * 0.40f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                drawContext.canvas.nativeCanvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
            }
            "ram_clear" -> {
                // RAM chip body and contact pins.
                drawRoundRect(tint, Offset(w * .19f, h * .28f), Size(w * .53f, h * .48f),
                    androidx.compose.ui.geometry.CornerRadius(m * .07f), style = stroke)
                for (i in 0..3) {
                    val x = w * (.27f + i * .12f)
                    drawLine(tint, Offset(x, h * .15f), Offset(x, h * .28f), sw)
                    drawLine(tint, Offset(x, h * .76f), Offset(x, h * .88f), sw)
                }
                drawLine(tint, Offset(w * .28f, h * .43f), Offset(w * .62f, h * .43f), sw * .8f)
                drawLine(tint, Offset(w * .28f, h * .58f), Offset(w * .55f, h * .58f), sw * .8f)
                // Sweeping arrow indicates memory clearing.
                val sweep = Path().apply {
                    moveTo(w * .48f, h * .16f)
                    cubicTo(w * .78f, h * .08f, w * .91f, h * .30f, w * .84f, h * .51f)
                }
                drawPath(sweep, tint, style = Stroke(sw * 1.15f, cap = StrokeCap.Round))
                drawLine(tint, Offset(w * .84f, h * .51f), Offset(w * .69f, h * .48f), sw * 1.15f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .84f, h * .51f), Offset(w * .79f, h * .36f), sw * 1.15f, cap = StrokeCap.Round)
            }
            "broom" -> {
                // Broom handle and angled bristle head.
                val handle = Path().apply {
                    moveTo(w * .34f, h * .17f)
                    lineTo(w * .67f, h * .57f)
                }
                drawPath(handle, tint, style = Stroke(sw * 1.35f, cap = StrokeCap.Round))
                val bristles = Path().apply {
                    moveTo(w * .56f, h * .48f)
                    lineTo(w * .82f, h * .70f)
                    lineTo(w * .64f, h * .88f)
                    lineTo(w * .42f, h * .64f)
                    close()
                }
                drawPath(bristles, tint, style = stroke)
                for (i in 0..3) {
                    val x = w * (.49f + i * .075f)
                    drawLine(tint, Offset(x, h * .70f), Offset(x - w * .04f, h * .82f), sw * .8f)
                }
                drawLine(tint, Offset(w * .13f, h * .80f), Offset(w * .36f, h * .80f), sw * .8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .18f, h * .90f), Offset(w * .43f, h * .90f), sw * .8f, cap = StrokeCap.Round)
            }
            "lightning" -> {
                val bolt = Path().apply {
                    moveTo(w * .58f, h * .08f)
                    lineTo(w * .25f, h * .55f)
                    lineTo(w * .46f, h * .55f)
                    lineTo(w * .38f, h * .92f)
                    lineTo(w * .78f, h * .40f)
                    lineTo(w * .55f, h * .40f)
                    close()
                }
                drawPath(bolt, tint)
            }
            "rocket" -> {
                // Rocket body.
                val body = Path().apply {
                    moveTo(cx, h * 0.10f)
                    cubicTo(w * 0.85f, h * 0.25f, w * 0.85f, h * 0.65f, cx, h * 0.85f)
                    cubicTo(w * 0.15f, h * 0.65f, w * 0.15f, h * 0.25f, cx, h * 0.10f)
                    close()
                }
                drawPath(body, tint, style = stroke)
                // Rocket window.
                drawCircle(tint, m * 0.10f, Offset(cx, h * 0.35f), style = stroke)
                // Left and right fins.
                drawLine(tint, Offset(w * 0.30f, h * 0.55f), Offset(w * 0.15f, h * 0.80f), sw * 1.5f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.70f, h * 0.55f), Offset(w * 0.85f, h * 0.80f), sw * 1.5f, cap = StrokeCap.Round)
                // Flame.
                val flame = Path().apply {
                    moveTo(w * 0.42f, h * 0.85f)
                    quadraticBezierTo(cx, h * 1.0f, w * 0.58f, h * 0.85f)
                }
                drawPath(flame, tint, style = Stroke(sw * 1.2f, cap = StrokeCap.Round))
            }
            "chevron_down" -> {
                drawLine(tint, Offset(w * .20f, h * .38f), Offset(cx, h * .65f), sw * 1.35f, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx, h * .65f), Offset(w * .80f, h * .38f), sw * 1.35f, cap = StrokeCap.Round)
            }
            "back" -> {
                drawLine(tint, Offset(w * .80f, h * .50f), Offset(w * .22f, h * .50f), sw * 1.45f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .22f, h * .50f), Offset(w * .48f, h * .23f), sw * 1.45f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .22f, h * .50f), Offset(w * .48f, h * .77f), sw * 1.45f, cap = StrokeCap.Round)
            }
            "info" -> {
                drawCircle(tint, m * .39f, Offset(cx, cy), style = stroke)
                drawCircle(tint, sw * .85f, Offset(cx, cy - m * .16f))
                drawLine(tint, Offset(cx, cy - m * .04f), Offset(cx, cy + m * .20f), sw * 1.25f, cap = StrokeCap.Round)
            }
            "chevron_up" -> {
                drawLine(tint, Offset(w * .20f, h * .62f), Offset(cx, h * .35f), sw * 1.35f, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx, h * .35f), Offset(w * .80f, h * .62f), sw * 1.35f, cap = StrokeCap.Round)
            }
            "refresh" -> {
                drawArc(tint, 35f, 285f, false, Offset(w * .16f, h * .16f), Size(w * .68f, h * .68f), style = stroke)
                drawLine(tint, Offset(w * .70f, h * .12f), Offset(w * .86f, h * .18f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .86f, h * .18f), Offset(w * .81f, h * .34f), sw, cap = StrokeCap.Round)
            }
            "home" -> {
                val p = Path().apply { moveTo(w*.12f,h*.46f); lineTo(cx,h*.14f); lineTo(w*.88f,h*.46f); lineTo(w*.78f,h*.46f); lineTo(w*.78f,h*.86f); lineTo(w*.56f,h*.86f); lineTo(w*.56f,h*.61f); lineTo(w*.40f,h*.61f); lineTo(w*.40f,h*.86f); lineTo(w*.22f,h*.86f); lineTo(w*.22f,h*.46f); close() }
                drawPath(p, tint, style = stroke)
            }
            "cpu" -> {
                // CPU package body and contact pins.
                drawRoundRect(tint, Offset(w * .27f, h * .27f), Size(w * .46f, h * .46f),
                    androidx.compose.ui.geometry.CornerRadius(m * .06f), style = stroke)
                for (i in 0..2) {
                    val x = w * (.36f + i * .14f)
                    val y = h * (.36f + i * .14f)
                    drawLine(tint, Offset(x, h * .12f), Offset(x, h * .27f), sw)
                    drawLine(tint, Offset(x, h * .73f), Offset(x, h * .88f), sw)
                    drawLine(tint, Offset(w * .12f, y), Offset(w * .27f, y), sw)
                    drawLine(tint, Offset(w * .73f, y), Offset(w * .88f, y), sw)
                }
                drawRoundRect(tint, Offset(w * .40f, h * .40f), Size(w * .20f, h * .20f),
                    androidx.compose.ui.geometry.CornerRadius(m * .025f), style = stroke)
            }
            "network" -> {
                val barWidth = w * 0.14f
                val gap = w * 0.09f
                val bottom = h * 0.84f
                val heights = listOf(0.28f, 0.48f, 0.68f)
                heights.forEachIndexed { index, fraction ->
                    val left = cx + (index - 1) * (barWidth + gap) - barWidth / 2f
                    val top = bottom - h * fraction
                    drawRoundRect(
                        tint,
                        Offset(left, top),
                        Size(barWidth, h * fraction),
                        androidx.compose.ui.geometry.CornerRadius(m * 0.035f)
                    )
                }
            }
            "wifi" -> {
                drawArc(tint, 225f, 90f, false, Offset(w * .10f, h * .14f), Size(w * .80f, h * .76f), style = stroke)
                drawArc(tint, 225f, 90f, false, Offset(w * .25f, h * .32f), Size(w * .50f, h * .49f), style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(tint, 225f, 90f, false, Offset(w * .39f, h * .51f), Size(w * .22f, h * .25f), style = Stroke(sw, cap = StrokeCap.Round))
                drawCircle(tint, sw * 1.35f, Offset(cx, h * .82f))
            }
            "bell" -> {
                val bell = Path().apply {
                    moveTo(w * .23f, h * .70f)
                    cubicTo(w * .31f, h * .60f, w * .31f, h * .48f, w * .34f, h * .36f)
                    cubicTo(w * .38f, h * .17f, w * .62f, h * .17f, w * .66f, h * .36f)
                    cubicTo(w * .69f, h * .48f, w * .69f, h * .60f, w * .77f, h * .70f)
                    close()
                }
                drawPath(bell, tint, style = stroke)
                drawLine(tint, Offset(w * .20f, h * .73f), Offset(w * .80f, h * .73f), sw, cap = StrokeCap.Round)
                drawArc(tint, 0f, 180f, false, Offset(w * .41f, h * .69f), Size(w * .18f, h * .18f), style = stroke)
                drawCircle(tint, sw * .75f, Offset(cx, h * .13f))
            }
            "clipboard" -> {
                drawRoundRect(tint, Offset(w * .20f, h * .18f), Size(w * .60f, h * .72f),
                    androidx.compose.ui.geometry.CornerRadius(m * .06f), style = stroke)
                drawRoundRect(tint, Offset(w * .37f, h * .10f), Size(w * .26f, h * .18f),
                    androidx.compose.ui.geometry.CornerRadius(m * .04f), style = stroke)
                drawLine(tint, Offset(w * .32f, h * .46f), Offset(w * .68f, h * .46f), sw)
                drawLine(tint, Offset(w * .32f, h * .61f), Offset(w * .68f, h * .61f), sw)
                drawLine(tint, Offset(w * .32f, h * .76f), Offset(w * .57f, h * .76f), sw)
            }
            "sun" -> {
                drawCircle(tint, m * .19f, Offset(cx, cy), style = stroke)
                for (i in 0..7) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val inner = m * .29f
                    val outer = m * .42f
                    drawLine(tint,
                        Offset(cx + kotlin.math.cos(a).toFloat() * inner, cy + kotlin.math.sin(a).toFloat() * inner),
                        Offset(cx + kotlin.math.cos(a).toFloat() * outer, cy + kotlin.math.sin(a).toFloat() * outer),
                        sw, cap = StrokeCap.Round)
                }
            }
            "system" -> {
                drawRoundRect(tint, Offset(w*.15f,h*.15f), Size(w*.70f,h*.70f), androidx.compose.ui.geometry.CornerRadius(m*.08f), style=stroke)
                for (i in 0..2) {
                    val x = w * (.32f + i * .18f)
                    drawLine(tint, Offset(x,h*.28f), Offset(x,h*.72f), sw)
                }
                drawLine(tint, Offset(w*.25f,h*.50f), Offset(w*.75f,h*.50f), sw)
            }
            "script" -> {
                drawRoundRect(tint, Offset(w*.20f,h*.12f), Size(w*.60f,h*.76f), androidx.compose.ui.geometry.CornerRadius(m*.06f), style=stroke)
                drawLine(tint, Offset(w*.32f,h*.34f), Offset(w*.68f,h*.34f), sw)
                drawLine(tint, Offset(w*.32f,h*.50f), Offset(w*.68f,h*.50f), sw)
                drawLine(tint, Offset(w*.32f,h*.66f), Offset(w*.58f,h*.66f), sw)
            }
            "beaker" -> {
                val path = Path().apply {
                    moveTo(w * 0.35f, h * 0.15f); lineTo(w * 0.65f, h * 0.15f)
                    lineTo(w * 0.65f, h * 0.45f); lineTo(w * 0.80f, h * 0.80f)
                    cubicTo(w * 0.82f, h * 0.85f, w * 0.75f, h * 0.90f, w * 0.65f, h * 0.90f)
                    lineTo(w * 0.35f, h * 0.90f)
                    cubicTo(w * 0.25f, h * 0.90f, w * 0.18f, h * 0.85f, w * 0.20f, h * 0.80f)
                    lineTo(w * 0.35f, h * 0.45f); close()
                }
                drawPath(path, tint, style = stroke)
                drawCircle(tint, sw * 1.2f, Offset(w * 0.45f, h * 0.75f))
                drawCircle(tint, sw * 0.8f, Offset(w * 0.55f, h * 0.65f))
                drawCircle(tint, sw * 1.0f, Offset(w * 0.50f, h * 0.85f))
                drawLine(tint, Offset(w * 0.30f, h * 0.50f), Offset(w * 0.70f, h * 0.50f), sw * 0.8f)
            }
            "search" -> {
                drawCircle(tint, m*.27f, Offset(w*.42f,h*.40f), style=stroke)
                drawLine(tint, Offset(w*.61f,h*.61f), Offset(w*.86f,h*.86f), sw*1.4f, cap=StrokeCap.Round)
            }
            "battery" -> {
                drawRoundRect(tint, Offset(w*.10f,h*.24f), Size(w*.74f,h*.52f), androidx.compose.ui.geometry.CornerRadius(h*.07f), style=stroke)
                drawRoundRect(tint, Offset(w*.86f,h*.39f), Size(w*.07f,h*.22f), androidx.compose.ui.geometry.CornerRadius(h*.03f))
                drawRoundRect(tint, Offset(w*.17f,h*.32f), Size(w*.47f,h*.36f), androidx.compose.ui.geometry.CornerRadius(h*.03f))
            }
            "gear" -> {
                val teeth = 8
                val outerRadius = m * 0.42f
                val innerRadius = m * 0.30f
                val path = Path()

                for (i in 0 until teeth) {
                    val angle = i * (2.0 * Math.PI / teeth)
                    val nextAngle = (i + 1) * (2.0 * Math.PI / teeth)
                    val midAngle = (angle + nextAngle) / 2.0

                    val p1 = Offset(
                        cx + kotlin.math.cos(angle).toFloat() * innerRadius,
                        cy + kotlin.math.sin(angle).toFloat() * innerRadius
                    )
                    val p2 = Offset(
                        cx + kotlin.math.cos(angle - 0.15).toFloat() * outerRadius,
                        cy + kotlin.math.sin(angle - 0.15).toFloat() * outerRadius
                    )
                    val p3 = Offset(
                        cx + kotlin.math.cos(angle + 0.15).toFloat() * outerRadius,
                        cy + kotlin.math.sin(angle + 0.15).toFloat() * outerRadius
                    )
                    val p4 = Offset(
                        cx + kotlin.math.cos(midAngle).toFloat() * innerRadius,
                        cy + kotlin.math.sin(midAngle).toFloat() * innerRadius
                    )

                    if (i == 0) path.moveTo(p1.x, p1.y) else path.lineTo(p1.x, p1.y)
                    path.lineTo(p2.x, p2.y)
                    path.lineTo(p3.x, p3.y)
                    path.lineTo(p4.x, p4.y)
                }
                path.close()

                drawPath(path, tint, style = stroke)
                drawCircle(tint, m * 0.14f, Offset(cx, cy), style = stroke)
            }
            "bookmark", "bookmark_filled" -> {
                val bookmark = Path().apply {
                    moveTo(w * .27f, h * .12f); lineTo(w * .73f, h * .12f)
                    lineTo(w * .73f, h * .88f); lineTo(cx, h * .68f)
                    lineTo(w * .27f, h * .88f); close()
                }
                drawPath(bookmark, tint, style = if (kind.lowercase() == "bookmark_filled") androidx.compose.ui.graphics.drawscope.Fill else stroke)
            }
            "app" -> {
                drawRoundRect(tint, Offset(w*.15f,h*.15f), Size(w*.70f,h*.70f), androidx.compose.ui.geometry.CornerRadius(m*.13f), style=stroke)
                drawCircle(tint,m*.11f,Offset(cx,cy),style=stroke)
            }
            "memory" -> {
                drawRoundRect(tint, Offset(w*.18f,h*.20f), Size(w*.64f,h*.60f), androidx.compose.ui.geometry.CornerRadius(m*.06f), style=stroke)
                for (i in 0..3) {
                    val x = w*(.27f+i*.15f)
                    drawLine(tint,Offset(x,h*.10f),Offset(x,h*.20f),sw)
                    drawLine(tint,Offset(x,h*.80f),Offset(x,h*.90f),sw)
                }
            }
            "cache" -> {
                drawPath(Path().apply { moveTo(w*.25f,h*.25f); lineTo(w*.75f,h*.25f); lineTo(w*.68f,h*.75f); lineTo(w*.32f,h*.75f); close() }, tint, style=stroke)
                drawLine(tint,Offset(w*.33f,h*.43f),Offset(w*.67f,h*.43f),sw)
                drawLine(tint,Offset(w*.36f,h*.57f),Offset(w*.64f,h*.57f),sw)
            }
            "tuning" -> {
                val r = m * 0.16f
                val pts = listOf(
                    Offset(cx, cy - m * 0.28f),
                    Offset(cx - m * 0.28f, cy + m * 0.22f),
                    Offset(cx + m * 0.28f, cy + m * 0.22f)
                )
                drawLine(tint, pts[0], pts[1], sw * 1.2f, cap = StrokeCap.Round)
                drawLine(tint, pts[1], pts[2], sw * 1.2f, cap = StrokeCap.Round)
                drawLine(tint, pts[2], pts[0], sw * 1.2f, cap = StrokeCap.Round)
                pts.forEach { drawCircle(tint, r, it) }
                drawCircle(tint, r * 0.4f, Offset(cx, cy + m * 0.05f))
            }
            "appops" -> {
                val r = m * 0.35f
                val path = Path()
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60 + 30).toDouble())
                    val x = cx + kotlin.math.cos(angle).toFloat() * r
                    val y = cy + kotlin.math.sin(angle).toFloat() * r
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(
                    path,
                    tint,
                    style = Stroke(sw * 1.2f, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
                drawCircle(tint, m * 0.12f, Offset(cx, cy), style = Stroke(sw * 1.2f))
            }
            "running" -> {
                // Simple running figure: circular head with a dynamic stick-body pose.
                drawCircle(tint, m * 0.095f, Offset(w * 0.57f, h * 0.18f))
                drawLine(tint, Offset(w * 0.54f, h * 0.31f), Offset(w * 0.43f, h * 0.56f), sw * 1.35f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.38f), Offset(w * 0.27f, h * 0.34f), sw * 1.25f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.39f), Offset(w * 0.73f, h * 0.48f), sw * 1.25f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.43f, h * 0.56f), Offset(w * 0.24f, h * 0.79f), sw * 1.35f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.43f, h * 0.56f), Offset(w * 0.70f, h * 0.75f), sw * 1.35f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.24f, h * 0.79f), Offset(w * 0.13f, h * 0.79f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.70f, h * 0.75f), Offset(w * 0.84f, h * 0.72f), sw, cap = StrokeCap.Round)
            }
            "location" -> {
                // Filled map pin with a circular center.
                val pin = Path().apply {
                    moveTo(cx, h * 0.94f)
                    cubicTo(w * 0.78f, h * 0.65f, w * 0.84f, h * 0.49f, w * 0.84f, h * 0.37f)
                    cubicTo(w * 0.84f, h * 0.16f, w * 0.69f, h * 0.07f, cx, h * 0.07f)
                    cubicTo(w * 0.31f, h * 0.07f, w * 0.16f, h * 0.19f, w * 0.16f, h * 0.38f)
                    cubicTo(w * 0.16f, h * 0.53f, w * 0.30f, h * 0.72f, cx, h * 0.94f)
                    close()
                }
                drawPath(pin, tint)
                drawCircle(Color.White, m * 0.105f, Offset(cx, h * 0.37f))
            }
            "camera" -> {
                // Camera body, top viewfinder bump, and lens.
                drawRoundRect(tint, Offset(w * 0.12f, h * 0.30f), Size(w * 0.76f, h * 0.52f),
                    androidx.compose.ui.geometry.CornerRadius(m * 0.10f), style = stroke)
                val bump = Path().apply {
                    moveTo(w * 0.29f, h * 0.30f)
                    lineTo(w * 0.38f, h * 0.17f)
                    lineTo(w * 0.63f, h * 0.17f)
                    lineTo(w * 0.72f, h * 0.30f)
                }
                drawPath(bump, tint, style = Stroke(sw * 1.15f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawCircle(tint, m * 0.16f, Offset(cx, h * 0.56f), style = Stroke(sw * 1.2f))
                drawCircle(tint, m * 0.045f, Offset(w * 0.75f, h * 0.41f))
            }
            "mic" -> {
                // Microphone capsule, curved stand, and base.
                drawRoundRect(tint, Offset(w * 0.37f, h * 0.10f), Size(w * 0.26f, h * 0.52f),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.13f))
                drawArc(tint, 0f, 180f, false, Offset(w * 0.22f, h * 0.30f), Size(w * 0.56f, h * 0.48f),
                    style = Stroke(sw * 1.25f, cap = StrokeCap.Round))
                drawLine(tint, Offset(cx, h * 0.78f), Offset(cx, h * 0.90f), sw * 1.2f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.90f), Offset(w * 0.66f, h * 0.90f), sw * 1.2f, cap = StrokeCap.Round)
            }
            else -> drawCircle(tint, m * .32f, Offset(cx, cy), style = stroke)
        }
    }
}

// -----------------------------------------------------------------------------
// REUSABLE UI COMPONENTS REQUIRED BY THE PHASE-1 SPEC
// -----------------------------------------------------------------------------
@Composable
fun TuningToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    iconKind: String = "system",
    loadingLabel: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonetCanvasIcon(iconKind, Modifier.size(24.dp), LaunchDashboardCyan)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = LaunchDashboardText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MutedText, fontSize = 11.sp)
        }
        if (loading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), color = LaunchDashboardCyan, strokeWidth = 2.dp)
                if (loadingLabel.isNotBlank()) {
                    Text(loadingLabel, color = LaunchDashboardCyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        } else {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
fun ExpanderCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    iconKind: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(14.dp))
            .background(LaunchDashboardSurface)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clickable { onExpandedChange(!expanded) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                leadingContent != null -> leadingContent()
                iconKind != null -> MonetCanvasIcon(iconKind, Modifier.size(24.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(title, Modifier.weight(1f), color = LaunchDashboardText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            MonetCanvasIcon(if (expanded) "chevron_up" else "chevron_down", Modifier.size(20.dp))
        }
        if (expanded) Column(Modifier.fillMaxWidth(), content = content)
    }
}


// -----------------------------------------------------------------------------
// APPLICATION ROOT AND BOTTOM NAVIGATION
// -----------------------------------------------------------------------------
private enum class AppTab(val label: String, val icon: String) {
    HOME("Home", "Home"),
    TUNING("Tuning", "Tuning"),
    APPOPS("AppOps", "appops"),
    TWEAKS("Tweaks", "gear")
}

@Composable
fun TurboSpaceAppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE) }
    var isPolicyAccepted by remember { mutableStateOf(prefs.getBoolean("policy_accepted", false)) }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var showAppOpsScreen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var returnToSettingsAfterAppOps by remember { mutableStateOf(false) }
    var showTuningConsole by remember { mutableStateOf(false) }
    var tuningTargetApp by remember { mutableStateOf<ApplicationInfo?>(null) }
    val navScrollState = rememberScrollState()
    val scheme = androidx.compose.material3.lightColorScheme(
        primary = LaunchDashboardCyan,
        onPrimary = Color.White,
        background = LaunchDashboardBackground,
        surface = LaunchDashboardSurface,
        onSurface = LaunchDashboardText,
        secondary = LaunchDashboardCyan
    )
    LaunchedEffect(Unit) { TurboSpaceRepository.load(context) }

    MaterialTheme(colorScheme = scheme) {
        if (!isPolicyAccepted) {
            StartupWarningScreen(
                onCancel = { (context as? Activity)?.finish() },
                onAccept = {
                    prefs.edit().putBoolean("policy_accepted", true).apply()
                    isPolicyAccepted = true
                }
            )
        } else {
            Scaffold(
                containerColor = LaunchDashboardBackground,
                bottomBar = {
                    if (!showAppOpsScreen && !showTuningConsole) {
                        NavigationBar(
                            containerColor = LaunchDashboardBackground,
                            contentColor = LaunchDashboardText
                        ) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(navScrollState),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppTab.values().forEach { tab ->
                                    val selected = selectedTab == tab && !showSettings
                                    Column(
                                        Modifier.width(85.dp)
                                            .clickable {
                                                showSettings = false
                                                returnToSettingsAfterAppOps = false
                                                when (tab) {
                                                    AppTab.APPOPS -> {
                                                        selectedTab = AppTab.APPOPS
                                                        showAppOpsScreen = true
                                                    }
                                                    AppTab.TUNING -> {
                                                        val selectedPackage = TurboSpaceRepository.selectedGame.value
                                                        tuningTargetApp = if (selectedPackage.isBlank()) null else try {
                                                            context.packageManager.getApplicationInfo(selectedPackage, 0)
                                                        } catch (_: Throwable) { null }
                                                        selectedTab = AppTab.TUNING
                                                        showTuningConsole = true
                                                    }
                                                    else -> {
                                                        showTuningConsole = false
                                                        selectedTab = tab
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 4.dp, vertical = 7.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Box(
                                            Modifier.clip(RoundedCornerShape(16.dp))
                                                .background(if (selected) LaunchDashboardCyan.copy(alpha = .13f) else Color.Transparent)
                                                .padding(horizontal = 18.dp, vertical = 3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            MonetCanvasIcon(
                                                tab.icon,
                                                Modifier.size(22.dp),
                                                if (selected) LaunchDashboardCyan else MutedText
                                            )
                                        }
                                        Text(
                                            tab.label,
                                            color = if (selected) LaunchDashboardCyan else MutedText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                when {
                    showAppOpsScreen -> {
                        AppOpsMainScreen(
                            modifier = Modifier.padding(padding),
                            onBack = {
                                showAppOpsScreen = false
                                if (returnToSettingsAfterAppOps) {
                                    showSettings = true
                                    selectedTab = AppTab.HOME
                                } else {
                                    selectedTab = AppTab.HOME
                                }
                                returnToSettingsAfterAppOps = false
                            }
                        )
                    }
                    showTuningConsole -> {
                        TuningConsoleScreen(
                            app = tuningTargetApp,
                            onBack = {
                                showTuningConsole = false
                                selectedTab = AppTab.HOME
                            },
                            modifier = Modifier.padding(padding)
                        )
                    }
                    showSettings -> {
                        SettingsTabScreen(
                            modifier = Modifier.padding(padding),
                            onOpenAppOps = {
                                returnToSettingsAfterAppOps = true
                                selectedTab = AppTab.APPOPS
                                showSettings = false
                                showAppOpsScreen = true
                            }
                        )
                    }
                    else -> {
                        when (selectedTab) {
                            AppTab.HOME -> HomeTabScreen(
                                modifier = Modifier.padding(padding),
                                onSettingsClick = { showSettings = true },
                                onAppSelected = { appInfo ->
                                    tuningTargetApp = appInfo
                                    showTuningConsole = true
                                }
                            )
                            AppTab.TUNING -> {
                                // The tab click opens the full-screen console above.
                            }
                            AppTab.TWEAKS -> SystemTweaksScreen(Modifier.padding(padding))
                            AppTab.APPOPS -> AppOpsMainScreen(
                                modifier = Modifier.padding(padding),
                                onBack = { selectedTab = AppTab.HOME }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StartupWarningScreen(
    onCancel: () -> Unit,
    onAccept: () -> Unit
) {
    val warningText = "This app does not collect any personal data or history. It is completely free, ad-free, and all data remains strictly on your device. We strive for the highest security, but we recommend caution when using various commands and features. We are NOT responsible for any errors, system instability, or damages caused by user mistakes. You take full responsibility for your actions. This app is optimized for Android 12 up to Android 15/16. It works on Android 10 and 11, but performance may be limited. Have fun using the commands! By clicking 'I ACCEPT', you acknowledge and agree to all terms above."

    Box(
        Modifier.fillMaxSize().background(LaunchDashboardBackground).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
                .clip(RoundedCornerShape(18.dp))
                .background(LaunchDashboardSurface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "WARNING & TERMS",
                color = LaunchDashboardText,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(LaunchDashboardCyan))
            Text(
                warningText,
                color = LaunchDashboardText,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE0E0E0))
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CANCEL", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Box(
                    Modifier.weight(1f).height(46.dp)
                        .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
                        .clip(RoundedCornerShape(10.dp))
                        .background(LaunchDashboardCyan)
                        .clickable(onClick = onAccept),
                    contentAlignment = Alignment.Center
                ) {
                    Text("I ACCEPT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun SettingsTabScreen(modifier: Modifier = Modifier, onOpenAppOps: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasShizuku by remember { mutableStateOf(TurboSpaceManager.isShizukuAvailableAndGranted()) }
    var hasWriteSettings by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context))
    }
    var hasBatteryOptimizationExemption by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || try {
                context.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
            } catch (_: Throwable) { false }
        )
    }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetRunning by remember { mutableStateOf(false) }

    fun refreshPermissionStatus() {
        hasShizuku = TurboSpaceManager.isShizukuAvailableAndGranted()
        hasWriteSettings = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
        hasBatteryOptimizationExemption = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || try {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (_: Throwable) { false }
    }

    fun requestPermission(permission: String) {
        try {
            when (permission) {
                "SHIZUKU" -> TurboSpaceManager.requestShizukuPermission()
                "WRITE_SETTINGS" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
                "BATTERY_OPTIMIZATION" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Throwable) {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            android.widget.Toast.makeText(
                context,
                "Unable to open permission settings",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { refreshPermissionStatus() }
    val versionName = remember(context) {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (_: Throwable) { "Unknown" }
    }

    Column(
        modifier.fillMaxSize()
            .background(LaunchDashboardBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "SETTINGS",
                color = LaunchDashboardCyan,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "SYSTEM & PERMISSIONS",
                color = MutedText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(LaunchDashboardSurface)
                .border(1.dp, OutlineColor, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text(
                "PERMISSION STATUS",
                color = LaunchDashboardCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            SettingsPermissionRow("SHIZUKU", hasShizuku) { requestPermission("SHIZUKU") }
            SettingsPermissionRow("WRITE_SETTINGS", hasWriteSettings) { requestPermission("WRITE_SETTINGS") }
            SettingsPermissionRow("BATTERY_OPTIMIZATION", hasBatteryOptimizationExemption) {
                requestPermission("BATTERY_OPTIMIZATION")
            }
            TextButton(
                onClick = { refreshPermissionStatus() },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("REFRESH STATUS", color = LaunchDashboardCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFE8F5E9))
                .border(1.dp, Color(0xFFC8E6C9), RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenAppOps)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonetCanvasIcon("script", Modifier.size(25.dp), LaunchDashboardCyan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ADVANCED (APPOPS)", color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("Manage app operation restrictions", color = MutedText, fontSize = 10.sp)
                }
                MonetCanvasIcon("chevron_down", Modifier.size(18.dp), LaunchDashboardCyan)
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFDAD6))
                .border(1.dp, Color(0xFFF2B8B5), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "DANGER ZONE",
                color = Color(0xFF410002),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp
            )
            Text(
                "RESTORE SYSTEM DEFAULTS",
                color = Color(0xFF410002),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Undo selected system changes made by this app. Shizuku permission is required.",
                color = Color(0xFF93000A),
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            Box(
                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (resetRunning) Color(0xFF7A3535) else Color(0xFFD32F2F))
                    .clickable(enabled = !resetRunning) { showResetDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (resetRunning) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("RESET NOW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(LaunchDashboardSurface)
                .border(1.dp, OutlineColor, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("DEVELOPER CREDITS", color = LaunchDashboardCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(
                "If you like and enjoy this app, don't forget to subscribe to my YouTube channel @BoungenBml. It would mean a lot!",
                color = MutedText,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))
            Text(
                "TURBOTUNE TOOLS",
                color = LaunchDashboardText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text("VERSION $versionName", color = MutedText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = LaunchDashboardSurface,
            title = {
                Text("RESTORE SYSTEM DEFAULTS?", color = LaunchDashboardCyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    "This will run the reset commands and restore the selected system settings. Continue?",
                    color = LaunchDashboardText,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    resetRunning = true
                    scope.launch {
                        val success = withContext(Dispatchers.IO) { performFullReset(context) }
                        resetRunning = false
                        android.widget.Toast.makeText(
                            context,
                            if (success) "SYSTEM RESTORED" else "RESET FAILED",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        refreshPermissionStatus()
                    }
                }) {
                    Text("CONFIRM", color = ErrorColor, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", color = MutedText)
                }
            }
        )
    }
}


// -----------------------------------------------------------------------------
// ADVANCED APPOPS - per-app operation restrictions managed through Shizuku.
// Boolean preference value: true = BLOCKED/IGNORE (switch OFF), false = ALLOWED (switch ON).
// -----------------------------------------------------------------------------
private data class AppOpsOperation(
    val id: String,
    val title: String,
    val desc: String,
    val iconKind: String
)

private data class AppOpsAppItem(
    val info: ApplicationInfo,
    val packageName: String,
    val label: String
)

private val APP_OPS_OPERATIONS = listOf(
    AppOpsOperation("RUN_IN_BACKGROUND", "Background Activity", "Prevent background activity", "running"),
    AppOpsOperation("FINE_LOCATION", "Location Access", "Block location access", "location"),
    AppOpsOperation("CAMERA", "Camera Access", "Block camera access", "camera"),
    AppOpsOperation("RECORD_AUDIO", "Microphone Access", "Block microphone access", "mic"),
    AppOpsOperation("POST_NOTIFICATION", "Notification Access", "Block notifications", "bell"),
    AppOpsOperation("WAKE_LOCK", "Data Access", "Restrict data access", "network")
)

@Composable
fun AppOpsMainScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
    }
    val listState = rememberLazyListState()
    var showWarning by remember {
        mutableStateOf(!prefs.getBoolean("appops_warning_shown", false))
    }
    var showInfo by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppOpsAppItem>>(emptyList()) }
    var selectedApp by remember { mutableStateOf<AppOpsAppItem?>(null) }
    var loadingApps by remember { mutableStateOf(true) }

    LaunchedEffect(context) {
        loadingApps = true
        apps = withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .asSequence()
                    // This screen is intentionally limited to user-installed apps.
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    // Keep the existing safeguard that hides protected/core packages.
                    .filter { TurboSpaceManager.isSafeToKill(it.packageName) }
                    .map { info ->
                        val label = try {
                            context.packageManager.getApplicationLabel(info).toString()
                        } catch (_: Throwable) {
                            info.packageName
                        }
                        AppOpsAppItem(info, info.packageName, label)
                    }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            } catch (_: Throwable) {
                emptyList()
            }
        }
        loadingApps = false
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    if (selectedApp != null) {
        val app = selectedApp!!
        AppOpsConsoleScreen(
            packageName = app.packageName,
            appLabel = app.label,
            onBack = { selectedApp = null }
        )
    } else {
        Column(
            modifier.fillMaxSize().background(LaunchDashboardBackground)
        ) {
            // Header: back arrow, centered AppOps title, and information button.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clickable(onClick = onBack),
                    contentAlignment = Alignment.CenterStart
                ) {
                    MonetCanvasIcon("back", Modifier.size(30.dp), LaunchDashboardCyan)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "AppOps",
                        color = LaunchDashboardCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Box(
                    Modifier.size(42.dp).clickable { showInfo = true },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    MonetCanvasIcon("info", Modifier.size(30.dp), LaunchDashboardCyan)
                }
            }

            // Explanatory banner.
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE8F5E9))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MonetCanvasIcon("app", Modifier.size(34.dp), LaunchDashboardCyan)
                Text(
                    "Control app permissions and prevent unwanted background activity.",
                    modifier = Modifier.weight(1f),
                    color = LaunchDashboardCyan,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            // Pill-shaped app search.
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(LaunchDashboardSurface)
                    .border(1.dp, OutlineColor, RoundedCornerShape(50))
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonetCanvasIcon("search", Modifier.size(22.dp), LaunchDashboardText)
                Spacer(Modifier.width(11.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = LaunchDashboardText,
                        fontSize = 14.sp
                    ),
                    decorationBox = { inner ->
                        if (searchQuery.isBlank()) {
                            Text("Search apps...", color = MutedText, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (loadingApps) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(35.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = LaunchDashboardCyan,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    item {
                        Text(
                            if (apps.isEmpty()) "No user apps found" else "No matching apps",
                            Modifier.fillMaxWidth().padding(28.dp),
                            color = MutedText,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { selectedApp = app }
                                    .padding(horizontal = 18.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconImageRaw(
                                    app.packageName,
                                    app.info,
                                    Modifier.size(48.dp)
                                )
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        color = LaunchDashboardText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        app.packageName,
                                        color = MutedText,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "User App",
                                        color = LaunchDashboardCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(9.dp))
                                MonetCanvasIcon(
                                    "back",
                                    Modifier.size(21.dp).rotate(180f),
                                    LaunchDashboardText
                                )
                            }
                            Box(
                                Modifier.fillMaxWidth()
                                    .padding(start = 78.dp, end = 16.dp)
                                    .height(1.dp)
                                    .background(OutlineColor.copy(alpha = 0.75f))
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    // Informational dialog opened from the top-right info icon.
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = LaunchDashboardSurface,
            title = {
                Text(
                    "AppOps Info",
                    color = LaunchDashboardCyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Advanced AppOps allows you to restrict sensitive permissions for specific apps. Use with caution, as blocking certain permissions may cause apps to malfunction.",
                    color = LaunchDashboardText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("OK", color = LaunchDashboardCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Custom warning dialog matching the supplied reference image.
    if (showWarning) {
        Dialog(onDismissRequest = onBack) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(LaunchDashboardSurface)
                    .padding(20.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Canvas(Modifier.size(68.dp)) {
                        val warningTriangle = Path().apply {
                            moveTo(size.width * 0.50f, size.height * 0.06f)
                            lineTo(size.width * 0.96f, size.height * 0.88f)
                            quadraticTo(size.width * 0.99f, size.height * 0.96f,
                                size.width * 0.88f, size.height * 0.96f)
                            lineTo(size.width * 0.12f, size.height * 0.96f)
                            quadraticTo(size.width * 0.01f, size.height * 0.96f,
                                size.width * 0.04f, size.height * 0.88f)
                            close()
                        }
                        drawPath(warningTriangle, LaunchDashboardCyan)
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(size.width * 0.465f, size.height * 0.32f),
                            size = Size(size.width * 0.07f, size.height * 0.31f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.035f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = size.width * 0.045f,
                            center = Offset(size.width * 0.50f, size.height * 0.77f)
                        )
                    }
                    Text(
                        "Warning!",
                        color = LaunchDashboardText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        "This feature may affect some apps. Notifications may be delayed, apps may become unstable or laggy, and GPS may stop working. Use with caution! NEVER apply this to system apps or apps you don't understand. Have fun!",
                        color = LaunchDashboardText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Transparent)
                                .border(1.5.dp, LaunchDashboardCyan, RoundedCornerShape(14.dp))
                                .clickable(onClick = onBack)
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "CANCEL",
                                color = LaunchDashboardCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(LaunchDashboardCyan)
                                .clickable {
                                    prefs.edit().putBoolean("appops_warning_shown", true).apply()
                                    showWarning = false
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "CONTINUE",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppOpsConsoleScreen(
    packageName: String,
    appLabel: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    val appInfo = remember(packageName) {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (_: Throwable) {
            null
        }
    }
    // Stored preference: true = blocked/ignore, false = allowed.
    var ignoredStates by remember(packageName) {
        mutableStateOf(
            APP_OPS_OPERATIONS.associate { operation ->
                operation.id to prefs.getBoolean("appops_${operation.id}_${packageName}", false)
            }
        )
    }
    var loadingIds by remember(packageName) { mutableStateOf<Set<String>>(emptySet()) }

    fun runOperation(operation: AppOpsOperation, allow: Boolean) {
        if (operation.id in loadingIds) return
        val ignore = !allow
        loadingIds = loadingIds + operation.id
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                TurboSpaceManager.executeCommandDetailedNoCtx(
                    "cmd appops set $packageName ${operation.id} ${if (ignore) "ignore" else "allow"}"
                )
            }
            loadingIds = loadingIds - operation.id
            if (result.success) {
                ignoredStates = ignoredStates + (operation.id to ignore)
                prefs.edit().putBoolean("appops_${operation.id}_${packageName}", ignore).apply()
            } else {
                android.widget.Toast.makeText(
                    context,
                    "${operation.title}: ${result.error.ifBlank { result.output.ifBlank { "COMMAND FAILED" } }}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(LaunchDashboardBackground)) {
        // App bar.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clickable { onBack() },
                contentAlignment = Alignment.CenterStart
            ) {
                MonetCanvasIcon("back", Modifier.size(28.dp), LaunchDashboardCyan)
            }
            Text(
                appLabel,
                Modifier.weight(1f),
                color = LaunchDashboardCyan,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(42.dp))
        }

        // Selected app identity card.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(LaunchDashboardSurface)
                .border(1.dp, OutlineColor, RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appInfo != null) {
                AppIconImageRaw(packageName, appInfo, Modifier.size(68.dp))
            } else {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)).background(LaunchDashboardBackground), contentAlignment = Alignment.Center) {
                    MonetCanvasIcon("app", Modifier.size(34.dp), LaunchDashboardCyan)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(appLabel, color = LaunchDashboardText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(packageName, color = MutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE8F5E9)).padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("User App", color = LaunchDashboardCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section heading.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonetCanvasIcon("gear", Modifier.size(24.dp), LaunchDashboardCyan)
            Spacer(Modifier.width(10.dp))
            Text("AppOps Controls", color = LaunchDashboardCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            items(APP_OPS_OPERATIONS, key = { it.id }) { operation ->
                val isBlocked = ignoredStates[operation.id] == true
                val isAllowed = !isBlocked
                val slashProgress by animateFloatAsState(
                    targetValue = if (isBlocked) 1f else 0f,
                    animationSpec = tween(300),
                    label = "appops-slash-${operation.id}"
                )
                AppOpsOperationRow(
                    operation = operation,
                    packageName = packageName,
                    appInfo = appInfo,
                    checked = isAllowed,
                    slashProgress = slashProgress,
                    loading = operation.id in loadingIds,
                    onCheckedChange = { nextAllowed -> runOperation(operation, nextAllowed) }
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    MonetCanvasIcon("info", Modifier.size(27.dp), LaunchDashboardCyan)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Note:", color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "These settings use AppOps permissions to control app behavior on your device.",
                            color = LaunchDashboardText,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppOpsOperationRow(
    operation: AppOpsOperation,
    packageName: String,
    appInfo: ApplicationInfo?,
    checked: Boolean,
    slashProgress: Float,
    loading: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val isBlocked = !checked
    val iconTint = if (isBlocked) MutedText else {
        if (operation.iconKind == "network") LaunchDashboardCyan else Color(0xFF1769E0)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(LaunchDashboardSurface)
            .border(1.dp, OutlineColor.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            if (operation.id == "RUN_IN_BACKGROUND" && appInfo != null) {
                AppIconImageRaw(
                    packageName = packageName,
                    appInfo = appInfo,
                    modifier = Modifier.size(34.dp).alpha(if (isBlocked) 0.42f else 1f)
                )
            } else {
                MonetCanvasIcon(
                    operation.iconKind,
                    Modifier.size(34.dp).alpha(if (isBlocked) 0.42f else 1f),
                    iconTint
                )
            }
            if (slashProgress > 0f) {
                Canvas(Modifier.size(36.dp)) {
                    drawLine(
                        color = ErrorColor.copy(alpha = slashProgress),
                        start = Offset(size.width * .12f, size.height * .88f),
                        end = Offset(size.width * .88f, size.height * .12f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(operation.title, color = LaunchDashboardText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(operation.desc, color = MutedText, fontSize = 10.sp, lineHeight = 14.sp)
            Text(
                if (isBlocked) "BLOCKED" else "ALLOWED",
                color = if (isBlocked) ErrorColor else SuccessColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(25.dp), color = LaunchDashboardCyan, strokeWidth = 2.dp)
        } else {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LaunchDashboardCyan,
                    checkedBorderColor = LaunchDashboardCyan,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = OutlineColor,
                    uncheckedBorderColor = OutlineColor
                )
            )
        }
    }
}

@Composable
private fun SettingsPermissionRow(
    title: String,
    granted: Boolean,
    onFix: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            color = LaunchDashboardText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        if (granted) {
            Text("GRANTED", color = SuccessColor, fontSize = 9.sp, fontWeight = FontWeight.Black)
        } else {
            Box(
                Modifier.clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFFFF7043))
                    .clickable(onClick = onFix)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("FIX", color = Color(0xFF1A0B08), fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ComingSoonScreen(title: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(LaunchDashboardBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MonetCanvasIcon(if (title == "SCRIPT") "script" else if (title == "SYSTEM") "system" else "gear", Modifier.size(48.dp))
            Text(title, color = LaunchDashboardCyan, fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("COMING SOON", color = MutedText, fontSize = 11.sp, letterSpacing = 2.sp)
        }
    }
}

// -----------------------------------------------------------------------------
// COMPILE PROGRESS DIALOG
// -----------------------------------------------------------------------------
private data class CompileDialogUiState(
    val title: String,
    val message: String,
    val progress: Float?,
    val isDismissible: Boolean
)

@Composable
fun CompileProgressDialog(
    title: String,
    message: String,
    progress: Float?,
    onDismiss: () -> Unit,
    isDismissible: Boolean
) {
    Dialog(onDismissRequest = { if (isDismissible) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(24.dp))
                .background(LaunchDashboardSurface)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDismissible) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", color = MutedText, fontSize = 25.sp, lineHeight = 25.sp)
                    }
                } else {
                    Spacer(Modifier.size(30.dp))
                }
            }

            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                if (progress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(62.dp),
                        color = LaunchDashboardCyan,
                        strokeWidth = 5.dp
                    )
                } else {
                    val normalizedProgress = progress.coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxSize()) {
                        val strokeWidth = 7.dp.toPx()
                        drawCircle(
                            color = Color(0xFFE8F5E9),
                            style = Stroke(width = strokeWidth)
                        )
                        drawArc(
                            color = LaunchDashboardCyan,
                            startAngle = -90f,
                            sweepAngle = normalizedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        "${(normalizedProgress * 100f).roundToInt()}%",
                        color = LaunchDashboardCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                title,
                color = LaunchDashboardText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                color = MutedText,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// -----------------------------------------------------------------------------
// SYSTEM TWEAKS - device-wide adjustments and per-app compilation.
// -----------------------------------------------------------------------------
@Composable
fun SystemTweaksScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { TurboSpaceRepository.appPrefs(context) }
    val scope = rememberCoroutineScope()

    var extremeMode by remember { mutableStateOf(prefs.getBoolean("global_extreme_mode", false)) }
    var extremeLoading by remember { mutableStateOf(false) }

    var wifiScanOptimized by remember(prefs) {
        mutableStateOf(prefs.getBoolean("tweak_wifi_scan", false))
    }
    var wifiScanLoading by remember { mutableStateOf(false) }

    // User-installed apps only; TurboSpaceManager filters system apps.
    var installedApps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var compileAppStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    // A single active operation prevents concurrent compile jobs.
    var activeOperationPackage by remember { mutableStateOf<String?>(null) }
    var activeCompileDialog by remember { mutableStateOf<CompileDialogUiState?>(null) }

    LaunchedEffect(context, prefs) {
        installedApps = withContext(Dispatchers.IO) {
            TurboSpaceManager.getInstalledApps(context)
        }
        compileAppStates = installedApps.associate { app ->
            app.packageName to prefs.getBoolean("app_compiled_${app.packageName}", false)
        }
    }

    fun runCompileCommand(
        operationKey: String,
        dialogTitle: String,
        command: String,
        successMessage: String,
        failureMessage: String,
        onSuccess: () -> Unit = {}
    ) {
        if (activeOperationPackage != null) return

        activeOperationPackage = operationKey
        activeCompileDialog = CompileDialogUiState(
            title = dialogTitle,
            message = "Please wait, this may take a while.",
            progress = null,
            isDismissible = false
        )

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    TurboSpaceManager.executeCommandDetailedNoCtx(command)
                } catch (error: Throwable) {
                    TurboSpaceManager.CommandResult(
                        success = false,
                        output = "",
                        exitCode = -1,
                        error = error.message ?: "Command execution failed"
                    )
                }
            }

            val succeeded = result.success && result.exitCode == 0
            if (succeeded) onSuccess()

            activeCompileDialog = CompileDialogUiState(
                title = if (succeeded) "SUCCESS" else "FAILED",
                message = if (succeeded) successMessage else failureMessage,
                progress = 1f,
                isDismissible = true
            )

            delay(1500)
            activeCompileDialog = null
            activeOperationPackage = null
        }
    }

    fun runAppCompile(pkg: String, reset: Boolean) {
        val command = if (reset) {
            "cmd package compile --reset $pkg"
        } else {
            "cmd package compile -m speed -f $pkg"
        }
        runCompileCommand(
            operationKey = pkg,
            dialogTitle = if (reset) "Resetting Compile..." else "Compiling App...",
            command = command,
            successMessage = if (reset) "App compile settings were reset." else "App compilation completed.",
            failureMessage = if (reset) "Could not reset app compilation." else "App compilation failed."
        ) {
            compileAppStates = compileAppStates + (pkg to !reset)
            prefs.edit().putBoolean("app_compiled_$pkg", !reset).apply()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(LaunchDashboardBackground),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonetCanvasIcon("beaker", Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "SYSTEM TWEAKS",
                            color = LaunchDashboardCyan,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        "ADVANCED SYSTEM ADJUSTMENTS",
                        color = MutedText,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            // 1. Extreme Mode
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LaunchDashboardSurface)
                        .border(1.dp, OutlineColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    TuningToggleRow(
                        title = "Extreme Mode",
                        subtitle = "Applies a system-wide CPU/GPU performance policy.",
                        checked = extremeMode,
                        iconKind = "speedometer",
                        loading = extremeLoading,
                        onCheckedChange = { checked ->
                            if (!extremeLoading) {
                                extremeLoading = true
                                scope.launch {
                                    val cmd = "cmd device_config put activity_manager performance.mode_extreme_enabled ${if (checked) "true" else "false"}"
                                    val result = withContext(Dispatchers.IO) {
                                        TurboSpaceManager.executeCommandDetailedNoCtx(cmd)
                                    }
                                    if (result.success && result.exitCode == 0) {
                                        extremeMode = checked
                                        prefs.edit().putBoolean("global_extreme_mode", checked).apply()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "EXTREME MODE FAILED: ${result.error}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    extremeLoading = false
                                }
                            }
                        }
                    )
                }
            }

            // 2. BG-DEXOPT Job: tap the row to run the system job.
            item {
                val bgDexoptRunning = activeOperationPackage == "__BG_DEXOPT__"
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LaunchDashboardSurface)
                        .border(1.dp, OutlineColor, RoundedCornerShape(14.dp))
                        .clickable(enabled = activeOperationPackage == null) {
                            runCompileCommand(
                                operationKey = "__BG_DEXOPT__",
                                dialogTitle = "BG-DEXOPT JOB",
                                command = "cmd package bg-dexopt-job",
                                successMessage = "Background app optimization completed.",
                                failureMessage = "Background app optimization failed."
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonetCanvasIcon("beaker", Modifier.size(34.dp), LaunchDashboardCyan)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "BG-DEXOPT JOB",
                            color = LaunchDashboardText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Recompiles background app code system-wide.",
                            color = MutedText,
                            fontSize = 10.sp
                        )
                    }
                    if (bgDexoptRunning) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = LaunchDashboardCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("›", color = LaunchDashboardText, fontSize = 30.sp, lineHeight = 30.sp)
                    }
                }
            }

            // 3. Wi-Fi Scan
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LaunchDashboardSurface)
                        .border(1.dp, OutlineColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    TuningToggleRow(
                        title = "WI-FI SCAN",
                        subtitle = "Disable background Wi-Fi scanning to save battery.",
                        checked = wifiScanOptimized,
                        enabled = !wifiScanLoading,
                        loading = wifiScanLoading,
                        onCheckedChange = { checked ->
                            if (!wifiScanLoading) {
                                wifiScanLoading = true
                                scope.launch {
                                    val command = if (checked) {
                                        "settings put global wifi_scan_always_enabled 0"
                                    } else {
                                        "settings put global wifi_scan_always_enabled 1"
                                    }
                                    val result = withContext(Dispatchers.IO) {
                                        TurboSpaceManager.executeCommandDetailedNoCtx(command)
                                    }
                                    if (result.success && result.exitCode == 0) {
                                        wifiScanOptimized = checked
                                        prefs.edit().putBoolean("tweak_wifi_scan", checked).apply()
                                        android.widget.Toast.makeText(
                                            context,
                                            if (checked) "WI-FI SCAN OPTIMIZED" else "WI-FI SCAN RESTORED",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "WI-FI SCAN UPDATE FAILED",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    wifiScanLoading = false
                                }
                            }
                        }
                    )
                }
            }

            // 4. App Compile Section
            item {
                Text(
                    "APP COMPILE (OPTIMIZE)",
                    color = LaunchDashboardCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(installedApps, key = { it.packageName }) { app ->
                val pkg = app.packageName
                val isCompiled = compileAppStates[pkg] == true
                val isLoading = activeOperationPackage == pkg

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LaunchDashboardSurface)
                        .border(1.dp, OutlineColor, RoundedCornerShape(12.dp))
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconImageRaw(pkg, app, Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.loadLabel(context.packageManager).toString(),
                            color = LaunchDashboardText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            pkg,
                            color = MutedText,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = LaunchDashboardCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFE8F5E9))
                                .clickable(enabled = activeOperationPackage == null) {
                                    runAppCompile(pkg, reset = isCompiled)
                                }
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                        ) {
                            Text(
                                if (isCompiled) "RESET" else "START",
                                color = LaunchDashboardCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        activeCompileDialog?.let { dialogState ->
            CompileProgressDialog(
                title = dialogState.title,
                message = dialogState.message,
                progress = dialogState.progress,
                isDismissible = dialogState.isDismissible,
                onDismiss = { activeCompileDialog = null }
            )
        }
    }
}
// -----------------------------------------------------------------------------
// SYSTEM - real-time RAM consumers and safe app process management.
// Google apps, Google Chrome, Android core, and vendor system apps are excluded.
// -----------------------------------------------------------------------------
private data class RunningAppRam(
    val packageName: String,
    val label: String,
    val ramMb: Double,
    val appInfo: ApplicationInfo
)

private fun isExcludedFromSystemStats(packageName: String, label: String): Boolean {
    val pkg = packageName.lowercase()
    val appLabel = label.lowercase()
    return pkg.contains("google") ||
        pkg == "com.android.chrome" ||
        pkg.startsWith("com.chrome.") ||
        appLabel.contains("google") ||
        appLabel.contains("chrome")
}

/** Parse ps NAME,RSS output, combine multiple processes belonging to one app, and keep safe apps only. */
private fun parseRunningAppRam(context: Context, output: String): List<RunningAppRam> {
    val pm = context.packageManager
    val installedApps = try {
        TurboSpaceManager.getInstalledApps(context).associateBy { it.packageName }
    } catch (_: Throwable) {
        emptyMap()
    }
    val totalsKb = linkedMapOf<String, Long>()

    output.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("NAME", ignoreCase = true)) return@forEach
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return@forEach
        val processName = parts[0].trim()
        val rssKb = parts[1].toLongOrNull() ?: return@forEach
        if (rssKb <= 0L) return@forEach

        // ps can show a secondary process as package.name:remote; map it to its base package.
        val pkg = processName.substringBefore(':')
        if (!TurboSpaceManager.isSafeToKill(pkg)) return@forEach
        if (!installedApps.containsKey(pkg)) return@forEach
        totalsKb[pkg] = (totalsKb[pkg] ?: 0L) + rssKb
    }

    return totalsKb.mapNotNull { (pkg, rssKb) ->
        val info = installedApps[pkg] ?: return@mapNotNull null
        val label = try { pm.getApplicationLabel(info).toString() } catch (_: Throwable) { pkg }
        if (isExcludedFromSystemStats(pkg, label)) return@mapNotNull null
        val ramMb = rssKb / 1024.0
        if (ramMb <= 0.0) return@mapNotNull null
        RunningAppRam(pkg, label, ramMb, info)
    }.sortedByDescending { it.ramMb }.take(20)
}

@Composable
fun SystemTabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var runningApps by remember { mutableStateOf<List<RunningAppRam>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf("CONNECTING TO SHIZUKU") }
    var refreshTrigger by remember { mutableStateOf(0) }
    var killingPackage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Manual refresh increments the key; the effect immediately fetches, then polls every 5 seconds.
    LaunchedEffect(refreshTrigger) {
        while (true) {
            isRefreshing = true
            val result = withContext(Dispatchers.IO) {
                val shell = TurboSpaceManager.executeCommandDetailedNoCtx(
                    "ps -A -o NAME,RSS | grep -v '\\[' | sort -k2 -nr | head -n 20"
                )
                if (shell.success && shell.exitCode == 0) {
                    Pair(parseRunningAppRam(context, shell.output), "")
                } else {
                    Pair(emptyList<RunningAppRam>(), shell.error.ifBlank { "SHIZUKU COMMAND FAILED" })
                }
            }
            runningApps = result.first
            refreshMessage = result.second
            isRefreshing = false
            delay(5000)
        }
    }

    val maxRam = runningApps.maxOfOrNull { it.ramMb }?.coerceAtLeast(1.0) ?: 1.0
    val topFive = runningApps.take(5)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(LaunchDashboardBackground),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SYSTEM STATS", color = LaunchDashboardCyan, fontSize = 19.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("LIVE RAM CONSUMPTION · 5 SEC REFRESH", color = MutedText, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(LaunchDashboardSurface)
                        .clickable(enabled = !isRefreshing) { refreshTrigger++ },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) CircularProgressIndicator(Modifier.size(19.dp), color = LaunchDashboardCyan, strokeWidth = 2.dp)
                    else MonetCanvasIcon("refresh", Modifier.size(21.dp), LaunchDashboardCyan)
                }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
                    .background(LaunchDashboardSurface).border(1.dp, OutlineColor, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("TOP 5 RAM CONSUMERS", color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (topFive.isEmpty()) {
                    Text(
                        if (refreshMessage.isNotBlank()) refreshMessage else "NO RUNNING THIRD-PARTY APPS",
                        color = MutedText, fontSize = 10.sp, modifier = Modifier.padding(vertical = 30.dp).align(Alignment.CenterHorizontally)
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(190.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        topFive.forEachIndexed { index, app ->
                            val ratio = (app.ramMb / maxRam).toFloat().coerceIn(0.04f, 1f)
                            val targetBarHeight = 105f * ratio
                            val animatedBarHeight by animateFloatAsState(
                                targetValue = targetBarHeight,
                                animationSpec = tween(
                                    durationMillis = 800,
                                    delayMillis = index * 100,
                                    easing = FastOutSlowInEasing
                                ),
                                label = "ram_bar_height_$index"
                            )
                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Text("${app.ramMb.roundToInt()}M", color = LaunchDashboardText, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Spacer(Modifier.height(5.dp))
                                    AppIconImageRaw(app.packageName, app.appInfo, Modifier.size(25.dp))
                                    Spacer(Modifier.height(5.dp))
                                    Box(
                                        Modifier.width(25.dp).height(animatedBarHeight.dp).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                            .background(SystemGreen)
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        app.label.take(8), color = MutedText, fontSize = 8.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Text("BAR HEIGHT IS RELATIVE TO THE HIGHEST APP", color = MutedText, fontSize = 8.sp, letterSpacing = .5.sp)
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RUNNING APPS", color = LaunchDashboardCyan, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text("${runningApps.size} APPS", color = MutedText, fontSize = 9.sp)
            }
        }

        if (runningApps.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
                        .background(LaunchDashboardSurface).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonetCanvasIcon("memory", Modifier.size(32.dp), MutedText)
                    Text(if (refreshMessage.isNotBlank()) refreshMessage else "NO MATCHING RUNNING APPS", color = MutedText, fontSize = 11.sp)
                    Text("Google apps and Chrome are hidden from this list.", color = MutedText, fontSize = 9.sp)
                }
            }
        } else {
            items(runningApps, key = { it.packageName }) { app ->
                val percentage = ((app.ramMb / maxRam) * 100.0).roundToInt().coerceIn(1, 100)
                RunningAppRamRow(
                    app = app,
                    percentage = percentage,
                    killing = killingPackage == app.packageName,
                    onKill = {
                        if (killingPackage == null) {
                            scope.launch {
                                killingPackage = app.packageName
                                val result = withContext(Dispatchers.IO) {
                                    // Mandatory safety check is repeated immediately before every kill.
                                    if (!TurboSpaceManager.isSafeToKill(app.packageName)) {
                                        TurboSpaceManager.CommandResult(false, "", -1, "APP IS PROTECTED")
                                    } else {
                                        TurboSpaceManager.executeCommandDetailedNoCtx("am force-stop ${app.packageName}")
                                    }
                                }
                                killingPackage = null
                                android.widget.Toast.makeText(
                                    context,
                                    if (result.success && result.exitCode == 0) "APP STOPPED" else result.error.ifBlank { "KILL FAILED" },
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                refreshTrigger++
                            }
                        }
                    }
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RunningAppRamRow(
    app: RunningAppRam,
    percentage: Int,
    killing: Boolean,
    modifier: Modifier = Modifier,
    onKill: () -> Unit
) {
    val targetProgress = (percentage / 100f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 500),
        label = "ram_row_progress"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val killScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(),
        label = "kill_button_scale"
    )
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
            .background(LaunchDashboardSurface).border(1.dp, OutlineColor, RoundedCornerShape(12.dp)).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIconImageRaw(app.packageName, app.appInfo, Modifier.size(36.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(app.label, color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(LaunchDashboardBackground)) {
                Box(Modifier.fillMaxWidth(animatedProgress).height(6.dp).clip(CircleShape).background(SystemGreen))
            }
            Text("$percentage% OF TOP APP · ${app.ramMb.roundToInt()}M", color = MutedText, fontSize = 8.sp)
        }
        Box(
            Modifier.scale(killScale).clip(RoundedCornerShape(7.dp)).background(Color(0xFFFF7043))
                .clickable(
                    enabled = !killing,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onKill
                ).padding(horizontal = 11.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            if (killing) CircularProgressIndicator(Modifier.size(15.dp), color = LaunchDashboardText, strokeWidth = 2.dp)
            else Text("KILL", color = LaunchDashboardText, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AppIconImageRaw(
    packageName: String,
    appInfo: ApplicationInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try { drawableToBitmap(context.packageManager.getApplicationIcon(appInfo)) } catch (_: Throwable) { null }
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = modifier.clip(RoundedCornerShape(7.dp)))
    } else {
        Box(modifier.clip(RoundedCornerShape(7.dp)).background(LaunchDashboardBackground), contentAlignment = Alignment.Center) {
            MonetCanvasIcon("app", Modifier.size(20.dp), MutedText)
        }
    }
}

// -----------------------------------------------------------------------------
// HOME
// -----------------------------------------------------------------------------
private data class InstalledAppItem(
    val info: ApplicationInfo,
    val packageName: String,
    val label: String
)

@Composable
fun HomeTabScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
    onAppSelected: (ApplicationInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) { TurboSpaceRepository.appPrefs(context) }
    var prefsRevision by remember(prefs) { mutableStateOf(0) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            prefsRevision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val selectedPackage by TurboSpaceRepository.selectedGame.collectAsState()
    var batteryPercent by remember { mutableStateOf(TurboSpaceManager.getBatteryPercent(context)) }
    var hasShizuku by remember { mutableStateOf(TurboSpaceManager.isShizukuAvailableAndGranted()) }
    var hasWriteSettings by remember { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)) }
    var hasBattery by remember {
        mutableStateOf(try {
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (_: Throwable) { false })
    }
    var apps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var favoritePackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }

    var isOptimizing by remember { mutableStateOf(false) }
    var showOptimizeResult by remember { mutableStateOf(false) }
    var ramFreedText by remember { mutableStateOf("") }
    var cacheFreedText by remember { mutableStateOf("") }
    var ramPercent by remember { mutableStateOf(0) }
    var cachePercent by remember { mutableStateOf(0) }

    fun runOptimizeNow() {
        if (isOptimizing) return
        if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
            hasShizuku = false
            android.widget.Toast.makeText(
                context,
                "Shizuku permission is required to optimize the device",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        isOptimizing = true
        scope.launch {
            try {
                val measurements = withContext(Dispatchers.IO) {
                    val activityManager = context.getSystemService(ActivityManager::class.java)
                        ?: error("ActivityManager is unavailable")

                    val beforeRamInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(beforeRamInfo)
                    val beforeAvailRam = beforeRamInfo.availMem
                    val totalRam = beforeRamInfo.totalMem

                    val dataStatsBefore = StatFs(Environment.getDataDirectory().path)
                    val beforeAvailableStorage = dataStatsBefore.availableBytes
                    val totalStorage = dataStatsBefore.totalBytes

                    val killResult = TurboSpaceManager.executeCommandDetailedNoCtx("am kill-all")
                    val trimResult = TurboSpaceManager.executeCommandDetailedNoCtx("pm trim-caches 999G")

                    delay(1500)

                    val afterRamInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(afterRamInfo)
                    val afterAvailRam = afterRamInfo.availMem

                    val dataStatsAfter = StatFs(Environment.getDataDirectory().path)
                    val afterAvailableStorage = dataStatsAfter.availableBytes

                    val freedRam = (afterAvailRam - beforeAvailRam).coerceAtLeast(0L)
                    val freedStorage = (afterAvailableStorage - beforeAvailableStorage).coerceAtLeast(0L)
                    val calculatedRamPercent = if (totalRam > 0L) {
                        (freedRam.toDouble() / totalRam.toDouble() * 100.0).toInt().coerceIn(0, 100)
                    } else 0
                    val calculatedStoragePercent = if (totalStorage > 0L) {
                        (freedStorage.toDouble() / totalStorage.toDouble() * 100.0).toInt().coerceIn(0, 100)
                    } else 0
                    val commandsSucceeded = killResult.success && killResult.exitCode == 0 &&
                        trimResult.success && trimResult.exitCode == 0

                    Triple(
                        Pair(freedRam, calculatedRamPercent),
                        Pair(freedStorage, calculatedStoragePercent),
                        commandsSucceeded
                    )
                }

                ramFreedText = "${measurements.first.first / (1024L * 1024L)} MB"
                ramPercent = measurements.first.second
                cacheFreedText = "${measurements.second.first / (1024L * 1024L)} MB"
                cachePercent = measurements.second.second
                showOptimizeResult = true

                if (!measurements.third) {
                    android.widget.Toast.makeText(
                        context,
                        "Some optimization commands could not be completed; values show the measured change",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (error: Throwable) {
                android.widget.Toast.makeText(
                    context,
                    "Optimization failed: ${error.message ?: "Unable to measure system resources"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } finally {
                isOptimizing = false
                hasShizuku = TurboSpaceManager.isShizukuAvailableAndGranted()
            }
        }
    }

    fun refreshAll() {
        scope.launch {
            refreshing = true
            val result = withContext(Dispatchers.IO) {
                val installed = TurboSpaceManager.getInstalledApps(context).map { info ->
                    InstalledAppItem(info, info.packageName, context.packageManager.getApplicationLabel(info).toString())
                }
                installed
            }
            apps = result
            favoritePackages = result.map { it.packageName }
                .filter { prefs.getBoolean("favorite_$it", false) }.toSet()
            batteryPercent = TurboSpaceManager.getBatteryPercent(context)
            hasShizuku = TurboSpaceManager.isShizukuAvailableAndGranted()
            hasWriteSettings = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
            hasBattery = try {
                context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
            } catch (_: Throwable) { false }
            refreshing = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { refreshAll() }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps else apps.filter {
            it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(LaunchDashboardBackground),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OptimizeDashboard(
                isOptimizing = isOptimizing,
                onOptimizeClick = { runOptimizeNow() },
                batteryPercent = batteryPercent,
                refreshing = refreshing,
                onRefresh = { refreshAll() },
                selectedPackage = selectedPackage,
                onSettingsClick = onSettingsClick
            )
        }
        if (!hasShizuku || !hasWriteSettings || !hasBattery) {
            item {
                PermissionSetupCard(
                    hasShizuku = hasShizuku,
                    hasWriteSettings = hasWriteSettings,
                    hasBattery = hasBattery,
                    onRequest = { key ->
                        try {
                            when (key) {
                                "SHIZUKU" -> TurboSpaceManager.requestShizukuPermission()
                                "WRITE_SETTINGS" -> context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                                "BATTERY_OPTIMIZATION" -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                            android.widget.Toast.makeText(context, "Unable to open permission settings", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRefresh = { refreshAll() }
                )
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("INSTALLED APPS", color = LaunchDashboardCyan, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(LaunchDashboardSurface)
                        .border(1.dp, OutlineColor, RoundedCornerShape(11.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonetCanvasIcon("search", Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = LaunchDashboardText, fontSize = 13.sp),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Search app or package...", color = MutedText, fontSize = 13.sp)
                            inner()
                        }
                    )
                }
            }
        }
        if (filteredApps.isEmpty()) {
            item {
                Text(
                    if (apps.isEmpty()) "No installed apps found" else "No matching apps",
                    Modifier.fillMaxWidth().padding(24.dp), color = MutedText, fontSize = 12.sp
                )
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                val pkg = app.packageName
                val isConfigured = remember(pkg, prefsRevision) {
                    prefs.getBoolean("game_performance_$pkg", false) ||
                    prefs.getBoolean("game_battery_$pkg", false) ||
                    prefs.getBoolean("gpu_performance_$pkg", false) ||
                    prefs.getBoolean("app_optimized_$pkg", false) ||
                    prefs.getInt("fps_max_$pkg", 0) > 0
                }
                InstalledAppRow(
                    app = app,
                    selected = app.packageName == selectedPackage,
                    favorite = app.packageName in favoritePackages,
                    isConfigured = isConfigured,
                    onSelect = {
                        TurboSpaceRepository.setSelectedGame(app.packageName, context)
                        onAppSelected(app.info)
                    },
                    onFavorite = {
                        val key = "favorite_${app.packageName}"
                        val next = app.packageName !in favoritePackages
                        prefs.edit().putBoolean(key, next).apply()
                        favoritePackages = if (next) favoritePackages + app.packageName else favoritePackages - app.packageName
                    }
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (showOptimizeResult) {
        AlertDialog(
            onDismissRequest = { showOptimizeResult = false },
            containerColor = LaunchDashboardSurface,
            title = {
                Text(
                    "OPTIMIZATION SUCCESS",
                    color = LaunchDashboardCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("RAM Freed: $ramFreedText ($ramPercent%)", color = LaunchDashboardText, fontSize = 13.sp)
                    Text("Storage Freed: $cacheFreedText ($cachePercent%)", color = LaunchDashboardText, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptimizeResult = false }) {
                    Text("DONE", color = LaunchDashboardCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun OptimizeDashboard(
    isOptimizing: Boolean,
    onOptimizeClick: () -> Unit,
    batteryPercent: Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    selectedPackage: String,
    onSettingsClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(LaunchDashboardBackground)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp)
                    .clip(CircleShape)
                    .background(LaunchDashboardSurface)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                MonetCanvasIcon("gear", Modifier.size(23.dp), LaunchDashboardCyan)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "TURBOTUNE TOOLS",
                    color = LaunchDashboardText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    if (selectedPackage.isBlank()) "SYSTEM PERFORMANCE CONSOLE" else "TARGET: $selectedPackage",
                    color = LaunchDashboardCyan,
                    fontSize = 8.sp,
                    letterSpacing = 0.7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MonetCanvasIcon("battery", Modifier.size(20.dp), LaunchDashboardCyan)
            Spacer(Modifier.width(5.dp))
            Text("$batteryPercent%", color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(36.dp)
                    .clip(CircleShape)
                    .background(LaunchDashboardSurface)
                    .clickable(enabled = !refreshing, onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = LaunchDashboardCyan, strokeWidth = 2.dp)
                } else {
                    MonetCanvasIcon("refresh", Modifier.size(19.dp))
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(122.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(width = 200.dp, height = 122.dp)) {
                val radius = minOf(size.width * 0.45f, size.height * 0.76f)
                val center = Offset(size.width / 2f, size.height * 0.86f)
                val arcTopLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2f, radius * 2f)
                drawArc(
                    LaunchDashboardCyan,
                    180f,
                    180f,
                    false,
                    arcTopLeft,
                    arcSize,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                for (i in 0..6) {
                    val angle = Math.toRadians((180 + i * 30).toDouble())
                    val innerRadius = radius - 20.dp.toPx()
                    val outerRadius = radius - 9.dp.toPx()
                    val start = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * innerRadius,
                        center.y + kotlin.math.sin(angle).toFloat() * innerRadius
                    )
                    val end = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * outerRadius,
                        center.y + kotlin.math.sin(angle).toFloat() * outerRadius
                    )
                    drawLine(
                        LaunchDashboardCyan.copy(alpha = 0.35f),
                        start,
                        end,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            MonetCanvasIcon(
                "rocket",
                Modifier.size(48.dp).align(Alignment.Center),
                LaunchDashboardCyan
            )
        }

        Text(
            "OPTIMIZED",
            color = LaunchDashboardCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            "Your device is running smoothly!",
            color = LaunchDashboardText,
            fontSize = 13.sp
        )

        Row(
            Modifier.fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(LaunchDashboardCyan)
                .clickable(enabled = !isOptimizing, onClick = onOptimizeClick)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOptimizing) {
                CircularProgressIndicator(
                    Modifier.size(23.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
                Spacer(Modifier.width(12.dp))
                Text("OPTIMIZING...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            } else {
                MonetCanvasIcon("lightning", Modifier.size(28.dp), Color.White)
                Spacer(Modifier.width(14.dp))
                Text("Optimize Now", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermissionSetupCard(
    hasShizuku: Boolean,
    hasWriteSettings: Boolean,
    hasBattery: Boolean,
    onRequest: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
            .background(LaunchDashboardSurface).border(1.dp, OutlineColor, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("PERMISSION SETUP", color = LaunchDashboardCyan, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        PermissionRow("SHIZUKU", "Required to execute system commands", hasShizuku) { onRequest("SHIZUKU") }
        PermissionRow("WRITE SETTINGS", "Modify supported display and system settings", hasWriteSettings) { onRequest("WRITE_SETTINGS") }
        PermissionRow("BATTERY OPTIMIZATION", "Allow unrestricted background operation", hasBattery) { onRequest("BATTERY_OPTIMIZATION") }
        TextButton(onClick = onRefresh, modifier = Modifier.align(Alignment.End)) {
            Text("CHECK AGAIN", color = LaunchDashboardCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onRequest: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LaunchDashboardText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MutedText, fontSize = 10.sp)
        }
        if (granted) {
            Text("GRANTED", color = SuccessColor, fontSize = 9.sp, fontWeight = FontWeight.Black)
        } else {
            Box(Modifier.clip(RoundedCornerShape(7.dp)).background(LaunchDashboardCyan).clickable(onClick = onRequest).padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text("ALLOW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledAppItem,
    selected: Boolean,
    favorite: Boolean,
    isConfigured: Boolean,
    onSelect: () -> Unit,
    onFavorite: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(app.packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(app.info)
            drawableToBitmap(drawable)
        } catch (_: Throwable) { null }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
            .border(1.dp, if (isConfigured) Color(0xFFC8E6C9) else OutlineColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) LaunchDashboardCyan.copy(alpha = .10f) else LaunchDashboardSurface)
            .clickable(onClick = onSelect).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)))
        else Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(LaunchDashboardBackground), contentAlignment = Alignment.Center) {
            MonetCanvasIcon("app", Modifier.size(22.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    Modifier.weight(1f),
                    color = LaunchDashboardText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isConfigured) {
                    Spacer(Modifier.width(6.dp))
                    Text("CONFIGURED", color = LaunchDashboardCyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            Text(app.packageName, color = MutedText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onFavorite), contentAlignment = Alignment.Center) {
            MonetCanvasIcon(if (favorite) "bookmark_filled" else "bookmark", Modifier.size(19.dp), if (favorite) LaunchDashboardCyan else MutedText)
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? = try {
    val width = drawable.intrinsicWidth.coerceIn(1, 192)
    val height = drawable.intrinsicHeight.coerceIn(1, 192)
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
    }
} catch (_: Throwable) { null }

// -----------------------------------------------------------------------------
// TUNING CONSOLE - every switch persists state per target package.
// -----------------------------------------------------------------------------
@Composable
fun TuningConsoleScreen(
    app: ApplicationInfo?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) { TurboSpaceRepository.appPrefs(context) }
    val scope = rememberCoroutineScope()
    val packageName = app?.packageName.orEmpty()
    val appLabel = remember(app, context) {
        app?.let { try { context.packageManager.getApplicationLabel(it).toString() } catch (_: Throwable) { it.packageName } }
            ?: "No app selected"
    }

    var showInfo by remember { mutableStateOf(false) }
    var showFpsLimit by remember { mutableStateOf(false) }
    var fpsBoostEnabled by remember { mutableStateOf(prefs.getBoolean("global_fps_boost", false)) }
    var gpuEnabled by remember(packageName) {
        mutableStateOf(packageName.isNotBlank() && prefs.getBoolean("gpu_performance_$packageName", false))
    }
    var fpsBoostLoading by remember { mutableStateOf(false) }
    var gpuLoading by remember { mutableStateOf(false) }

    fun executeFeatureCommands(commands: List<String>, onFinished: (Boolean) -> Unit) {
        if (!TurboSpaceManager.isShizukuAvailableAndGranted()) {
            android.widget.Toast.makeText(
                context,
                "Shizuku permission is required to apply this setting",
                android.widget.Toast.LENGTH_LONG
            ).show()
            onFinished(false)
            return
        }
        scope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                commands.all { command ->
                    val result = TurboSpaceManager.executeCommandDetailedNoCtx(command)
                    result.success && result.exitCode == 0
                }
            }
            onFinished(succeeded)
        }
    }

    Column(
        modifier.fillMaxSize().background(LaunchDashboardBackground)
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                MonetCanvasIcon("back", Modifier.size(30.dp), LaunchDashboardCyan)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Tuning Console",
                    color = LaunchDashboardText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { showInfo = true },
                contentAlignment = Alignment.Center
            ) {
                MonetCanvasIcon("info", Modifier.size(30.dp), LaunchDashboardText)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(20.dp)).background(LaunchDashboardSurface).padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app != null) {
                    val appIcon = remember(packageName) {
                        try { drawableToBitmap(context.packageManager.getApplicationIcon(app)) } catch (_: Throwable) { null }
                    }
                    if (appIcon != null) {
                        Image(appIcon.asImageBitmap(), contentDescription = appLabel, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)))
                    } else {
                        MonetCanvasIcon("app", Modifier.size(38.dp))
                    }
                } else {
                    MonetCanvasIcon("app", Modifier.size(38.dp), MutedText)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(appLabel, color = LaunchDashboardText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (packageName.isBlank()) "Choose an app from Home for GPU settings" else packageName,
                        color = MutedText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))

            TuningToggleRow(
                title = "FPS Boost",
                subtitle = "Unlock higher FPS (system command)",
                checked = fpsBoostEnabled,
                iconKind = "fps",
                loading = fpsBoostLoading,
                loadingLabel = "APPLYING",
                enabled = !fpsBoostLoading,
                onCheckedChange = { checked ->
                    fpsBoostLoading = true
                    val commands = if (checked) listOf(
                        "cmd device_config put activity_manager high_fps_config on",
                        "cmd device_config put activity_manager fpsbooster_enable 1"
                    ) else listOf(
                        "cmd device_config delete activity_manager high_fps_config",
                        "cmd device_config delete activity_manager fpsbooster_enable"
                    )
                    executeFeatureCommands(commands) { success ->
                        fpsBoostLoading = false
                        if (success) {
                            fpsBoostEnabled = checked
                            prefs.edit().putBoolean("global_fps_boost", checked).apply()
                        } else {
                            android.widget.Toast.makeText(context, "FPS Boost command failed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))

            TuningToggleRow(
                title = "GPU Performance",
                subtitle = if (packageName.isBlank()) "Select an app from Home to enable this setting" else "Optimize GPU rendering (more performance)",
                checked = gpuEnabled,
                iconKind = "gpu",
                enabled = app != null && !gpuLoading,
                loading = gpuLoading,
                loadingLabel = "APPLYING",
                onCheckedChange = { checked ->
                    gpuLoading = true
                    val command = if (checked) {
                        "cmd game downscale 0.95 $packageName"
                    } else {
                        "cmd game downscale disable $packageName"
                    }
                    executeFeatureCommands(listOf(command)) { success ->
                        gpuLoading = false
                        if (success) {
                            gpuEnabled = checked
                            prefs.edit().putBoolean("gpu_performance_$packageName", checked).apply()
                        } else {
                            android.widget.Toast.makeText(context, "GPU Performance command failed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showFpsLimit = true }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("UNLOCK FPS", color = LaunchDashboardText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Set maximum FPS", color = MutedText, fontSize = 12.sp)
                }
                Text("›", color = LaunchDashboardText, fontSize = 34.sp, fontWeight = FontWeight.Light)
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = LaunchDashboardSurface,
            title = { Text("TUNING CONSOLE INFO", color = LaunchDashboardCyan, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FPS Boost: Applies the global high-FPS device configuration.", color = LaunchDashboardText, fontSize = 13.sp)
                    Text("GPU Performance: Applies a per-app rendering downscale setting to the selected app.", color = LaunchDashboardText, fontSize = 13.sp)
                    Text("FPS Limit: Sets the system minimum and peak display refresh rates to a supported value.", color = LaunchDashboardText, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("GOT IT", color = LaunchDashboardCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showFpsLimit) {
        FpsLimitDialog(
            packageName = packageName,
            onDismiss = { showFpsLimit = false }
        )
    }
}

@Composable
fun FpsLimitDialog(
    packageName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { TurboSpaceRepository.appPrefs(context) }
    val maxSupportedHz = remember(context) { detectMaxSupportedRefreshRateHz(context) }
    var applyingHz by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = { if (applyingHz == null) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("FPS LIMIT", color = LaunchDashboardCyan, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text("Choose a refresh rate supported by your display.", color = LaunchDashboardText, fontSize = 12.sp)

            listOf(
                Triple(60, Color(0xFFE8F5E9), Color(0xFF2E7D32)) to 1000,
                Triple(90, Color(0xFFE3F2FD), Color(0xFF1976D2)) to 666,
                Triple(120, Color(0xFFFFEBEE), Color(0xFFC62828)) to 500
            ).forEach { (entry, duration) ->
                val (hz, rowColor, ballColor) = entry
                val supported = hz <= maxSupportedHz
                val transition = rememberInfiniteTransition()
                val bounce by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = duration),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(rowColor)
                        .alpha(if (supported) 1f else .42f)
                        .clickable(enabled = supported && applyingHz == null) {
                            applyingHz = hz
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    val first = TurboSpaceManager.executeCommandDetailedNoCtx(
                                        "settings put system peak_refresh_rate ${hz}.0"
                                    )
                                    val second = if (first.success && first.exitCode == 0) {
                                        TurboSpaceManager.executeCommandDetailedNoCtx(
                                            "settings put system min_refresh_rate ${hz}.0"
                                        )
                                    } else first
                                    first.success && first.exitCode == 0 && second.success && second.exitCode == 0
                                }
                                applyingHz = null
                                if (result) {
                                    val key = if (packageName.isBlank()) "fps_max_global" else "fps_max_$packageName"
                                    prefs.edit().putInt(key, hz).apply()
                                    onDismiss()
                                } else {
                                    android.widget.Toast.makeText(context, "Unable to apply ${hz}Hz", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("$hz FPS", color = LaunchDashboardText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                !supported -> "Not Supported"
                                applyingHz == hz -> "Applying..."
                                else -> "Tap to select"
                            },
                            color = if (supported) MutedText else LaunchDashboardText,
                            fontSize = 10.sp,
                            fontWeight = if (!supported) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(13.dp).offset(y = (-9f * bounce).dp)
                                .clip(CircleShape).background(ballColor)
                        )
                    }
                    if (applyingHz == hz) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.size(17.dp), color = ballColor, strokeWidth = 2.dp)
                    }
                }
            }

            TextButton(onClick = onDismiss, enabled = applyingHz == null, modifier = Modifier.align(Alignment.End)) {
                Text("CANCEL", color = LaunchDashboardCyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ActionButtonCard uses a weight modifier from the calling row.
@Composable
fun ActionButtonCard(
    title: String,
    description: String,
    iconKind: String,
    onClick: () -> Unit,
    loading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.shadow(2.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(14.dp))
            .background(LaunchDashboardSurface)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonetCanvasIcon(iconKind, Modifier.size(25.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = LaunchDashboardText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(description, color = MutedText, fontSize = 9.sp, minLines = 2)
            }
            if (loading) {
                Spacer(Modifier.width(5.dp))
                CircularProgressIndicator(Modifier.size(18.dp), color = LaunchDashboardCyan, strokeWidth = 2.dp)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(7.dp)).background(LaunchDashboardCyan),
            contentAlignment = Alignment.Center
        ) {
            Text(if (loading) "RUNNING" else "START", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}


// -----------------------------------------------------------------------------
// MAIN ACTIVITY
// -----------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurboSpaceAppRoot() }
    }
}
