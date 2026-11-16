#!/usr/bin/env bash
# Post-PR checklist (AGENTS 5f) — run after a PR is merged.
# Steps: stash (if any) -> checkout dev -> remote update ->
# merge --ff-only origin/dev -> stash pop (if was stashed) ->
# delete local branch -> delete remote branch -> verify.
#
# Deps: git.
# Args: <branch> (the merged branch name to delete).
# Env:  none.
# Exit: 0 on success; non-zero on failure (branch still exists, etc.).
#
# Note (l-scripts-100): stash pop only if push actually created a stash.

set -uo pipefail
BRANCH="${1:-}"
if [ -z "$BRANCH" ]; then
  echo "usage: $0 <branch-to-delete>"
  exit 2
fi

echo "=== post-pr: branch=$BRANCH ==="
echo "current: $(git branch --show-current)"
echo "HEAD:    $(git log --oneline -1)"
echo

echo "=== 1. stash (if any) ==="
STASHED=0
if [ -n "$(git status --porcelain)" ]; then
  git stash push -u -m "post-pr: $BRANCH"
  STASHED=1
  echo "  stashed"
else
  echo "  nothing to stash"
fi
echo

echo "=== 2. checkout dev + sync ==="
git checkout dev
git remote update
git merge --ff-only origin/dev
echo

echo "=== 3. stash pop (if stashed) ==="
if [ "$STASHED" = "1" ]; then
  git stash pop || { echo "POP CONFLICT — resolve manually"; exit 1; }
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
sleep 2
if git branch -r | grep -q "$BRANCH"; then
  echo "  STILL EXISTS (check manually!)"
  git remote prune origin
  exit 1
fi
echo "  remote branch deleted"
echo

echo "=== 7. final state ==="
git status
