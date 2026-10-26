#!/usr/bin/env bash
# Preserve the test exit code and collect screenshots before emulator teardown.
set -u
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=it.pulse.workout.ui.StrengthRanksScreenTest,it.pulse.workout.ui.AppNavigationTest,it.pulse.workout.ui.GuidedDialogueTest,it.pulse.workout.ui.QuickWorkoutFlowTest,it.pulse.workout.ui.CatalogAndSharingTest
result=$?
mkdir -p ui-screenshots
adb pull /sdcard/Pictures/ButtiGoGymQA/. ui-screenshots/ || true
if [[ "$result" -eq 0 ]]; then
  for image in 00-home 04-recupero 06-myself 08-rank-legenda 09-rank-dark 10-rank-expanded 11-quick-entry 12-quick-rest 13-quick-dark 14-catalog-added 15-history-preview; do
    test -s "ui-screenshots/$image.png" || result=1
  done
fi
exit "$result"
