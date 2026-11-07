"""Bounded, deterministic Python repair engine for WOLF AI itself.

This engine is intentionally not a general LLM that can rewrite arbitrary
repositories. It uses a pinned local linter, verifies the git diff allowlist,
runs tests, and emits an immutable receipt for a human-reviewed PR.
No secrets, arbitrary commands, hidden scheduling or paid APIs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

SCAN_PATHS = ("wolf_ai", "tests")
REPAIRABLE_EXTENSIONS = (".py",)
MAX_PATCH_BYTES = 120_000
MAX_MODIFIED_FILES = 30


class RepairError(RuntimeError):
    """A user-correctable safety or validation failure."""


def command(repo: Path, args: list[str], timeout: int = 180) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=repo, text=True, encoding="utf-8", errors="replace",
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          check=False, timeout=timeout,
                          env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"})


def is_repairable(path: str) -> bool:
    p = PurePosixPath(path)
    return (not p.is_absolute() and ".." not in p.parts and
            len(p.parts) >= 2 and p.parts[0] in SCAN_PATHS and
            p.suffix in REPAIRABLE_EXTENSIONS and
            not any(s.startswith(".") or s.startswith("__") for s in p.parts))


def dirty_paths(repo: Path) -> list[str]:
    # Detect both tracked and untracked files; never operate on a dirty checkout.
    result = subprocess.run(["git", "status", "--porcelain=v1", "-z"],
                            cwd=repo, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            check=False, timeout=20)
    if result.returncode:
        raise RepairError("Git repository is required")
    entries = [s.decode("utf-8", "replace") for s in result.stdout.split(b"\x00") if s]
    return entries


def validate_diff(repo: Path) -> tuple[list[str], str]:
    status = command(repo, ["git", "diff", "--name-only", "--no-ext-diff", "--", *SCAN_PATHS], 20)
    if status.returncode:
        raise RepairError("Could not list modified files")
    names = [x for x in status.stdout.splitlines() if x]
    all_changed = command(repo, ["git", "diff", "--name-only", "--no-ext-diff"], 20)
    if all_changed.returncode or sorted(all_changed.stdout.splitlines()) != sorted(names):
        raise RepairError("A repair attempted to modify files outside the allowlist")
    if len(names) > MAX_MODIFIED_FILES or any(not is_repairable(n) for n in names):
        raise RepairError("Too many files or unsafe file paths in repair")
    if any((repo / x).is_symlink() for x in names):
        raise RepairError("Symlink changes are not permitted")
    patch = command(repo, ["git", "diff", "--no-ext-diff", "--binary", "--", *SCAN_PATHS], 20)
    if patch.returncode:
        raise RepairError("Could not produce git diff")
    data = patch.stdout.encode("utf-8")
    if len(data) > MAX_PATCH_BYTES:
        raise RepairError("Repair patch exceeds byte budget")
    if any(flag in patch.stdout for flag in
           ("GIT binary patch", "new file mode", "deleted file mode", "rename from", "rename to")):
        raise RepairError("Unsafe binary/new/delete/rename diff")
    return names, patch.stdout


def run_repair(repo: Path, *, apply: bool, output: Path, linter: str = "ruff") -> dict:
    repo = repo.resolve(strict=True)
    if not (repo / ".git").exists():
        raise RepairError("Must work in a checked-out git repository")
    if output.resolve() == repo or repo in output.resolve().parents:
        raise RepairError("Report must be stored outside source checkout")
    if dirty_paths(repo):
        raise RepairError("Dirty checkout refused; changes must be isolated on a fresh branch")
    report = {
        "schema": 1, "product": "wolf-ai", "recipe": "python-safe-autofix",
        "repo": "19wolfalone92/WOLF-AI", "applied": False,
        "auto_merged": False, "no_external_ai": True, "no_paid_api": True,
        "allowed_paths": list(SCAN_PATHS), "started_utc": datetime.now(timezone.utc).isoformat(),
        "modified_files": [], "status": "planned", "tests": "not_run"
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    if not apply:
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        return report
    # F401 unused imports; F841 unused local variables; I import sorting.
    # Includes no unsafe --unsafe-fixes or broad refactors.
    fix = command(repo, [linter, "check", "--select", "F401,F841,I",
                         "--fix", "--no-unsafe-fixes", *SCAN_PATHS], 160)
    names, patch = validate_diff(repo)
    report["modified_files"] = names
    report["patch_sha256"] = hashlib.sha256(patch.encode("utf-8")).hexdigest()
    report["patch_bytes"] = len(patch.encode("utf-8"))
    report["applied"] = bool(names)
    # Always validate again, even if linter reports a failed fix.
    lint = command(repo, [linter, "check", "--select", "F401,F841,I", *SCAN_PATHS], 120)
    tests = command(repo, [sys.executable, "-B", "-m", "unittest", "discover",
                           "-s", "tests", "-p", "test_wolf*.py", "-v"], 180)
    report["linter_passed"] = lint.returncode == 0
    report["tests"] = "passed" if tests.returncode == 0 else "failed"
    report["test_excerpt"] = tests.stdout[-3500:]
    report["status"] = ("verified_change" if names else "clean") if (
        lint.returncode == 0 and tests.returncode == 0
    ) else "verification_failed"
    report["completed_utc"] = datetime.now(timezone.utc).isoformat()
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # A failed test never becomes a PR; caller must check status.
    if report["status"] == "verification_failed":
        raise RepairError("Auto-fix did not pass linter and tests; do NOT open PR")
    return report


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--repo", type=Path, default=Path("."))
    p.add_argument("--report", type=Path, required=True)
    p.add_argument("--apply", action="store_true", help="Execute restricted local repair; default is plan only")
    args = p.parse_args()
    try:
        result = run_repair(args.repo, apply=args.apply, output=args.report)
    except (RepairError, subprocess.TimeoutExpired, OSError) as exc:
        print("WOLF repair blocked:", exc, file=sys.stderr)
        return 2
    print(json.dumps({k: result[k] for k in ("status", "modified_files", "tests")}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
