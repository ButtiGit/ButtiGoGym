#!/usr/bin/env bash
# Preserve the test exit code and collect screenshots before emulator teardown.
set -u
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=it.pulse.workout.ui.StrengthRanksScreenTest,it.pulse.workout.ui.AppNavigationTest,it.pulse.workout.ui.GuidedDialogueTest
result=$?
mkdir -p ui-screenshots
adb pull /sdcard/Pictures/ButtiGoGymQA/. ui-screenshots/ || true
if [[ "$result" -eq 0 ]]; then
  for image in 00-home 04-recupero 06-myself 08-rank-legenda 09-rank-dark 10-rank-expanded; do
    test -s "ui-screenshots/$image.png" || result=1
  done
fi
exit "$result"
