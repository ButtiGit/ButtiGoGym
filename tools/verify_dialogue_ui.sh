#!/usr/bin/env bash
# Collect screenshots while the emulator is still running, including on failure.
set -u
gradle --no-daemon :app:connectedDebugAndroidTest
result=$?
mkdir -p ui-screenshots
adb pull /sdcard/Android/data/it.pulse.workout/files/screenshots/. ui-screenshots/ || true
exit "$result"
