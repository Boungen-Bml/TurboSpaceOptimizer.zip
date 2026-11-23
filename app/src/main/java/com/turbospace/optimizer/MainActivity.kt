package com.turbospace.optimizer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

// ═══ 1. CONSTANTS ═══
private const val SHIZUKU_REQUEST_CODE = 1000
private const val PROCESS_TIMEOUT_MS = 2000L
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SELF_PACKAGE = "com.turbospace.optimizer"
private const val PREFS_NAME = "turbospace_prefs"
private const val SWIPE_THRESHOLD_DP = 80
private const val COMPILE_TIMEOUT_MS = 300_000L   // cmd package compile ใช้เวลานานกว่า 2 วินาทีมาก
private const val ICON_COMPARE_PX = 48

// ═══ 2. THEME (Emerald) ═══
private val EmeraldPrimary = Color(0xFF0EA5E9)   // Sky Blue 500
private val SuccessGreen = Color(0xFF386A20)     // semantic success green (status badges) - unchanged
private val EmeraldBackground = Color(0xFFFFFFFF)
private val EmeraldSurface = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1C19)
private val MutedText = Color(0xFF64748B)
private val OutlineColor = Color(0xFFCBD5E1)
private val ErrorColor = Color(0xFFB3261E)
private val WarnAmber = Color(0xFFF9A825)
private val RowGreenBg = Color(0xFFE8F5E9)
private val RowRedBg = Color(0xFFFFEBEE)
private val RowGrayBg = Color(0xFFEEEEEE)
private val RowYellowBg = Color(0xFFFFF8E1)

private val EmeraldColors = lightColorScheme(
    primary = Color(0xFF0EA5E9),           // Sky Blue 500
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF0284C7),         // Sky Blue 600
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

private val DarkEmeraldColors = darkColorScheme(
    primary = Color(0xFF38BDF8),           // Sky Blue 400
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF075985),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF7DD3FC),         // Sky Blue 300
    onSecondary = Color(0xFF0F172A),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF0F172A)
)

// ═══ 3. DATA ═══
data class CommandResult(
    val success: Boolean,
    val output: String,
    val error: String?,
    val exitCode: Int?
)

data class BackgroundApp(
    val info: ApplicationInfo,
    val packageName: String,
    val label: String,
    val ramMb: Long,
    val isRunning: Boolean,
    val isSystem: Boolean,
    val safetyLevel: SafetyLevel
)

enum class SafetyLevel { SAFE, MEDIUM, DANGEROUS }
enum class AppFilter { ALL, SYSTEM_ONLY, DOWNLOADED_ONLY }
data class AppInfoItem(
    val info: ApplicationInfo,
    val packageName: String,
    val label: String,
    val isBloatware: Boolean
)

enum class TuningFilter { ALL, NO_SYSTEM, NO_DOWNLOADED }
enum class TuningBucketState { NORMAL, INACTIVE, ACTIVE }

enum class AppTheme(val label: String) {
    SYSTEM("ตามระบบ"),
    DARK("สีดำ"),
    LIGHT("สีสว่าง")
}

enum class CompileMode(val cmd: String, val label: String) {
    SPEED("speed", "Compile Speed"),
    SPEED_PROFILE("speed-profile", "Compile Speed Profile"),
    EVERYTHING("everything", "Compile Everything")
}

private enum class AppScreen { HOME, APP_MANAGEMENT, BACKGROUND_APPS, APP_TUNING, STATUS_MONITOR, PROGRAMMER_MODE, SETTINGS }

// ═══ 4. SHIZUKU MANAGER — จุดเดียวที่คุยกับ Shizuku ═══
object ShizukuManager {

    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
    }

    /** เปิดแอป Shizuku — คืน false ถ้ายังไม่ได้ติดตั้ง */
    fun openShizukuApp(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /**
     * รันคำสั่ง `sh -c <cmd>` ผ่าน Shizuku บน Dispatchers.IO
     * Shizuku API 13 ทำให้ newProcess เป็น private จึงต้องเรียกผ่าน reflection
     */
    suspend fun executeCommand(cmd: String, timeoutMs: Long = PROCESS_TIMEOUT_MS): CommandResult =
        withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext CommandResult(false, "", "Shizuku permission not granted", null)
        }
        var process: Process? = null
        try {
            val newProcess = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcess.isAccessible = true
            val proc = newProcess.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            process = proc

            val out = StringBuffer()
            val err = StringBuffer()
            val outThread = thread(isDaemon = true) {
                runCatching { proc.inputStream.bufferedReader().use { out.append(it.readText()) } }
            }
            val errThread = thread(isDaemon = true) {
                runCatching { proc.errorStream.bufferedReader().use { err.append(it.readText()) } }
            }
            outThread.join(timeoutMs)
            errThread.join(timeoutMs)

            if (outThread.isAlive || errThread.isAlive) {
                return@withContext CommandResult(
                    false, out.toString().trim(), "Timed out after $timeoutMs ms", null
                )
            }
            val exit = if (proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) proc.exitValue() else null
            CommandResult(exit == 0, out.toString().trim(), err.toString().trim().ifEmpty { null }, exit)
        } catch (e: InvocationTargetException) {
            CommandResult(false, "", (e.targetException ?: e).message ?: "newProcess failed", null)
        } catch (e: Throwable) {
            CommandResult(false, "", e.message ?: e.javaClass.simpleName, null)
        } finally {
            runCatching { process?.destroy() }
        }
    }
    // ───── แอปเบื้องหลัง ─────

    /** แอปที่มี process ทำงานอยู่ + RAM (RSS) จาก `ps -A -o NAME,RSS` */
    @Suppress("DEPRECATION")
    suspend fun getBackgroundApps(context: Context): List<BackgroundApp> =
        withContext(Dispatchers.IO) {
            val result = executeCommand("ps -A -o NAME,RSS")
            if (!result.success) return@withContext emptyList()

            val pm = context.packageManager
            val rssMap = mutableMapOf<String, Long>()  // package → total KB

            // บรรทัดแรกเป็น header ข้ามไป
            result.output.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("NAME", ignoreCase = true)) return@forEach
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size < 2) return@forEach
                val rss = parts[1].toLongOrNull() ?: return@forEach
                if (rss <= 0) return@forEach
                val pkg = parts[0].substringBefore(':')   // ตัด suffix ":xxx" ออก
                rssMap[pkg] = (rssMap[pkg] ?: 0L) + rss
            }

            rssMap.mapNotNull { (pkg, rssKb) ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                    ?: return@mapNotNull null
                val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                BackgroundApp(info, pkg, label, rssKb / 1024, true, isSystem, calculateSafety(info))
            }.sortedByDescending { it.ramMb }
        }

    fun calculateSafety(info: ApplicationInfo): SafetyLevel {
        val pkg = info.packageName
        val critical = listOf(
            "com.android.", "com.google.", "com.qualcomm.", "com.mediatek.",
            "com.samsung.", "com.miui.", "com.oplus.", "com.coloros.",
            "com.vivo.", "com.huawei.", "com.realme.", "android"
        )
        if (critical.any { pkg.startsWith(it) }) return SafetyLevel.DANGEROUS
        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return SafetyLevel.MEDIUM
        return SafetyLevel.SAFE
    }

    /** บังคับหยุดแอป (ป้องกันตัวแอปเองและ "android") */
    suspend fun forceStopApp(pkg: String): CommandResult {
        if (pkg == SELF_PACKAGE || pkg == "android") {
            return CommandResult(false, "", "Protected app", null)
        }
        return executeCommand("am force-stop $pkg")
    }

    // ───── ปรับแต่งระบบ (App Tuning) ─────

    private val PACKAGE_NAME_REGEX = Regex("""^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*$""")
    private val FAILURE_PREFIX_REGEX = Regex("^(Failure|Error|Exception|Unknown)", RegexOption.IGNORE_CASE)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadThemeMode(context: Context): AppTheme {
        val s = prefs(context).getString("app_theme_mode", "SYSTEM") ?: "SYSTEM"
        return runCatching { AppTheme.valueOf(s) }.getOrDefault(AppTheme.SYSTEM)
    }

    fun saveThemeMode(context: Context, mode: AppTheme) {
        prefs(context).edit().putString("app_theme_mode", mode.name).apply()
    }

    /** รันคำสั่งปรับแต่งของแอปหนึ่งตัว (ป้องกันตัวแอปเองและ "android") */
    suspend fun runTuningCommand(
        pkg: String,
        command: String,
        timeoutMs: Long = PROCESS_TIMEOUT_MS
    ): CommandResult {
        if (pkg == SELF_PACKAGE || pkg == "android") {
            return CommandResult(false, "", "Protected app", null)
        }
        if (!PACKAGE_NAME_REGEX.matches(pkg)) {
            return CommandResult(false, "", "Invalid package name", null)
        }
        val result = executeCommand(command, timeoutMs)
        // บางคำสั่งพิมพ์ "Failure"/"Error" แต่ exit 0 → ถือว่าล้มเหลว
        val firstLine = result.output.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (result.success && FAILURE_PREFIX_REGEX.containsMatchIn(firstLine)) {
            result.copy(success = false, error = result.output)
        } else {
            result
        }
    }

    fun loadCompileMode(context: Context, pkg: String): CompileMode? {
        val value = prefs(context).getString("tuning_compile_$pkg", null) ?: return null
        return runCatching { CompileMode.valueOf(value) }.getOrNull()
    }

    fun saveCompileMode(context: Context, pkg: String, mode: CompileMode?) {
        val editor = prefs(context).edit()
        if (mode == null) editor.remove("tuning_compile_$pkg")
        else editor.putString("tuning_compile_$pkg", mode.name)
        editor.apply()
    }

    fun loadTuningBucket(context: Context, pkg: String): String {
        return prefs(context).getString("tuning_bucket_$pkg", "NORMAL") ?: "NORMAL"
    }

    fun saveTuningBucket(context: Context, pkg: String, value: String) {
        prefs(context).edit().putString("tuning_bucket_$pkg", value).apply()
    }

    // ── Deep Detection (AppOps) ──

    /** อ่าน permission ที่แอปขอใน manifest */
    @Suppress("DEPRECATION")
    private fun getRequestedPermissions(context: Context, pkg: String): Set<String> {
        return try {
            context.packageManager
                .getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.toSet() ?: emptySet()
        } catch (_: Throwable) { emptySet() }
    }

    /**
     * อ่านสถานะปัจจุบันของแต่ละ AppOp จากระบบ
     * คำสั่ง: cmd appops get <pkg>
     * Output ตัวอย่าง:
     *   Uid mode: WAKE_LOCK: ignore
     *   RUN_IN_BACKGROUND: allow
     * คืน Map<opName, isAllowed> — op ที่ไม่มีในผลลัพธ์ = default allow
     */
    suspend fun readCurrentAppOpStates(pkg: String): Map<String, Boolean> {
        val result = executeCommand("cmd appops get $pkg")
        if (!result.success) return emptyMap()
        val states = mutableMapOf<String, Boolean>()
        val regex = Regex("""([A-Z_]{3,}):\s*(\w+)""")
        val offModes = setOf("ignore", "deny", "errored")
        result.output.lineSequence().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val op = match.groupValues[1]
                val mode = match.groupValues[2].lowercase()
                // ignore / deny / errored = ปิดอยู่
                states[op] = mode !in offModes
            }
        }
        return states
    }

    /**
     * Deep Detection: คืนรายการ op ที่แอปนั้นรองรับ + สถานะปัจจุบัน
     * - only show op ที่แอปมี permission จริง
     * - currentEnabled = อ่านจากระบบ (ถ้ามีใน states) หรือ default true
     */
    suspend fun getAvailableAppOps(
        context: Context,
        pkg: String
    ): List<AppOpState> {
        val requested = getRequestedPermissions(context, pkg)
        val currentStates = readCurrentAppOpStates(pkg)

        return APP_OPS_TABLE.mapNotNull { def ->
            // Deep Detection: ต้องมีอย่างน้อย 1 permission ใน requiredPermissions
            val hasPermission = def.requiredPermissions.any { it in requested }
            if (!hasPermission) return@mapNotNull null
            if (def.opName == "READ_CLIPBOARD" && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@mapNotNull null
            }

            // อ่านสถานะปัจจุบัน - default = true (อนุญาต) ถ้าไม่มีในรายงาน
            val currentEnabled = currentStates[def.opName] ?: true

            AppOpState(
                opName = def.opName,
                allowDesc = def.allowDesc,
                ignoreDesc = def.ignoreDesc,
                available = true,
                currentEnabled = currentEnabled
            )
        }
    }

    // ── Status Monitor ──

    fun loadStatusToggle(context: Context, key: String, default: Boolean): Boolean =
        prefs(context).getBoolean("status_toggle_$key", default)

    fun saveStatusToggle(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("status_toggle_$key", enabled).apply()
    }

    // ── Programmer Mode ──

    /** ตรวจ refresh rates ที่จอรองรับ (สำหรับ FPS preset) */
    @Suppress("DEPRECATION")
    fun getSupportedRefreshRates(context: Context): List<Int> {
        return try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            }
            val rates = display?.supportedModes?.map { it.refreshRate.roundToInt() }?.distinct()?.sorted()
                ?: listOf(60)
            // กรองเฉพาะ 60, 90, 120 ที่มีจริง
            listOf(60, 90, 120).filter { preset ->
                rates.any { abs(it - preset) <= 5 }
            }.ifEmpty { listOf(60) }
        } catch (_: Throwable) { listOf(60) }
    }

    /** เปิดเกมที่เลือก */
    fun launchApp(context: Context, pkg: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Throwable) { false }
    }
}

// ═══ 4.1 TURBOSPACE MANAGER — รายการแอปที่แสดงได้ (ไม่คุยกับ Shizuku) ═══
object TurboSpaceManager {

    private val PROTECTED_PREFIXES = listOf(
        "com.android.", "com.google.", "com.qualcomm.", "com.mediatek.",
        "com.oplus.", "com.coloros.", "com.vivo.", "com.miui.", "com.xiaomi.",
        "com.samsung.", "com.huawei."
    )

    // "ad" / "ads" สั้นเกินไปสำหรับ substring (เช่น "download", "adobe") → จับเฉพาะ segment เต็ม
    private val AD_SEGMENT_KEYWORDS = setOf("ad", "ads")
    private val AD_SUBSTRING_KEYWORDS = listOf(
        "advert", "promo", "sponsor", "mopub", "applovin", "unityads",
        "facebook.katana", "gms.ads", "bytedance", "pangle"
    )

    private val frameworkDefaultIcon: Bitmap by lazy {
        @Suppress("DEPRECATION")
        val drawable = Resources.getSystem().getDrawable(android.R.drawable.sym_def_app_icon, null)
        renderIconBitmap(drawable, ICON_COMPARE_PX)
    }

    fun isProtectedSystemApp(pkg: String): Boolean =
        pkg == "android" || pkg.startsWith("android.") || PROTECTED_PREFIXES.any { pkg.startsWith(it) }

    /** ไอคอน launcher ของแอป (ถ้าไม่มีใช้ไอคอนของ application) */
    fun loadAppIcon(context: Context, info: ApplicationInfo): Drawable {
        val pm = context.packageManager
        val launcherIcon = runCatching {
            pm.getLaunchIntentForPackage(info.packageName)?.let { pm.getActivityIcon(it) }
        }.getOrNull()
        return launcherIcon ?: pm.getApplicationIcon(info)
    }

    fun renderIconBitmap(drawable: Drawable, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(android.graphics.Canvas(bmp))
        return bmp
    }

    /** เทียบ bitmap ของไอคอนกับ android.R.drawable.sym_def_app_icon (หุ่นยนต์เขียว) */
    fun isDefaultAndroidIcon(context: Context, info: ApplicationInfo): Boolean = try {
        renderIconBitmap(loadAppIcon(context, info), ICON_COMPARE_PX).sameAs(frameworkDefaultIcon)
    } catch (t: Throwable) {
        false
    }

    private fun isBloatwarePackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (lower.split('.').any { it in AD_SEGMENT_KEYWORDS }) return true
        return AD_SUBSTRING_KEYWORDS.any { lower.contains(it) }
    }

    @Suppress("DEPRECATION")
    private fun installedApplications(pm: PackageManager): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            pm.getInstalledApplications(0)
        }

    /** เรียกจาก background thread เท่านั้น (ต้องโหลดไอคอนทุกแอป) */
    fun getFilteredApps(context: Context): List<AppInfoItem> {
        val pm = context.packageManager
        val result = ArrayList<AppInfoItem>()
        for (info in installedApplications(pm)) {
            val pkg = info.packageName
            if (pkg == SELF_PACKAGE) continue
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && isProtectedSystemApp(pkg)) continue
            if (isDefaultAndroidIcon(context, info)) continue
            result += AppInfoItem(info, pkg, pm.getApplicationLabel(info).toString(), isBloatwarePackage(pkg))
        }
        return result.sortedBy { it.label.lowercase() }
    }
}

// ═══ 5. MAIN ACTIVITY ═══
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurboSpaceApp() }
    }
}

// ═══ 6. UI ═══
@Composable
fun TurboSpaceApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var themeMode by remember { mutableStateOf(ShizukuManager.loadThemeMode(context)) }
    val isSystemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        AppTheme.DARK -> DarkEmeraldColors
        AppTheme.LIGHT -> EmeraldColors
        AppTheme.SYSTEM -> if (isSystemDark) DarkEmeraldColors else EmeraldColors
    }
    BackHandler(enabled = screen != AppScreen.HOME) { screen = AppScreen.HOME }
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    onOpenAppManagement = { screen = AppScreen.APP_MANAGEMENT },
                    onOpenBackgroundApps = { screen = AppScreen.BACKGROUND_APPS },
                    onOpenAppTuning = { screen = AppScreen.APP_TUNING },
                    onOpenStatusMonitor = { screen = AppScreen.STATUS_MONITOR },
                    onOpenProgrammerMode = { screen = AppScreen.PROGRAMMER_MODE },
                    onOpenSettings = { screen = AppScreen.SETTINGS }
                )
                AppScreen.APP_MANAGEMENT -> AppManagementScreen(onBack = { screen = AppScreen.HOME })
                AppScreen.BACKGROUND_APPS -> BackgroundAppsScreen(onBack = { screen = AppScreen.HOME })
                AppScreen.APP_TUNING -> AppTuningScreen(onBack = { screen = AppScreen.HOME })
                AppScreen.STATUS_MONITOR -> StatusMonitorScreen(onBack = { screen = AppScreen.HOME })
                AppScreen.PROGRAMMER_MODE -> ProgrammerModeScreen(onBack = { screen = AppScreen.HOME })
                AppScreen.SETTINGS -> SettingsScreen(
                    currentTheme = themeMode,
                    onThemeChange = {
                        themeMode = it
                        ShizukuManager.saveThemeMode(context, it)
                    },
                    onBack = { screen = AppScreen.HOME }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    onOpenAppManagement: () -> Unit,
    onOpenBackgroundApps: () -> Unit,
    onOpenAppTuning: () -> Unit,
    onOpenStatusMonitor: () -> Unit,
    onOpenProgrammerMode: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var binderAlive by remember { mutableStateOf(ShizukuManager.isBinderAlive()) }
    var available by remember { mutableStateOf(ShizukuManager.isAvailable()) }
    val refresh: () -> Unit = {
        binderAlive = ShizukuManager.isBinderAlive()
        available = ShizukuManager.isAvailable()
    }
    DisposableEffect(Unit) {
        val onReceived = Shizuku.OnBinderReceivedListener { refresh() }
        val onDead = Shizuku.OnBinderDeadListener { refresh() }
        val onResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
        Shizuku.addBinderReceivedListenerSticky(onReceived)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onResult)
        onDispose {
            Shizuku.removeBinderReceivedListener(onReceived)
            Shizuku.removeBinderDeadListener(onDead)
            Shizuku.removeRequestPermissionResultListener(onResult)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TurboSpace Optimizer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Shizuku Connection Test",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "การตั้งค่า",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Card(
                    onClick = onOpenAppManagement,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(vertical = 24.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PackageBoxIcon(Modifier.size(56.dp), EmeraldPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "การจัดการ app",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Card(
                    onClick = onOpenAppTuning,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(vertical = 24.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RepairBoxIcon(Modifier.size(56.dp), EmeraldPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "ปรับแต่งระบบ",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Card(
                    onClick = onOpenStatusMonitor,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(vertical = 24.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FpsIcon(Modifier.size(56.dp), EmeraldPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "แสดงสถานะ",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Card(
                    onClick = onOpenProgrammerMode,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(vertical = 24.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GamepadIcon(Modifier.size(56.dp), EmeraldPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "โหมดโปรแกรมเมอร์",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Card(
                onClick = onOpenBackgroundApps,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, EmeraldPrimary)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ThreeLinesIcon(Modifier.size(56.dp), EmeraldPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "แอปเบื้องหลัง",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        StatusCard(binderAlive = binderAlive, available = available, onRefresh = refresh)
        ShellTestCard(enabled = available)
    }
}

@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onBack: () -> Unit
) {
    var binderAlive by remember { mutableStateOf(ShizukuManager.isBinderAlive()) }
    var available by remember { mutableStateOf(ShizukuManager.isAvailable()) }
    val refresh = {
        binderAlive = ShizukuManager.isBinderAlive()
        available = ShizukuManager.isAvailable()
    }
    DisposableEffect(Unit) {
        val onBinder = Shizuku.OnBinderReceivedListener { refresh() }
        val onDead = Shizuku.OnBinderDeadListener { refresh() }
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        onDispose {
            Shizuku.removeBinderReceivedListener(onBinder)
            Shizuku.removeBinderDeadListener(onDead)
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).size(48.dp)
                    .clip(RoundedCornerShape(24.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("←", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(
                "การตั้งค่า",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "ธีมของแอป",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                AppTheme.values().forEach { theme ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onThemeChange(theme) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = { onThemeChange(theme) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                theme.label,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                when (theme) {
                                    AppTheme.SYSTEM -> "ใช้ตามการตั้งค่าของระบบ"
                                    AppTheme.DARK -> "พื้นหลังสีดำ เหมาะกับตอนกลางคืน"
                                    AppTheme.LIGHT -> "พื้นหลังสว่าง เหมาะกับตอนกลางวัน"
                                },
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "สถานะ Shizuku",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { refresh() }) {
                        Text("รีเฟรช", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val (dotColor, statusText, description) = when {
                    available -> Triple(EmeraldPrimary, "พร้อมใช้งาน", "เชื่อมต่อและได้รับอนุญาตแล้ว")
                    binderAlive -> Triple(WarnAmber, "ยังไม่ได้อนุญาต", "Shizuku กำลังทำงาน แต่แอปยังไม่ได้รับสิทธิ์")
                    else -> Triple(ErrorColor, "ไม่ได้ทำงาน", "Shizuku ยังไม่ทำงาน หรือหลุดการเชื่อมต่อ")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).background(dotColor, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            statusText,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "TurboSpace Optimizer",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusCard(binderAlive: Boolean, available: Boolean, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val (dotColor, label) = when {
        available -> EmeraldPrimary to "เชื่อมต่อแล้ว"
        binderAlive -> WarnAmber to "ยังไม่ได้อนุญาต"
        else -> MaterialTheme.colorScheme.error to "ไม่ได้ทำงาน"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("สถานะ Shizuku", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (binderAlive && !available) {
                    Button(onClick = { ShizukuManager.requestPermission() }) { Text("อนุญาต") }
                }
                if (!binderAlive) {
                    Button(onClick = {
                        if (!ShizukuManager.openShizukuApp(context)) {
                            Toast.makeText(context, "ไม่พบแอป Shizuku — กรุณาติดตั้งก่อน", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("เปิด Shizuku") }
                }
                OutlinedButton(onClick = onRefresh) { Text("รีเฟรช") }
            }
        }
    }
}

@Composable
fun ShellTestCard(enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("id") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CommandResult?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ทดสอบ shell command", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Command") }
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = enabled && !running,
                    onClick = {
                        scope.launch {
                            running = true
                            try {
                                result = ShizukuManager.executeCommand(command)
                            } finally {
                                running = false
                            }
                        }
                    }
                ) { Text("RUN") }
                if (running) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            result?.let { r ->
                Text(
                    "Status: ${if (r.success) "SUCCESS" else "FAILED"}",
                    fontWeight = FontWeight.SemiBold,
                    color = if (r.success) EmeraldPrimary else MaterialTheme.colorScheme.error
                )
                Text("Exit code: ${r.exitCode ?: "-"}")
                Text("stdout", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text(r.output.ifEmpty { "(empty)" })
                Text("stderr", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text(r.error ?: "(empty)")
            }
        }
    }
}

// ═══ 7. FEATURE: แอปเบื้องหลัง (Background Apps) ═══

@Composable
fun ThreeLinesIcon(modifier: Modifier = Modifier, tint: Color = EmeraldPrimary) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 2.5.dp.toPx()
        val gap = h * 0.22f
        val topY = h * 0.28f

        // เส้นบน — ยาว 80%
        drawLine(tint, Offset(w * 0.10f, topY), Offset(w * 0.90f, topY), strokeWidth = sw, cap = StrokeCap.Round)
        // เส้นกลาง — ยาว 60%
        drawLine(tint, Offset(w * 0.20f, topY + gap), Offset(w * 0.80f, topY + gap), strokeWidth = sw, cap = StrokeCap.Round)
        // เส้นล่าง — ยาว 40%
        drawLine(tint, Offset(w * 0.30f, topY + 2 * gap), Offset(w * 0.70f, topY + 2 * gap), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun BackgroundAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<BackgroundApp>?>(null) }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var killingPkg by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            apps = withContext(Dispatchers.IO) { ShizukuManager.getBackgroundApps(context) }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val filteredApps = remember(apps, filter) {
        when (filter) {
            AppFilter.ALL -> apps.orEmpty()
            AppFilter.SYSTEM_ONLY -> apps.orEmpty().filter { it.isSystem }
            AppFilter.DOWNLOADED_ONLY -> apps.orEmpty().filter { !it.isSystem }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {

        // ─── Header ───
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
            // Back
            Box(
                Modifier.align(Alignment.CenterStart).size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            // Title
            Text(
                "แอปเบื้องหลัง",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = EmeraldPrimary
            )

            // ปุ่มขวา: Refresh + Filter + Info (36dp เพื่อไม่ชนชื่อหัวข้อบนจอแคบ)
            Row(
                Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Refresh
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                        .clickable { refresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("↻", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                // Filter button + DropdownMenu
                Box {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                            .clickable { showFilterMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        ThreeLinesIcon(Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        FilterMenuItem("แสดงทั้งหมด", filter == AppFilter.ALL) {
                            filter = AppFilter.ALL; showFilterMenu = false
                        }
                        FilterMenuItem("แอประบบเท่านั้น", filter == AppFilter.SYSTEM_ONLY) {
                            filter = AppFilter.SYSTEM_ONLY; showFilterMenu = false
                        }
                        FilterMenuItem("แอป Download เท่านั้น", filter == AppFilter.DOWNLOADED_ONLY) {
                            filter = AppFilter.DOWNLOADED_ONLY; showFilterMenu = false
                        }
                    }
                }

                // Info button (วงกลม + i)
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { showInfoDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("i", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // ─── List ───
        when {
            apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
            filteredApps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (ShizukuManager.isAvailable()) "ไม่พบแอปที่ทำงานเบื้องหลัง"
                    else "Shizuku ยังไม่พร้อม — กลับหน้าหลักเพื่ออนุญาตก่อน",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    BackgroundAppRow(
                        app = app,
                        killing = killingPkg == app.packageName,
                        onKill = {
                            scope.launch {
                                killingPkg = app.packageName
                                val result = ShizukuManager.forceStopApp(app.packageName)
                                killingPkg = null
                                if (result.success) {
                                    apps = apps?.filter { it.packageName != app.packageName }
                                } else {
                                    Toast.makeText(context, result.error ?: "Failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // ─── Info Dialog ───
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("คำอธิบายสีและสถานะ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("สถานะการทำงาน", fontWeight = FontWeight.SemiBold, color = EmeraldPrimary)
                    Text("• ✓ เขียว = กำลังทำงานอยู่", fontSize = 13.sp)
                    Text("• ✗ เทา = ไม่ได้ทำงาน", fontSize = 13.sp)

                    Text("ประเภทแอป", fontWeight = FontWeight.SemiBold, color = EmeraldPrimary)
                    Text("• ขอบแดง + 'แอประบบ' = ติดมากับเครื่อง", fontSize = 13.sp)
                    Text("• ขอบเขียว + 'แอปทั่วไป' = ดาวน์โหลดจาก Store", fontSize = 13.sp)

                    Text("ระดับอันตรายเมื่อบังคับหยุด", fontWeight = FontWeight.SemiBold, color = EmeraldPrimary)
                    Text("• ปลอดภัย (เขียว) = หยุดได้ไม่มีปัญหา", fontSize = 13.sp)
                    Text("• ปานกลาง (เหลือง) = อาจกระทบบางฟังก์ชัน", fontSize = 13.sp)
                    Text("• อันตรายมาก (แดง) = ระบบอาจมีปัญหา", fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("ตกลง", color = EmeraldPrimary)
                }
            }
        )
    }
}

@Composable
private fun FilterMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, modifier = Modifier.weight(1f))
                if (selected) Text("✓", color = EmeraldPrimary)
            }
        },
        onClick = onClick
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundAppRow(app: BackgroundApp, killing: Boolean, onKill: () -> Unit) {
    val borderColor = if (app.isSystem) ErrorColor else EmeraldPrimary

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(app.info, app.packageName, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text("${app.ramMb} MB", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(6.dp))
            // FlowRow: badge 3 ตัวรวมกันกว้างกว่าพื้นที่บนจอ 360dp จึงให้ขึ้นบรรทัดใหม่เองแทนการบีบข้อความ
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Badge 1: สถานะทำงาน
                StatusBadge(
                    text = if (app.isRunning) "✓ กำลังทำงาน" else "✗ ไม่ทำงาน",
                    bgColor = if (app.isRunning) RowGreenBg else RowGrayBg,
                    textColor = if (app.isRunning) SuccessGreen else Color(0xFF757575),
                    borderColor = null
                )
                // Badge 2: ประเภท
                StatusBadge(
                    text = if (app.isSystem) "แอประบบ" else "แอปทั่วไป",
                    bgColor = if (app.isSystem) RowRedBg else RowGreenBg,
                    textColor = if (app.isSystem) ErrorColor else SuccessGreen,
                    borderColor = if (app.isSystem) ErrorColor else SuccessGreen
                )
                // Badge 3: ความปลอดภัย
                val (safetyText, safetyBg, safetyColor) = when (app.safetyLevel) {
                    SafetyLevel.SAFE -> Triple("ปลอดภัย", RowGreenBg, SuccessGreen)
                    SafetyLevel.MEDIUM -> Triple("ปานกลาง", RowYellowBg, WarnAmber)
                    SafetyLevel.DANGEROUS -> Triple("อันตรายมาก", RowRedBg, ErrorColor)
                }
                StatusBadge(text = safetyText, bgColor = safetyBg, textColor = safetyColor, borderColor = null)
            }
        }

        Spacer(Modifier.width(8.dp))

        // ปุ่มบังคับหยุด
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(RowRedBg)
                .border(1.dp, ErrorColor, RoundedCornerShape(8.dp))
                .clickable(enabled = !killing, onClick = onKill)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (killing) {
                CircularProgressIndicator(Modifier.size(16.dp), color = ErrorColor, strokeWidth = 2.dp)
            } else {
                Text("บังคับหยุด", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorColor)
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, bgColor: Color, textColor: Color, borderColor: Color?) {
    val shape = RoundedCornerShape(50)
    var modifier = Modifier.clip(shape).background(bgColor)
    if (borderColor != null) {
        modifier = modifier.border(1.dp, borderColor, shape)
    }
    Box(modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
private fun AppIconImage(info: ApplicationInfo, packageName: String, modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(iconCache[packageName], packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    TurboSpaceManager
                        .renderIconBitmap(TurboSpaceManager.loadAppIcon(appContext, info), 96)
                        .asImageBitmap()
                }.getOrNull()
            }?.also { iconCache[packageName] = it }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.background(RowGrayBg))
    }
}

// ═══ 7.5 FEATURE: การจัดการ app (App Management) ═══

@Composable
fun PackageBoxIcon(modifier: Modifier = Modifier, tint: Color = EmeraldPrimary) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val u = size.minDimension / 24f
        val sw = 1.8f * u
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val topPt    = Offset(cx,          cy - 9f * u)
        val leftTop  = Offset(cx - 10f * u, cy - 4f * u)
        val rightTop = Offset(cx + 10f * u, cy - 4f * u)
        val midPt    = Offset(cx,          cy + 1f * u)
        val bottomPt = Offset(cx,          cy + 11f * u)
        val leftBot  = Offset(cx - 10f * u, cy + 6f * u)
        val rightBot = Offset(cx + 10f * u, cy + 6f * u)
        drawPath(Path().apply {
            moveTo(topPt.x, topPt.y); lineTo(rightTop.x, rightTop.y)
            lineTo(midPt.x, midPt.y); lineTo(leftTop.x, leftTop.y); close()
        }, tint, style = stroke)
        drawPath(Path().apply {
            moveTo(leftTop.x, leftTop.y); lineTo(midPt.x, midPt.y)
            lineTo(bottomPt.x, bottomPt.y); lineTo(leftBot.x, leftBot.y); close()
        }, tint, style = stroke)
        drawPath(Path().apply {
            moveTo(rightTop.x, rightTop.y); lineTo(midPt.x, midPt.y)
            lineTo(bottomPt.x, bottomPt.y); lineTo(rightBot.x, rightBot.y); close()
        }, tint, style = stroke)
        drawLine(tint, topPt, midPt, strokeWidth = sw, cap = StrokeCap.Round)
    }
}

enum class BucketState { NORMAL, RESTRICTED, ACTIVE, IGNORED }

/**
 * คำนิยามของแต่ละ AppOp พร้อม permission ที่ต้องมีจริง
 * requiredPermissions = ANY of these permissions (ถ้ามีอย่างน้อย 1 → แสดง)
 */
private data class AppOpsDef(
    val opName: String,
    val requiredPermissions: List<String>,   // ANY of these
    val allowDesc: String,
    val ignoreDesc: String
)

private val APP_OPS_TABLE = listOf(
    AppOpsDef("RUN_IN_BACKGROUND", listOf(
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.WAKE_LOCK",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.ACCESS_BACKGROUND_LOCATION"
    ),
        "แอปทำงานเบื้องหลังได้ตามปกติ",
        "แอปหยุดทำงานเมื่อไม่ได้ใช้งาน แจ้งเตือนอาจไม่มา"),

    AppOpsDef("BOOT_COMPLETED", listOf("android.permission.RECEIVE_BOOT_COMPLETED"),
        "แอปเริ่มทำงานอัตโนมัติเมื่อเปิดเครื่อง",
        "แอปไม่เริ่มเองเมื่อบูตเครื่อง"),

    AppOpsDef("START_FOREGROUND", listOf("android.permission.FOREGROUND_SERVICE"),
        "แอปเปิด foreground service ได้",
        "แอปเปิด foreground service ไม่ได้"),

    AppOpsDef("WAKE_LOCK", listOf("android.permission.WAKE_LOCK"),
        "แอปปลุกเครื่องได้ (อาจกินแบต)",
        "แอปปลุกเครื่องไม่ได้ ประหยัดแบต"),

    AppOpsDef("SYSTEM_ALERT_WINDOW", listOf("android.permission.SYSTEM_ALERT_WINDOW"),
        "แอปแสดงหน้าต่างซ้อนทับได้",
        "แอปแสดงหน้าต่างซ้อนไม่ได้ ปุ่มลอยจะหายไป"),

    AppOpsDef("POST_NOTIFICATION", listOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.POST_NOTIFICATION"
    ),
        "แอปส่งการแจ้งเตือนได้",
        "แอปส่งการแจ้งเตือนไม่ได้"),

    AppOpsDef("CHANGE_WIFI_STATE", listOf(
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.ACCESS_WIFI_STATE"
    ),
        "แอปเปิด/ปิด Wi-Fi ได้",
        "แอปเปิด/ปิด Wi-Fi ไม่ได้"),

    AppOpsDef("FINE_LOCATION", listOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION"   // fallback: ถ้ามีแค่ coarse ก็พอ
    ),
        "แอปเข้าถึงตำแหน่งละเอียดได้",
        "แอปเข้าถึงตำแหน่งไม่ได้"),

    AppOpsDef("COARSE_LOCATION", listOf("android.permission.ACCESS_COARSE_LOCATION"),
        "แอปเข้าถึงตำแหน่งแบบหยาบได้",
        "แอปเข้าถึงตำแหน่งแบบหยาบไม่ได้"),

    AppOpsDef("ACTIVITY_RECOGNITION", listOf("android.permission.ACTIVITY_RECOGNITION"),
        "แอปตรวจจับการเดิน/วิ่งได้",
        "แอปตรวจจับการเคลื่อนไหวไม่ได้"),

    AppOpsDef("READ_CLIPBOARD", listOf("android.permission.READ_CLIPBOARD"),
        "แอปอ่านคลิปบอร์ดได้",
        "แอปอ่านคลิปบอร์ดไม่ได้")
)

/**
 * ผลลัพธ์ Deep Detection:
 * - available: แสดง op นี้หรือไม่
 * - currentEnabled: true = อนุญาต (เขียว), false = เพิกเฉย (เทา)
 */
data class AppOpState(
    val opName: String,
    val allowDesc: String,
    val ignoreDesc: String,
    val available: Boolean,
    val currentEnabled: Boolean
)

@Composable
fun AppManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppInfoItem>?>(null) }
    val bucketStates = remember { mutableStateMapOf<String, BucketState>() }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val opsByPkg = remember { mutableStateMapOf<String, List<AppOpState>>() }
    var expandedPkgState by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { TurboSpaceManager.getFilteredApps(context) }
        loaded.forEach {
            bucketStates[it.packageName] = runCatching {
                BucketState.valueOf(
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString("am_bucket_${it.packageName}", "NORMAL") ?: "NORMAL"
                )
            }.getOrDefault(BucketState.NORMAL)
        }
        apps = loaded
    }

    fun saveBucket(pkg: String, state: BucketState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("am_bucket_$pkg", state.name).apply()
    }
    fun toastError(r: CommandResult) {
        Toast.makeText(context, r.error ?: r.output.ifBlank { "Failed" }, Toast.LENGTH_LONG).show()
    }
    fun swipe(app: AppInfoItem, target: BucketState) {
        val pkg = app.packageName
        if (busy[pkg] == true) return
        val cmd = when (target) {
            BucketState.RESTRICTED -> "am set-standby-bucket $pkg restricted"
            BucketState.ACTIVE -> "cmd activity set-standby-bucket $pkg active"
            else -> return
        }
        scope.launch {
            busy[pkg] = true
            val r = ShizukuManager.executeCommand(cmd)
            busy.remove(pkg)
            if (r.success) {
                bucketStates[pkg] = target
                saveBucket(pkg, target)
            } else toastError(r)
        }
    }

    // กดที่ row → toggle AppOps panel
    fun toggleRow(app: AppInfoItem) {
        val pkg = app.packageName
        if (expandedPkgState == pkg) {
            expandedPkgState = null
            return
        }
        expandedPkgState = pkg
        // โหลด ops ด้วย Deep Detection (ถ้ายังไม่มีใน cache)
        if (opsByPkg[pkg] == null) {
            scope.launch {
                val ops = withContext(Dispatchers.IO) {
                    ShizukuManager.getAvailableAppOps(context, pkg)
                }
                opsByPkg[pkg] = ops
            }
        }
    }

    fun toggleOp(app: AppInfoItem, op: AppOpState, newEnabled: Boolean) {
        val pkg = app.packageName
        scope.launch {
            busy[pkg] = true
            val mode = if (newEnabled) "allow" else "ignore"
            val r = ShizukuManager.executeCommand("cmd appops set $pkg ${op.opName} $mode")
            busy.remove(pkg)
            if (r.success) {
                // อัปเดต state ทันที
                opsByPkg[pkg]?.let { current ->
                    opsByPkg[pkg] = current.map {
                        if (it.opName == op.opName) it.copy(currentEnabled = newEnabled) else it
                    }
                }
            } else toastError(r)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).size(48.dp)
                    .clip(RoundedCornerShape(24.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("←", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(
                "การจัดการ app",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = EmeraldPrimary
            )
        }
        val list = apps
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ไม่พบแอป", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.packageName }) { app ->
                    val pkg = app.packageName
                    Column {
                        AppManageRow(
                            app = app,
                            bucketState = bucketStates[pkg] ?: BucketState.NORMAL,
                            expanded = expandedPkgState == pkg,
                            busy = busy[pkg] == true,
                            onClick = { toggleRow(app) },
                            onSwipeLeft = { swipe(app, BucketState.RESTRICTED) },
                            onSwipeRight = { swipe(app, BucketState.ACTIVE) }
                        )
                        AnimatedVisibility(visible = expandedPkgState == pkg) {
                            val ops = opsByPkg[pkg]
                            if (ops == null) {
                                // กำลังโหลด
                                Box(
                                    Modifier.fillMaxWidth()
                                        .padding(top = 6.dp, start = 8.dp, end = 8.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = EmeraldPrimary
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text("กำลังตรวจสอบสิทธิ์…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else if (ops.isEmpty()) {
                                // ไม่มี op ที่ตรงกับ permission จริง
                                Box(
                                    Modifier.fillMaxWidth()
                                        .padding(top = 6.dp, start = 8.dp, end = 8.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        "แอปนี้ไม่มีการขอสิทธิ์ที่เกี่ยวข้องกับ AppOps",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                AppOpsPanel(
                                    ops = ops,
                                    busy = busy[pkg] == true,
                                    onToggle = { op, e -> toggleOp(app, op, e) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppManageRow(
    app: AppInfoItem,
    bucketState: BucketState,
    expanded: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val maxDragPx = thresholdPx * 1.6f
    var offsetX by remember { mutableFloatStateOf(0f) }
    val latestLeft by rememberUpdatedState(onSwipeLeft)
    val latestRight by rememberUpdatedState(onSwipeRight)

    val bg by animateColorAsState(
        targetValue = when (bucketState) {
            BucketState.NORMAL -> MaterialTheme.colorScheme.surface
            BucketState.RESTRICTED -> RowGreenBg
            BucketState.ACTIVE -> RowRedBg
            BucketState.IGNORED -> RowGrayBg
        }, label = "amBg"
    )
    val border by animateColorAsState(
        targetValue = when (bucketState) {
            BucketState.NORMAL -> MaterialTheme.colorScheme.outline
            BucketState.RESTRICTED -> EmeraldPrimary
            BucketState.ACTIVE -> ErrorColor
            BucketState.IGNORED -> Color(0xFF9E9E9E)
        }, label = "amBorder"
    )
    val chevronRot by animateFloatAsState(if (expanded) 90f else 0f, label = "amChev")
    val previewAlpha = (abs(offsetX) / thresholdPx).coerceIn(0f, 1f) * 0.5f

    val settle: () -> Unit = {
        val from = offsetX
        scope.launch {
            animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                offsetX = v
            }
        }
    }

    Box(Modifier.fillMaxWidth().heightIn(min = 62.dp)) {
        if (offsetX > 0f) {
            RocketIcon(
                Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(24.dp).alpha(previewAlpha),
                ErrorColor
            )
        }
        if (offsetX < 0f) {
            BatteryIcon(
                Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(24.dp).alpha(previewAlpha),
                EmeraldPrimary
            )
        }
        Row(
            Modifier.fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .shadow(2.dp, shape)
                .background(bg, shape)
                .border(1.dp, border, shape)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDrag = { change, drag ->
                            change.consume()
                            offsetX = (offsetX + drag.x).coerceIn(-maxDragPx, maxDragPx)
                        },
                        onDragEnd = {
                            val r = offsetX
                            when {
                                r <= -thresholdPx -> latestLeft()
                                r >= thresholdPx -> latestRight()
                            }
                            settle()
                        },
                        onDragCancel = { settle() }
                    )
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                app.info, app.packageName,
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Txt(app.label, 15, MaterialTheme.colorScheme.onSurface, FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Txt(app.packageName, 12, MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (text, bgC, fgC) = when (bucketState) {
                        BucketState.NORMAL -> Triple("ปกติ", RowGrayBg, Color(0xFF757575))
                        BucketState.RESTRICTED -> Triple("Restricted", RowGreenBg, SuccessGreen)
                        BucketState.ACTIVE -> Triple("Active", RowRedBg, ErrorColor)
                        BucketState.IGNORED -> Triple("Ignored", RowGrayBg, Color(0xFF757575))
                    }
                    TuningBadge(text, bgC, fgC, null)
                    if (app.isBloatware) TuningBadge("ad/bloat", RowYellowBg, WarnAmber, null)
                }
            }
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(22.dp), strokeWidth = 2.dp, color = EmeraldPrimary
                )
                Spacer(Modifier.width(6.dp))
            }
            if (bucketState == BucketState.RESTRICTED) {
                BatteryIcon(Modifier.size(22.dp), EmeraldPrimary)
                Spacer(Modifier.width(6.dp))
            } else if (bucketState == BucketState.ACTIVE) {
                RocketIcon(Modifier.size(22.dp), ErrorColor)
                Spacer(Modifier.width(6.dp))
            }
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(">", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRot))
            }
        }
    }
}

@Composable
private fun AppOpsPanel(
    ops: List<AppOpState>,
    busy: Boolean,
    onToggle: (AppOpState, Boolean) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth()
            .padding(top = 6.dp, start = 8.dp, end = 8.dp)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("AppOps", 13, EmeraldPrimary, FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Txt("${ops.size} รายการ", 11, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))

        ops.forEachIndexed { idx, op ->
            val enabled = op.currentEnabled
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(op.opName, 14, MaterialTheme.colorScheme.onSurface, FontWeight.Bold)
                        Text("อนุญาต: ${op.allowDesc}",
                            fontSize = 11.sp, color = EmeraldPrimary, lineHeight = 15.sp)
                        Text("เพิกเฉย: ${op.ignoreDesc}",
                            fontSize = 11.sp, color = ErrorColor, lineHeight = 15.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(
                                if (enabled) RowGreenBg else RowGrayBg
                            )
                            .border(
                                1.dp,
                                if (enabled) SuccessGreen else Color(0xFF9E9E9E),
                                RoundedCornerShape(50)
                            )
                            .clickable(enabled = !busy) { onToggle(op, !enabled) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (enabled) "อนุญาต ▼" else "เพิกเฉย ▼",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (enabled) SuccessGreen else Color(0xFF757575)
                        )
                    }
                }
                if (idx < ops.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// ═══ 7.6 FEATURE: แสดงสถานะ (Status Monitor) ═══

@Composable
fun FpsIcon(modifier: Modifier = Modifier, tint: Color = EmeraldPrimary) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val u = size.minDimension / 24f
        val sw = 1.8f * u
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        
        // กรอบสี่เหลี่ยมขอบโค้ง
        drawRoundRect(
            color = tint,
            topLeft = Offset(2f * u, 5f * u),
            size = Size(20f * u, 14f * u),
            cornerRadius = CornerRadius(3f * u),
            style = stroke
        )
        
        // ตัวอักษร "FPS" ตรงกลาง
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = tint.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 5.5f * u
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText("FPS", cx, cy + 2f * u, paint)
        
        // เส้นกราฟ ECG ด้านบน
        val path = Path().apply {
            moveTo(3f * u, 3f * u)
            lineTo(7f * u, 3f * u)
            lineTo(9f * u, 1f * u)
            lineTo(11f * u, 4f * u)
            lineTo(13f * u, 2f * u)
            lineTo(15f * u, 3f * u)
            lineTo(21f * u, 3f * u)
        }
        drawPath(path, tint, style = Stroke(sw * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

data class StatusReading(
    val refreshHz: Int = 0,
    val ramPercent: Int = 0,
    val ramUsedMb: Long = 0,
    val ramTotalMb: Long = 0,
    val cpuGhz: Float = 0f,
    val cpuLabel: String = "",
    val tempCelsius: Float = 0f,
    val tempLabel: String = "",
    val batteryPercent: Int = 0
)

enum class StatusToggle(val key: String, val label: String, val defaultOn: Boolean) {
    FPS("fps", "แสดงสถานะ FPS", true),
    RAM("ram", "แสดงแรม", true),
    CPU("cpu", "แสดง CPU", false),
    TEMP("temp", "แสดงอุณหภูมิ", false),
    BATTERY("battery", "แสดงแบตเตอรี่", true)
}

enum class TempMethod { THERMAL_ZONE, HWMON, THERMAL_SERVICE, BATTERY_FALLBACK, NONE }
enum class CpuMethod { SCALING_CUR, CPUINFO_CUR, SCALING_MAX, NONE }

object StatusDetector {
    private var cachedTempMethod: TempMethod? = null
    private var cachedCpuMethod: CpuMethod? = null

    suspend fun detectAll() {
        cachedTempMethod = detectTempMethod()
        cachedCpuMethod = detectCpuMethod()
    }

    private suspend fun detectTempMethod(): TempMethod {
        // 1. thermal_zone + type matching
        val r1 = ShizukuManager.executeCommand(
            "for z in /sys/class/thermal/thermal_zone*; do " +
            "t=\$(cat \$z/type 2>/dev/null); " +
            "if echo \"\$t\" | grep -qiE 'cpu|soc|ap-|tsens|mtkts|quiet_therm'; then " +
            "echo \$z; fi; done | head -1"
        )
        if (r1.success && r1.output.isNotBlank()) return TempMethod.THERMAL_ZONE

        // 2. hwmon
        val r2 = ShizukuManager.executeCommand("ls /sys/class/hwmon/hwmon*/temp1_input 2>/dev/null | head -1")
        if (r2.success && r2.output.isNotBlank()) return TempMethod.HWMON

        // 3. dumpsys thermalservice
        val r3 = ShizukuManager.executeCommand("dumpsys thermalservice 2>/dev/null | grep -i 'Temperature{' | head -1")
        if (r3.success && r3.output.isNotBlank()) return TempMethod.THERMAL_SERVICE

        // 4. fallback: battery
        return TempMethod.BATTERY_FALLBACK
    }

    private suspend fun detectCpuMethod(): CpuMethod {
        val r1 = ShizukuManager.executeCommand(
            "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null"
        )
        if (r1.success && r1.output.trim().toLongOrNull() != null) return CpuMethod.SCALING_CUR

        val r2 = ShizukuManager.executeCommand(
            "cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq 2>/dev/null"
        )
        if (r2.success && r2.output.trim().toLongOrNull() != null) return CpuMethod.CPUINFO_CUR

        val r3 = ShizukuManager.executeCommand(
            "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null"
        )
        if (r3.success && r3.output.trim().toLongOrNull() != null) return CpuMethod.SCALING_MAX

        return CpuMethod.NONE
    }

    suspend fun readTemperature(): Pair<Float, String> {
        if (!ShizukuManager.isAvailable()) return 0f to ""
        val method = cachedTempMethod ?: detectTempMethod().also { cachedTempMethod = it }
        return try {
            when (method) {
                TempMethod.THERMAL_ZONE -> readThermalZone() to ""
                TempMethod.HWMON -> readHwmon() to "hwmon"
                TempMethod.THERMAL_SERVICE -> readThermalService() to "service"
                TempMethod.BATTERY_FALLBACK -> readBatteryTemp() to "battery"
                TempMethod.NONE -> 0f to ""
            }
        } catch (_: Throwable) {
            cachedTempMethod = null
            0f to ""
        }
    }

    private suspend fun readThermalZone(): Float {
        val r = ShizukuManager.executeCommand(
            "for z in /sys/class/thermal/thermal_zone*; do " +
            "t=\$(cat \$z/type 2>/dev/null); v=\$(cat \$z/temp 2>/dev/null); " +
            "echo \"\$t:\$v\"; done"
        )
        if (!r.success) return 0f
        val keywords = listOf("cpu", "soc", "ap-", "tsens", "mtkts", "quiet_therm", "big", "little")
        val zones = r.output.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(":")
            if (parts.size < 2) return@mapNotNull null
            val type = parts[0].lowercase()
            val raw = parts[1].trim().toLongOrNull() ?: return@mapNotNull null
            val celsius = when {
                raw > 1000L -> raw / 1000f
                raw > 200L -> raw / 10f
                else -> raw.toFloat()
            }
            if (celsius !in 15f..120f) return@mapNotNull null
            if (!keywords.any { type.contains(it) }) return@mapNotNull null
            celsius
        }.toList()
        return if (zones.isEmpty()) 0f else zones.average().toFloat()
    }

    private suspend fun readHwmon(): Float {
        val r = ShizukuManager.executeCommand(
            "for f in /sys/class/hwmon/hwmon*/temp*_input; do cat \$f 2>/dev/null; done"
        )
        if (r.output.isBlank()) return 0f
        val values = r.output.lineSequence().mapNotNull { it.trim().toLongOrNull() }
            .map { it / 1000f }
            .filter { it in 15f..120f }
            .toList()
        return if (values.isEmpty()) 0f else values.average().toFloat()
    }

    private suspend fun readThermalService(): Float {
        val r = ShizukuManager.executeCommand("dumpsys thermalservice 2>/dev/null")
        if (!r.success) return 0f
        val regex = Regex("""Temperature\{[^}]*mValue=([\d.]+)""")
        val values = regex.findAll(r.output).mapNotNull { it.groupValues[1].toFloatOrNull() }.toList()
        return if (values.isEmpty()) 0f else values.average().toFloat()
    }

    private suspend fun readBatteryTemp(): Float {
        // ผ่าน Shizuku อ่าน /sys/class/power_supply/battery/temp
        val r = ShizukuManager.executeCommand("cat /sys/class/power_supply/battery/temp 2>/dev/null")
        if (!r.success) return 0f
        val raw = r.output.trim().toLongOrNull() ?: return 0f
        return raw / 10f  // deci-degree
    }

    suspend fun readCpuGhz(): Pair<Float, String> {
        if (!ShizukuManager.isAvailable()) return 0f to ""
        val method = cachedCpuMethod ?: detectCpuMethod().also { cachedCpuMethod = it }
        val path = when (method) {
            CpuMethod.SCALING_CUR -> "scaling_cur_freq"
            CpuMethod.CPUINFO_CUR -> "cpuinfo_cur_freq"
            CpuMethod.SCALING_MAX -> "scaling_max_freq"
            CpuMethod.NONE -> return 0f to ""
        }
        val r = ShizukuManager.executeCommand(
            "cat /sys/devices/system/cpu/cpu*/cpufreq/$path 2>/dev/null"
        )
        if (r.output.isBlank()) return 0f to ""
        val freqs = r.output.lineSequence().mapNotNull { line ->
            val khz = line.trim().toLongOrNull() ?: return@mapNotNull null
            val ghz = khz / 1_000_000f
            if (ghz in 0.1f..6f) ghz else null
        }.toList()
        val label = if (method == CpuMethod.SCALING_MAX) "max" else ""
        return if (freqs.isEmpty()) 0f to "" else freqs.average().toFloat() to label
    }

    @Suppress("DEPRECATION")
    fun readRefreshHz(context: Context): Int = try {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        }
        (display?.refreshRate ?: 60f).roundToInt()
    } catch (_: Throwable) { 60 }

    fun readRam(context: Context): Triple<Int, Long, Long> = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availMb
        val percent = if (totalMb > 0) ((usedMb * 100) / totalMb).toInt() else 0
        Triple(percent, usedMb, totalMb)
    } catch (_: Throwable) { Triple(0, 0L, 0L) }

    fun readBattery(context: Context): Int = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    } catch (_: Throwable) { 0 }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusMonitorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val toggles = remember {
        mutableStateMapOf<String, Boolean>().apply {
            StatusToggle.values().forEach { t ->
                put(t.key, ShizukuManager.loadStatusToggle(context, t.key, t.defaultOn))
            }
        }
    }
    
    var reading by remember { mutableStateOf(StatusReading()) }
    var detecting by remember { mutableStateOf(true) }
    val detectMsg = "กำลังตรวจจับระบบ…"
    
    LaunchedEffect(Unit) {
        // ตรวจจับครั้งแรก
        if (ShizukuManager.isAvailable()) {
            withContext(Dispatchers.IO) { StatusDetector.detectAll() }
        }
        detecting = false
        
        // Loop อัปเดตทุก 1 วินาที
        while (true) {
            val ram = StatusDetector.readRam(context)
            val battery = StatusDetector.readBattery(context)
            val hz = StatusDetector.readRefreshHz(context)
            
            val (cpuGhz, cpuLabel) = if (toggles["cpu"] == true) {
                withContext(Dispatchers.IO) { StatusDetector.readCpuGhz() }
            } else 0f to ""
            
            val (temp, tempLabel) = if (toggles["temp"] == true) {
                withContext(Dispatchers.IO) { StatusDetector.readTemperature() }
            } else 0f to ""
            
            reading = StatusReading(
                refreshHz = hz,
                ramPercent = ram.first,
                ramUsedMb = ram.second,
                ramTotalMb = ram.third,
                cpuGhz = cpuGhz,
                cpuLabel = cpuLabel,
                tempCelsius = temp,
                tempLabel = tempLabel,
                batteryPercent = battery
            )
            delay(1000)
        }
    }
    
    fun toggleStatus(key: String, enabled: Boolean) {
        toggles[key] = enabled
        ShizukuManager.saveStatusToggle(context, key, enabled)
    }
    
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ─── Header ───
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).size(48.dp)
                    .clip(RoundedCornerShape(24.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("←", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(
                "แสดงสถานะ",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = EmeraldPrimary
            )
        }
        
        // ─── Preview Card ───
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, EmeraldPrimary)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("ตัวอย่างที่แสดง", 12, EmeraldPrimary, FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (detecting) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = EmeraldPrimary)
                        Spacer(Modifier.width(6.dp))
                        Txt(detectMsg, 10, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (toggles["fps"] == true) {
                        StatusPill(
                            label = "Hz",
                            value = "${reading.refreshHz}",
                            color = if (reading.refreshHz >= 60) EmeraldPrimary else WarnAmber,
                            onClose = { toggleStatus("fps", false) }
                        )
                    }
                    if (toggles["ram"] == true) {
                        StatusPill(
                            label = "RAM",
                            value = "${reading.ramPercent}%",
                            color = if (reading.ramPercent < 80) EmeraldPrimary else ErrorColor,
                            onClose = { toggleStatus("ram", false) }
                        )
                    }
                    if (toggles["cpu"] == true && reading.cpuGhz > 0f) {
                        StatusPill(
                            label = if (reading.cpuLabel.isBlank()) "CPU" else "CPU (${reading.cpuLabel})",
                            value = String.format(java.util.Locale.US, "%.2fGHz", reading.cpuGhz),
                            color = EmeraldPrimary,
                            onClose = { toggleStatus("cpu", false) }
                        )
                    }
                    if (toggles["temp"] == true && reading.tempCelsius > 0f) {
                        StatusPill(
                            label = if (reading.tempLabel.isBlank()) "Temp" else "Temp (${reading.tempLabel})",
                            value = "${reading.tempCelsius.toInt()}°C",
                            color = when {
                                reading.tempCelsius < 50f -> EmeraldPrimary
                                reading.tempCelsius < 65f -> WarnAmber
                                else -> ErrorColor
                            },
                            onClose = { toggleStatus("temp", false) }
                        )
                    }
                    if (toggles["battery"] == true) {
                        StatusPill(
                            label = "Batt",
                            value = "${reading.batteryPercent}%",
                            color = if (reading.batteryPercent > 20) EmeraldPrimary else ErrorColor,
                            onClose = { toggleStatus("battery", false) }
                        )
                    }
                }
                
                if (toggles["ram"] == true) {
                    Spacer(Modifier.height(6.dp))
                    Txt("RAM: ${reading.ramUsedMb} MB / ${reading.ramTotalMb} MB", 11, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        // ─── Switches ───
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(14.dp)) {
                Txt("เลือกสถานะที่ต้องการแสดง", 14, MaterialTheme.colorScheme.onSurface, FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                
                StatusToggle.values().forEachIndexed { idx, toggle ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt(toggle.label, 14, MaterialTheme.colorScheme.onSurface, FontWeight.Medium)
                            Text(
                                when (toggle) {
                                    StatusToggle.FPS -> "แสดงอัตรารีเฟรชของจอ (Hz)"
                                    StatusToggle.RAM -> "เปอร์เซ็นต์และ MB ที่ใช้"
                                    StatusToggle.CPU -> "ความถี่ CPU เฉลี่ย (ต้องใช้ Shizuku)"
                                    StatusToggle.TEMP -> "อุณหภูมิระบบ (ต้องใช้ Shizuku)"
                                    StatusToggle.BATTERY -> "เปอร์เซ็นต์แบต"
                                },
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = toggles[toggle.key] ?: toggle.defaultOn,
                            onCheckedChange = { toggleStatus(toggle.key, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = EmeraldPrimary
                            )
                        )
                    }
                    if (idx < StatusToggle.values().lastIndex) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
        
        // ─── Warning Shizuku ───
        if (!ShizukuManager.isAvailable()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = RowYellowBg),
                border = BorderStroke(1.dp, WarnAmber)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Txt("Shizuku ไม่พร้อม", 13, WarnAmber, FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "CPU และอุณหภูมิจะแสดงเป็น 0 — กลับหน้าหลักเพื่ออนุญาตสิทธิ์",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusPill(
    label: String,
    value: String,
    color: Color,
    onClose: () -> Unit
) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(color.copy(alpha = 0.2f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text("×", fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "$label: $value",
            fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
    }
}

// ═══ 7.7 FEATURE: โหมดโปรแกรมเมอร์ (Programmer Mode / Game Booster) ═══

@Composable
fun GamepadIcon(modifier: Modifier = Modifier, tint: Color = EmeraldPrimary) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = size.minDimension / 24f
        val sw = 1.8f * u
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // ตัวจอย — ทรงรีแนวนอน + มือจับ 2 ข้าง
        val body = Path().apply {
            moveTo(3f * u, 11f * u)
            cubicTo(3f * u, 7f * u, 6f * u, 6f * u, 8f * u, 6f * u)
            lineTo(16f * u, 6f * u)
            cubicTo(18f * u, 6f * u, 21f * u, 7f * u, 21f * u, 11f * u)
            cubicTo(21f * u, 14f * u, 20f * u, 18f * u, 18f * u, 18f * u)
            cubicTo(16f * u, 18f * u, 15f * u, 16f * u, 12f * u, 16f * u)
            cubicTo(9f * u, 16f * u, 8f * u, 18f * u, 6f * u, 18f * u)
            cubicTo(4f * u, 18f * u, 3f * u, 14f * u, 3f * u, 11f * u)
            close()
        }
        drawPath(body, tint, style = stroke)

        // ปุ่ม D-Pad (บวก) ฝั่งซ้าย
        drawLine(tint, Offset(7f * u, 10f * u), Offset(7f * u, 13f * u), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(5.5f * u, 11.5f * u), Offset(8.5f * u, 11.5f * u), strokeWidth = sw, cap = StrokeCap.Round)

        // ปุ่ม 4 ปุ่มฝั่งขวา (วงกลมเล็กๆ)
        drawCircle(tint, radius = 0.8f * u, center = Offset(16f * u, 10f * u), style = Stroke(sw * 0.7f))
        drawCircle(tint, radius = 0.8f * u, center = Offset(18f * u, 11.5f * u), style = Stroke(sw * 0.7f))
        drawCircle(tint, radius = 0.8f * u, center = Offset(16f * u, 13f * u), style = Stroke(sw * 0.7f))
        drawCircle(tint, radius = 0.8f * u, center = Offset(14f * u, 11.5f * u), style = Stroke(sw * 0.7f))
    }
}

// ── Floating panel: data models ──

enum class DisplayMode { FPS, RAM, CPU }
enum class CrosshairShape { CROSS, DOT, CIRCLE, DIAMOND }

data class CrosshairConfig(
    val shape: CrosshairShape = CrosshairShape.CROSS,
    val color: Int = 0xFF1A73E8.toInt(),
    val size: Int = 32,
    val opacity: Float = 0.8f,
    val x: Int = 100,
    val y: Int = 100
)

data class ProgrammerConfig(
    val packageName: String,
    val label: String,
    val performanceMode: Boolean = false,
    val dnd: Boolean = false,
    val lockRotation: Boolean = false,
    val targetFps: Int = 0,
    val crosshairActive: Boolean = false,
    val statusMonitor: Boolean = false,
    val extremeSaver: Boolean = false
)

@Composable
fun ProgrammerModeScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var showConfirmDialog by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf<AppInfoItem?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }

    // Back closes the floating panel first, instead of leaving the screen
    BackHandler(enabled = showPanel) { showPanel = false }

    // ─── Confirmation Dialog ───
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = onBack,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("โหมดโปรแกรมเมอร์", fontWeight = FontWeight.Bold, color = EmeraldPrimary) },
            text = { Text("ต้องการเปิดโหมดโปรแกรมเมอร์ (โหมดเกมบูสเตอร์) ใช่ไหม?", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Yes", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("ยกเลิก", color = ErrorColor) }
            }
        )
    }

    // ─── App Picker Dialog ───
    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelect = { app ->
                selectedApp = app
                showAppPicker = false
            }
        )
    }

    // ─── Background: Programmer Style ───
    Box(
        Modifier.fillMaxSize().background(Color(0xFF1A1A1A))
    ) {
        // Animation: 3D Cube ลอย + Hexagons ด้านขวา
        ProgrammerAnimation(Modifier.fillMaxSize())

        // ─── Header ───
        Row(
            Modifier.fillMaxWidth().systemBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 24.sp, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text("PROGRAMMER MODE", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFE53935), fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        // ─── ปุ่ม + ด้านซ้าย ───
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                .size(56.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2A2A))
                .border(2.dp, Color(0xFFE53935), RoundedCornerShape(12.dp))
                .clickable { showAppPicker = true },
            contentAlignment = Alignment.Center
        ) {
            // รูป + วาดด้วย Canvas
            Canvas(Modifier.size(28.dp)) {
                val s = size.minDimension
                val sw = 3.dp.toPx()
                drawLine(Color(0xFFE53935),
                    Offset(s * 0.2f, s / 2), Offset(s * 0.8f, s / 2),
                    strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(Color(0xFFE53935),
                    Offset(s / 2, s * 0.2f), Offset(s / 2, s * 0.8f),
                    strokeWidth = sw, cap = StrokeCap.Round)
            }
        }

        // ─── App ที่เลือก (กลางจอ) ───
        val app = selectedApp
        if (app != null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(120.dp).clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF2A2A2A))
                        .border(2.dp, Color(0xFFE53935), RoundedCornerShape(24.dp))
                        .clickable { showPanel = true },
                    contentAlignment = Alignment.Center
                ) {
                    AppIconImage(
                        app.info, app.packageName,
                        Modifier.size(96.dp).clip(RoundedCornerShape(20.dp))
                    )
                }
                Text(app.label, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color.White)
                Text("แตะไอคอนเพื่อตั้งค่า", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, Color(0xFFE53935), RoundedCornerShape(20.dp))
                        .clickable {
                            if (!ShizukuManager.launchApp(context, app.packageName)) {
                                Toast.makeText(context, "Cannot launch this app", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text("LAUNCH", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935), fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("กดปุ่ม + เพื่อเลือกแอป", fontSize = 14.sp, color = Color(0xFFAAAAAA))
            }
        }

        // ─── Floating panel ───
        if (showPanel && app != null) {
            ProgrammerFloatingPanel(app = app, onClose = { showPanel = false })
        }
    }
}
@Composable
fun ProgrammerAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "programmerAnim")

    // 1 นาที = 60000ms
    val cubeProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cubeProgress"
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // ─── Hexagon Pattern (ฝั่งขวา) ───
        val hexRadius = 20f
        val hexX = w - 60f
        val hexStartY = h * 0.2f
        for (i in 0..4) {
            val cy = hexStartY + i * hexRadius * 1.8f
            drawHexagon(
                center = Offset(hexX, cy),
                radius = hexRadius,
                color = Color(0xFFE53935).copy(alpha = 0.3f),
                strokeWidth = 2f
            )
        }
        // แถวที่ 2
        for (i in 0..3) {
            val cy = hexStartY + i * hexRadius * 1.8f + hexRadius * 0.9f
            drawHexagon(
                center = Offset(hexX - hexRadius * 1.7f, cy),
                radius = hexRadius,
                color = Color(0xFFE53935).copy(alpha = 0.2f),
                strokeWidth = 2f
            )
        }

        // ─── 3D Cube ลอย (ฝั่งซ้าย-กลาง) ───
        val cubeSize = 60f
        val startX = w * 0.25f
        val startY = h * 0.4f
        // ลอยไปมา: sine wave
        val t = cubeProgress * 2 * Math.PI
        val cx = startX + kotlin.math.sin(t).toFloat() * 100f
        val cy = startY + kotlin.math.cos(t).toFloat() * 80f

        draw3DCube(
            center = Offset(cx, cy),
            size = cubeSize,
            color = Color(0xFFE53935)
        )
    }
}

private fun DrawScope.drawHexagon(center: Offset, radius: Float, color: Color, strokeWidth: Float) {
    val path = Path()
    for (i in 0..5) {
        val angle = Math.toRadians((i * 60 - 30).toDouble())
        val x = center.x + kotlin.math.cos(angle).toFloat() * radius
        val y = center.y + kotlin.math.sin(angle).toFloat() * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(strokeWidth))
}

private fun DrawScope.draw3DCube(
    center: Offset,
    size: Float,
    color: Color
) {
    // วาด cube แบบ isometric ง่ายๆ
    val s = size / 2f
    // มุมของ cube 3D
    val top = Offset(center.x, center.y - s)
    val leftTop = Offset(center.x - s, center.y - s * 0.5f)
    val rightTop = Offset(center.x + s, center.y - s * 0.5f)
    val mid = Offset(center.x, center.y)
    val leftMid = Offset(center.x - s, center.y + s * 0.5f)
    val rightMid = Offset(center.x + s, center.y + s * 0.5f)
    val bottom = Offset(center.x, center.y + s)

    // หน้า 1 (ซ้าย)
    drawPath(Path().apply {
        moveTo(top.x, top.y); lineTo(leftTop.x, leftTop.y)
        lineTo(leftMid.x, leftMid.y); lineTo(mid.x, mid.y); close()
    }, color.copy(alpha = 0.5f))

    // หน้า 2 (ขวา)
    drawPath(Path().apply {
        moveTo(top.x, top.y); lineTo(rightTop.x, rightTop.y)
        lineTo(rightMid.x, rightMid.y); lineTo(mid.x, mid.y); close()
    }, color.copy(alpha = 0.7f))

    // หน้า 3 (หน้า)
    drawPath(Path().apply {
        moveTo(mid.x, mid.y); lineTo(leftMid.x, leftMid.y)
        lineTo(bottom.x, bottom.y); lineTo(rightMid.x, rightMid.y); close()
    }, color.copy(alpha = 0.9f))
}

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (AppInfoItem) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfoItem>?>(null) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { TurboSpaceManager.getFilteredApps(context) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("เลือกแอป", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text("×", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(onClick = onDismiss))
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ค้นหา...") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            val list = apps
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EmeraldPrimary)
                }
                else -> {
                    val filtered = list.filter {
                        search.isBlank() || it.label.contains(search, true) || it.packageName.contains(search, true)
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelect(app) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconImage(app.info, app.packageName, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(app.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══ 7.8 FLOATING PANEL (TurboTune) — Chamfer panel + side tab + big display + 8 features ═══

// ── Colors (Material 3 Android 15/16 bright) ──
private val PnlSurface = Color(0xFFFFFFFF)
private val PnlSurfaceVariant = Color(0xFFF5F5F5)
private val PnlPrimary = Color(0xFF0EA5E9)   // Match main theme sky blue
private val PnlTextPrimary = Color(0xFF1A1C19)
private val PnlTextSecondary = Color(0xFF5F6368)
private val PnlOutline = Color(0xFFDADCE0)
private val PnlGreen = Color(0xFF34A853)
private val PnlRed = Color(0xFFEA4335)
private val PnlAmber = Color(0xFFFBBC04)
private val PnlDivider = Color(0xFFF0F0F0)

// ── SharedPrefs helpers ──
private fun loadProgBool(context: Context, key: String, default: Boolean): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(key, default)

private fun saveProgBool(context: Context, key: String, value: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(key, value).apply()
}

private fun loadProgInt(context: Context, key: String, default: Int): Int =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(key, default)

private fun saveProgInt(context: Context, key: String, value: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putInt(key, value).apply()
}

private fun loadCrosshairConfig(context: Context): CrosshairConfig {
    val d = CrosshairConfig()
    val shapes = CrosshairShape.values()
    val shapeIdx = loadProgInt(context, "prog_crosshair_shape", d.shape.ordinal)
    return CrosshairConfig(
        shape = shapes.getOrElse(shapeIdx) { d.shape },
        color = loadProgInt(context, "prog_crosshair_color", d.color),
        size = loadProgInt(context, "prog_crosshair_size", d.size).coerceIn(20, 64),
        opacity = loadProgInt(context, "prog_crosshair_opacity", (d.opacity * 100).roundToInt())
            .coerceIn(30, 100) / 100f,
        x = loadProgInt(context, "prog_crosshair_x", d.x),
        y = loadProgInt(context, "prog_crosshair_y", d.y)
    )
}

private fun saveCrosshairConfig(context: Context, cfg: CrosshairConfig) {
    saveProgInt(context, "prog_crosshair_shape", cfg.shape.ordinal)
    saveProgInt(context, "prog_crosshair_color", cfg.color)
    saveProgInt(context, "prog_crosshair_size", cfg.size)
    saveProgInt(context, "prog_crosshair_opacity", (cfg.opacity * 100).roundToInt())
    saveProgInt(context, "prog_crosshair_x", cfg.x)
    saveProgInt(context, "prog_crosshair_y", cfg.y)
}

/** Clears every panel pref EXCEPT prog_fps_target (Reset must not touch the refresh rate). */
private fun clearProgPrefs(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val ed = prefs.edit()
    listOf("prog_perf_mode", "prog_dnd", "prog_lock_rotation", "prog_status_monitor", "prog_power_saver")
        .forEach { ed.remove(it) }
    prefs.all.keys.filter { it.startsWith("prog_crosshair_") }.forEach { ed.remove(it) }
    ed.apply()
}

private fun formatMb(mb: Long): String =
    if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024f)
    else "$mb MB"

private data class PanelReading(
    val hz: Int = 0,
    val cpuGhz: Float = 0f,
    val ramPercent: Int = 0,
    val ramUsedMb: Long = 0L,
    val battery: Int = 0,
    val tempC: Float = 0f
)

// ── Chamfered shape (cut corners) ──
private class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(c, size.height)
            lineTo(0f, size.height - c)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

// ═══ Canvas icons (12) ═══

@Composable
fun IconLightning(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val path = Path().apply {
            moveTo(13.5f * u, 2f * u)
            lineTo(5f * u, 13.5f * u)
            lineTo(11f * u, 13.5f * u)
            lineTo(10f * u, 22f * u)
            lineTo(19f * u, 10f * u)
            lineTo(13f * u, 10f * u)
            close()
        }
        drawPath(path, tint)
        drawPath(path, tint, style = Stroke(1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconBellSlash(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val st = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val bell = Path().apply {
            moveTo(6f * u, 17f * u)
            lineTo(7.5f * u, 15.5f * u)
            lineTo(7.5f * u, 11f * u)
            cubicTo(7.5f * u, 6.5f * u, 16.5f * u, 6.5f * u, 16.5f * u, 11f * u)
            lineTo(16.5f * u, 15.5f * u)
            lineTo(18f * u, 17f * u)
            close()
        }
        drawPath(bell, tint, style = st)
        drawLine(tint, Offset(10.5f * u, 20f * u), Offset(13.5f * u, 20f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
        drawLine(tint, Offset(4f * u, 4f * u), Offset(20f * u, 20f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
    }
}

@Composable
fun IconLock(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        drawRoundRect(tint, Offset(5f * u, 11f * u), Size(14f * u, 10f * u), CornerRadius(2.5f * u))
        val shackle = Path().apply {
            moveTo(8f * u, 11f * u)
            lineTo(8f * u, 8f * u)
            cubicTo(8f * u, 3.5f * u, 16f * u, 3.5f * u, 16f * u, 8f * u)
            lineTo(16f * u, 11f * u)
        }
        drawPath(shackle, tint, style = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconChart(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val base = 21f * u
        listOf(2.5f to 8f, 8f to 13f, 13.5f to 10f, 19f to 17f).forEach { (x, h) ->
            drawRoundRect(tint, Offset(x * u, base - h * u), Size(4f * u, h * u), CornerRadius(1.6f * u))
        }
    }
}

@Composable
fun IconCrosshair(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val sw = 2f * u
        val c = Offset(12f * u, 12f * u)
        drawCircle(tint, radius = 7f * u, center = c, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, Offset(12f * u, 2f * u), Offset(12f * u, 6f * u), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(12f * u, 18f * u), Offset(12f * u, 22f * u), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(2f * u, 12f * u), Offset(6f * u, 12f * u), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(18f * u, 12f * u), Offset(22f * u, 12f * u), strokeWidth = sw, cap = StrokeCap.Round)
        drawCircle(tint, radius = 1.8f * u, center = c)
    }
}

@Composable
fun IconRefresh(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val sw = 2f * u
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = 8f * u
        // 270° arc starting at 45° (clockwise)
        drawArc(
            tint, startAngle = 45f, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(cx - r, cy - r), size = Size(2f * r, 2f * r),
            style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Arrowhead at the arc end (315°), pointing along the clockwise tangent
        val a = Math.toRadians(315.0)
        val ex = cx + r * cos(a).toFloat()
        val ey = cy + r * sin(a).toFloat()
        val tx = (-sin(a)).toFloat()
        val ty = cos(a).toFloat()
        val nx = cos(a).toFloat()
        val ny = sin(a).toFloat()
        val len = 4f * u
        drawLine(tint, Offset(ex, ey), Offset(ex - tx * len + nx * len, ey - ty * len + ny * len),
            strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(ex, ey), Offset(ex - tx * len - nx * len, ey - ty * len - ny * len),
            strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun IconTrendUp(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val sw = 2f * u
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val axis = Path().apply {
            moveTo(3f * u, 3f * u)
            lineTo(3f * u, 21f * u)
            lineTo(21f * u, 21f * u)
        }
        drawPath(axis, tint, style = st)
        val line = Path().apply {
            moveTo(6.5f * u, 17f * u)
            lineTo(10f * u, 12f * u)
            lineTo(13f * u, 15f * u)
            lineTo(19f * u, 7f * u)
        }
        drawPath(line, tint, style = st)
        val head = Path().apply {
            moveTo(14.5f * u, 7f * u)
            lineTo(19f * u, 7f * u)
            lineTo(19f * u, 11.5f * u)
        }
        drawPath(head, tint, style = st)
    }
}

@Composable
fun IconBattery(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val sw = 2f * u
        drawRoundRect(
            tint, Offset(2f * u, 7f * u), Size(17f * u, 10f * u), CornerRadius(2.5f * u),
            style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(tint, Offset(21.5f * u, 10.5f * u), Offset(21.5f * u, 13.5f * u), strokeWidth = sw, cap = StrokeCap.Round)
        listOf(4.5f, 8.5f, 12.5f).forEach { x ->
            drawRoundRect(tint, Offset(x * u, 9.5f * u), Size(3f * u, 5f * u), CornerRadius(0.8f * u))
        }
    }
}

@Composable
fun IconClose(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val sw = 2f * u
        drawLine(tint, Offset(6f * u, 6f * u), Offset(18f * u, 18f * u), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(18f * u, 6f * u), Offset(6f * u, 18f * u), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun IconChevronDown(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val path = Path().apply {
            moveTo(6f * u, 9f * u)
            lineTo(12f * u, 15f * u)
            lineTo(18f * u, 9f * u)
        }
        drawPath(path, tint, style = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconChevronRight(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val path = Path().apply {
            moveTo(9f * u, 6f * u)
            lineTo(15f * u, 12f * u)
            lineTo(9f * u, 18f * u)
        }
        drawPath(path, tint, style = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconHamburger(modifier: Modifier = Modifier, tint: Color = Color(0xFF1A73E8)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val sw = 2f * u
        val x0 = 3f * u
        val w = size.width - 2f * x0
        drawLine(tint, Offset(x0, 6f * u), Offset(x0 + w * 0.8f, 6f * u), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(x0, 12f * u), Offset(x0 + w * 0.6f, 12f * u), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(x0, 18f * u), Offset(x0 + w * 0.4f, 18f * u), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

// ═══ Crosshair: shared drawing, composable preview, overlay window ═══

private fun DrawScope.drawCrosshairShape(shape: CrosshairShape, color: Color, sw: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    when (shape) {
        CrosshairShape.CROSS -> {
            drawLine(color, Offset(0f, cy), Offset(size.width, cy), sw, StrokeCap.Round)
            drawLine(color, Offset(cx, 0f), Offset(cx, size.height), sw, StrokeCap.Round)
        }
        CrosshairShape.DOT -> {
            drawCircle(color, radius = size.minDimension / 6f, center = Offset(cx, cy))
        }
        CrosshairShape.CIRCLE -> {
            drawCircle(color, radius = size.minDimension / 2.5f, center = Offset(cx, cy), style = Stroke(sw))
        }
        CrosshairShape.DIAMOND -> {
            val path = Path().apply {
                moveTo(cx, 0f); lineTo(size.width, cy)
                lineTo(cx, size.height); lineTo(0f, cy); close()
            }
            drawPath(path, color, style = Stroke(sw))
        }
    }
}

@Composable
fun CrosshairView(config: CrosshairConfig) {
    Canvas(Modifier.size(config.size.dp).padding(2.dp)) {
        drawCrosshairShape(config.shape, Color(config.color).copy(alpha = config.opacity), 2.dp.toPx())
    }
}

/**
 * Plain Android View for the overlay window. A ComposeView added straight to WindowManager
 * crashes (no ViewTreeLifecycleOwner), so the overlay is drawn with android.graphics instead.
 */
private class CrosshairOverlayView(context: Context, private val config: CrosshairConfig) :
    android.view.View(context) {

    private val d = context.resources.displayMetrics.density
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = config.color
        alpha = (config.opacity * 255f).roundToInt().coerceIn(0, 255)
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        strokeWidth = 2f * d
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = ((config.size + 4) * d).roundToInt()
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val pad = 2f * d
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val inner = minOf(w, h) - 2f * pad
        when (config.shape) {
            CrosshairShape.CROSS -> {
                paint.style = android.graphics.Paint.Style.STROKE
                canvas.drawLine(pad, cy, w - pad, cy, paint)
                canvas.drawLine(cx, pad, cx, h - pad, paint)
            }
            CrosshairShape.DOT -> {
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawCircle(cx, cy, inner / 6f, paint)
            }
            CrosshairShape.CIRCLE -> {
                paint.style = android.graphics.Paint.Style.STROKE
                canvas.drawCircle(cx, cy, inner / 2.5f, paint)
            }
            CrosshairShape.DIAMOND -> {
                paint.style = android.graphics.Paint.Style.STROKE
                val p = android.graphics.Path().apply {
                    moveTo(cx, pad); lineTo(w - pad, cy)
                    lineTo(cx, h - pad); lineTo(pad, cy); close()
                }
                canvas.drawPath(p, paint)
            }
        }
    }
}

object CrosshairOverlayManager {
    private var overlayView: android.view.View? = null
    private var windowManager: android.view.WindowManager? = null

    fun canShow(context: Context): Boolean = android.provider.Settings.canDrawOverlays(context)

    fun isShowing(): Boolean = overlayView != null

    @Suppress("DEPRECATION")
    fun show(context: Context, config: CrosshairConfig): Boolean {
        remove(context)
        return try {
            val appContext = context.applicationContext
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                android.view.WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = config.x.coerceAtLeast(0)
                y = config.y.coerceAtLeast(0)
            }
            val view = CrosshairOverlayView(appContext, config)
            var initX = 0
            var initY = 0
            var touchX = 0f
            var touchY = 0f
            view.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initX = params.x; initY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.x = (initX + (event.rawX - touchX)).toInt().coerceAtLeast(0)
                        params.y = (initY + (event.rawY - touchY)).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(view, params) }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        saveProgInt(appContext, "prog_crosshair_x", params.x)
                        saveProgInt(appContext, "prog_crosshair_y", params.y)
                        true
                    }
                    else -> false
                }
            }
            wm.addView(view, params)
            overlayView = view
            windowManager = wm
            true
        } catch (_: Throwable) {
            overlayView = null
            windowManager = null
            false
        }
    }

    fun remove(context: Context) {
        overlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
        overlayView = null
        windowManager = null
    }
}

// ═══ Small building blocks ═══

@Composable
private fun PanelSpinner() {
    Box(Modifier.width(52.dp).height(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(14.dp), color = PnlPrimary, strokeWidth = 2.dp)
    }
}

@Composable
private fun PanelSwitch(checked: Boolean, busy: Boolean, onCheckedChange: (Boolean) -> Unit) {
    if (busy) {
        PanelSpinner()
    } else {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PnlPrimary,
                checkedBorderColor = PnlPrimary,
                uncheckedThumbColor = Color(0xFF80868B),
                uncheckedTrackColor = Color(0xFFE8EAED),
                uncheckedBorderColor = PnlOutline
            )
        )
    }
}

@Composable
private fun PanelPill(
    text: String,
    width: Dp,
    bg: Color,
    fg: Color,
    border: Color?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.width(width).height(24.dp).clip(shape).background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun PanelFeatureRow(
    icon: @Composable () -> Unit,
    label: String,
    showDivider: Boolean = true,
    control: @Composable () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = PnlTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            control()
        }
        if (showDivider) HorizontalDivider(thickness = 1.dp, color = PnlDivider)
    }
}

@Composable
private fun StatusPillSmall(text: String, color: Color) {
    Box(
        Modifier.height(24.dp).clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun PanelBigDisplay(mode: DisplayMode, reading: PanelReading, onTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        Modifier.fillMaxWidth().height(130.dp)
            .scale(bounce.value)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFF8F9FA), Color(0xFFE8F0FE))))
            .border(1.5.dp, PnlOutline, shape)
            .clickable {
                onTap()
                scope.launch {
                    bounce.snapTo(0.9f)
                    bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            }
    ) {
        // Background pattern — FPS mode only
        AnimatedVisibility(
            visible = mode == DisplayMode.FPS,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val bw = 30.dp.toPx()
                val bh = 8.dp.toPx()
                val gap = 10.dp.toPx()
                val alphas = listOf(1f, 0.7f, 0.5f, 0.3f)
                val rowGap = size.height / (alphas.size + 1)
                alphas.forEachIndexed { r, a ->
                    val y = rowGap * (r + 1) - bh / 2f
                    var x = -10.dp.toPx() + (r % 2) * 18.dp.toPx()
                    while (x < size.width) {
                        withTransform({ rotate(12f, pivot = Offset(x + bw / 2f, y + bh / 2f)) }) {
                            drawRoundRect(
                                Color(0xFFE3F2FD).copy(alpha = a),
                                Offset(x, y), Size(bw, bh), CornerRadius(2.dp.toPx())
                            )
                        }
                        x += bw + gap
                    }
                }
            }
        }

        AnimatedContent(
            targetState = mode,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "bigDisplay"
        ) { m ->
            val value = when (m) {
                DisplayMode.FPS -> "${reading.hz}"
                DisplayMode.RAM -> formatMb(reading.ramUsedMb)
                DisplayMode.CPU ->
                    if (reading.cpuGhz > 0f) String.format(java.util.Locale.US, "%.2f", reading.cpuGhz) else "--"
            }
            val label = when (m) {
                DisplayMode.FPS -> "FPS (Hz)"
                DisplayMode.RAM -> "RAM used"
                DisplayMode.CPU -> "CPU GHz"
            }
            Column {
                Text(value, fontSize = 48.sp, fontWeight = FontWeight.Black, color = PnlPrimary, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PnlTextSecondary)
            }
        }
    }
}

// ═══ Crosshair settings dialog ═══

@Composable
private fun CrosshairConfigDialog(
    initial: CrosshairConfig,
    onDismiss: () -> Unit,
    onApply: (CrosshairConfig) -> Unit
) {
    var shape by remember { mutableStateOf(initial.shape) }
    var color by remember { mutableStateOf(initial.color) }
    var sizeDp by remember { mutableFloatStateOf(initial.size.toFloat()) }
    var opacity by remember { mutableFloatStateOf(initial.opacity) }
    val palette = remember {
        listOf(0xFF1A73E8, 0xFF34A853, 0xFF795548, 0xFFE91E63, 0xFFEA4335, 0xFFFFFFFF, 0xFFFF9800)
            .map { it.toInt() }
    }
    val sliderColors = SliderDefaults.colors(
        thumbColor = PnlPrimary,
        activeTrackColor = PnlPrimary,
        inactiveTrackColor = PnlOutline
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.9f).widthIn(max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PnlSurface)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Crosshair Settings",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PnlTextPrimary
                )
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { IconClose(Modifier.size(20.dp), PnlTextSecondary) }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = PnlDivider)
            Spacer(Modifier.height(12.dp))

            Text("Shape", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PnlTextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CrosshairShape.values().forEach { s ->
                    val selected = s == shape
                    val btnShape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier.size(40.dp).clip(btnShape)
                            .background(if (selected) Color(0xFFE8F0FE) else Color.White)
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) PnlPrimary else PnlOutline,
                                btnShape
                            )
                            .clickable { shape = s },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(22.dp)) {
                            drawCrosshairShape(s, PnlTextPrimary, 1.5.dp.toPx())
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Text("Color", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PnlTextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                palette.forEach { c ->
                    val selected = c == color
                    val isWhite = c == 0xFFFFFFFF.toInt()
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(Color(c))
                            .then(
                                when {
                                    selected -> Modifier.border(3.dp, PnlTextPrimary, CircleShape)
                                    isWhite -> Modifier.border(1.dp, PnlOutline, CircleShape)
                                    else -> Modifier
                                }
                            )
                            .clickable { color = c }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Size: ${sizeDp.roundToInt()}dp",
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PnlTextSecondary
                    )
                    Slider(
                        value = sizeDp,
                        onValueChange = { sizeDp = it },
                        valueRange = 20f..64f,
                        steps = 43,
                        colors = sliderColors
                    )
                    Text(
                        "Opacity: ${(opacity * 100).roundToInt()}%",
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PnlTextSecondary
                    )
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.3f..1.0f,
                        colors = sliderColors
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3C4043)),
                    contentAlignment = Alignment.Center
                ) {
                    CrosshairView(
                        CrosshairConfig(shape, color, sizeDp.roundToInt(), opacity, initial.x, initial.y)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = PnlDivider)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, PnlOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PnlTextSecondary)
                ) { Text("CANCEL", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        onApply(CrosshairConfig(shape, color, sizeDp.roundToInt(), opacity, initial.x, initial.y))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PnlPrimary, contentColor = Color.White)
                ) { Text("APPLY", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ═══ The floating panel ═══

@Composable
fun ProgrammerFloatingPanel(app: AppInfoItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supportedRates = remember { ShizukuManager.getSupportedRefreshRates(context) }

    var config by remember {
        mutableStateOf(
            ProgrammerConfig(
                packageName = app.packageName,
                label = app.label,
                performanceMode = loadProgBool(context, "prog_perf_mode", false),
                dnd = loadProgBool(context, "prog_dnd", false),
                lockRotation = loadProgBool(context, "prog_lock_rotation", false),
                targetFps = loadProgInt(context, "prog_fps_target", 0),
                crosshairActive = CrosshairOverlayManager.isShowing(),
                statusMonitor = loadProgBool(context, "prog_status_monitor", false),
                extremeSaver = loadProgBool(context, "prog_power_saver", false)
            )
        )
    }
    var perfBusy by remember { mutableStateOf(false) }
    var saverBusy by remember { mutableStateOf(false) }
    var fpsBusy by remember { mutableStateOf(false) }
    var fpsMenuOpen by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var displayMode by remember { mutableStateOf(DisplayMode.FPS) }
    var reading by remember { mutableStateOf(PanelReading()) }
    var showCrosshairDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val statusScroll = rememberScrollState()
    val listState = rememberLazyListState()

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // ── Polling: every 1 second, cancelled automatically when the panel leaves composition ──
    LaunchedEffect(Unit) {
        if (ShizukuManager.isAvailable()) {
            withContext(Dispatchers.IO) { StatusDetector.detectAll() }
        }
        while (true) {
            val hz = StatusDetector.readRefreshHz(context)
            val ram = StatusDetector.readRam(context)
            val battery = StatusDetector.readBattery(context)
            val statusOn = config.statusMonitor
            val needCpu = statusOn || displayMode == DisplayMode.CPU
            val cpu = if (needCpu) withContext(Dispatchers.IO) { StatusDetector.readCpuGhz().first } else 0f
            val temp = if (statusOn) withContext(Dispatchers.IO) { StatusDetector.readTemperature().first } else 0f
            reading = PanelReading(
                hz = hz,
                cpuGhz = cpu,
                ramPercent = ram.first,
                ramUsedMb = ram.second,
                battery = battery,
                tempC = temp
            )
            delay(1000L)
        }
    }

    // ── Feature actions ──
    fun setPerformance(enable: Boolean) {
        if (perfBusy) return
        scope.launch {
            perfBusy = true
            val r = withContext(Dispatchers.IO) {
                ShizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled $enable")
            }
            perfBusy = false
            if (r.success) {
                config = config.copy(performanceMode = enable)
                saveProgBool(context, "prog_perf_mode", enable)
            } else toast("Command failed. Is Shizuku running?")
        }
    }

    fun setPowerSaver(enable: Boolean) {
        if (saverBusy) return
        scope.launch {
            saverBusy = true
            val r = withContext(Dispatchers.IO) {
                ShizukuManager.executeCommand("cmd power set-adaptive-power-saver-enabled $enable")
            }
            saverBusy = false
            if (r.success) {
                config = config.copy(extremeSaver = enable)
                saveProgBool(context, "prog_power_saver", enable)
            } else toast("Command failed. Is Shizuku running?")
        }
    }

    fun setDnd(enable: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (enable) {
            if (nm.isNotificationPolicyAccessGranted) {
                runCatching { nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE) }
                config = config.copy(dnd = true)
                saveProgBool(context, "prog_dnd", true)
            } else {
                toast("Grant DND access first")
                runCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        } else {
            if (nm.isNotificationPolicyAccessGranted) {
                runCatching { nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL) }
            }
            config = config.copy(dnd = false)
            saveProgBool(context, "prog_dnd", false)
        }
    }

    fun setRotationLock(enable: Boolean) {
        (context as? android.app.Activity)?.requestedOrientation =
            if (enable) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        config = config.copy(lockRotation = enable)
        saveProgBool(context, "prog_lock_rotation", enable)
    }

    fun setFpsTarget(rate: Int) {
        if (fpsBusy) return
        scope.launch {
            fpsBusy = true
            val ok = withContext(Dispatchers.IO) {
                val a = ShizukuManager.executeCommand("settings put system peak_refresh_rate $rate.0")
                val b = ShizukuManager.executeCommand("settings put system min_refresh_rate $rate.0")
                a.success && b.success
            }
            fpsBusy = false
            if (ok) {
                config = config.copy(targetFps = rate)
                saveProgInt(context, "prog_fps_target", rate)
            } else toast("Command failed. Is Shizuku running?")
        }
    }

    fun onCrosshairTap() {
        if (config.crosshairActive) {
            CrosshairOverlayManager.remove(context)
            config = config.copy(crosshairActive = false)
        } else if (CrosshairOverlayManager.canShow(context)) {
            showCrosshairDialog = true
        } else {
            toast("Overlay permission required")
            runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun doReset() {
        scope.launch {
            withContext(Dispatchers.IO) {
                ShizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled false")
                ShizukuManager.executeCommand("cmd power set-adaptive-power-saver-enabled false")
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                runCatching { nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL) }
            }
            (context as? android.app.Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            CrosshairOverlayManager.remove(context)
            clearProgPrefs(context)
            config = config.copy(
                performanceMode = false,
                dnd = false,
                lockRotation = false,
                crosshairActive = false,
                statusMonitor = false,
                extremeSaver = false
            )
            toast("Settings reset")
        }
    }

    // ── Dialogs ──
    if (showCrosshairDialog) {
        CrosshairConfigDialog(
            initial = loadCrosshairConfig(context),
            onDismiss = { showCrosshairDialog = false },
            onApply = { cfg ->
                showCrosshairDialog = false
                saveCrosshairConfig(context, cfg)
                if (CrosshairOverlayManager.show(context, cfg)) {
                    config = config.copy(crosshairActive = true)
                } else {
                    toast("Could not show crosshair")
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = PnlSurface,
            title = { Text("Reset All Settings?", fontWeight = FontWeight.Bold, color = PnlTextPrimary) },
            text = {
                Text(
                    "Revert Performance, DND, Rotation, Crosshair, and Power Saver. Refresh Rate will NOT change.",
                    color = PnlTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; doReset() }) {
                    Text("RESET", color = PnlRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", color = PnlTextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── Layout ──
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val panelWidth by animateDpAsState(if (collapsed) 0.dp else 320.dp, tween(300), label = "panelWidth")
    val panelShape = remember { ChamferShape(14.dp) }
    val noRipple = remember { MutableInteractionSource() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelHeight = minOf(400.dp, maxHeight * 0.92f)

        // Dim backdrop — tap outside the panel closes it (hidden while collapsed)
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0x40000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
            )
        }

        AnimatedVisibility(
            visibleState = visibleState,
            modifier = Modifier.align(Alignment.CenterStart).systemBarsPadding().padding(4.dp),
            enter = slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)),
            exit = slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (panelWidth > 0.5.dp) {
                    Box(
                        Modifier.width(panelWidth).height(panelHeight)
                            .shadow(2.dp, panelShape)
                            .background(PnlSurface, panelShape)
                            .border(1.5.dp, PnlOutline, panelShape)
                            .clip(panelShape)
                            // swallow taps so they never reach the backdrop
                            .clickable(interactionSource = noRipple, indication = null) { }
                    ) {
                        Column(Modifier.requiredWidth(320.dp).fillMaxHeight().padding(12.dp)) {
                            // Header
                            Row(
                                Modifier.fillMaxWidth().height(40.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose),
                                    contentAlignment = Alignment.Center
                                ) { IconClose(Modifier.size(20.dp), PnlTextSecondary) }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "TURBOTUNE",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = PnlPrimary,
                                    letterSpacing = 2.sp
                                )
                                Spacer(Modifier.weight(1f))
                                Spacer(Modifier.size(32.dp))
                            }

                            // Status bar (Feature 7)
                            if (config.statusMonitor) {
                                Row(
                                    Modifier.fillMaxWidth().height(32.dp).horizontalScroll(statusScroll),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusPillSmall("FPS ${reading.hz}", PnlPrimary)
                                    StatusPillSmall(
                                        if (reading.cpuGhz > 0f) "CPU " + String.format(java.util.Locale.US, "%.1f", reading.cpuGhz)
                                        else "CPU --",
                                        PnlPrimary
                                    )
                                    StatusPillSmall("RAM ${reading.ramPercent}%", PnlPrimary)
                                    StatusPillSmall(
                                        "Batt ${reading.battery}%",
                                        if (reading.battery > 20) PnlGreen else PnlRed
                                    )
                                    StatusPillSmall(
                                        if (reading.tempC > 0f) "${reading.tempC.roundToInt()}°C" else "--°C",
                                        when {
                                            reading.tempC <= 0f -> PnlTextSecondary
                                            reading.tempC < 50f -> PnlGreen
                                            reading.tempC < 65f -> PnlAmber
                                            else -> PnlRed
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // Big display (tap to cycle FPS → RAM → CPU)
                            PanelBigDisplay(
                                mode = displayMode,
                                reading = reading,
                                onTap = {
                                    displayMode = when (displayMode) {
                                        DisplayMode.FPS -> DisplayMode.RAM
                                        DisplayMode.RAM -> DisplayMode.CPU
                                        DisplayMode.CPU -> DisplayMode.FPS
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))

                            // Feature list
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                                    // 1. Performance
                                    item {
                                        PanelFeatureRow(
                                            icon = { IconLightning(Modifier.size(18.dp), PnlPrimary) },
                                            label = "Performance"
                                        ) {
                                            PanelSwitch(config.performanceMode, perfBusy) { setPerformance(it) }
                                        }
                                    }
                                    // 2. DND
                                    item {
                                        PanelFeatureRow(
                                            icon = { IconBellSlash(Modifier.size(18.dp), PnlPrimary) },
                                            label = "DND"
                                        ) {
                                            PanelSwitch(config.dnd, false) { setDnd(it) }
                                        }
                                    }
                                    // 3. Rotation
                                    item {
                                        PanelFeatureRow(
                                            icon = { IconLock(Modifier.size(18.dp), PnlPrimary) },
                                            label = "Rotation"
                                        ) {
                                            PanelSwitch(config.lockRotation, false) { setRotationLock(it) }
                                        }
                                    }
                                    // 4. Adjust FPS
                                    item {
                                        val currentRate = if (config.targetFps > 0) config.targetFps else reading.hz
                                        PanelFeatureRow(
                                            icon = { IconChart(Modifier.size(18.dp), PnlPrimary) },
                                            label = "FPS: $currentRate Hz"
                                        ) {
                                            if (fpsBusy) {
                                                PanelSpinner()
                                            } else {
                                                Box {
                                                    Row(
                                                        Modifier.clip(RoundedCornerShape(8.dp))
                                                            .clickable { fpsMenuOpen = true }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            "Change",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = PnlPrimary
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        IconChevronDown(Modifier.size(16.dp), PnlPrimary)
                                                    }
                                                    DropdownMenu(
                                                        expanded = fpsMenuOpen,
                                                        onDismissRequest = { fpsMenuOpen = false },
                                                        modifier = Modifier.background(PnlSurface)
                                                    ) {
                                                        supportedRates.forEach { rate ->
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Text(
                                                                        "$rate Hz",
                                                                        color = PnlTextPrimary,
                                                                        fontWeight = if (config.targetFps == rate) FontWeight.Bold
                                                                        else FontWeight.Normal
                                                                    )
                                                                },
                                                                onClick = {
                                                                    fpsMenuOpen = false
                                                                    setFpsTarget(rate)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // 5. Crosshair
                                    item {
                                        PanelFeatureRow(
                                            icon = { IconCrosshair(Modifier.size(18.dp), PnlPrimary) },
                                            label = if (config.crosshairActive) "Crosshair: ON" else "Crosshair: OFF"
                                        ) {
                                            PanelPill(
                                                text = if (config.crosshairActive) "ON" else "OFF",
                                                width = 48.dp,
                                                bg = if (config.crosshairActive) PnlPrimary else PnlDivider,
                                                fg = if (config.crosshairActive) Color.White else PnlTextSecondary,
                                                border = null,
                                                onClick = { onCrosshairTap() }
                                            )
                                        }
                                    }
                                    // 6. Reset
                                    item {
                                        PanelFeatureRow(
                                            icon = { IconRefresh(Modifier.size(18.dp), PnlRed) },
                                            label = "Reset All"
                                        ) {
                                            PanelPill(
                                                text = "RESET",
                                                width = 56.dp,
                                                bg = Color(0xFFFFEBEE),
                                                fg = PnlRed,
                                                border = PnlRed,
                                                onClick = { showResetDialog = true }
                                            )
                                        }
                                    }
                                    // 7. Status bar
                                    item {
                                        PanelFeatureRow(
                                            icon = { IconTrendUp(Modifier.size(18.dp), PnlPrimary) },
                                            label = "Status Bar"
                                        ) {
                                            PanelSwitch(config.statusMonitor, false) {
                                                config = config.copy(statusMonitor = it)
                                                saveProgBool(context, "prog_status_monitor", it)
                                            }
                                        }
                                    }
                                    // 8. Extreme power saver
                                    item {
                                        PanelFeatureRow(
                                            icon = { IconBattery(Modifier.size(18.dp), PnlPrimary) },
                                            label = "Power Saver",
                                            showDivider = false
                                        ) {
                                            PanelSwitch(config.extremeSaver, saverBusy) { setPowerSaver(it) }
                                        }
                                    }
                                }

                                // Thin scroll indicator on the right edge
                                Canvas(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(2.dp)) {
                                    val info = listState.layoutInfo
                                    val total = info.totalItemsCount
                                    val shown = info.visibleItemsInfo
                                    if (total == 0 || shown.isEmpty()) return@Canvas
                                    val rowH = shown.first().size.toFloat()
                                    val contentH = rowH * total
                                    val viewH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                                    if (contentH <= viewH) return@Canvas
                                    val scrollY = shown.first().index * rowH - shown.first().offset
                                    val thumbH = (viewH / contentH * size.height).coerceAtLeast(12.dp.toPx())
                                    val thumbY = (scrollY / (contentH - viewH)).coerceIn(0f, 1f) * (size.height - thumbH)
                                    drawRoundRect(
                                        PnlTextSecondary.copy(alpha = 0.45f),
                                        Offset(0f, thumbY), Size(size.width, thumbH), CornerRadius(size.width / 2f)
                                    )
                                }

                                // Scroll hint: fade + chevron while more rows are below
                                if (listState.canScrollForward) {
                                    Box(
                                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(20.dp)
                                            .background(Brush.verticalGradient(listOf(Color.Transparent, PnlSurface))),
                                        contentAlignment = Alignment.BottomCenter
                                    ) { IconChevronDown(Modifier.size(14.dp), PnlTextSecondary) }
                                }
                            }
                        }
                    }
                }

                // Side tab (OPPO Game Space style) — toggles collapse / expand
                Box(
                    Modifier.width(20.dp).height(64.dp)
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .background(Color(0xFFE0E0E0))
                        .clickable { collapsed = !collapsed },
                    contentAlignment = Alignment.Center
                ) { IconHamburger(Modifier.size(16.dp), PnlTextSecondary) }
            }
        }
    }
}

// ═══ 8. FEATURE: ปรับแต่งระบบ (App Tuning) ═══

@Composable
fun RepairBoxIcon(modifier: Modifier = Modifier, tint: Color = EmeraldPrimary) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.8f * (size.minDimension / 24f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // ตัวกล่อง (สี่เหลี่ยมคางหมู — เปิดด้านบน)
        val box = Path().apply {
            moveTo(w * 0.14f, h * 0.55f)    // top-left
            lineTo(w * 0.86f, h * 0.55f)    // top-right
            lineTo(w * 0.78f, h * 0.92f)    // bottom-right
            lineTo(w * 0.22f, h * 0.92f)    // bottom-left
            close()
        }
        drawPath(box, tint, style = stroke)

        // ฝากล่องเปิดออก 2 ข้าง
        drawLine(tint, Offset(w * 0.14f, h * 0.55f), Offset(w * 0.04f, h * 0.38f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.86f, h * 0.55f), Offset(w * 0.96f, h * 0.38f), strokeWidth = sw, cap = StrokeCap.Round)

        // ประแจ (เส้นทแยง)
        drawLine(tint, Offset(w * 0.30f, h * 0.48f), Offset(w * 0.66f, h * 0.18f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)

        // หัวประแจ (หกเหลี่ยม)
        val head = Path().apply {
            val cx = w * 0.70f
            val cy = h * 0.15f
            val r = 2.5f * (size.minDimension / 24f)
            for (i in 0..5) {
                val angle = Math.toRadians((i * 60 - 30).toDouble())
                val px = cx + cos(angle).toFloat() * r
                val py = cy + sin(angle).toFloat() * r
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(head, tint, style = stroke)
    }
}

/** แบตเตอรี่: ตัวขอบโค้ง + ขั้วขวา + แถบเขียว 3 แถบ */
@Composable
fun BatteryIcon(modifier: Modifier = Modifier, tint: Color = EmeraldPrimary) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 2.dp.toPx()
        val bodyLeft = sw / 2f
        val bodyTop = h * 0.28f
        val bodyW = w * 0.84f - sw
        val bodyH = h * 0.44f

        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyH * 0.22f),
            style = Stroke(width = sw)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft + bodyW + sw * 0.6f, h * 0.41f),
            size = Size(w * 0.07f, h * 0.18f),
            cornerRadius = CornerRadius(sw / 2f)
        )
        val pad = sw * 1.4f
        val gap = sw * 0.9f
        val barW = (bodyW - 2f * pad - 2f * gap) / 3f
        val barH = bodyH - 2f * pad
        repeat(3) { i ->
            drawRoundRect(
                color = tint,
                topLeft = Offset(bodyLeft + pad + i * (barW + gap), bodyTop + pad),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(sw / 2f)
            )
        }
    }
}

/** จรวด: ตัวทรงรี + หน้าต่างวงกลม + ปีก 2 ข้าง + เปลวไฟเส้นโค้ง */
@Composable
fun RocketIcon(modifier: Modifier = Modifier, tint: Color = ErrorColor) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        val ox = (size.width - 100f * u) / 2f
        val oy = (size.height - 100f * u) / 2f
        fun x(v: Float) = ox + v * u
        fun y(v: Float) = oy + v * u
        val stroke = Stroke(width = 3.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val body = Path().apply {
            moveTo(x(50f), y(6f))
            cubicTo(x(70f), y(22f), x(68f), y(50f), x(62f), y(66f))
            lineTo(x(38f), y(66f))
            cubicTo(x(32f), y(50f), x(30f), y(22f), x(50f), y(6f))
            close()
        }
        drawPath(body, tint, style = stroke)
        drawCircle(tint, radius = 6f * u, center = Offset(x(50f), y(34f)), style = stroke)

        val leftWing = Path().apply { moveTo(x(38f), y(52f)); lineTo(x(24f), y(72f)); lineTo(x(38f), y(66f)) }
        val rightWing = Path().apply { moveTo(x(62f), y(52f)); lineTo(x(76f), y(72f)); lineTo(x(62f), y(66f)) }
        drawPath(leftWing, tint, style = stroke)
        drawPath(rightWing, tint, style = stroke)

        val flame = Path().apply {
            moveTo(x(43f), y(73f))
            quadraticBezierTo(x(50f), y(100f), x(57f), y(73f))
        }
        drawPath(flame, tint, style = stroke)
    }
}

/** Text ที่กำหนด lineHeight เอง (Material3 Text ที่ตั้งแค่ fontSize จะใช้ lineHeight 24sp ทำให้แถวสูงเกินไป) */
@Composable
private fun Txt(
    text: String,
    size: Int,
    color: Color = MaterialTheme.colorScheme.onSurface,
    weight: FontWeight? = null,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = (size * 1.35f).sp,
        maxLines = maxLines,
        overflow = overflow
    )
}

private data class TuningSwitch(val title: String, val subtitle: String)

private val TUNING_SWITCHES = listOf(
    TuningSwitch("Compile Speed", "คอมไพล์แอปให้เปิดเร็วขึ้น (ใช้พื้นที่เพิ่มขึ้น)"),
    TuningSwitch("Compile Speed Profile", "คอมไพล์แบบสมดุล เปิดเร็ว + ประหยัดพื้นที่"),
    TuningSwitch("Compile Everything", "คอมไพล์เต็มรูปแบบ เปิดเร็วสุด แต่ใช้พื้นที่มาก"),
    TuningSwitch("Trim Memory", "คืนหน่วยความจำของแอปทันที (ไม่ถาวร)"),
    TuningSwitch("Reset Compile", "ล้างการคอมไพล์ทั้งหมด กลับสู่ค่าเริ่มต้น")
)

private fun tuningCommand(index: Int, pkg: String): String = when (index) {
    0 -> "cmd package compile -m speed -f $pkg"
    1 -> "cmd package compile -m speed-profile -f $pkg"
    2 -> "cmd package compile -m everything $pkg"
    3 -> "am send-trim-memory $pkg COMPLETE"
    else -> "cmd package compile --reset $pkg"
}

@Composable
fun AppTuningScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppInfoItem>?>(null) }
    var filter by remember { mutableStateOf(TuningFilter.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showTuningInfoDialog by remember { mutableStateOf(false) }
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    val bucketStates = remember { mutableStateMapOf<String, TuningBucketState>() }
    val compileModes = remember { mutableStateMapOf<String, CompileMode?>() }
    val switchLoading = remember { mutableStateMapOf<String, String?>() }   // "${pkg}_$index" → loading / success / error
    val rowBusy = remember { mutableStateMapOf<String, Boolean>() }

    fun refresh() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { TurboSpaceManager.getFilteredApps(context) }
            loaded.forEach { app ->
                val pkg = app.packageName
                bucketStates[pkg] = runCatching {
                    TuningBucketState.valueOf(ShizukuManager.loadTuningBucket(context, pkg))
                }.getOrDefault(TuningBucketState.NORMAL)
                compileModes[pkg] = ShizukuManager.loadCompileMode(context, pkg)
            }
            apps = loaded
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val shown = remember(apps, filter) {
        val all = apps.orEmpty()
        when (filter) {
            TuningFilter.ALL -> all
            TuningFilter.NO_SYSTEM -> all.filter { (it.info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            TuningFilter.NO_DOWNLOADED -> all.filter { (it.info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
        }
    }

    fun toastError(result: CommandResult) {
        val msg = result.error ?: result.output.ifBlank { "Command failed (exit ${result.exitCode})" }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    // ── Swipe: ขวา = Inactive, ซ้าย = Active ──
    fun swipe(app: AppInfoItem, target: TuningBucketState) {
        val pkg = app.packageName
        if (rowBusy[pkg] == true) return
        scope.launch {
            rowBusy[pkg] = true
            val cmd = if (target == TuningBucketState.INACTIVE) {
                "cmd activity set-inactive $pkg true"
            } else {
                "cmd activity set-standby-bucket $pkg active"
            }
            val result = ShizukuManager.runTuningCommand(pkg, cmd)
            rowBusy.remove(pkg)
            if (result.success) {
                bucketStates[pkg] = target
                ShizukuManager.saveTuningBucket(context, pkg, target.name)
            } else {
                toastError(result)   // ไม่เปลี่ยน state = revert
            }
        }
    }

    // ── Switch ทั้ง 5 ──
    fun anyLoading(pkg: String) = (0..4).any { switchLoading["${pkg}_$it"] == "loading" }

    fun runSwitch(app: AppInfoItem, index: Int) {
        val pkg = app.packageName
        if (anyLoading(pkg)) return
        val key = "${pkg}_$index"
        scope.launch {
            switchLoading[key] = "loading"
            val timeout = if (index == 3) PROCESS_TIMEOUT_MS else COMPILE_TIMEOUT_MS
            val result = ShizukuManager.runTuningCommand(pkg, tuningCommand(index, pkg), timeout)
            if (result.success) {
                when (index) {
                    0, 1, 2 -> {
                        val mode = CompileMode.values().getOrNull(index)
                        if (mode != null) {
                            compileModes[pkg] = mode
                            ShizukuManager.saveCompileMode(context, pkg, mode)
                        }
                    }
                    4 -> {
                        compileModes[pkg] = null
                        ShizukuManager.saveCompileMode(context, pkg, null)
                    }
                }
                switchLoading[key] = "success"
            } else {
                switchLoading[key] = "error"
                toastError(result)
            }
            delay(2000)
            switchLoading.remove(key)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {

        // ─── Header ───
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            Text(
                "ปรับแต่งระบบ",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = EmeraldPrimary
            )

            // ปุ่มขวา: Refresh + Filter + Info (36dp เพื่อไม่ชนชื่อหัวข้อบนจอแคบ)
            Row(
                Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable { refresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("↻", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                Box {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                            .clickable { showFilterMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        ThreeLinesIcon(Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        FilterMenuItem("แสดงทั้งหมด", filter == TuningFilter.ALL) {
                            filter = TuningFilter.ALL; showFilterMenu = false
                        }
                        FilterMenuItem("ไม่มีระบบ", filter == TuningFilter.NO_SYSTEM) {
                            filter = TuningFilter.NO_SYSTEM; showFilterMenu = false
                        }
                        FilterMenuItem("ไม่มีแอป Download", filter == TuningFilter.NO_DOWNLOADED) {
                            filter = TuningFilter.NO_DOWNLOADED; showFilterMenu = false
                        }
                    }
                }

                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { showInfoDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("i", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // ─── List ───
        when {
            apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ไม่พบแอปที่แสดงได้", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.packageName }) { app ->
                    val pkg = app.packageName
                    Column {
                        TuningAppRow(
                            app = app,
                            bucketState = bucketStates[pkg] ?: TuningBucketState.NORMAL,
                            expanded = expandedPkg == pkg,
                            busy = rowBusy[pkg] == true,
                            onToggle = { expandedPkg = if (expandedPkg == pkg) null else pkg },
                            onSwipeRight = { swipe(app, TuningBucketState.INACTIVE) },
                            onSwipeLeft = { swipe(app, TuningBucketState.ACTIVE) }
                        )
                        AnimatedVisibility(visible = expandedPkg == pkg) {
                            TuningPanel(
                                app = app,
                                activeMode = compileModes[pkg],
                                states = (0..4).map { switchLoading["${pkg}_$it"] },
                                anyLoading = anyLoading(pkg),
                                onToggle = { index ->
                                    val currentMode = CompileMode.values().getOrNull(index)
                                    if (index <= 2 && currentMode != null && compileModes[pkg] == currentMode) {
                                        Toast.makeText(
                                            context,
                                            "กด Reset Compile ก่อนจึงจะปิด/สลับโหมดได้",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        runSwitch(app, index)
                                    }
                                },
                                onShowInfo = { showTuningInfoDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── Info Dialog (Header) ───
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("คำอธิบายสีและสถานะ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt("สถานะการปรับแต่ง", 14, EmeraldPrimary, FontWeight.SemiBold)
                    Txt("• แถวเขียว = Inactive (ประหยัดพลังงาน)", 13)
                    Txt("• แถวแดง = Active (แนวหน้า)", 13)
                    Spacer(Modifier.height(8.dp))
                    Txt("ประเภทแอป", 14, EmeraldPrimary, FontWeight.SemiBold)
                    Txt("• ขอบแดง + แอประบบ = ติดมากับเครื่อง", 13)
                    Txt("• ขอบเขียว + แอปทั่วไป = ดาวน์โหลดจาก Store", 13)
                    Spacer(Modifier.height(8.dp))
                    Txt("ระดับอันตราย", 14, EmeraldPrimary, FontWeight.SemiBold)
                    Txt("• ปลอดภัย / ปานกลาง / อันตรายมาก", 13)
                    Spacer(Modifier.height(8.dp))
                    Txt("หมายเหตุ: ดูรายละเอียดฟีเจอร์ในแต่ละแอปได้จากไอคอน ⓘ ใน panel", 13, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("ตกลง", color = EmeraldPrimary) }
            }
        )
    }

    if (showTuningInfoDialog) {
        TuningInfoDialog(onDismiss = { showTuningInfoDialog = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TuningAppRow(
    app: AppInfoItem,
    bucketState: TuningBucketState,
    expanded: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val maxDragPx = thresholdPx * 1.6f

    var offsetX by remember { mutableFloatStateOf(0f) }
    val latestSwipeRight by rememberUpdatedState(onSwipeRight)
    val latestSwipeLeft by rememberUpdatedState(onSwipeLeft)

    val background by animateColorAsState(
        targetValue = when (bucketState) {
            TuningBucketState.NORMAL -> MaterialTheme.colorScheme.surface
            TuningBucketState.INACTIVE -> RowGreenBg
            TuningBucketState.ACTIVE -> RowRedBg
        },
        label = "tuningRowBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when (bucketState) {
            TuningBucketState.NORMAL -> MaterialTheme.colorScheme.outline
            TuningBucketState.INACTIVE -> EmeraldPrimary
            TuningBucketState.ACTIVE -> ErrorColor
        },
        label = "tuningRowBorder"
    )
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "tuningChevron")
    val previewAlpha = (abs(offsetX) / thresholdPx).coerceIn(0f, 1f) * 0.5f

    val settleBack: () -> Unit = {
        val from = offsetX
        scope.launch {
            animate(
                initialValue = from,
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) { value, _ -> offsetX = value }
        }
    }

    val isSystem = (app.info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    val safety = remember(app.packageName) { ShizukuManager.calculateSafety(app.info) }

    Box(Modifier.fillMaxWidth().heightIn(min = 62.dp)) {
        // preview ไอคอนปลายทางจางๆ ระหว่างลาก
        if (offsetX > 0f) {
            BatteryIcon(
                Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(24.dp).alpha(previewAlpha),
                EmeraldPrimary
            )
        }
        if (offsetX < 0f) {
            RocketIcon(
                Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(24.dp).alpha(previewAlpha),
                ErrorColor
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .shadow(2.dp, shape)
                .background(background, shape)
                .border(1.dp, borderColor, shape)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(-maxDragPx, maxDragPx)
                        },
                        onDragEnd = {
                            val released = offsetX
                            when {
                                released >= thresholdPx -> latestSwipeRight()
                                released <= -thresholdPx -> latestSwipeLeft()
                            }
                            settleBack()
                        },
                        onDragCancel = { settleBack() }
                    )
                }
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (bucketState) {
                TuningBucketState.INACTIVE -> {
                    BatteryIcon(Modifier.size(24.dp), EmeraldPrimary)   // แบตเตอรี่ มุมซ้ายของแถว
                    Spacer(Modifier.width(8.dp))
                }
                TuningBucketState.ACTIVE -> {
                    RocketIcon(Modifier.size(24.dp), ErrorColor)         // จรวด มุมซ้ายของแถว
                    Spacer(Modifier.width(8.dp))
                }
                TuningBucketState.NORMAL -> Unit
            }

            AppIconImage(app.info, app.packageName, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Txt(app.label, 15, MaterialTheme.colorScheme.onSurface, FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Txt(app.packageName, 12, MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Badge 1: สถานะการปรับแต่ง
                    when (bucketState) {
                        TuningBucketState.NORMAL -> TuningBadge("ปกติ", RowGrayBg, Color(0xFF757575), null)
                        TuningBucketState.INACTIVE -> TuningBadge("Inactive", RowGreenBg, SuccessGreen, null)
                        TuningBucketState.ACTIVE -> TuningBadge("Active", RowRedBg, ErrorColor, null)
                    }
                    // Badge 2: ประเภท
                    TuningBadge(
                        text = if (isSystem) "แอประบบ" else "แอปทั่วไป",
                        bg = if (isSystem) RowRedBg else RowGreenBg,
                        fg = if (isSystem) ErrorColor else SuccessGreen,
                        border = if (isSystem) ErrorColor else SuccessGreen
                    )
                    // Badge 3: ความปลอดภัย
                    val (safetyText, safetyBg, safetyColor) = when (safety) {
                        SafetyLevel.SAFE -> Triple("ปลอดภัย", RowGreenBg, SuccessGreen)
                        SafetyLevel.MEDIUM -> Triple("ปานกลาง", RowYellowBg, WarnAmber)
                        SafetyLevel.DANGEROUS -> Triple("อันตรายมาก", RowRedBg, ErrorColor)
                    }
                    TuningBadge(safetyText, safetyBg, safetyColor, null)
                }
            }

            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = EmeraldPrimary)
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Text(">", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(chevronRotation))
            }
        }
    }
}

@Composable
private fun TuningBadge(text: String, bg: Color, fg: Color, border: Color?) {
    val shape = RoundedCornerShape(50)
    var modifier = Modifier.clip(shape).background(bg)
    if (border != null) {
        modifier = modifier.border(1.dp, border, shape)
    }
    Box(modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
        Txt(text, 11, fg, FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun TuningPanel(
    app: AppInfoItem,
    activeMode: CompileMode?,
    states: List<String?>,
    anyLoading: Boolean,
    onToggle: (Int) -> Unit,
    onShowInfo: () -> Unit
) {
    val context = LocalContext.current
    val panelHeight = LocalConfiguration.current.screenHeightDp.dp * 0.20f
    val shape = RoundedCornerShape(14.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 8.dp, end = 8.dp)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp)
    ) {
        // ── ส่วน A: ข้อมูลแอป (สูงขั้นต่ำ 20% ของหน้าจอ) ──
        Column(Modifier.fillMaxWidth().heightIn(min = panelHeight)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AppIconImage(app.info, app.packageName, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(app.label, 17, MaterialTheme.colorScheme.onSurface, FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Txt(app.packageName, 12, MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable(onClick = onShowInfo),
                    contentAlignment = Alignment.Center
                ) {
                    Text("i", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE0F2FE))
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("package", app.packageName))
                        Toast.makeText(context, "คัดลอกแล้ว: ${app.packageName}", Toast.LENGTH_SHORT).show()
                    }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        app.packageName, 12, EmeraldPrimary,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Txt("คัดลอก", 12, EmeraldPrimary, FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── ส่วน B: 5 switches ──
        TUNING_SWITCHES.forEachIndexed { index, sw ->
            val state = states[index]
            val isCompile = index <= 2
            val mode = if (isCompile) CompileMode.values().getOrNull(index) else null
            val checked = isCompile && activeMode == mode
            val enabled = !anyLoading && (!isCompile || activeMode == null || activeMode == mode)

            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(sw.title, 14, MaterialTheme.colorScheme.onSurface, FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(sw.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(104.dp), contentAlignment = Alignment.CenterEnd) {
                        when (state) {
                            "loading" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = EmeraldPrimary)
                                Spacer(Modifier.width(6.dp))
                                Txt("โปรดรอสักครู่", 11, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            "success" -> Txt("สำเร็จ", 12, EmeraldPrimary)
                            "error" -> Txt("error", 12, ErrorColor)
                            else -> Switch(
                                checked = checked,
                                onCheckedChange = { onToggle(index) },
                                enabled = enabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                                    checkedTrackColor = EmeraldPrimary
                                )
                            )
                        }
                    }
                }
                if (index < TUNING_SWITCHES.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledLine(label: String, body: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label: ") }
            append(body)
        },
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun FeatureBlock(title: String, desc: String, recommend: String, drawback: String, warning: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(title, 14, MaterialTheme.colorScheme.onSurface, FontWeight.Bold)
        LabeledLine("คำอธิบาย", desc)
        LabeledLine("แนะนำ", recommend)
        LabeledLine("ข้อเสีย", drawback)
        LabeledLine("คำเตือน", warning)
    }
}

@Composable
fun TuningInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("คำอธิบายฟีเจอร์ปรับแต่ง", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Txt("ฟีเจอร์ทั้ง 5 อย่าง", 15, EmeraldPrimary, FontWeight.Bold)

                FeatureBlock(
                    "1. Compile Speed",
                    "คอมไพล์โค้ดของแอปให้เป็นรูปแบบที่เครื่องอ่านได้เร็วขึ้น",
                    "ใช้กับแอปที่คุณเปิดบ่อย รู้สึกเปิดช้า",
                    "ใช้พื้นที่เก็บข้อมูลเพิ่มขึ้น 20-30% และครั้งแรกที่เปิดอาจช้าเล็กน้อย",
                    "เปิดได้ทีละโหมด ต้อง Reset (ข้อ 5) ก่อนสลับ"
                )
                FeatureBlock(
                    "2. Compile Speed Profile",
                    "คอมไพล์แบบสมดุล เปิดเร็วขึ้น + ไม่กินพื้นที่มาก",
                    "เหมาะกับเครื่องที่พื้นที่เหลือน้อย แต่ยังอยากให้แอปเร็วขึ้น",
                    "เร็วน้อยกว่า Compile Everything แต่ประหยัดพื้นที่กว่า",
                    "mutually exclusive กับข้อ 1 และ 3"
                )
                FeatureBlock(
                    "3. Compile Everything",
                    "คอมไพล์เต็มรูปแบบทุกส่วนของแอป",
                    "ใช้กับแอปที่สำคัญที่สุด และเครื่องมีพื้นที่เหลือเยอะ",
                    "ใช้พื้นที่มากที่สุด (อาจ 50-100 MB ต่อแอป) ใช้เวลาคอมไพล์นาน",
                    "ถ้าพื้นที่เหลือน้อยกว่า 2GB ควรเลี่ยง"
                )
                FeatureBlock(
                    "4. Trim Memory",
                    "สั่งให้ระบบคืนหน่วยความจำ (RAM) ของแอปนั้นทันที",
                    "ใช้เมื่อรู้สึกเครื่องช้า หรือต้องการ RAM คืนชั่วคราว",
                    "ไม่ถาวร — แอปอาจโหลดข้อมูลใหม่เมื่อเปิดอีกครั้ง (ช้าลงเล็กน้อย)",
                    "ใช้บ่อยเกินไปอาจทำให้แอปทำงานผิดปกติ"
                )
                FeatureBlock(
                    "5. Reset Compile",
                    "ล้างการคอมไพล์ทั้งหมดของแอป กลับสู่ค่าเริ่มต้น",
                    "ใช้เมื่อต้องการสลับโหมด compile หรือคืนพื้นที่",
                    "หลัง Reset แอปจะเปิดช้าลงชั่วคราว จนระบบคอมไพล์เองอัตโนมัติ",
                    "ใช้เมื่อจำเป็น — ไม่ต้อง Reset ถ้าไม่ได้เปลี่ยนโหมด"
                )

                Txt("การตั้งค่าสถานะ (Swipe)", 15, EmeraldPrimary, FontWeight.Bold)
                LabeledLine(
                    "• Swipe ขวา (Inactive)",
                    "ตั้งแอปเป็น \"ไม่ใช้งาน\" ระบบจำกัดการทำงานเบื้องหลัง — ประหยัดแบต แต่แจ้งเตือนอาจช้า"
                )
                LabeledLine(
                    "• Swipe ซ้าย (Active)",
                    "ตั้งแอปเป็น \"ใช้งานบ่อย\" ระบบให้ priority สูง — เร็วขึ้น แต่กินแบตมากกว่า"
                )

                Txt("หมายเหตุ", 15, EmeraldPrimary, FontWeight.Bold)
                Text(
                    "ระดับอันตราย (ปลอดภัย / ปานกลาง / อันตรายมาก) คำนวณจากประเภทแอป — " +
                        "แอประบบที่สำคัญจะถูกทำเครื่องหมาย 'อันตรายมาก' แนะนำให้หลีกเลี่ยง",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("เข้าใจแล้ว", color = EmeraldPrimary) }
        }
    )
}
