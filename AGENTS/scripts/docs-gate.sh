#!/usr/bin/env bash
# Docs gate (5a): run before any docs-only commit.
# Checks: trailing ws, double "---", fence parity, broken docs/*.md links.
#
# Deps: git, grep, awk.
# Args: optional list of .md files.
#       If none: all .md from `git diff --name-only HEAD` + `git diff --cached --name-only`.
# Exit: 0 = all pass; 1 = any FAIL (reason on stderr).
#
# Read-only; safe to re-run. Not in Gradle gate.

set -uo pipefail

FILES=()
if [ "$#" -gt 0 ]; then
  FILES=("$@")
else
  # union of changed (unstaged+staged) and untracked .md files
  {
    git diff --name-only HEAD 2>/dev/null || true
    git diff --cached --name-only 2>/dev/null || true
    git ls-files --others --exclude-standard 2>/dev/null || true
  } | grep -E '\.md$' | sort -u | while IFS= read -r f; do
    printf '%s\n' "$f"
  done > /tmp/docs-gate-files.txt
  while IFS= read -r f; do
    FILES+=("$f")
  done < /tmp/docs-gate-files.txt
fi

if [ "${#FILES[@]}" -eq 0 ]; then
  echo "=== docs-gate: no .md files to check"
  exit 0
fi

echo "=== docs-gate: ${#FILES[@]} file(s)"
echo

FAIL=0
for F in "${FILES[@]}"; do
  echo "--- $F ---"
  if [ ! -f "$F" ]; then
    echo "FAIL: not a file: $F" >&2; FAIL=1; continue
  fi

  # 1. trailing whitespace via git diff --check (tracked files)
  if git ls-files --error-unmatch "$F" >/dev/null 2>&1; then
    if ! git diff --check -- "$F" 2>/dev/null; then
      echo "FAIL: trailing ws in $F" >&2; FAIL=1
    else
      echo "  OK: trailing ws"
    fi
  else
    # untracked: direct regex
    if grep -nE ' +$' "$F" >/dev/null 2>&1; then
      echo "FAIL: trailing ws in $F" >&2; FAIL=1
    else
      echo "  OK: trailing ws (untracked)"
    fi
  fi

  # 2. double "---" (line-anchored)
  if awk 'prev=="---" && $0=="---" {print "DOUBLE --- at " NR; bad=1} {prev=$0} END{exit bad+0}' "$F"; then
    echo "  OK: no double ---"
  else
    echo "FAIL: double --- in $F" >&2; FAIL=1
  fi

  # 3. fence parity (``` and ~~~ counted together)
  N=$(grep -cE '^(```|~~~)' "$F" || true)
  N=${N:-0}
  if [ $((N % 2)) -eq 0 ]; then
    echo "  OK: fence parity ($N)"
  else
    echo "FAIL: fence parity ($N) in $F" >&2; FAIL=1
  fi

  # 4. broken docs/*.md links
  BROKEN=0
  for d in $(grep -oE 'docs/[a-zA-Z0-9_./-]+\.md' "$F" | sort -u); do
    if [ ! -f "$d" ]; then
      echo "FAIL: broken link $d in $F" >&2; BROKEN=1
    fi
  done
  if [ "$BROKEN" -eq 0 ]; then
    echo "  OK: docs/*.md links"
  else
    FAIL=1
  fi
  echo
done

if [ "$FAIL" -ne 0 ]; then
  echo "=== docs-gate: FAIL" >&2
  exit 1
fi
echo "=== docs-gate: PASS"
exit 0
