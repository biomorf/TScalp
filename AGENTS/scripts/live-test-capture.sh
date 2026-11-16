#!/usr/bin/env bash
# Live-test capture, mode A (automatic): start app, capture logcat to
# file for CAPTURE_SECONDS, run quick analysis (RESOURCE_EXHAUSTED,
# [rate-limit/], crashes, timeouts).
#
# Deps: adb, timeout (GNU coreutils), grep.
# Args: none (env-based).
# Env:  CAPTURE_SECONDS (default 600), LOG (default /tmp/tscalp-live.log),
#       PKG (default com.gitlab.biomorf.tscalp),
#       ACTIVITY (default com.gitlab.biomorf.tscalp/.MainActivity).
#
# Ctrl+C is delivered to adb logcat via timeout --foreground (l-scripts-115).

set -uo pipefail
CAPTURE_SECONDS="${CAPTURE_SECONDS:-600}"
LOG="${LOG:-/tmp/tscalp-live.log}"
PKG="${PKG:-com.gitlab.biomorf.tscalp}"
ACTIVITY="${ACTIVITY:-com.gitlab.biomorf.tscalp/.MainActivity}"

echo "=== live-test-capture (mode A) ==="
echo "  CAPTURE_SECONDS=$CAPTURE_SECONDS"
echo "  LOG=$LOG"
echo "  PKG=$PKG"
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

echo "STEP 2: clear + capture ${CAPTURE_SECONDS}s — Ctrl+C when done"
adb logcat -c < /dev/null
timeout --foreground "$CAPTURE_SECONDS" adb logcat --pid="$PID" -v threadtime > "$LOG" 2>&1 || true
echo "capture done"
echo

echo "STEP 3: quick analysis"
LINES=$(wc -l < "$LOG")
SIZE=$(du -h "$LOG" | awk '{print $1}')
echo "  log lines: $LINES"
echo "  log size:  $SIZE"
echo

N=$(grep -c "RESOURCE_EXHAUSTED" "$LOG" 2>/dev/null || true)
echo "=== RESOURCE_EXHAUSTED (must be 0) ==="
echo "  count: ${N:-0}"
if [ "${N:-0}" -gt 0 ]; then
  grep -n "RESOURCE_EXHAUSTED" "$LOG" | head -10
fi
echo

R=$(grep -c "\[rate-limit/" "$LOG" 2>/dev/null || true)
echo "=== [rate-limit/] count (limiter active; >0) ==="
echo "  count: ${R:-0}"
echo

S=$(grep -c "\[stream-limit/" "$LOG" 2>/dev/null || true)
echo "=== [stream-limit/] count (stream limiter active; >0) ==="
echo "  count: ${S:-0}"
if [ "${S:-0}" -gt 0 ]; then
  grep -oE '\[stream-limit/[^]]+\]' "$LOG" | sort | uniq -c | sort -rn | head -10
fi
echo

echo "=== crashes / fatal ==="
grep -E "FATAL EXCEPTION|AndroidRuntime.*FATAL|Process .* has died|ANR in" "$LOG" | head -5 || echo "  (none)"
echo

echo "=== rate-limit timeout ==="
grep -E "rate-limit.*timeout|RateLimitTimeoutException" "$LOG" | head -5 || echo "  (none)"
