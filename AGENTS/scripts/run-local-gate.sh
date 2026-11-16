#!/usr/bin/env bash
# Full local Gradle gate (5a): testDebugUnitTest + lintDebug + ktlintCheck + detekt + Roborazzi verify.
# Pre-commit check for feature/dev work on desktop. Not in CI.
#
# Deps: ./gradlew (project wrapper), grep.
# Args:
#   --no-build-cache   pass --no-build-cache (new test files / cache suspicion)
#   --keep             do not run :app:clean (faster incremental run)
#   --log <path>       override log path (default: /tmp/gate.log)
# Env: none.
# Exit: 0 = BUILD SUCCESSFUL; non-zero = gate red (see log).
#
# Read-only wrt repo; only writes the log file.

set -uo pipefail

NO_BUILD_CACHE=0
KEEP=0
LOG=/tmp/gate.log
while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-build-cache) NO_BUILD_CACHE=1; shift ;;
    --keep) KEEP=1 ;;
    --log) LOG="$2"; shift 2 ;;
    -h|--help)
      echo "usage: $0 [--no-build-cache] [--keep] [--log <path>]"
      exit 0
      ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

CLEAN=:app:clean
if [ "$KEEP" = "1" ]; then CLEAN=""; fi

EXTRA=""
if [ "$NO_BUILD_CACHE" = "1" ]; then EXTRA="--no-build-cache"; fi

echo "=== run-gate: log=$LOG clean=${CLEAN:-none} no-build-cache=$NO_BUILD_CACHE"
./gradlew $CLEAN :app:ktlintCheck :app:lintDebug :app:detekt :app:testDebugUnitTest \
  -Proborazzi.test.verify=true --continue --rerun-tasks $EXTRA > "$LOG" 2>&1
GATE=$?

echo "--- gate exit=$GATE ---"
grep -E '\[warn\]|\[error\]|^w:|^e:|FAILED|BUILD|\.kt:[0-9]+:[0-9]+' "$LOG" || true
echo "---"
tail -5 "$LOG"

if [ "$GATE" -ne 0 ]; then
  echo "GATE RED — see $LOG" >&2
  exit "$GATE"
fi
echo "GATE GREEN"
exit 0
