#!/usr/bin/env bash
# Post-PR checklist (AGENTS 5f) \u2014 run after a PR is merged.
# Steps: stash (if any) -> checkout dev -> remote update ->
# merge --ff-only origin/dev -> stash pop (if was stashed) ->
# delete local branch -> delete remote branch -> verify.
#
# Deps: git, grep, awk.
# Args:
#   <branch>          merged branch name to delete (required).
#   --task T-N        optional: verify ROADMAP state for T-N.
# Env:  none.
# Exit: 0 on success; non-zero on failure (branch still exists, missing
#       mark done, etc.).
#
# Note (l-scripts-100): stash pop only if push actually created a stash.

set -euo pipefail
BRANCH=""
TASK=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --task) TASK="$2"; shift 2 ;;
    --task=*) TASK="${1#--task=}"; shift ;;
    -h|--help)
      echo "usage: $0 <branch-to-delete> [--task T-N]"
      exit 0
      ;;
    --) shift; break ;;
    -*) echo "unknown flag: $1" >&2; exit 2 ;;
    *) BRANCH="$1"; shift ;;
  esac
done

if [ -z "$BRANCH" ]; then
  echo "usage: $0 <branch-to-delete> [--task T-N]" >&2
  exit 2
fi

echo "=== post-pr: branch=$BRANCH task=${TASK:-none} ==="
echo "current: $(git branch --show-current)"
echo "HEAD:    $(git log --oneline -1)"
echo

echo "=== 1. stash (if any) ==="
STASHED=0
if [ -n "$(git status --porcelain)" ]; then
  git stash push -u -m "post-pr: $BRANCH" || {
    echo "ERROR: git stash push failed \u2014 tree not saved, abort" >&2
    exit 1
  }
  STASHED=1
  echo "  stashed"
else
  echo "  nothing to stash"
fi
echo

echo "=== 2. checkout dev + sync ==="
git checkout dev || { echo "ERROR: git checkout dev failed, abort" >&2; exit 1; }

UPD_OUT=""
if ! UPD_OUT="$(git remote update --prune 2>&1)"; then
  printf '%s\n' "$UPD_OUT" >&2
  echo "ERROR: git remote update failed \u2014 dev is NOT synced with origin, abort" >&2
  exit 1
fi
printf '%s\n' "$UPD_OUT"

git rev-parse --verify -q origin/dev >/dev/null || {
  echo "ERROR: origin/dev not found after remote update, abort" >&2
  exit 1
}
git merge --ff-only origin/dev || {
  echo "ERROR: git merge --ff-only origin/dev failed, abort" >&2
  exit 1
}
echo

echo "=== 3. stash pop (if stashed) ==="
if [ "$STASHED" = "1" ]; then
  git stash pop || { echo "POP CONFLICT \u2014 resolve manually"; exit 1; }
else
  echo "  nothing to pop"
fi
echo

echo "=== 4. delete local branch ==="
git branch -d "$BRANCH" 2>&1 || { echo "branch -d failed (not merged?)"; exit 1; }
echo

echo "=== 5. delete remote branch ==="
git push origin --delete "$BRANCH" 2>&1 || echo "  skip: remote already gone"
echo

echo "=== 6. verify remote branch gone ==="
i=0
while [ "$i" -lt 5 ] && git branch -r | grep -q "$BRANCH"; do
  i=$((i + 1))
  sleep 2
  git fetch --prune --quiet origin || break
done
if git branch -r | grep -q "$BRANCH"; then
  echo "  STILL EXISTS (check manually!)"
  git remote prune origin || true
  exit 1
fi
echo "  remote branch deleted"
echo

echo "=== 7. final state ==="
git status
echo

if [ -n "$TASK" ]; then
  echo "=== 8. ROADMAP check for $TASK (docs_lint) ==="
  if python3 AGENTS/scripts/docs_lint.py --require-done "$TASK"; then
    echo "  OK: $TASK state accepted"
  else
    echo "  ERROR: mark done missing for $TASK" >&2
    echo "  fix: docs(roadmap): mark $TASK done (docs-only commit in dev)" >&2
    exit 1
  fi
  echo
else
  echo "=== 8. ROADMAP check: skip (no --task) ==="
  echo "  tip: pass --task T-N to verify mark done"
  echo
fi

echo "=== post-pr: DONE ==="
