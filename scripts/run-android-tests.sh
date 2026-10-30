#!/usr/bin/env bash
set -euo pipefail

diagnostics=app/build/outputs/android-test-diagnostics
mkdir -p "$diagnostics"

# Capture while the emulator is running; the runner shuts it down after this script.
adb logcat -v threadtime > "$diagnostics/logcat.txt" 2>&1 &
logcat_pid=$!
collect_diagnostics() {
    result=$?
    trap - EXIT
    set +e
    kill "$logcat_pid" 2>/dev/null
    wait "$logcat_pid" 2>/dev/null
    printf '%s\n' "$result" > "$diagnostics/exit-code.txt"
    if (( result != 0 )); then
        adb shell dumpsys activity activities > "$diagnostics/activities.txt" 2>&1
        adb shell dumpsys window windows > "$diagnostics/windows.txt" 2>&1
        adb exec-out screencap -p > "$diagnostics/screen.png" 2> "$diagnostics/screen-error.txt"
    fi
    exit "$result"
}
trap collect_diagnostics EXIT

bash gradlew :app:connectedDebugAndroidTest --console=plain --stacktrace 2>&1 | tee "$diagnostics/gradle.log"
