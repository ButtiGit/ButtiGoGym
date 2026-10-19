#!/usr/bin/env bash
# Collect screenshots while the emulator is still running, including on failure.
set -u
gradle --no-daemon :app:connectedDebugAndroidTest
result=$?
mkdir -p ui-screenshots
adb pull /sdcard/Pictures/ButtiGoGymQA/. ui-screenshots/ || true
if [[ "$result" -eq 0 ]]; then
  test -s ui-screenshots/00-home.png && test -s ui-screenshots/04-recupero.png || result=1
fi
exit "$result"
