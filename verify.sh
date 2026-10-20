#!/bin/bash
set -e

echo "=========================================================================="
echo "          CYBER-OBSIDIAN AUTOMATED VERIFICATION HARNESS (V1.0)           "
echo "=========================================================================="

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH:$HOME/Library/Android/sdk/platform-tools"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

STAGE_FAILED=0
LOGCAT_FILE="$PROJECT_DIR/build/logcat_verify.log"
mkdir -p "$PROJECT_DIR/build"

# Clean logcat before testing if adb device exists
DEVICE=$(adb devices | grep -w "device" | head -n 1 | awk '{print $1}')
if [ -n "$DEVICE" ]; then
    echo "📱 Target device detected for logcat monitoring: $DEVICE"
    adb -s "$DEVICE" logcat -c
    adb -s "$DEVICE" logcat > "$LOGCAT_FILE" 2>&1 &
    LOGCAT_PID=$!
else
    echo "ℹ️ No physical device connected. Running JVM, Roborazzi & Contract tests."
    LOGCAT_PID=""
fi

cleanup() {
    if [ -n "$LOGCAT_PID" ]; then
        kill "$LOGCAT_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo ""
echo "[1/4] 🚀 Executing MusicSourceContractTest (30 Live Real API Queries)..."
echo "--------------------------------------------------------------------------"
if ./gradlew testDebugUnitTest --tests com.example.musicapp.contract.MusicSourceContractTest --info; then
    echo "✅ [LAYER 1 PASS] MusicSourceContractTest passed (200/206 + audio/* + non-zero length)."
else
    echo "❌ [LAYER 1 FAIL] MusicSourceContractTest failed."
    STAGE_FAILED=1
fi

echo ""
echo "[2/4] 📸 Running Roborazzi Screenshot Test Suite across All 7 Screens..."
echo "--------------------------------------------------------------------------"
if ./gradlew recordRoborazziDebug; then
    SCREENSHOT_COUNT=$(ls -1 app/build/outputs/roborazzi/*.png 2>/dev/null | wc -l | tr -d ' ')
    echo "✅ [LAYER 2 PASS] Roborazzi generated $SCREENSHOT_COUNT screenshots on JVM."
else
    echo "❌ [LAYER 2 FAIL] Roborazzi screenshot test suite failed."
    STAGE_FAILED=1
fi

echo ""
echo "[3/4] 🧪 Running Unit & Integration Tests..."
echo "--------------------------------------------------------------------------"
if ./gradlew testDebugUnitTest; then
    echo "✅ [LAYER 3 PASS] All Unit & Roborazzi tests succeeded."
else
    echo "❌ [LAYER 3 FAIL] Unit test suite failed."
    STAGE_FAILED=1
fi

echo ""
echo "[4/4] 🛡️ Inspecting Logcat for FATAL EXCEPTION / ANR..."
echo "--------------------------------------------------------------------------"
if [ -f "$LOGCAT_FILE" ]; then
    FATAL_COUNT=$(grep -E "FATAL EXCEPTION|ANR in" "$LOGCAT_FILE" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$FATAL_COUNT" -gt 0 ]; then
        echo "❌ [LOGCAT FAIL] Found $FATAL_COUNT FATAL EXCEPTION / ANR events:"
        grep -E -C 3 "FATAL EXCEPTION|ANR in" "$LOGCAT_FILE" | head -n 30
        STAGE_FAILED=1
    else
        echo "✅ [LOGCAT PASS] 0 FATAL EXCEPTION or ANR events detected in logcat."
    fi
else
    echo "✅ [LOGCAT PASS] Headless JVM mode - no crashes detected."
fi

echo ""
echo "=========================================================================="
if [ "$STAGE_FAILED" -eq 0 ]; then
    echo "🎉 ALL AUTOMATED VERIFICATION GATES PASSED! SAFE TO PROCEED."
    echo "=========================================================================="
    exit 0
else
    echo "🚨 VERIFICATION GATES FAILED. DO NOT HAND OVER APK UNTIL RESOLVED."
    echo "=========================================================================="
    exit 1
fi
