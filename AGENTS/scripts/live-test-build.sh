#!/usr/bin/env bash
# Build + install debug APK, verify branch/HEAD, force-stop app.
# Use before a live-test capture (see docs/live-test.md).
#
# Deps: ./gradlew, adb, git.
# Args: none (branch/HEAD printed for sanity).
# Env:  none.
#
# Exit: 0 on build+install+force-stop success; non-zero otherwise.

set -uo pipefail
LOG="${LOG:-/tmp/build-live-test.log}"

echo "=== live-test-build: branch/HEAD ==="
git branch --show-current
git log --oneline -1
echo

echo "=== installDebug (log: $LOG) ==="
./gradlew :app:installDebug > "$LOG" 2>&1
RC=$?
echo "--- installDebug exit=$RC ---"
tail -5 "$LOG"
echo

if [ "$RC" -ne 0 ]; then
  echo "BUILD FAILED — see $LOG"
  exit 1
fi

echo "=== force-stop ==="
adb shell am force-stop com.gitlab.biomorf.tscalp < /dev/null
echo "OK"
