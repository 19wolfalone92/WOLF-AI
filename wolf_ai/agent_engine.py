"""WOLF AI 6: bounded, evidence-first autonomous project agent.

The agent has a genuine multi-step observe -> plan -> act -> verify loop,
state checkpoints, one bounded transient-error recovery and safe local
Python auto-fix (opt-in, WOLF repository only). It is NOT an unrestricted
code-writing model, cloud browser, publishing bot or trading agent.

Only developer-allowlisted recipes can execute. The goal is untrusted
context: it is never used to construct a command, repository URL or path.
The resulting patch (if any) stays in an artifact for human review.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import os
import re
import subprocess
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from wolf_ai.orchestrator import PROJECTS, atomic_json, recipes, run

MAX_GOAL = 600
MAX_FILES = 2500
MAX_DIAGNOSES = 12
SKIP_DIRS = {".git", ".venv", "venv", "__pycache__", "node_modules", "build",
             "dist", ".gradle", "generated", ".next"}
FORBIDDEN = re.compile(
    r"(?i)(github_pat_|ghp_[A-Za-z0-9]{15}|sk-[A-Za-z0-9]{15}|"
    r"password\s*[:=]|bearer\s+[A-Za-z0-9]{12}|private.key)",
)


class AgentError(ValueError):
    """Safe, human-readable validation error; never log secrets."""


@dataclass(frozen=True)
class Plan:
    project: str
    mode: str
    goal_sha256: str
    steps: tuple[str, ...]
    safe_fix: bool = False

    def as_dict(self) -> dict:
        return {"project": self.project, "mode": self.mode,
                "goal_sha256": self.goal_sha256,
                "steps": list(self.steps), "safe_fix": self.safe_fix}


def make_plan(project: str, mode: str, goal: str, *, safe_fix: bool = False) -> Plan:
    if project not in PROJECTS or mode not in {"audit", "test"}:
        raise AgentError("Unsupported project or tool recipe")
    if not isinstance(goal, str) or not 3 <= len(goal.strip()) <= MAX_GOAL:
        raise AgentError("Goal must contain 3..600 characters")
    if FORBIDDEN.search(goal):
        raise AgentError("Goal resembles a credential: remove secrets")
    if safe_fix and not (project == "wolf" and mode == "test"):
        raise AgentError("Local auto-fix is restricted to WOLF Python tests")
    from wolf_ai.dispatch_contract import validate_dispatch
    validate_dispatch({"project": project, "mode": mode,
                       "client_task_id": "manual", "goal": goal,
                       "safe_fix": safe_fix})
    steps = ["inventory", "execute_allowlisted_checks", "diagnose_failures"]
    if safe_fix:
        steps.append("safe_python_repair")
    steps.extend(("verify_evidence", "recommend_next_actions"))
    digest = hashlib.sha256(goal.encode("utf-8")).hexdigest()
    return Plan(project, mode, digest, tuple(steps), safe_fix)


def inventory(source: Path) -> dict:
    """Non-executing filesystem observation. No symlinks or file bodies."""
    counts: collections.Counter[str] = collections.Counter()
    found = 0
    truncated = False
    for base, dirs, files in os.walk(source, followlinks=False):
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS and
                         not (Path(base) / d).is_symlink())
        for name in sorted(files):
            file = Path(base) / name
            if file.is_symlink() or not file.is_file():
                continue
            found += 1
            if found > MAX_FILES:
                truncated = True
                break
            counts[file.suffix.lower() or "<no_extension>"] += 1
        if truncated:
            break
    # This is a *local* read of HEAD, not an online API call or code execution.
    revision = "unavailable"
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--verify", "HEAD"], cwd=source,
            capture_output=True, text=True, check=False, timeout=5)
        if result.returncode == 0 and re.fullmatch(r"[a-f0-9]{40,64}", result.stdout.strip()):
            revision = result.stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        pass
    return {"repo": str(source.name), "revision": revision, "files_scanned": min(found, MAX_FILES),
            "truncated": truncated, "extension_counts": dict(counts.most_common(18))}


def classify(log: str, status: str) -> tuple[str, str]:
    """Bounded rule-based diagnosis. No claims of LLM reasoning or fixes."""
    if status == "timeout":
        return "timeout", "Read-only check exceeded its time limit; reduce scope or rerun explicitly."
    if status == "unavailable":
        return "environment_unavailable", "Required runtime/tool is unavailable on this runner."
    patterns = (
        ("ModuleNotFoundError", "dependency", "Check pinned dependency installation in CI."),
        ("command not found", "missing_tool", "Install the missing CLI in a trusted workflow."),
        ("SyntaxError", "syntax", "Review the indicated Python syntax; no automatic edit applied."),
        ("AssertionError", "assertion", "Reproduce the failing test and inspect expected behavior."),
        ("FAILED (", "test_failure", "Examine unit test failures before proposing a patch."),
        ("npm ERR!", "node_failure", "Check npm dependencies and the project test script."),
        ("error:", "compiler_failure", "Inspect compiler diagnostics and the referenced source."),
    )
    for needle, category, recommendation in patterns:
        if needle.lower() in log.lower():
            return category, recommendation
    return ("failed_check", "Inspect the GitHub Actions check and reproduce locally.")


def diagnoses(audit: dict) -> list[dict]:
    output: list[dict] = []
    for step in audit.get("steps", []):
        state = step.get("status")
        if state in {"passed", "planned"}:
            continue
        code, action = classify(str(step.get("log", "")), str(state))
        output.append({"step": str(step.get("name", "?"))[:80],
                       "status": str(state)[:35], "kind": code,
                       "recommended_action": action})
        if len(output) >= MAX_DIAGNOSES:
            break
    return output


def _audit(source: Path, out: Path, plan: Plan, remaining: int) -> dict:
    # The immutable recipe registry creates exact argv; goal text is NOT run.
    return run(plan.project, plan.mode, source, out, execute=True,
               budget_seconds=max(15, min(remaining, 900)))


def execute(plan: Plan, source: Path, output: Path, *, budget_seconds: int = 750,
            allow_safe_fix: bool = False) -> dict:
    """Execute and checkpoint a bounded agent workflow with real evidence."""
    source, output = source.resolve(), output.resolve()
    if not source.is_dir() or not (source / ".git").exists():
        raise AgentError("Target must be a real Git checkout")
    if output == source or source in output.parents:
        raise AgentError("Agent reports must be outside source checkout")
    if not 30 <= budget_seconds <= 1200:
        raise AgentError("Budget must be 30..1200 seconds")
    if plan.safe_fix and not allow_safe_fix:
        raise AgentError("Safe fix needs a separate explicit approval")
    # Do not silently allow an unexpected plan structure or different mode.
    expected_steps = make_plan(plan.project, plan.mode, "valid dummy goal",
                               safe_fix=plan.safe_fix).steps
    if plan.steps != expected_steps:
        raise AgentError("Plan steps have been modified outside the allowlist")
    if not re.fullmatch(r"[a-f0-9]{64}", plan.goal_sha256):
        raise AgentError("Invalid goal digest")

    start = time.monotonic()
    report = {
        "schema": 1, "product": "wolf-ai-6", "plan": plan.as_dict(),
        "status": "running", "started_utc": datetime.now(timezone.utc).isoformat(),
        "events": [], "diagnoses": [], "human_approval_required_for_merge": True,
        "source_sent_to_external_llm": False, "paid_api_used": False,
        "social_posting": False, "trading": False,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    audit_file = output.parent / "agent_audit.json"
    patch_file = output.parent / "agent_safe_python.patch"

    def checkpoint(event: str, state: str, **fields: object) -> None:
        report["events"].append({"stage": event, "state": state, **fields})
        atomic_json(output, report)

    checkpoint("plan", "approved", tools=list(plan.steps))
    try:
        observed = inventory(source)
        report["inventory"] = observed
        checkpoint("inventory", "complete", files=observed["files_scanned"])

        budget_left = max(15, int(budget_seconds - (time.monotonic() - start)))
        original = _audit(source, audit_file, plan, budget_left)
        report["audit_status"] = original["status"]
        checkpoint("execute_allowlisted_checks", original["status"],
                   checks=[{"name": x["name"], "status": x["status"]}
                           for x in original["steps"]])

        report["diagnoses"] = diagnoses(original)
        checkpoint("diagnose_failures",
                   "complete" if report["diagnoses"] else "no_failures",
                   count=len(report["diagnoses"]))

        # The only automatic recovery is ONE retry of read-only checks that
        # timed out or suffered an unavailable environment. Logical test
        # failures are never retried in an endless "hope it turns green" loop.
        transient = any(x.get("status") in {"timeout", "unavailable"}
                        for x in original["steps"])
        if transient and time.monotonic() - start < budget_seconds - 60:
            retried = _audit(source, output.parent / "agent_retry.json",
                             plan, max(15, int(budget_seconds -
                                              (time.monotonic() - start))))
            report["retry_status"] = retried["status"]
            report["retry_diagnoses"] = diagnoses(retried)
            checkpoint("recover_transient_checks", retried["status"],
                       attempt=2, max_attempts=2)
            final_audit = retried
        else:
            final_audit = original

        if plan.safe_fix:
            # Ruff applies only safe import-formatting/unused-import fixes to
            # WOLF Python on the temporary checkout. Patch is NOT merged.
            if time.monotonic() - start >= budget_seconds - 80:
                checkpoint("safe_python_repair", "skipped_budget")
            else:
                try:
                    from wolf_ai.repair_engine import run_repair
                    fix = run_repair(source, apply=True, output=output.parent / "agent_repair.json")
                    report["repair"] = {"status": fix["status"],
                                        "modified_files": fix["modified_files"],
                                        "patch_sha256": fix.get("patch_sha256"),
                                        "auto_merged": False}
                    # Read the bounded local diff; it is already validated by
                    # repair_engine. Save it only in a user-visible artifact.
                    if fix["modified_files"]:
                        patch = subprocess.run(
                            ["git", "diff", "--no-ext-diff", "--binary", "--",
                             "wolf_ai", "tests"],
                            cwd=source, capture_output=True, text=True, timeout=15,
                            check=False)
                        if patch.returncode != 0 or len(patch.stdout.encode()) > 120000:
                            raise AgentError("Could not save bounded verified patch")
                        patch_file.write_text(patch.stdout, encoding="utf-8")
                        checkpoint("safe_python_repair", "verified_patch",
                                   modified=len(fix["modified_files"]))
                        final_audit = _audit(source, output.parent / "agent_postfix_audit.json",
                                             plan, max(15, int(budget_seconds -
                                                               (time.monotonic() - start))))
                    else:
                        checkpoint("safe_python_repair", "no_safe_changes")
                except Exception as exc:
                    # No merge and no secret-bearing exception raw text.
                    report["repair"] = {"status": "blocked", "auto_merged": False,
                                        "reason": type(exc).__name__}
                    checkpoint("safe_python_repair", "blocked",
                               reason=type(exc).__name__)

        checks = final_audit.get("steps", [])
        statuses = [x.get("status") for x in checks]
        if not checks:
            raise AgentError("No actual verification steps were run")
        report["verified_steps"] = [{"name": x["name"], "status": x["status"]}
                                    for x in checks]
        all_passed = all(x == "passed" for x in statuses)
        report["verification"] = "passed" if all_passed else "failed"
        checkpoint("verify_evidence", report["verification"],
                   expected=len(recipes(plan.project, plan.mode)),
                   observed=len(checks))

        # Use concrete evidence; never claim a project is generally bug-free.
        report["diagnoses"] = diagnoses(final_audit)
        report["next_actions"] = (
            ["Review the verified patch artifact and tests before opening/merging a PR."]
            if patch_file.is_file() else
            [d["recommended_action"] for d in report["diagnoses"][:4]] or
            ["All configured checks passed. This does NOT prove complete correctness."]
        )
        report["status"] = "verified_checks" if all_passed else "needs_attention"
        checkpoint("recommend_next_actions", "complete",
                   recommendations=len(report["next_actions"]))
    except Exception as exc:
        report["status"] = "blocked"
        report["error_kind"] = type(exc).__name__
        checkpoint("failure", "blocked", reason=type(exc).__name__)
    report["elapsed_seconds"] = round(time.monotonic() - start, 2)
    report["finished_utc"] = datetime.now(timezone.utc).isoformat()
    atomic_json(output, report)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", choices=tuple(PROJECTS), required=True)
    parser.add_argument("--mode", choices=("audit", "test"), required=True)
    parser.add_argument("--goal", required=True)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--budget-seconds", type=int, default=750)
    parser.add_argument("--allow-safe-fix", action="store_true")
    opts = parser.parse_args()
    try:
        plan = make_plan(opts.project, opts.mode, opts.goal,
                         safe_fix=opts.allow_safe_fix)
        result = execute(plan, opts.source, opts.output,
                         budget_seconds=opts.budget_seconds,
                         allow_safe_fix=opts.allow_safe_fix)
    except (AgentError, ValueError, OSError) as exc:
        print("WOLF agent blocked: " + type(exc).__name__)
        return 2
    print(json.dumps({"product": "WOLF AI 6", "status": result["status"],
                      "verified": result.get("verification", "not_run"),
                      "stage_count": len(result["events"])}, ensure_ascii=False))
    return 0 if result["status"] == "verified_checks" else 1


if __name__ == "__main__":
    raise SystemExit(main())
