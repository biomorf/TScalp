"""Shared canon library for docs tooling. Import only, never executed.

SSoT for the rules: docs/docs-structure.md
Used by: docs_normalize.py (fixer), docs_lint.py (gate), docs_add.py / docs_cut.py.
"""
import os
import re
from pathlib import Path

PAD_LINES = 10

FILE_BEGIN_LINE = "<!-- FILE_BEGIN -->"
FILE_END_LINE = "<!-- FILE_END -->"
SECTION_BEGIN_PREFIX = "<!-- SECTION_BEGIN: "
SECTION_END_PREFIX = "<!-- SECTION_END: "

# SUBSECTION-only markers (used by the fixer)
BEGIN_RE = re.compile(r"^<!-- SUBSECTION_BEGIN: ([A-Za-z0-9_-]+) -->$")
END_RE = re.compile(r"^<!-- SUBSECTION_END: ([A-Za-z0-9_-]+) -->$")

# Any structural marker (used by the gate)
ANY_BEGIN_RE = re.compile(r"^<!-- (FILE|SECTION|SUBSECTION)_BEGIN(?:: ([A-Za-z0-9_-]+))? -->$")
ANY_END_RE = re.compile(r"^<!-- (FILE|SECTION|SUBSECTION)_END(?:: ([A-Za-z0-9_-]+))? -->$")

PREFIX_RE = re.compile(
    r"^<!-- (?:AGENTS_|LESSONS_)(FILE|SECTION|SUBSECTION)_(BEGIN|END)(?:: ([A-Za-z0-9_-]+))? -->$"
)
ANCHOR_RE = re.compile(r'^<a id="([A-Za-z0-9_-]+)"></a>$')
HEADING_RE = re.compile(r"^### \[([A-Z-]+)\] ([A-Za-z0-9_-]+) (.*)$")
NEXT_ID_RE = re.compile(r"^\*\*Next ID:\*\* ([TC])-(\d+)$")
STATUS_RE = re.compile(r"^\*\*Статус:\*\*\s*(.+)$")

ROADMAP_TOKENS = {"WIP", "PLAN", "DEFER", "DONE"}
TOKEN_STATUS = {
    "WIP": "в работе",
    "PLAN": "запланировано",
    "DEFER": "отложено",
    "DONE": "выполнено",
}

ID_WORD_RE = re.compile(r"[A-Za-z][A-Za-z0-9-]*\Z")

TOKEN_BY_FILE = {
    "ROADMAP-plan.md": "PLAN",
    "ROADMAP-defer.md": "DEFER",
    "ROADMAP-wip.md": "WIP",
    "ROADMAP-done.md": "DONE",
    "ci-roadmap-plan.md": "PLAN",
    "ci-roadmap-defer.md": "DEFER",
    "ci-roadmap-wip.md": "WIP",
    "ci-roadmap-done.md": "DONE",
}


def expected_token(file_path: str) -> str | None:
    """TOKEN for the file family, or None for files outside level 3."""
    name = file_path.rsplit("/", 1)[-1]
    if name in TOKEN_BY_FILE:
        return TOKEN_BY_FILE[name]
    if name.startswith("AGENTS-LESSONS-") and name.endswith(".md"):
        return "AGENTS-LESSONS"
    if name.startswith("AGENTS-") and name.endswith(".md"):
        return "AGENTS"
    return None


def strip_prefix(line: str) -> tuple[str, bool]:
    """Replace a prefixed marker (AGENTS_FILE_BEGIN, LESSONS_SECTION_END) with a plain one."""
    m = PREFIX_RE.match(line)
    if not m:
        return line, False
    kind, bound, mid = m.group(1), m.group(2), m.group(3)
    plain = f"<!-- {kind}_{bound}: {mid} -->" if mid else f"<!-- {kind}_{bound} -->"
    return plain, True


def looks_like_id(word: str) -> bool:
    """True for marker-id-like words (code-project, r4k, T-44); titles do not match."""
    if not ID_WORD_RE.fullmatch(word):
        return False
    return any(c.isdigit() or c == "-" for c in word)


def normalize_heading(heading: str, sid: str, token: str | None) -> tuple[str, list[str]]:
    """Return '### [TOKEN] <ID> <title>' plus deviations; None token leaves it untouched."""
    devs: list[str] = []
    if token is None:
        return heading, devs

    body = heading[4:]
    cur_tok = None
    m = re.match(r"^\[([A-Za-z-]+)\] (.*)$", body)
    if m:
        cur_tok = m.group(1)
        body = m.group(2)

    want_id = sid.upper() if token in ROADMAP_TOKENS else sid

    parts = body.split(" ", 1)
    first = parts[0] if parts else ""
    if first.lower() == sid.lower() or looks_like_id(first):
        got_id, title = first, (parts[1] if len(parts) == 2 else "")
    else:
        got_id, title = None, body

    if cur_tok is None:
        devs.append(f"missing TOKEN (expected [{token}])")
    elif cur_tok != token:
        devs.append(f"TOKEN mismatch: [{cur_tok}] (expected [{token}])")

    if got_id is None:
        devs.append(f"missing heading ID (expected {want_id!r})")
    elif got_id != want_id:
        devs.append(f"heading ID {got_id!r} != {want_id!r}")

    out = f"### [{token}] {want_id}"
    if title:
        out += f" {title}"
    return out, devs


def enforce_transitions(lines: list[str]) -> tuple[list[str], int]:
    """Exactly PAD_LINES blanks before SECTION_END, FILE_END and SECTION_BEGIN.

    The first SECTION_BEGIN sits right after FILE_BEGIN with no blank lines.
    """
    out: list[str] = []
    fixed = 0
    for line in lines:
        s = line.strip()
        target = None
        if s.startswith(SECTION_END_PREFIX) or s == FILE_END_LINE:
            target = PAD_LINES
        elif s.startswith(SECTION_BEGIN_PREFIX):
            prev = out[-1].strip() if out else ""
            target = 0 if prev == FILE_BEGIN_LINE else PAD_LINES
        if target is not None:
            had = 0
            while out and out[-1].strip() == "":
                out.pop()
                had += 1
            if out:
                out.extend(["\n"] * target)
                if had != target:
                    fixed += 1
        out.append(line)
    return out, fixed

def find_block(lines: list[str], sid: str) -> tuple[int, int]:
    """Return (begin, end) line indexes of the SUBSECTION block with marker id sid."""
    begin = f"<!-- SUBSECTION_BEGIN: {sid} -->"
    end = f"<!-- SUBSECTION_END: {sid} -->"
    try:
        b = lines.index(begin)
    except ValueError as exc:
        raise ValueError(f"block not found: {sid}") from exc
    try:
        e = lines.index(end)
    except ValueError as exc:
        raise ValueError(f"block end not found: {sid}") from exc
    if e < b:
        raise ValueError(f"block markers out of order: {sid}")
    return b, e


def find_section_end(lines: list[str]) -> int:
    """Return the index of the first SECTION_END line."""
    for i, line in enumerate(lines):
        if line.startswith(SECTION_END_PREFIX):
            return i
    raise ValueError("SECTION_END not found")


def tmp_path(name: str) -> Path:
    """tmp policy (4k): primary /tmp, fallback $HOME/tmp."""
    primary = Path("/tmp")
    if primary.is_dir() and os.access(primary, os.W_OK):
        return primary / name
    home_tmp = Path.home() / "tmp"
    home_tmp.mkdir(parents=True, exist_ok=True)
    return home_tmp / name
