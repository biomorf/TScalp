#!/usr/bin/env bash
# inline.sh — carcass for inline blocks (AGENTS/AGENTS-style-docs.md -> r4i).
#
# Usage:
#   bash AGENTS/scripts/inline.sh [--log FILE] [--keep] <<'BASH_END'
#   set -euo pipefail
#   cd "$(git rev-parse --show-toplevel)"
#   <commands>
#   BASH_END
#
# The body is a normal self-contained script: run it as `bash <<'BASH_END'`
# and it behaves the same. The script only adds the carcass: tee to the log,
# exit code of the body, log line count.
#
# Options:
#   --log FILE  tee target (default: $HOME/deepseek-chat.txt)
#   --keep      keep the temporary body file and print its path
set -uo pipefail

LOG="$HOME/deepseek-chat.txt"
KEEP=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --log)
      LOG="${2:?--log requires a value}"
      shift 2
      ;;
    --keep)
      KEEP=1
      shift
      ;;
    -h|--help)
      sed -n '2,16p' "$0"
      exit 0
      ;;
    *)
      echo "ERROR: unknown option: $1" >&2
      exit 64
      ;;
  esac
done

TMP_BASE=/tmp
if [ ! -d "$TMP_BASE" ] || [ ! -w "$TMP_BASE" ]; then
  TMP_BASE="$HOME/tmp"
  mkdir -p "$TMP_BASE"
fi
BODY_FILE="$TMP_BASE/inline-body.$$.sh"
trap 'if [ "$KEEP" -eq 0 ]; then rm -f "$BODY_FILE"; fi' EXIT

cat > "$BODY_FILE"
if [ ! -s "$BODY_FILE" ]; then
  echo "ERROR: empty body on stdin" >&2
  exit 64
fi

CARCASS=1
if ! grep -qE '^[[:space:]]*set +-[A-Za-z]*e' "$BODY_FILE"; then
  grep -qE '^[[:space:]]*set +.*pipefail' "$BODY_FILE" || CARCASS=0
fi

{
  echo
  echo "---"
  if [ "$CARCASS" -eq 0 ]; then
    echo "WARN: carcass missing: no 'set -...e' line in body"
  fi
  bash "$BODY_FILE"
} 2>&1 | tee "$LOG"
RC=${PIPESTATUS[0]}
echo
echo "---"
wc -l "$LOG"
if [ "$KEEP" -eq 1 ]; then
  echo "body kept: $BODY_FILE"
fi
exit $RC
