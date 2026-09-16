#!/usr/bin/env bash
# Runs automatically once when the Codespace is first created
# (see devcontainer.json's postCreateCommand). Installs just enough of the
# Android SDK to run `./gradlew assembleDebug` - no full Android Studio,
# no emulator, nothing extra.
set -e

export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME/cmdline-tools"

# Looks up whatever Google's CURRENT "latest" command-line tools link is,
# instead of a hardcoded version number - Google rotates this file's name
# periodically, so a hardcoded link tends to go stale after a while.
DOWNLOAD_URL=$(curl -s https://developer.android.com/studio \
  | grep -oE 'https://dl\.google\.com/android/repository/commandlinetools-linux-[0-9]+_latest\.zip' \
  | head -n 1)

if [ -z "$DOWNLOAD_URL" ]; then
  echo "Could not auto-detect the command-line tools URL."
  echo "Open https://developer.android.com/studio#command-tools yourself,"
  echo "copy the 'Linux' download link, and re-run:"
  echo "  curl -o cmdline-tools.zip <the link you copied>"
  exit 1
fi

echo "Downloading: $DOWNLOAD_URL"
curl -sL -o cmdline-tools.zip "$DOWNLOAD_URL"
unzip -q cmdline-tools.zip
mv cmdline-tools latest
rm cmdline-tools.zip

{
  echo "export ANDROID_HOME=$ANDROID_HOME"
  echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools'
} >> ~/.bashrc

export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"

yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo ""
echo "==================================================================="
echo " Android SDK ready."
echo " Open a NEW terminal (so the PATH/ANDROID_HOME changes take effect),"
echo " then run:"
echo "   ./gradlew assembleDebug"
echo " Your APK will show up at:"
echo "   app/build/outputs/apk/debug/app-debug.apk"
echo "==================================================================="
