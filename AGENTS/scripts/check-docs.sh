#!/usr/bin/env bash
# check-docs.sh — 5 checks for docs/ journals and AGENTS/.
# Exit 0 if all pass, 1 on first failure. Read-only.
# tmp: primary /tmp, fallback $HOME/tmp (AGENTS 4k).
set -uo pipefail

REPO_ROOT="${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$REPO_ROOT" || { echo "FAIL: cannot cd to $REPO_ROOT"; exit 1; }

NORM="AGENTS/scripts/docs_normalize.py"

# tmp policy (4k): primary /tmp, fallback $HOME/tmp
TMPDIR_LOCAL="/tmp"
if [ ! -d "$TMPDIR_LOCAL" ] || [ ! -w "$TMPDIR_LOCAL" ]; then
  TMPDIR_LOCAL="$HOME/tmp"
  mkdir -p "$TMPDIR_LOCAL"
fi

FAIL=0

echo "=== 1. docs/: normalize --check ==="
if python3 "$NORM" --check --all --root docs >"$TMPDIR_LOCAL/check-docs-1.log" 2>&1; then
  echo "OK: docs/ normalize clean"
else
  echo "FAIL: docs/ normalize deviations:"
  tail -20 "$TMPDIR_LOCAL/check-docs-1.log"
  FAIL=1
fi

echo "=== 2. AGENTS/: normalize --check --check-index ==="
if python3 "$NORM" --check --all --check-index >"$TMPDIR_LOCAL/check-docs-2.log" 2>&1; then
  echo "OK: AGENTS/ clean, check-index consistent"
else
  echo "FAIL: AGENTS/ deviations or check-index errors:"
  tail -20 "$TMPDIR_LOCAL/check-docs-2.log"
  FAIL=1
fi

echo "=== 3. index row counts ==="
check_count() {
  local f="$1"; local exp="$2"; local name="$3"
  local n
  n=$(grep -cE '^\| ' "$f" 2>/dev/null || true)
  n=${n:-0}
  if [ "$n" -eq "$exp" ]; then
    echo "OK: $name rows=$n (expected $exp)"
  else
    echo "FAIL: $name rows=$n (expected $exp)"
    FAIL=1
  fi
}
check_count "docs/roadmap/ROADMAP.md" 39 "ROADMAP index"
check_count "docs/roadmap/ci-roadmap.md" 13 "ci-roadmap index"
check_count "docs/issues/ISSUES.md" 33 "ISSUES index"
check_count "docs/clean-audit/clean-audit.md" 10 "clean-audit index"

echo "=== 4. status tokens in roadmap files ==="
for f in docs/roadmap/ROADMAP-wip.md docs/roadmap/ROADMAP-plan.md \
         docs/roadmap/ROADMAP-defer.md docs/roadmap/ROADMAP-done.md \
         docs/roadmap/ci-roadmap-wip.md docs/roadmap/ci-roadmap-plan.md \
         docs/roadmap/ci-roadmap-defer.md docs/roadmap/ci-roadmap-done.md; do
  [ -f "$f" ] || continue
  N=$(grep -cE '^### \[(WIP|PLAN|DEFER|DONE)\] [TC]-[0-9]+' "$f" 2>/dev/null || true)
  N=${N:-0}
  S=$(grep -cE '^<!-- SUBSECTION_BEGIN:' "$f" 2>/dev/null || true)
  S=${S:-0}
  if [ "$N" -eq "$S" ]; then
    echo "OK: $f tokens=$N subs=$S"
  else
    echo "FAIL: $f tokens=$N subs=$S (all ### must have [TOKEN] T-/C-N)"
    FAIL=1
  fi
done

echo "=== 5. status body matches token (WIP/PLAN/DEFER/DONE) ==="
check_status() {
  local f="$1"
  [ -f "$f" ] || return 0
  python3 - "$f" <<'PY'
import re, sys
from pathlib import Path
f = Path(sys.argv[1])
text = f.read_text(encoding="utf-8")
lines = text.splitlines()
cur_tok = None
bad = 0
tok_map = {"WIP": "в работе", "PLAN": "запланировано", "DEFER": "отложено", "DONE": "выполнено"}
for line in lines:
    m = re.match(r"^### \[(WIP|PLAN|DEFER|DONE)\] ", line)
    if m:
        cur_tok = m.group(1); continue
    if cur_tok:
        ms = re.match(r"^\*\*Статус:\*\*\s*(.+)$", line)
        if ms:
            cur_status = ms.group(1).strip().lower()
            expected = tok_map[cur_tok]
            ok = cur_status.startswith(expected) or (cur_tok == "DONE" and "✅" in line)
            if not ok:
                print(f"FAIL: {f.name}: [{cur_tok}] status={cur_status!r} (expected {expected!r})")
                bad += 1
            cur_tok = None
if bad: sys.exit(1)
PY
}
for f in docs/roadmap/ROADMAP-wip.md docs/roadmap/ROADMAP-plan.md \
         docs/roadmap/ROADMAP-defer.md docs/roadmap/ROADMAP-done.md \
         docs/roadmap/ci-roadmap-wip.md docs/roadmap/ci-roadmap-plan.md \
         docs/roadmap/ci-roadmap-defer.md docs/roadmap/ci-roadmap-done.md; do
  check_status "$f" || FAIL=1
done
[ "$FAIL" -eq 0 ] && echo "OK: status body matches token"

echo "=== 6. link checker (relative .md links) ==="
python3 - <<'PY' || FAIL=1
import re, sys
from pathlib import Path
bad = 0
for f in Path("docs").rglob("*.md"):
    if ".git" in f.parts: continue
    for m in re.finditer(r"\]\(([^)]+\.md)(#[^)]*)?\)", f.read_text(encoding="utf-8")):
        target = m.group(1)
        if target.startswith("http") or target.startswith("/"):
            continue
        p = (f.parent / target).resolve()
        if not p.exists():
            print(f"FAIL: {f}: broken link -> {target}")
            bad += 1
if bad: sys.exit(1)
PY
[ "$FAIL" -eq 0 ] && echo "OK: all links alive"

echo "---"
if [ "$FAIL" -eq 0 ]; then
  echo "ALL CHECKS PASSED"
  exit 0
else
  echo "SOME CHECKS FAILED"
  exit 1
fi
