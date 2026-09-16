# TurboSpace Optimizer

## What is still missing before this will actually build

1. Launcher icon: add `mipmap` icon assets (or replace
   `android:icon="@mipmap/ic_launcher"` in AndroidManifest.xml with a
   drawable you provide) — Android Studio's "New Project" wizard
   generates a default one automatically if you create the project
   through the wizard instead of by hand.
2. Gradle wrapper: open this folder in Android Studio once and let it
   generate `gradlew`, `gradlew.bat`, and `gradle/wrapper/*` for you —
   these are binary/generated files this tool cannot produce directly.
3. Shizuku must be installed and running on the test device (from the
   Play Store or shizuku.rikka.app), and the user must grant this app
   permission inside the Shizuku app before any command will succeed.

## How to build

Open the `TurboSpaceOptimizer/` folder directly in Android Studio
(File > Open), let it sync Gradle, then:

    Build > Build Bundle(s) / APK(s) > Build APK(s)

or from a terminal once the Gradle wrapper exists:

    ./gradlew assembleDebug

The output APK will be under
`app/build/outputs/apk/debug/app-debug.apk`.

## Distribution note

This APK is unsigned by default (debug build). If you plan to install
it on a device other than the one you built it on, you'll want a
release build signed with your own keystore — Android will otherwise
refuse to install it or show an "unknown source" warning.
