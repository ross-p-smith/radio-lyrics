#!/usr/bin/env bash
set -euo pipefail

# Verify toolchain
java -version
sdkmanager --version || true
adb version

# Pre-warm Gradle wrapper if it exists
if [[ -x ./gradlew ]]; then
  ./gradlew --version || true
fi

# Hint for ADB-over-WiFi
cat <<'EOF'

✅ Devcontainer ready.

Connect to the in-car head unit (once it's on the same Wi-Fi):
    adb connect 192.168.X.Y:5555
    adb devices

Build & install the debug APK:
    ./gradlew installDebug

Run JVM unit tests (no device required):
    ./gradlew test
EOF
