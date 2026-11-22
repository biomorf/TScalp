#!/usr/bin/env bash
# gate-commit.sh — run the local gate, then commit the named files.
#
# Usage:
#   bash AGENTS/scripts/gate-commit.sh --msg-file <path> --files <f1> [f2 ...] \
#     [--no-build-cache] [--keep] [--log <path>] [--branch <name>] [--push]
#
# Steps:
#   1. Cyrillic in the message file is rejected (AGENTS-style-git 5b);
#   2. gate: bash AGENTS/scripts/run-local-gate.sh [passed flags];
#   3. gate red -> nothing is committed, exit 1;
#   4. gate green -> git add -- <files> (explicit list, 4g), the staged list
#      must match the requested list exactly, then git commit -F <msg-file>;
#   5. push to origin <branch> only with --push.
#
# Args:
#   --msg-file <path>  commit message file (required)
#   --files <f1> ...   explicit file list for git add (required)
#   --no-build-cache   passed to run-local-gate.sh
#   --keep             passed to run-local-gate.sh
#   --log <path>       passed to run-local-gate.sh (default /tmp/gate.log)
#   --branch <name>    assert the current branch equals <name>
#   --push             push to origin <branch> after a successful commit
# Env:
#   GATE_CMD           gate command (default: bash AGENTS/scripts/run-local-gate.sh); tests only
# Exit: 0 = committed; 1 = gate red; 2 = usage; 3 = pre-check failed; 4 = staged list mismatch
set -euo pipefail

MSG=""
WANT_BRANCH=""
PUSH=0
FILES=()
GATE_ARGS=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    --msg-file)
      MSG="${2:?--msg-file requires a value}"
      shift 2
      ;;
    --files)
      shift
      while [ "$#" -gt 0 ] && [ "${1#--}" = "$1" ]; do
        FILES+=("$1")
        shift
      done
      ;;
    --no-build-cache)
      GATE_ARGS+=("--no-build-cache")
      shift
      ;;
    --keep)
      GATE_ARGS+=("--keep")
      shift
      ;;
    --log)
      GATE_ARGS+=("--log" "${2:?--log requires a value}")
      shift 2
      ;;
    --branch)
      WANT_BRANCH="${2:?--branch requires a value}"
      shift 2
      ;;
    --push)
      PUSH=1
      shift
      ;;
    -h|--help)
      sed -n '2,32p' "$0"
      exit 0
      ;;
    *)
      echo "unknown arg: $1" >&2
      exit 2
      ;;
  esac
done

if [ -z "$MSG" ]; then
  echo "ERROR: --msg-file is required" >&2
  exit 2
fi
if [ "${#FILES[@]}" -eq 0 ]; then
  echo "ERROR: --files is required" >&2
  exit 2
fi
if [ ! -s "$MSG" ]; then
  echo "ERROR: message file is empty: $MSG" >&2
  exit 2
fi
for f in "${FILES[@]}"; do
  if [ ! -e "$f" ]; then
    echo "ERROR: file not found: $f" >&2
    exit 2
  fi
done
if grep -qP '[\x{0400}-\x{04FF}]' "$MSG"; then
  echo "ERROR: cyrillic in commit message — abort" >&2
  exit 3
fi

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ -n "$WANT_BRANCH" ] && [ "$BRANCH" != "$WANT_BRANCH" ]; then
  echo "ERROR: branch mismatch: $BRANCH != $WANT_BRANCH" >&2
  exit 2
fi

GATE_CMD="${GATE_CMD:-bash AGENTS/scripts/run-local-gate.sh}"
echo "=== gate-commit: branch=$BRANCH files=${FILES[*]}"
echo "=== gate: $GATE_CMD ${GATE_ARGS[*]:-}"
set +e
$GATE_CMD "${GATE_ARGS[@]}"
GATE=$?
set -e
echo "--- gate exit=$GATE ---"
if [ "$GATE" -ne 0 ]; then
  echo "GATE RED — commit skipped" >&2
  exit 1
fi

git add -- "${FILES[@]}"
STAGED=$(git diff --cached --name-only | sort)
WANTED=$(printf '%s\n' "${FILES[@]}" | sort)
if [ "$STAGED" != "$WANTED" ]; then
  echo "ERROR: staged list mismatch" >&2
  echo "staged:" >&2
  echo "$STAGED" >&2
  echo "wanted:" >&2
  echo "$WANTED" >&2
  exit 4
fi
echo "--- staged ---"
git diff --cached --stat
git commit -F "$MSG"
echo "COMMITTED $(git rev-parse --short HEAD) on $BRANCH"
if [ "$PUSH" -eq 1 ]; then
  git push origin "$BRANCH"
  echo "PUSHED origin $BRANCH"
  echo "ahead/behind: $(git rev-list --count "$BRANCH...origin/$BRANCH" 2>/dev/null || echo n/a)"
else
  echo "not pushed (pass --push to push)"
fi
exit 0
