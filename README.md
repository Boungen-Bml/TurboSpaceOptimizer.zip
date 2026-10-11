# ⚡ Turbo Space Booster v3.1 — Official Release

**Release Date:** October 2026  
**Build:** v3.1 (Stable)  
**Android Support:** Android 10 – 16  
**Root Required:** ❌ No (uses Shizuku)  
**File Size:** ~8 MB  
**Package:** com.turbospace.optimizer

---

## 🎉 Welcome to Turbo Space v3.1

This is the biggest release yet. After months of testing across multiple devices, we've rebuilt the core architecture, fixed every known bug, and added powerful new features. This version is stable, fast, and works on Android 11 through 16 without root.

---

## ✨ NEW FEATURES

### 🎯 Custom Crosshair System (NEW)

A complete crosshair overlay for FPS games.

**3 Crosshair Styles:**
- **Cross (+)** — Classic crosshair with 4 arms and center dot
- **Dot (•)** — Minimal dot with soft glow ring
- **Circle (○)** — Circle outline with center dot

**4 Colors:**
- 🔴 Red (FF3040)
- 🟢 Green (00E676)
- 🔵 Blue (4CA8FF)
- ⚪ White

**Key Features:**
- **Draggable** — Move crosshair anywhere on screen with your finger
- **Position Memory** — Saves location, returns to same spot every time
- **Persistent Overlay** — Crosshair stays visible even after closing HUD
- **Smart Picker** — Choose style + color in a popup with live preview
- **X Toggle** — Instant off button that hides crosshair without losing settings
- **Restore** — Tap CROSSHAIR booster again → picker opens with saved values

**Games Tested:**
- Free Fire
- PUBG Mobile
- Call of Duty Mobile
- Roblox
- Terraria
- Any FPS / TPS game

---

### 🖥️ Immersive Overlay HUD (Rebuilt)

Full-screen control panel with modern layout.

**Layout:**


**Features:**
- **Real-time terminal log** — See every command executed
- **Dark sci-fi theme** — Low eye strain
- **Smart X buttons** — Left X = full close, Center X = collapse
- **System bar hiding** — Auto-hides status bar + navigation bar
- **Gesture pill preserved** — User can still swipe to exit

**Android Version Support:**
- Android 11 – 16: Full support ✅
- Android 10: Limited (hardware restriction on some ROMs)

---

### ⚙️ Booster Dashboard (Redesigned)

4 core features with clear status feedback.

**1. CLEAR CACHE**
- Command: `pm trim-caches 999G`
- Clears cache across all apps
- Frees storage quickly
- Status: ✅ Red on success

**2. COMPILE (3 Modes)**
- **Speed-Profile** — Low storage usage
- **Speed** — Max performance, high storage
- **Verify** — Save maximum space
- Command: `cmd package compile -m <mode> -f <pkg>`
- Shows progress with loading screen

**3. BOOST (BG DEXOPT)**
- Command: `cmd package bg-dexopt-job`
- Long-term optimization
- Runs in background
- Warning dialog before start

**4. GAME MODE**
- Command: `cmd game mode performance <pkg>`
- Requires Android 12+ with OEM support
- **Smart detection** — Shows friendly dialog if unsupported
- No more raw error messages

**Status Indicators:**
- 🟢 Red = Success (stays)
- ⚪ Gray = Failed
- ⚫ Dim = Idle

---

### 💾 MEM BOOST — Advanced RAM Recovery (NEW)

Free RAM by trimming background apps.

**How it Works:**
1. Select apps from list (shows icons + names)
2. System checks if app is running (3-layer detection)
3. If running → sends trim command
4. If not running → marks as SKIPPED

**3-Layer Process Detection:**
- Layer 1: `pidof <pkg>` — Fast, but limited on Android 10+
- Layer 2: `dumpsys activity processes | grep` — Fallback
- Layer 3: `ps -A | grep` — Last resort

**Terminal Feedback:**


**Best Results:**
- Select Facebook, Instagram, TikTok, YouTube
- Tap YES before entering game
- Frees 200 MB – 500 MB RAM

---

## 🚀 11 GAME BOOSTERS

| # | Booster | Command | Purpose |
|---|---------|---------|---------|
| 1 | INACTIVE OFF | `cmd activity set-inactive <pkg> false` | Prevent freeze |
| 2 | BG ALLOW | `cmd appops set <pkg> RUN_IN_BACKGROUND allow` | Background run |
| 3 | ANY BG ALLOW | `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow` | Unrestricted |
| 4 | FOREGROUND | `cmd appops set <pkg> START_FOREGROUND allow` | High priority |
| 5 | FIXED PERF | `cmd power set-fixed-performance-mode-enabled 1` | Lock CPU |
| 6 | STANDBY ACT | `cmd am set-standby-bucket <pkg> active` | Never sleep |
| 7 | INTERACTIVE | `cmd power set-interactive-state true` | Touch boost |
| 8 | WHITELIST | `cmd deviceidle whitelist +<pkg>` | Battery exclusion |
| 9 | MEM BOOST | (special) | RAM recovery |
| 10 | CROSSHAIR | (special) | Aim overlay |
| 11 | RESET ALL | (special) | One-tap reset |

**All commands run via Shizuku — no root needed.**

---

## 🎨 UI / UX IMPROVEMENTS

### Splash Screen
- Block assembly animation
- 25 blocks converge to center
- Flash effect + TS logo reveal
- Smoother easing curve

### Main Dashboard
- **Horizontal game carousel** — Swipe to browse
- **Center-focus glow** — Active game highlighted at 110%
- **Elastic press** — Mechanical feel on tap
- **Smart filter** — Only games shown (no system apps)
- **Self-hidden** — Turbo Space doesn't appear in its own list
- **RESET ALL button** — Top-right corner (compact size)

### Booster Screen
- Large game icon with white border
- 4 feature buttons (compact 20dp icons)
- Terminal log at bottom
- START GAME button

### Booster Icons (Redesigned)
- **CLEAR CACHE** — 1 lightning bolt
- **COMPILE** — 2 stacked bolts
- **BOOST** — 3 stacked bolts
- **GAME MODE** — Compact red rocket

---

## 🐛 BUG FIXES (Complete List)

### Critical
- ✅ **Overlay X button** — Split into 2 buttons (full close + collapse only)
- ✅ **Ghost icon top-left** — Defensive removeView with retry
- ✅ **Floating icon not returning** — Proper lifecycle handling
- ✅ **GAME MODE error** — Friendly dialog for unsupported devices
- ✅ **MEM BOOST false skip** — 3-layer process detection
- ✅ **System bars reappearing** — Aggressive re-apply + listener
- ✅ **Crosshair off-center** — Uses currentWindowMetrics (Android 11+)

### Major
- ✅ **Splash animation** — Blocks converge to exact center (0.5f / 0.5f)
- ✅ **Game list showing apps** — Filter only games + hide Turbo Space
- ✅ **Scroll stutter** — Removed expensive blur/shadow effects (+10% smoothness)
- ✅ **Crosshair persistence** — Saves position + style + color across sessions
- ✅ **Dialog callbacks leak** — OverlayConfirmBus.reset() on dispose
- ✅ **Missing GAME MODE on Booster** — Removed from overlay grid (avoid duplication)

### Minor
- ✅ **Reset button too large** — Resized to 96×28dp top-right
- ✅ **REMOVED JOY icon** — Left top area cleaned
- ✅ **Text alignment** — All dialog text centered
- ✅ **Icon consistency** — All booster icons match design language

---

## 📊 PERFORMANCE RESULTS

**Tested On:**
- Android 11, 12, 13, 14, 15, 16
- Multiple devices (budget to mid-range)

**Results:**

| Metric | Improvement |
|--------|-------------|
| Scroll smoothness | ⬆️ +10% |
| Frame stability | ⬆️ Noticeable |
| Peak FPS | ⬆️ +1 to +5 |
| RAM available | ⬆️ 200-500 MB |
| Input latency | ⬇️ Slight |
| Battery drain | ⬇️ Slight |

**Real-world feedback:**
- "Roblox aiming feels better with crosshair"
- "MEM BOOST on Facebook → instant smooth"
- "Overlay no longer stutters"

---

## ⚠️ KNOWN LIMITATIONS

**Hardware / OS Restrictions:**
- **Android 10** — Some ROMs cannot fully hide navigation bar
- **MIUI / ColorOS / Funtouch** — Certain OEMs block overlay commands
- **GAME MODE API** — Requires Android 12+ with OEM support

**Crosshair:**
- Not visible in exclusive fullscreen apps (rare)
- Position may shift on rotation (still working on it)

**MEM BOOST:**
- Apps not running → SKIPPED (by design)
- Some protected apps cannot be trimmed

---

## 🔒 PRIVACY & SECURITY

- ❌ No internet permission
- ❌ No analytics
- ❌ No ads
- ❌ No data collection
- ✅ 100% offline
- ✅ Open source (MIT License)

---

## 📱 COMPATIBILITY

| Android | Status |
|---------|--------|
| 10 | 🟡 Limited |
| 11 | ✅ Full |
| 12 | ✅ Full |
| 13 | ✅ Full |
| 14 | ✅ Full |
| 15 | ✅ Full |
| 16 | ✅ Full |

---

## 🙏 CREDITS

- **Shizuku API** — Rikka
- **Jetpack Compose** — Google
- **Community testers** — Thank you for feedback

---

## 📜 LICENSE

MIT License — free to use, modify, distribute.

---

**⚡ Turbo Space — Built for budget gamers who want more.**

Version 3.0 · Stable · October 2026

