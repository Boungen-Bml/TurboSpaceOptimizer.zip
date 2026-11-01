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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.animateItemPlacement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
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
// CYBER-TUNER THEME
// -----------------------------------------------------------------------------
val LaunchDashboardBackground = Color(0xFF121212)
val LaunchDashboardText = Color(0xFFE0E0E0)
val LaunchDashboardCyan = Color(0xFF00E5FF)
val LaunchDashboardSurface = Color(0xFF1E1E1E)
private val MutedText = Color(0xFF9E9E9E)
private val OutlineColor = Color(0xFF343434)
private val SuccessColor = Color(0xFF70D6A5)
private val SystemGreen = Color(0xFF00E676)
private val ErrorColor = Color(0xFFFF7272)

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
@Suppress("UNUSED_PARAMETER")
fun performFullReset(context: Context): Boolean {
    val resetCommands = listOf(
        "cmd power set-adaptive-power-saver-enabled false",
        "settings put system accelerometer_rotation 1",
        "settings put global wifi_scan_always_enabled 1",
        "cmd deviceidle whitelist -com.turbospace.optimizer"
    )
    return try {
        for (command in resetCommands) {
            val result = TurboSpaceManager.executeCommandDetailedNoCtx(command)
            if (!result.success) return false
        }
        true
    } catch (_: Throwable) {
        false
    }
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
            "refresh" -> {
                drawArc(tint, 35f, 285f, false, Offset(w * .16f, h * .16f), Size(w * .68f, h * .68f), style = stroke)
                drawLine(tint, Offset(w * .70f, h * .12f), Offset(w * .86f, h * .18f), sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .86f, h * .18f), Offset(w * .81f, h * .34f), sw, cap = StrokeCap.Round)
            }
            "home" -> {
                val p = Path().apply { moveTo(w*.12f,h*.46f); lineTo(cx,h*.14f); lineTo(w*.88f,h*.46f); lineTo(w*.78f,h*.46f); lineTo(w*.78f,h*.86f); lineTo(w*.56f,h*.86f); lineTo(w*.56f,h*.61f); lineTo(w*.40f,h*.61f); lineTo(w*.40f,h*.86f); lineTo(w*.22f,h*.86f); lineTo(w*.22f,h*.46f); close() }
                drawPath(p, tint, style = stroke)
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
                // วาดหลอดทดลอง
                val path = Path().apply {
                    moveTo(w * 0.35f, h * 0.15f)
                    lineTo(w * 0.65f, h * 0.15f)
                    lineTo(w * 0.65f, h * 0.45f)
                    lineTo(w * 0.80f, h * 0.80f)
                    cubicTo(w * 0.82f, h * 0.85f, w * 0.75f, h * 0.90f, w * 0.65f, h * 0.90f)
                    lineTo(w * 0.35f, h * 0.90f)
                    cubicTo(w * 0.25f, h * 0.90f, w * 0.18f, h * 0.85f, w * 0.20f, h * 0.80f)
                    lineTo(w * 0.35f, h * 0.45f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                // วาดฟองก๊าซ
                drawCircle(tint, sw * 1.2f, Offset(w * 0.45f, h * 0.75f))
                drawCircle(tint, sw * 0.8f, Offset(w * 0.55f, h * 0.65f))
                drawCircle(tint, sw * 1.0f, Offset(w * 0.50f, h * 0.85f))
                // ขีดบอกระดับ
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
                    moveTo(w * .27f, h * .12f)
                    lineTo(w * .73f, h * .12f)
                    lineTo(w * .73f, h * .88f)
                    lineTo(cx, h * .68f)
                    lineTo(w * .27f, h * .88f)
                    close()
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
    loading: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LaunchDashboardText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MutedText, fontSize = 11.sp)
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = LaunchDashboardCyan, strokeWidth = 2.dp)
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
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(LaunchDashboardSurface)
            .border(1.dp, OutlineColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clickable { onExpandedChange(!expanded) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, Modifier.weight(1f), color = LaunchDashboardText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(if (expanded) "−" else "+", color = LaunchDashboardCyan, fontSize = 24.sp)
        }
        if (expanded) Column(Modifier.fillMaxWidth(), content = content)
    }
}


// -----------------------------------------------------------------------------
// APPLICATION ROOT AND BOTTOM NAVIGATION
// -----------------------------------------------------------------------------
private enum class AppTab(val label: String, val icon: String) {
    HOME("HOME", "home"),
    SYSTEM("SYSTEM", "system"),
    TWEAKS("TWEAKS", "beaker"),
    SETTINGS("SETTINGS", "gear")
}

@Composable
fun TurboSpaceAppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("turbospace_prefs", Context.MODE_PRIVATE) }
    var isPolicyAccepted by remember {
        mutableStateOf(prefs.getBoolean("policy_accepted", false))
    }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    val scheme = androidx.compose.material3.darkColorScheme(
        primary = LaunchDashboardCyan,
        onPrimary = LaunchDashboardBackground,
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
                    NavigationBar(containerColor = LaunchDashboardBackground, contentColor = LaunchDashboardText) {
                        AppTab.values().forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    MonetCanvasIcon(
                                        tab.icon,
                                        Modifier.size(22.dp),
                                        if (selected) LaunchDashboardCyan else MutedText
                                    )
                                },
                                label = { Text(tab.label, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = LaunchDashboardCyan,
                                    selectedTextColor = LaunchDashboardCyan,
                                    indicatorColor = LaunchDashboardCyan.copy(alpha = .12f),
                                    unselectedIconColor = MutedText,
                                    unselectedTextColor = MutedText
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                when (selectedTab) {
                    AppTab.HOME -> HomeTabScreen(Modifier.padding(padding))
                    AppTab.SYSTEM -> SystemTabScreen(Modifier.padding(padding))
                    AppTab.TWEAKS -> SystemTweaksScreen(Modifier.padding(padding))
                    AppTab.SETTINGS -> SettingsTabScreen(Modifier.padding(padding))
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
        Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, LaunchDashboardCyan.copy(alpha = .55f), RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "WARNING & TERMS",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(LaunchDashboardCyan))
            Text(
                warningText,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF424242))
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CANCEL", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp))
                        .background(LaunchDashboardCyan)
                        .border(4.dp, LaunchDashboardCyan.copy(alpha = .22f), RoundedCornerShape(10.dp))
                        .border(1.5.dp, LaunchDashboardCyan, RoundedCornerShape(10.dp))
                        .clickable(onClick = onAccept),
                    contentAlignment = Alignment.Center
                ) {
                    Text("I ACCEPT", color = LaunchDashboardBackground, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun SettingsTabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                .background(Color(0xFF331111))
                .border(1.dp, Color(0xFF7D2929), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "DANGER ZONE",
                color = Color(0xFFFF8A80),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp
            )
            Text(
                "RESTORE SYSTEM DEFAULTS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Undo selected system changes made by this app. Shizuku permission is required.",
                color = Color(0xFFCCAAAA),
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
                "TURBO TUNE",
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
                    Text("CONFIRM", color = Color(0xFFFF7272), fontWeight = FontWeight.Black)
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
    var fastUiEnabled by remember(prefs) {
        mutableStateOf(prefs.getBoolean("tweak_fast_ui", false))
    }
    var bgDexoptLoading by remember { mutableStateOf(false) }
    var wifiScanLoading by remember { mutableStateOf(false) }
    var fastUiLoading by remember { mutableStateOf(false) }

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
                Text(
                    "SYSTEM TWEAKS",
                    color = LaunchDashboardCyan,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
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

        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
                    .background(LaunchDashboardSurface)
                    .border(1.dp, OutlineColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                TuningToggleRow(
                    title = "FAST UI ANIMATIONS",
                    subtitle = "Set system animation scales to 0.5x for snappier UI.",
                    checked = fastUiEnabled,
                    enabled = !fastUiLoading,
                    loading = fastUiLoading,
                    onCheckedChange = { checked ->
                        if (!fastUiLoading) {
                            fastUiLoading = true
                            val scale = if (checked) "0.5" else "1.0"
                            val commands = listOf(
                                "settings put global window_animation_scale $scale",
                                "settings put global transition_animation_scale $scale",
                                "settings put global animator_duration_scale $scale"
                            )
                            runTweakCommands(
                                commands = commands,
                                onSuccess = {
                                    fastUiEnabled = checked
                                    prefs.edit().putBoolean("tweak_fast_ui", checked).apply()
                                    showTweakToast(if (checked) "FAST UI ANIMATIONS ENABLED" else "UI ANIMATIONS RESTORED")
                                },
                                onFinish = { fastUiLoading = false }
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
                            Column(
                                Modifier.weight(1f).height(190.dp),
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
                    modifier = Modifier.animateItemPlacement(),
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
            if (killing) CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("KILL", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
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
    val prefs = remember(context) { TurboSpaceRepository.appPrefs(context) }
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

    LaunchedEffect(Unit) { refreshAll() }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps else apps.filter {
            it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true)
        }
    }

    LazyColumn(
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
                    appInfo = target?.info
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
                InstalledAppRow(
                    app = app,
                    selected = app.packageName == selectedPackage,
                    favorite = app.packageName in favoritePackages,
                    onSelect = { TurboSpaceRepository.setSelectedGame(app.packageName, context) },
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
            MonetCanvasIcon("speedometer", Modifier.size(38.dp), LaunchDashboardCyan)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("TURBO TUNE", color = LaunchDashboardText, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
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
                Text("ALLOW", color = LaunchDashboardBackground, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledAppItem,
    selected: Boolean,
    favorite: Boolean,
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
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) LaunchDashboardCyan.copy(alpha = .10f) else LaunchDashboardSurface)
            .border(1.dp, if (selected) LaunchDashboardCyan else OutlineColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)))
        else Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(LaunchDashboardBackground), contentAlignment = Alignment.Center) {
            MonetCanvasIcon("app", Modifier.size(22.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = LaunchDashboardText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun TuningConsole(packageName: String, appLabel: String, appInfo: ApplicationInfo?) {
    val context = LocalContext.current
    val prefs = remember(context) { TurboSpaceRepository.appPrefs(context) }
    val scope = rememberCoroutineScope()
    var appOptimizeLoading by remember(packageName) { mutableStateOf(false) }
    var memoryLoading by remember { mutableStateOf(false) }
    var cacheLoading by remember { mutableStateOf(false) }
    var fpsDialog by remember { mutableStateOf(false) }
    var showGameMode by remember(packageName) { mutableStateOf(false) }
    var showFpsPerformance by remember(packageName) { mutableStateOf(false) }

    var extreme by remember(packageName) { mutableStateOf(prefs.getBoolean("extreme_$packageName", false)) }
    var gamePerformance by remember(packageName) { mutableStateOf(prefs.getBoolean("game_performance_$packageName", false)) }
    var gameBattery by remember(packageName) { mutableStateOf(prefs.getBoolean("game_battery_$packageName", false)) }
    var gpuPerformance by remember(packageName) { mutableStateOf(prefs.getBoolean("gpu_performance_$packageName", false)) }
    var appOptimized by remember(packageName) { mutableStateOf(prefs.getBoolean("app_optimized_$packageName", false)) }
    var fpsBoost by remember(packageName) { mutableStateOf(prefs.getBoolean("fps_boost_$packageName", false)) }

    fun showResult(success: Boolean, title: String) {
        android.widget.Toast.makeText(context, if (success) "$title: DONE" else "$title: FAILED", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun runCommands(commands: List<String>, onSuccess: () -> Unit = {}, onFinish: () -> Unit = {}) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                var ok = true
                for (command in commands) {
                    val r = TurboSpaceManager.executeCommandDetailedNoCtx(command)
                    if (!r.success || r.exitCode != 0) { ok = false; break }
                }
                ok
            }
            if (result) onSuccess() else showResult(false, "SYSTEM COMMAND")
            onFinish()
        }
    }

    fun persist(key: String, value: Boolean) {
        prefs.edit().putBoolean("${key}_$packageName", value).apply()
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
            .background(LaunchDashboardSurface).border(1.dp, LaunchDashboardCyan.copy(alpha = .65f), RoundedCornerShape(16.dp)).padding(14.dp),
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
        TuningToggleRow("EXTREME MODE", "System activity manager performance mode", extreme, { checked ->
            val command = "cmd device_config put activity_manager performance.mode_extreme_enabled ${if (checked) "true" else "false"}"
            runCommands(listOf(command), {
                extreme = checked
                persist("extreme", checked)
                showResult(true, "EXTREME MODE")
            })
        })

        ExpanderCard("GAME MODE", showGameMode, { showGameMode = it }) {
            TuningToggleRow("PERFORMANCE", "Prioritize app performance", gamePerformance, { checked ->
                val command = if (checked) "cmd game mode set performance $packageName" else "cmd game mode set standard $packageName"
                runCommands(listOf(command), {
                    gamePerformance = checked
                    if (checked) { gameBattery = false; persist("game_battery", false) }
                    persist("game_performance", checked)
                    showResult(true, "GAME MODE")
                })
            })
            TuningToggleRow("BATTERY", "Prioritize power efficiency", gameBattery, { checked ->
                val command = if (checked) "cmd game mode set battery $packageName" else "cmd game mode set standard $packageName"
                runCommands(listOf(command), {
                    gameBattery = checked
                    if (checked) { gamePerformance = false; persist("game_performance", false) }
                    persist("game_battery", checked)
                    showResult(true, "GAME MODE")
                })
            })
        }

        TuningToggleRow("GPU PERFORMANCE", "Apply 0.95 game downscale profile", gpuPerformance, { checked ->
            val command = if (checked) "cmd game downscale 0.95 $packageName" else "cmd game downscale disable $packageName"
            runCommands(listOf(command), {
                gpuPerformance = checked
                persist("gpu_performance", checked)
                showResult(true, "GPU PERFORMANCE")
            })
        })

        TuningToggleRow("APP OPTIMIZE", "Compile app for speed", appOptimized, { checked ->
            appOptimizeLoading = true
            val command = if (checked) "cmd package compile -m speed -f $packageName" else "cmd package compile --reset $packageName"
            runCommands(listOf(command), {
                appOptimized = checked
                persist("app_optimized", checked)
                showResult(true, "APP OPTIMIZE")
            }, { appOptimizeLoading = false })
        }, loading = appOptimizeLoading)

        ExpanderCard("FPS PERFORMANCE", showFpsPerformance, { showFpsPerformance = it }) {
            Row(Modifier.fillMaxWidth().clickable { fpsDialog = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("FPS MAX OPTIMIZER", color = LaunchDashboardText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Choose a supported display refresh rate", color = MutedText, fontSize = 10.sp)
                }
                Text("OPEN", color = LaunchDashboardCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            TuningToggleRow("FPS BOOST", "Enable high FPS configuration", fpsBoost, { checked ->
                val commands = if (checked) listOf(
                    "cmd device_config put activity_manager high_fps_config on",
                    "settings put global fpsbooster_enable 1"
                ) else listOf(
                    "cmd device_config put activity_manager high_fps_config off",
                    "settings delete global fpsbooster_enable"
                )
                runCommands(commands, {
                    fpsBoost = checked
                    persist("fps_boost", checked)
                    showResult(true, "FPS BOOST")
                })
            })
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButtonCard("MEMORY FLUSH", "Stop cached background processes", "memory", onClick = {
                memoryLoading = true
                runCommands(listOf("am kill-all"), { showResult(true, "MEMORY FLUSH") }, { memoryLoading = false })
            }, loading = memoryLoading, modifier = Modifier.weight(1f))
            ActionButtonCard("CACHE PURGE", "Trim unused app cache", "cache", onClick = {
                cacheLoading = true
                runCommands(listOf("pm trim-caches 999G"), { showResult(true, "CACHE PURGE") }, { cacheLoading = false })
            }, loading = cacheLoading, modifier = Modifier.weight(1f))
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
                                    onSuccess = { showResult(true, "FPS MAX $hz Hz") }
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
        modifier.clip(RoundedCornerShape(14.dp)).background(LaunchDashboardSurface)
            .border(1.dp, OutlineColor, RoundedCornerShape(14.dp)).clickable(enabled = !loading, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonetCanvasIcon(iconKind, Modifier.size(25.dp))
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = LaunchDashboardCyan, strokeWidth = 2.dp)
        }
        Text(title, color = LaunchDashboardText, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        Text(description, color = MutedText, fontSize = 9.sp, minLines = 2)
        Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(7.dp)).background(LaunchDashboardCyan), contentAlignment = Alignment.Center) {
            Text(if (loading) "RUNNING" else "START", color = LaunchDashboardBackground, fontSize = 10.sp, fontWeight = FontWeight.Black)
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
