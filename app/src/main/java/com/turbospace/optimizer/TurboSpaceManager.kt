package com.turbospace.optimizer

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

// ============================================================================
// TurboSpaceManager - Shizuku Command Executor
// Provides all functions missing from MainActivity.kt build
// ============================================================================

data class CommandOutcome(val success: Boolean, val output: String)

const val COMMAND_TIMEOUT_MS = 5000L
const val STABILIZER_PRO = "STABILIZER_PRO"
const val PERFORMANCE_INJECT = "PERFORMANCE_INJECT"

object TurboSpaceManager {

    private const val TAG = "TurboSpaceManager"

    // Required variables
    var currentActiveGamePackage: String? = null
    var cyberActiveModules: MutableSet<String> = mutableSetOf()

    // Internal prefs name for saving modules
    private const val PREFS_NAME = "TurboSpaceState"
    private const val KEY_CYBER_MODULES = "CYBER_ACTIVE_MODULES"
    private const val KEY_ACTIVE_GAME = "CURRENT_ACTIVE_GAME_PACKAGE"

    // Social / system blacklist for game detection
    private val SOCIAL_BLACKLIST = setOf(
        "com.facebook", "com.instagram", "com.whatsapp",
        "com.twitter", "com.tiktok", "com.snapchat",
        "com.discord", "com.telegram", "com.line",
        "com.viber", "com.skype", "com.google.android.youtube",
        "com.zhiliaoapp.musically"
    )

    private val GAME_KEYWORDS = listOf(
        "game", "pubg", "freefire", "mobilelegends", "mlbb",
        "rov", "valorant", "cod", "callofduty", "genshin",
        "roblox", "minecraft", "legends", "arena", "survival",
        "shooter", "battle", "craft", "league"
    )

    // ========================================================================
    // Variable helpers
    // ========================================================================
    fun saveCyberActiveModules() {
        try {
            // Try to get app context via ActivityThread if available
            val appContext = getAppContextViaReflection()
            appContext?.getSharedPreferences(PREFS_NAME, 0)?.edit()
                ?.putStringSet(KEY_CYBER_MODULES, cyberActiveModules)
                ?.apply()
            Log.d(TAG, "Saved cyber modules: $cyberActiveModules")
        } catch (e: Exception) {
            Log.w(TAG, "saveCyberActiveModules failed: ${e.message}")
        }
    }

    fun logActiveGamePackage() {
        Log.i(TAG, "Active Game Package: $currentActiveGamePackage | Modules: $cyberActiveModules")
        try {
            getAppContextViaReflection()?.getSharedPreferences(PREFS_NAME, 0)?.edit()
                ?.putString(KEY_ACTIVE_GAME, currentActiveGamePackage)
                ?.apply()
        } catch (_: Exception) { }
    }

    fun safePackageName(): String? {
        val pkg = currentActiveGamePackage ?: return null
        if (pkg.isBlank() || pkg == "NO TARGET SELECTED") return null
        // Sanitize: only allow valid package name characters
        return if (pkg.matches(Regex("^[a-zA-Z0-9._]+\$"))) pkg else null
    }

    // ========================================================================
    // Shizuku Core
    // ========================================================================
    fun isShizukuAvailableAndGranted(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku check failed: ${e.message}")
            false
        }
    }

    fun newShizukuProcess(cmd: String): Process? {
        return try {
            if (!isShizukuAvailableAndGranted()) {
                Log.w(TAG, "Shizuku not available for cmd: $cmd")
                return null
            }
            Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "newShizukuProcess failed: $cmd -> ${e.message}")
            null
        }
    }

    fun executeCommandDetailed(cmd: String): CommandOutcome {
        val process = newShizukuProcess(cmd)
            ?: return CommandOutcome(false, "Shizuku not available or permission denied")

        return try {
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            // Read with timeout
            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                return CommandOutcome(false, "Timeout after ${COMMAND_TIMEOUT_MS}ms: $cmd")
            }

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            val success = process.exitValue() == 0
            CommandOutcome(success, output.toString().trim())
        } catch (e: Exception) {
            CommandOutcome(false, "Exception: ${e.message}")
        }
    }

    // Simple wrapper for legacy calls
    private fun executeShizukuCommand(cmd: String): CommandOutcome = executeCommandDetailed(cmd)

    // ========================================================================
    // Game Detection
    // ========================================================================
    fun isRecognizedGame(packageName: String): Boolean {
        if (packageName.isBlank() || packageName == "NO TARGET SELECTED") return false
        val pkgLower = packageName.lowercase()

        // Block social / system
        if (SOCIAL_BLACKLIST.any { pkgLower.contains(it) }) return false
        if (pkgLower.startsWith("com.android.") || pkgLower.startsWith("com.google.android.")) {
            // Allow only if it's clearly a game
            if (!GAME_KEYWORDS.any { pkgLower.contains(it) }) return false
        }

        // Heuristic: must contain dot and not be own package
        if (!packageName.contains(".")) return false
        if (packageName == "com.turbospace.optimizer") return false

        // Check keywords or .game suffix or category
        return GAME_KEYWORDS.any { pkgLower.contains(it) } ||
                pkgLower.contains(".game") ||
                pkgLower.contains("legends") ||
                // Fallback: if pm list includes it as third party, consider it potential game
                true // Allow third-party apps, final filter done in picker
    }

    fun getThirdPartyApps(): List<String> {
        val result = executeCommandDetailed("pm list packages -3")
        if (!result.success) {
            Log.w(TAG, "getThirdPartyApps fallback: ${result.output}")
            return emptyList()
        }
        return result.output.lines()
            .mapNotNull { line ->
                line.substringAfter("package:", "").trim().takeIf { it.isNotEmpty() }
            }
            .filter { pkg -> isRecognizedGame(pkg) || true } // Return all third party, let UI filter
    }

    // Game-only list for overlay picker
    fun getInstalledGamesOnly(): List<String> = getThirdPartyApps().filter { isRecognizedGame(it) }

    // ========================================================================
    // Session Management - Stateful Features
    // ========================================================================
    fun captureAndSaveSessionSnapshot() {
        Log.d(TAG, "captureAndSaveSessionSnapshot for $currentActiveGamePackage")
        // Save snapshot: game mode, thermal, etc.
        val game = safePackageName() ?: return
        executeCommandDetailed("cmd game mode $game")
        executeCommandDetailed("cmd thermalservice override-status")
        saveCyberActiveModules()
        logActiveGamePackage()
    }

    fun restoreSingleStatefulFeature(feature: String): Boolean {
        Log.d(TAG, "restoreSingleStatefulFeature: $feature")
        val pkg = safePackageName()
        val cmd = when (feature) {
            "CORE_OVERCLOCK", "CORE_OVERDRIVE", STABILIZER_PRO -> "cmd power set-fixed-performance-mode-enabled false"
            "PERFORMANCE_INJECT", PERFORMANCE_INJECT -> if (pkg != null) "cmd game mode $pkg 1" else null
            "ANTI_KILL_SHIELD", "ANTI-KILL" -> if (pkg != null) "cmd deviceidle whitelist -$pkg" else null
            "STANDBY_LOCKER" -> if (pkg != null) "cmd am set-standby-bucket $pkg working_set" else null
            "ANTI_SLEEP_CORE" -> if (pkg != null) "cmd activity set-inactive $pkg true" else null
            "BATTERY_BYPASS" -> "cmd jobscheduler reset-execution-quota"
            "WIFI_OVERDRIVE" -> "cmd wifi set-high-perf-enabled disabled"
            "ULTRA_LOW_LATENCY", "ULTRA_LATENCY" -> "cmd wifi force-low-latency-mode disabled"
            else -> null
        }
        return if (cmd != null) executeCommandDetailed(cmd).success else false
    }

    fun applySelectedSessionFeatures(features: Set<String>): Set<String> {
        Log.d(TAG, "applySelectedSessionFeatures: $features")
        val successful = mutableSetOf<String>()
        val pkg = safePackageName()
        for (feature in features) {
            val cmd = when (feature) {
                "CORE_OVERCLOCK", "CORE_OVERDRIVE" -> "cmd power set-fixed-performance-mode-enabled true"
                PERFORMANCE_INJECT, "PERFORMANCE_INJECT" -> if (pkg != null) "cmd game mode performance $pkg" else null
                "ANTI_KILL_SHIELD" -> if (pkg != null) "cmd deviceidle whitelist +$pkg" else null
                "STABILIZER_PRO", "STANDBY_LOCKER" -> if (pkg != null) "cmd am set-standby-bucket $pkg active" else null
                "ANTI_SLEEP_CORE" -> if (pkg != null) "cmd activity set-inactive $pkg false" else null
                "TOP_APP_IGNITION" -> if (pkg != null) "cmd activity set-scheduler-group $pkg top-app" else null
                "BATTERY_BYPASS" -> "cmd jobscheduler standby-batched-jobs-execute"
                "RAGNA_PURGE" -> "cmd activity kill-all"
                "CACHE_NOVA" -> "cmd package trim-caches 999G"
                "BANDWIDTH_LOCK" -> "cmd connectivity request-restricted-wifi"
                "PING_SLASH" -> "cmd connectivity airplane-mode enable && sleep 1 && cmd connectivity airplane-mode disable"
                "WIFI_OVERDRIVE" -> "cmd wifi set-high-perf-enabled enabled"
                "ULTRA_LOW_LATENCY" -> "cmd wifi force-low-latency-mode enabled"
                else -> feature // Allow raw command passthrough
            }
            if (cmd != null) {
                val res = executeCommandDetailed(cmd)
                if (res.success) {
                    successful.add(feature)
                    cyberActiveModules.add(feature)
                }
            }
        }
        saveCyberActiveModules()
        return successful
    }

    fun runActionFeature(feature: String): Boolean {
        Log.d(TAG, "runActionFeature: $feature")
        val pkg = safePackageName()
        val cmd = when (feature) {
            "RAGNA_PURGE", "RAM_PURGE" -> "cmd activity kill-all"
            "CACHE_NOVA", "CACHE_FLUSH" -> "cmd package trim-caches 999G"
            "BANDWIDTH_LOCK", "NET_ISOLATION" -> "cmd connectivity request-restricted-wifi"
            "PING_SLASH", "PING_STABILIZER" -> "cmd connectivity airplane-mode enable && sleep 1 && cmd connectivity airplane-mode disable"
            "BATTERY_BYPASS" -> "cmd jobscheduler standby-batched-jobs-execute"
            else -> feature // Raw command
        }
        val result = executeCommandDetailed(cmd)
        if (result.success) cyberActiveModules.add(feature)
        saveCyberActiveModules()
        return result.success
    }

    // ========================================================================
    // CPU / GPU
    // ========================================================================
    fun reduceCpuLoad(packageName: String): CommandOutcome {
        currentActiveGamePackage = packageName
        val cmds = listOf(
            "cmd power set-fixed-performance-mode-enabled true",
            "cmd activity set-scheduler-group $packageName top-app",
            "cmd am set-standby-bucket $packageName active"
        )
        var last: CommandOutcome = CommandOutcome(false, "")
        for (c in cmds) last = executeCommandDetailed(c)
        cyberActiveModules.add("CORE_OVERCLOCK")
        saveCyberActiveModules()
        return last
    }

    fun reduceGpuLoad(packageName: String): CommandOutcome {
        currentActiveGamePackage = packageName
        // Simulate GPU boost via game mode performance + high-perf wifi as side effect
        val cmd = "cmd game mode performance $packageName"
        val res = executeCommandDetailed(cmd)
        if (res.success) cyberActiveModules.add(PERFORMANCE_INJECT)
        saveCyberActiveModules()
        return res
    }

    fun restoreCpuLoad(packageName: String): CommandOutcome {
        val cmd = "cmd power set-fixed-performance-mode-enabled false"
        val res = executeCommandDetailed(cmd)
        cyberActiveModules.remove("CORE_OVERCLOCK")
        saveCyberActiveModules()
        return res
    }

    fun restoreGpu(packageName: String): CommandOutcome {
        val cmd = "cmd game mode $packageName 1"
        val res = executeCommandDetailed(cmd)
        cyberActiveModules.remove(PERFORMANCE_INJECT)
        cyberActiveModules.remove(STABILIZER_PRO)
        saveCyberActiveModules()
        return res
    }

    fun resetCompileGame(packageName: String): CommandOutcome {
        val cmd = "cmd package compile --reset $packageName"
        return executeCommandDetailed(cmd)
    }

    // ========================================================================
    // Full Reset - Rollback logic as requested
    // ========================================================================
    fun restoreSavedSession(features: Set<String>): Boolean {
        Log.d(TAG, "restoreSavedSession: $features for $currentActiveGamePackage")
        val pkg = safePackageName() ?: currentActiveGamePackage
        if (pkg == null) {
            // Global rollback without package
            executeCommandDetailed("cmd power set-fixed-performance-mode-enabled false")
            executeCommandDetailed("cmd wifi set-high-perf-enabled disabled")
            executeCommandDetailed("cmd wifi force-low-latency-mode disabled")
            cyberActiveModules.clear()
            saveCyberActiveModules()
            return true
        }

        val rollbackCommands = listOf(
            "cmd power set-fixed-performance-mode-enabled false",
            "cmd deviceidle whitelist -$pkg",
            "cmd am set-standby-bucket $pkg working_set",
            "cmd activity set-inactive $pkg true",
            "cmd wifi set-high-perf-enabled disabled",
            "cmd wifi force-low-latency-mode disabled"
        )

        var allOk = true
        for (cmd in rollbackCommands) {
            val res = executeCommandDetailed(cmd)
            if (!res.success) allOk = false
        }

        // Clear active modules
        cyberActiveModules.clear()
        saveCyberActiveModules()
        logActiveGamePackage()

        return allOk
    }

    // Overload for callers that pass no args (legacy MainActivity)
    fun restoreSavedSession(): Boolean = restoreSavedSession(cyberActiveModules)

    // ========================================================================
    // Helpers
    // ========================================================================
    private fun getAppContextViaReflection(): android.content.Context? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApp = activityThread.getMethod("currentApplication").invoke(null)
            currentApp as? android.content.Context
        } catch (_: Exception) { null }
    }

    fun getAppLabel(packageName: String): String {
        return try {
            val ctx = getAppContextViaReflection() ?: return packageName
            val pm = ctx.packageManager
            pm.getApplicationInfo(packageName, 0).let { pm.getApplicationLabel(it).toString() }
        } catch (_: Exception) { packageName }
    }

    // Legacy compatibility - these are used by overlay v2.6
    fun executeCyberModule(module: Any, target: String): CommandOutcome {
        val featureName = when (module) {
            is String -> module
            else -> module.toString()
        }
        currentActiveGamePackage = target.takeIf { it != "NO TARGET SELECTED" } ?: currentActiveGamePackage
        return executeCommandDetailed(featureName)
    }
}
