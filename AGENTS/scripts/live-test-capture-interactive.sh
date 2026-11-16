#!/usr/bin/env bash
# Live-test capture, mode B (interactive): start app, stream filtered
# logcat to terminal with --line-buffered, duplicate to file.
#
# Deps: adb, timeout (GNU coreutils), grep, tee.
# Args: none (env-based).
# Env:  CAPTURE_SECONDS (default 600), LOG (default /tmp/tscalp-live.log),
#       FILTER (terminal filter only — see below),
#       PKG (default com.gitlab.biomorf.tscalp),
#       ACTIVITY (default com.gitlab.biomorf.tscalp/.MainActivity).
#
# LOG is ALWAYS the full logcat (no grep): so any tag can be grepped
# later. FILTER only narrows the live terminal output; it must NOT
# drop lines from the file. Pass an empty FILTER="" to also see the
# full stream in the terminal.
#
# Ctrl+C is delivered to adb logcat via timeout --foreground (l-scripts-115).
# grep --line-buffered is required for interactive viewing (live-test.md).

set -uo pipefail
CAPTURE_SECONDS="${CAPTURE_SECONDS:-600}"
LOG="${LOG:-/tmp/tscalp-live.log}"
PKG="${PKG:-com.gitlab.biomorf.tscalp}"
ACTIVITY="${ACTIVITY:-com.gitlab.biomorf.tscalp/.MainActivity}"
FILTER="${FILTER:-FlowManager: state:|StreamStateTracker|TInvestOrchestrator|TInvestWS|TInvestRest|TInvestMarketData|rate-limit|stream-limit|RESOURCE_EXHAUSTED|4008}"

echo "=== live-test-capture-interactive (mode B) ==="
echo "  CAPTURE_SECONDS=$CAPTURE_SECONDS"
echo "  LOG=$LOG"
echo "  FILTER=$FILTER"
echo

echo "STEP 1: start app + wait 3s"
adb shell am start -n "$ACTIVITY" < /dev/null
sleep 3

PID=$(timeout 5 adb shell pidof "$PKG" < /dev/null 2>/dev/null | tr -d '\r\n' || true)
echo "PID='${PID}'"
if [ -z "$PID" ]; then
  echo "ABORT: app not running"
  exit 1
fi

echo "STEP 2: clear + live capture ${CAPTURE_SECONDS}s (Ctrl+C to stop)"
adb logcat -c < /dev/null
# Full log to file; grep only for the terminal.
timeout --foreground "$CAPTURE_SECONDS" adb logcat --pid="$PID" -v threadtime 2>&1 \
  | tee "$LOG" \
  | grep --line-buffered -E "$FILTER"
echo "capture done: $LOG"
