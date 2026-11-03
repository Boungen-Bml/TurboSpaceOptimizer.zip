package com.turbospace.optimizer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.BatteryManager
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

// -----------------------------------------------------------------------------
// ANDROID 12 MATERIAL 3 LIGHT THEME - PINK TINT
// -----------------------------------------------------------------------------
val LaunchDashboardBackground = Color(0xFFFDF7F9) // Soft pink-white background
val LaunchDashboardText = Color(0xFF4A0024)       // Deep berry primary text
val LaunchDashboardCyan = Color(0xFFD81B60)       // Pink primary accent
val LaunchDashboardSurface = Color(0xFFFFFFFF)    // Clean white cards
private val MutedText = Color(0xFF7A5C68)         // Muted rose-gray descriptions
private val OutlineColor = Color(0xFFF1D6E1)      // Soft pink dividers/outlines
private val SuccessColor = Color(0xFF2E7D32)      // Deep green status
private val SystemGreen = Color(0xFF4CAF50)       // System graph green
private val ErrorColor = Color(0xFFC62828)        // Deep red error

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
                val body = Path().apply {
                    moveTo(cx, h * .08f)
                    cubicTo(w * .76f, h * .22f, w * .75f, h * .54f, cx, h * .72f)
                    cubicTo(w * .25f, h * .54f, w * .24f, h * .22f, cx, h * .08f)
                    close()
                }
                drawPath(body, tint, style = stroke)
                drawCircle(tint, m * .075f, Offset(cx, h * .34f), style = stroke)
                val fins = Path().apply {
                    moveTo(w * .31f, h * .54f); lineTo(w * .12f, h * .75f); lineTo(w * .40f, h * .69f); close()
                    moveTo(w * .69f, h * .54f); lineTo(w * .88f, h * .75f); lineTo(w * .60f, h * .69f); close()
                }
                drawPath(fins, tint, style = stroke)
                drawLine(tint, Offset(w * .44f, h * .75f), Offset(w * .40f, h * .92f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .56f, h * .75f), Offset(w * .60f, h * .92f), sw, cap = StrokeCap.Round)
            }
            "chevron_down" -> {
                drawLine(tint, Offset(w * .20f, h * .38f), Offset(cx, h * .65f), sw * 1.35f, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx, h * .65f), Offset(w * .80f, h * .38f), sw * 1.35f, cap = StrokeCap.Round)
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
                drawCircle(tint,m*.25f,Offset(cx,cy),style=stroke)
                drawCircle(tint,m*.09f,Offset(cx,cy),style=stroke)
                for (i in 0..7) {
                    val a = Math.toRadians((i*45).toDouble())
                    drawLine(tint, Offset(cx+kotlin.math.cos(a).toFloat()*m*.30f,cy+kotlin.math.sin(a).toFloat()*m*.30f), Offset(cx+kotlin.math.cos(a).toFloat()*m*.43f,cy+kotlin.math.sin(a).toFloat()*m*.43f),sw*1.5f)
                }
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
    HOME("HOME", "home"),
    SYSTEM("STATS", "system"),
    TWEAKS("TWEAKS", "beaker"),
    SETTINGS("SETTINGS", "gear"),
    APPOPS("APPOPS", "script")
}

@Composable
fun TurboSpaceAppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE) }
    var isPolicyAccepted by remember {
        mutableStateOf(prefs.getBoolean("policy_accepted", false))
    }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var showAppOpsScreen by remember { mutableStateOf(false) }
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
                    if (!showAppOpsScreen) {
                        NavigationBar(
                            containerColor = LaunchDashboardBackground,
                            contentColor = LaunchDashboardText
                        ) {
                            // Fixed-width destinations inside a horizontal scroller keep every
                            // destination reachable on narrow phones and unusual display sizes.
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(navScrollState),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppTab.values().forEach { tab ->
                                    val selected = selectedTab == tab
                                    Column(
                                        Modifier.width(76.dp)
                                            .clickable {
                                                if (tab == AppTab.APPOPS) {
                                                    selectedTab = AppTab.APPOPS
                                                    showAppOpsScreen = true
                                                } else {
                                                    selectedTab = tab
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
                if (showAppOpsScreen) {
                    AppOpsMainScreen(
                        modifier = Modifier.padding(padding),
                        onBack = {
                            showAppOpsScreen = false
                            selectedTab = AppTab.SETTINGS
                        }
                    )
                } else {
                    when (selectedTab) {
                        AppTab.HOME -> HomeTabScreen(Modifier.padding(padding))
                        AppTab.SYSTEM -> SystemTabScreen(Modifier.padding(padding))
                        AppTab.TWEAKS -> SystemTweaksScreen(Modifier.padding(padding))
                        AppTab.SETTINGS -> SettingsTabScreen(
                            modifier = Modifier.padding(padding),
                            onOpenAppOps = {
                                selectedTab = AppTab.APPOPS
                                showAppOpsScreen = true
                            }
                        )
                        AppTab.APPOPS -> SettingsTabScreen(
                            modifier = Modifier.padding(padding),
                            onOpenAppOps = { showAppOpsScreen = true }
                        )
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
                .background(Color(0xFFFFE8F0))
                .border(1.dp, Color(0xFFF2B8D2), RoundedCornerShape(14.dp))
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
// Boolean preference value: true = IGNORE, false = ALLOW.
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
    AppOpsOperation("RUN_IN_BACKGROUND", "Run in Background", "Stops background execution.", "cpu"),
    AppOpsOperation("BOOT_RUN", "Boot Run", "Prevents auto-start on reboot.", "rocket"),
    AppOpsOperation("START_FOREGROUND", "Start Foreground", "Blocks foreground services.", "system"),
    AppOpsOperation("WAKE_LOCK", "Wake Lock", "Stops keeping CPU/screen awake.", "sun"),
    AppOpsOperation("SYSTEM_ALERT_WINDOW", "Alert Window", "Blocks floating windows.", "app"),
    AppOpsOperation("POST_NOTIFICATION", "Post Notification", "Blocks push notifications.", "bell"),
    AppOpsOperation("CHANGE_WIFI_STATE", "Change Wi-Fi", "Prevents turning Wi-Fi on/off.", "wifi"),
    AppOpsOperation("FINE_LOCATION", "Fine Location", "Blocks precise GPS.", "search"),
    AppOpsOperation("COARSE_LOCATION", "Coarse Location", "Blocks approximate location.", "network"),
    AppOpsOperation("ACTIVITY_RECOGNITION", "Activity Recognition", "Stops tracking physical movement.", "home"),
    AppOpsOperation("READ_CLIPBOARD", "Read Clipboard", "Prevents reading copied text.", "clipboard")
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
                    // Do not expose Android, Google, OEM/core, or otherwise protected packages.
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("BACK", color = LaunchDashboardCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    "ADVANCED (APPOPS)",
                    Modifier.weight(1f),
                    color = LaunchDashboardText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(52.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(11.dp))
                    .background(LaunchDashboardSurface).border(1.dp, OutlineColor, RoundedCornerShape(11.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonetCanvasIcon("search", Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = LaunchDashboardText, fontSize = 13.sp),
                    decorationBox = { inner ->
                        if (searchQuery.isBlank()) {
                            Text("Search app or package...", color = MutedText, fontSize = 13.sp)
                        }
                        inner()
                    }
                )
            }
            Text(
                "INSTALLED APPS · SYSTEM + USER (PROTECTED APPS HIDDEN)",
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MutedText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .4.sp
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loadingApps) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(35.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LaunchDashboardCyan, strokeWidth = 2.dp)
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    item {
                        Text(
                            if (apps.isEmpty()) "No eligible installed apps found" else "No matching apps",
                            Modifier.fillMaxWidth().padding(28.dp),
                            color = MutedText,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(12.dp)).background(LaunchDashboardSurface)
                                .border(1.dp, OutlineColor, RoundedCornerShape(12.dp))
                                .clickable { selectedApp = app }
                                .padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImageRaw(app.packageName, app.info, Modifier.size(39.dp))
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, color = LaunchDashboardText, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, color = MutedText, fontSize = 9.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            MonetCanvasIcon("chevron_down", Modifier.size(19.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = onBack,
            containerColor = Color.White,
            title = {
                Text(
                    "WARNING: SYSTEM RESTRICTIONS",
                    color = LaunchDashboardText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Text(
                    "This feature may affect some apps. Notifications may be delayed, apps may become unstable or laggy, and GPS may stop working. Use with caution! NEVER apply this to system apps or apps you don't understand. Have fun!",
                    color = LaunchDashboardText,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White)
                        .border(1.dp, LaunchDashboardCyan, RoundedCornerShape(8.dp))
                ) {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("appops_warning_shown", true).apply()
                        showWarning = false
                    }) {
                        Text("I UNDERSTAND", color = LaunchDashboardText, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            },
            dismissButton = {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFE0E0E0))) {
                    TextButton(onClick = onBack) {
                        Text("CANCEL", color = Color(0xFF424242), fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            }
        )
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
    var ignoredStates by remember(packageName) {
        mutableStateOf(
            APP_OPS_OPERATIONS.associate { operation ->
                operation.id to prefs.getBoolean("appops_${operation.id}_${packageName}", false)
            }
        )
    }
    var loadingIds by remember(packageName) { mutableStateOf<Set<String>>(emptySet()) }
    var resetting by remember(packageName) { mutableStateOf(false) }

    fun runOperation(operation: AppOpsOperation, ignore: Boolean) {
        if (operation.id in loadingIds || resetting) return
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, enabled = !resetting) {
                Text("BACK", color = LaunchDashboardCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Text(
                appLabel,
                Modifier.weight(1f),
                color = LaunchDashboardText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = {
                    if (!resetting && loadingIds.isEmpty()) {
                        resetting = true
                        scope.launch {
                            val results = withContext(Dispatchers.IO) {
                                APP_OPS_OPERATIONS.map { operation ->
                                    operation.id to TurboSpaceManager.executeCommandDetailedNoCtx(
                                        "cmd appops set $packageName ${operation.id} allow"
                                    ).success
                                }
                            }
                            val succeeded = results.filter { it.second }.map { it.first }.toSet()
                            val editor = prefs.edit()
                            succeeded.forEach { id -> editor.remove("appops_${id}_${packageName}") }
                            editor.apply()
                            ignoredStates = ignoredStates.mapValues { (id, value) ->
                                if (id in succeeded) false else value
                            }
                            resetting = false
                            val failed = results.size - succeeded.size
                            android.widget.Toast.makeText(
                                context,
                                if (failed == 0) "ALL APPOPS RESTORED" else "RESTORED ${succeeded.size}/11 · $failed FAILED",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                enabled = !resetting && loadingIds.isEmpty()
            ) {
                Text(if (resetting) "RESETTING" else "RESET", color = ErrorColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Text(
            "APP OPERATIONS · IGNORE BLOCKS THE SELECTED OPERATION",
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            color = MutedText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(APP_OPS_OPERATIONS, key = { it.id }) { operation ->
                val checked = ignoredStates[operation.id] == true
                val isIgnored = checked
                val slashProgress by animateFloatAsState(
                    targetValue = if (isIgnored) 1f else 0f,
                    animationSpec = tween(300),
                    label = "slash"
                )
                AppOpsOperationRow(
                    operation = operation,
                    checked = checked,
                    slashProgress = slashProgress,
                    loading = operation.id in loadingIds || resetting,
                    onCheckedChange = { nextChecked -> runOperation(operation, nextChecked) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AppOpsOperationRow(
    operation: AppOpsOperation,
    checked: Boolean,
    slashProgress: Float,
    loading: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(LaunchDashboardSurface)
            .border(1.dp, OutlineColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            MonetCanvasIcon(
                operation.iconKind,
                Modifier.size(26.dp).alpha(if (checked) .4f else 1f),
                if (checked) MutedText else LaunchDashboardCyan
            )
            if (slashProgress > 0f) {
                androidx.compose.foundation.Canvas(Modifier.size(29.dp)) {
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
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(operation.title, color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(operation.desc, color = MutedText, fontSize = 9.sp, lineHeight = 13.sp)
            Text(
                if (checked) "IGNORE" else "ALLOW",
                color = if (checked) ErrorColor else SuccessColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = .7.sp
            )
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = LaunchDashboardCyan, strokeWidth = 2.dp)
        } else {
            Box(
                Modifier.clip(RoundedCornerShape(7.dp))
                    .background(if (checked) ErrorColor.copy(alpha = 0.15f) else SuccessColor.copy(alpha = 0.15f))
                    .clickable { onCheckedChange(!checked) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (checked) "IGNORE" else "ALLOW",
                    color = if (checked) ErrorColor else SuccessColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
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
// SYSTEM TWEAKS - device-wide adjustments persisted in TurboSpace preferences.
// -----------------------------------------------------------------------------
@Composable
fun SystemTweaksScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { TurboSpaceRepository.appPrefs(context) }
    val scope = rememberCoroutineScope()

    var wifiScanOptimized by remember(prefs) {
        mutableStateOf(prefs.getBoolean("tweak_wifi_scan", false))
    }
    var bgDexoptLoading by remember { mutableStateOf(false) }
    var wifiScanLoading by remember { mutableStateOf(false) }

    fun showTweakToast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun runTweakCommands(
        commands: List<String>,
        onSuccess: () -> Unit,
        onFinish: () -> Unit = {}
    ) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                var completed = true
                for (command in commands) {
                    val result = TurboSpaceManager.executeCommandDetailedNoCtx(command)
                    if (!result.success || result.exitCode != 0) {
                        completed = false
                        break
                    }
                }
                completed
            }
            if (success) onSuccess() else showTweakToast("SYSTEM TWEAK FAILED")
            onFinish()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(LaunchDashboardBackground),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
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

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ActionButtonCard(
                    title = "BG-DEXOPT JOB",
                    description = "Recompiles background app code. Takes a long time. Run once.",
                    iconKind = "beaker",
                    onClick = {
                        if (!bgDexoptLoading) {
                            bgDexoptLoading = true
                            runTweakCommands(
                                commands = listOf("cmd package bg-dexopt-job"),
                                onSuccess = { showTweakToast("BG-DEXOPT JOB COMPLETED") },
                                onFinish = { bgDexoptLoading = false }
                            )
                        }
                    },
                    loading = bgDexoptLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
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
                            val command = if (checked) {
                                "settings put global wifi_scan_always_enabled 0"
                            } else {
                                "settings put global wifi_scan_always_enabled 1"
                            }
                            runTweakCommands(
                                commands = listOf(command),
                                onSuccess = {
                                    wifiScanOptimized = checked
                                    prefs.edit().putBoolean("tweak_wifi_scan", checked).apply()
                                    showTweakToast(if (checked) "WI-FI SCAN OPTIMIZED" else "WI-FI SCAN RESTORED")
                                },
                                onFinish = { wifiScanLoading = false }
                            )
                        }
                    }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
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
fun HomeTabScreen(modifier: Modifier = Modifier) {
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
            HomeHeader(
                batteryPercent = batteryPercent,
                refreshing = refreshing,
                onRefresh = { refreshAll() },
                selectedPackage = selectedPackage
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
        if (selectedPackage.isNotBlank()) {
            item {
                val target = apps.firstOrNull { it.packageName == selectedPackage }
                TuningConsole(
                    packageName = selectedPackage,
                    appLabel = target?.label ?: selectedPackage,
                    appInfo = target?.info,
                    isGame = target?.info?.category == ApplicationInfo.CATEGORY_GAME
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
                    prefs.getInt("fps_max_$pkg", 0) > 0 ||
                    prefs.getBoolean("extreme_$pkg", false) ||
                    prefs.getBoolean("fps_boost_$pkg", false) ||
                    prefs.getBoolean("global_extreme_mode", false) ||
                    prefs.getBoolean("global_fps_boost", false)
                }
                InstalledAppRow(
                    app = app,
                    selected = app.packageName == selectedPackage,
                    favorite = app.packageName in favoritePackages,
                    isConfigured = isConfigured,
                    onSelect = {
                        TurboSpaceRepository.setSelectedGame(app.packageName, context)
                        scope.launch {
                            // The permission card is optional, so the tuning panel index can shift.
                            val tuningItemIndex = if (!hasShizuku || !hasWriteSettings || !hasBattery) 2 else 1
                            listState.animateScrollToItem(tuningItemIndex)
                        }
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
}

@Composable
private fun HomeHeader(
    batteryPercent: Int,
    refreshing: Boolean,
    selectedPackage: String,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(LaunchDashboardBackground).padding(horizontal = 16.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TURBOTUNE TOOLS", color = LaunchDashboardText, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text("SYSTEM PERFORMANCE CONSOLE", color = LaunchDashboardCyan, fontSize = 9.sp, letterSpacing = 1.1.sp)
            }
            MonetCanvasIcon("battery", Modifier.size(20.dp), LaunchDashboardCyan)
            Spacer(Modifier.width(5.dp))
            Text("$batteryPercent%", color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(38.dp).clip(CircleShape).background(LaunchDashboardSurface).clickable(enabled = !refreshing, onClick = onRefresh), contentAlignment = Alignment.Center) {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = LaunchDashboardCyan, strokeWidth = 2.dp)
                else MonetCanvasIcon("refresh", Modifier.size(20.dp))
            }
        }
        if (selectedPackage.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("TARGET: $selectedPackage", color = LaunchDashboardCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            .border(1.dp, if (isConfigured) Color(0xFFFFB6C1) else OutlineColor, RoundedCornerShape(12.dp))
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
private fun TuningConsole(
    packageName: String,
    appLabel: String,
    appInfo: ApplicationInfo?,
    isGame: Boolean
) {
    val context = LocalContext.current
    val prefs = remember(context) { TurboSpaceRepository.appPrefs(context) }
    val scope = rememberCoroutineScope()
    var appOptimizeLoading by remember(packageName) { mutableStateOf(false) }
    var appResetLoading by remember(packageName) { mutableStateOf(false) }
    var appOptimizeError by remember(packageName) { mutableStateOf(false) }
    var memoryLoading by remember { mutableStateOf(false) }
    var cacheLoading by remember { mutableStateOf(false) }
    var fpsDialog by remember { mutableStateOf(false) }
    var showGameMode by remember(packageName) { mutableStateOf(false) }
    var showFpsPerformance by remember(packageName) { mutableStateOf(false) }

    var extreme by remember { mutableStateOf(prefs.getBoolean("global_extreme_mode", false)) }
    var gamePerformance by remember(packageName) { mutableStateOf(prefs.getBoolean("game_performance_$packageName", false)) }
    var gameBattery by remember(packageName) { mutableStateOf(prefs.getBoolean("game_battery_$packageName", false)) }
    var gpuPerformance by remember(packageName) { mutableStateOf(prefs.getBoolean("gpu_performance_$packageName", false)) }
    var appOptimized by remember(packageName) { mutableStateOf(prefs.getBoolean("app_optimized_$packageName", false)) }
    var fpsBoost by remember { mutableStateOf(prefs.getBoolean("global_fps_boost", false)) }
    var selectedFps by remember(packageName) { mutableStateOf(prefs.getInt("fps_max_$packageName", 0)) }

    fun showResult(success: Boolean, title: String) {
        android.widget.Toast.makeText(context, if (success) "$title: DONE" else "$title: FAILED", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun runCommands(
        commands: List<String>,
        onSuccess: () -> Unit = {},
        onFinish: () -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                var ok = true
                for (command in commands) {
                    val r = TurboSpaceManager.executeCommandDetailedNoCtx(command)
                    if (!r.success || r.exitCode != 0) { ok = false; break }
                }
                ok
            }
            if (result) onSuccess() else {
                showResult(false, "SYSTEM COMMAND")
                onFailure()
            }
            onFinish()
        }
    }

    fun persist(key: String, value: Boolean) {
        prefs.edit().putBoolean("${key}_$packageName", value).apply()
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(16.dp))
            .background(LaunchDashboardSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val appIcon = remember(packageName, appInfo) {
                try { appInfo?.let { drawableToBitmap(context.packageManager.getApplicationIcon(it)) } } catch (_: Throwable) { null }
            }
            if (appIcon != null) Image(appIcon.asImageBitmap(), contentDescription = appLabel, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)))
            else MonetCanvasIcon("app", Modifier.size(38.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(appLabel, color = LaunchDashboardText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(packageName, color = MutedText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("TARGET", color = LaunchDashboardCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }

        // 3.1 Extreme Mode
        TuningToggleRow(
            title = "Extreme Mode",
            subtitle = "Applies a system-wide CPU/GPU performance policy.",
            checked = extreme,
            iconKind = "speedometer",
            onCheckedChange = { checked ->
                val command = "cmd device_config put activity_manager performance.mode_extreme_enabled ${if (checked) "true" else "false"}"
                runCommands(listOf(command), {
                    extreme = checked
                    prefs.edit().putBoolean("global_extreme_mode", checked).apply()
                    showResult(true, "EXTREME MODE")
                })
            }
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))

        // 3.2 App Details / Game Mode - shown only for games.
        if (isGame) {
            ExpanderCard(
                title = "Game Mode",
                expanded = showGameMode,
                onExpandedChange = { showGameMode = it },
                leadingContent = {
                    if (appInfo != null) AppIconImageRaw(packageName, appInfo, Modifier.size(25.dp))
                    else MonetCanvasIcon("app", Modifier.size(25.dp))
                }
            ) {
                TuningToggleRow(
                    title = "Performance Mode",
                    subtitle = "Boosts hardware resources for maximum FPS and smoother gameplay.",
                    checked = gamePerformance,
                    iconKind = "lightning",
                    onCheckedChange = { checked ->
                        val command = if (checked) "cmd game mode set performance $packageName" else "cmd game mode set standard $packageName"
                        runCommands(listOf(command), {
                            gamePerformance = checked
                            if (checked) {
                                gameBattery = false
                                persist("game_battery", false)
                            }
                            persist("game_performance", checked)
                            showResult(true, "PERFORMANCE MODE")
                        })
                    }
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))
                TuningToggleRow(
                    title = "Battery Mode",
                    subtitle = "Optimizes power limits to reduce heat and extend battery life.",
                    checked = gameBattery,
                    iconKind = "battery",
                    onCheckedChange = { checked ->
                        val command = if (checked) "cmd game mode set battery $packageName" else "cmd game mode set standard $packageName"
                        runCommands(listOf(command), {
                            gameBattery = checked
                            if (checked) {
                                gamePerformance = false
                                persist("game_performance", false)
                            }
                            persist("game_battery", checked)
                            showResult(true, "BATTERY MODE")
                        })
                    }
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))
        }

        // 3.3 GPU Performance - shown only for games.
        if (isGame) {
            TuningToggleRow(
                title = "GPU Performance",
                subtitle = "Slightly reduces rendering resolution to boost FPS without losing visual quality.",
                checked = gpuPerformance,
                iconKind = "gpu",
                onCheckedChange = { checked ->
                    val command = if (checked) "cmd game downscale 0.95 $packageName" else "cmd game downscale disable $packageName"
                    runCommands(listOf(command), {
                        gpuPerformance = checked
                        persist("gpu_performance", checked)
                        showResult(true, "GPU PERFORMANCE")
                    })
                }
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))
        }

        // 3.4 App Optimize
        Column(Modifier.fillMaxWidth()) {
            TuningToggleRow(
                title = "App Optimize",
                subtitle = "Re-compiles the app based on usage profiles for faster launch times.",
                checked = appOptimized,
                enabled = !appOptimizeLoading && !appResetLoading,
                loading = appOptimizeLoading,
                loadingLabel = "PLEASE WAIT",
                iconKind = "rocket",
                onCheckedChange = { checked ->
                    appOptimizeError = false
                    appOptimizeLoading = true
                    val command = if (checked) "cmd package compile -m speed -f $packageName" else "cmd package compile --reset $packageName"
                    runCommands(
                        commands = listOf(command),
                        onSuccess = {
                            appOptimized = checked
                            persist("app_optimized", checked)
                            appOptimizeError = false
                            showResult(true, "APP OPTIMIZE")
                        },
                        onFinish = { appOptimizeLoading = false },
                        onFailure = {
                            appOptimized = false
                            persist("app_optimized", false)
                            appOptimizeError = true
                        }
                    )
                }
            )
            if (appOptimizeError) {
                Text("ERROR", Modifier.fillMaxWidth(), color = ErrorColor, fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }

            // Show RESET only after a successful compile.
            if (appOptimized) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFEBEE))
                        .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(8.dp))
                        .clickable(enabled = !appResetLoading) {
                            appResetLoading = true
                            runCommands(
                                commands = listOf("cmd package compile --reset $packageName"),
                                onSuccess = {
                                    appOptimized = false
                                    persist("app_optimized", false)
                                    showResult(true, "COMPILE RESET SUCCESS")
                                },
                                onFinish = { appResetLoading = false },
                                onFailure = {
                                    showResult(false, "COMPILE RESET FAILED")
                                }
                            )
                        }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (appResetLoading) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = ErrorColor, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("RESETTING...", color = ErrorColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    } else {
                        MonetCanvasIcon("refresh", Modifier.size(16.dp), ErrorColor)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("RESET COMPILE", color = ErrorColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text("This feature will revert the app back to normal (not speed mode). Uses system detection. If successful, it will show SUCCESS.", color = MutedText, fontSize = 9.sp, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))

        // 3.5 FPS Performance
        ExpanderCard(
            title = "FPS Performance",
            expanded = showFpsPerformance,
            onExpandedChange = { showFpsPerformance = it },
            iconKind = "fps"
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { fpsDialog = true }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonetCanvasIcon("fps", Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("FPS Max Optimizer", color = LaunchDashboardText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Locks the display refresh rate to your preferred level for smoothness or battery saving.",
                        color = MutedText,
                        fontSize = 10.sp
                    )
                }
                Text(
                    if (selectedFps > 0) "${selectedFps}Hz" else "SELECT",
                    color = LaunchDashboardCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
            TuningToggleRow(
                title = "FPS Boost",
                subtitle = "Applies FPS framework configuration system-wide.",
                checked = fpsBoost,
                iconKind = "lightning",
                onCheckedChange = { checked ->
                    val commands = if (checked) listOf(
                        "cmd device_config put activity_manager high_fps_config on",
                        "cmd device_config put activity_manager fpsbooster_enable 1"
                    ) else listOf(
                        "cmd device_config delete activity_manager high_fps_config",
                        "cmd device_config delete activity_manager fpsbooster_enable"
                    )
                    runCommands(commands, {
                        fpsBoost = checked
                        prefs.edit().putBoolean("global_fps_boost", checked).apply()
                        showResult(true, "FPS BOOST")
                    })
                }
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))

        // 3.6 Memory Flush and 3.7 Cache Purge
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButtonCard(
                title = "Memory Flush",
                description = "Force-closes non-essential background apps to free up maximum RAM.",
                iconKind = "ram_clear",
                onClick = {
                    if (!memoryLoading) {
                        memoryLoading = true
                        runCommands(listOf("am kill-all"),
                            onSuccess = { showResult(true, "MEMORY FLUSH") },
                            onFinish = { memoryLoading = false })
                    }
                },
                loading = memoryLoading,
                modifier = Modifier.weight(1f)
            )
            ActionButtonCard(
                title = "Cache Purge",
                description = "Trims and flushes system-wide temporary cache files to reclaim storage.",
                iconKind = "broom",
                onClick = {
                    if (!cacheLoading) {
                        cacheLoading = true
                        runCommands(listOf("pm trim-caches 999G"),
                            onSuccess = { showResult(true, "CACHE PURGE") },
                            onFinish = { cacheLoading = false })
                    }
                },
                loading = cacheLoading,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (fpsDialog) {
        val choices = supportedFpsChoices(context)
        AlertDialog(
            onDismissRequest = { fpsDialog = false },
            containerColor = LaunchDashboardSurface,
            title = { Text("FPS MAX OPTIMIZER", color = LaunchDashboardCyan, fontWeight = FontWeight.Black, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select a refresh rate supported by this display.", color = LaunchDashboardText, fontSize = 12.sp)
                    choices.forEach { hz ->
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(LaunchDashboardBackground)
                            .clickable {
                                fpsDialog = false
                                runCommands(
                                    listOf(
                                        "settings put system peak_refresh_rate ${hz}.0",
                                        "settings put system min_refresh_rate ${hz}.0"
                                    ),
                                    onSuccess = {
                                        selectedFps = hz
                                        prefs.edit().putInt("fps_max_$packageName", hz).apply()
                                        showResult(true, "FPS MAX $hz Hz")
                                    }
                                )
                            }.padding(12.dp)) {
                            Text("$hz Hz", color = LaunchDashboardText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { fpsDialog = false }) { Text("CLOSE", color = LaunchDashboardCyan) } }
        )
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
