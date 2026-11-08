"""WOLF Missions: durable, explicitly approved GitHub Issue task executor.

This is a small trustworthy execution layer, NOT an unrestricted autonomous LLM.
The sole executable action is a fixed, allowlisted GitHub audit/test workflow.
A real user must explicitly approve each run by a SHA-bound Issue comment.

Durability: approval, reservation, dispatch and final outcome are all stored
in GitHub Issues. A reservation is posted BEFORE the external dispatch call.
Unknown timeouts are never silently retried; the owner can issue a fresh
explicit approval. Concurrent workers are serialized by GitHub Actions.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from wolf_ai.orchestrator import PROJECTS, atomic_json

OWNER = "19wolfalone92"
REPO = "WOLF-AI"
BASE = "https://api.github.com/repos/" + OWNER + "/" + REPO
WORKFLOW = "wolf-ai-agent.yml"
LEGACY_AUDIT_WORKFLOW = "wolf-ai-audit.yml"
BOT = "github-actions[bot]"
MAX_ISSUES = 150
MAX_COMMENTS = 100
MAX_DISPATCH_PER_RUN = 3
MAX_RECONCILE_PER_RUN = 12
MAX_APPROVALS_PER_ISSUE = 3
UID = re.compile(r"^w[a-f0-9]{32}$")
PROJECT = re.compile(r"(?m)^\*\*Проект:\*\* ([a-z0-9-]+)$")
MODE = re.compile(r"(?m)^\*\*Режим:\*\* (audit|test)$")
TASK_ID = re.compile(r"(?m)^\*\*ID задания:\*\* (w[a-f0-9]{32})$")
RECEIPT = re.compile(
    r"<!-- WOLF-MISSION/v1 issue=(\d+) uid=(w[a-f0-9]{32}) "
    r"sha=([a-f0-9]{64}) approval=(\d+) event=(reserved|accepted|finished) -->"
)


class MissionError(RuntimeError):
    """Safe failure with no raw API credential or issue-body echo."""


@dataclass(frozen=True)
class Mission:
    issue: int
    project: str
    mode: str
    uid: str
    digest: str
    title: str
    goal: str

    @property
    def approval_prefix(self) -> str:
        return "/wolf run " + self.uid + " " + self.digest

    def marker(self, approval_id: int, event: str) -> str:
        if event not in {"reserved", "accepted", "finished"}:
            raise ValueError("Unsupported mission event")
        return (f"<!-- WOLF-MISSION/v1 issue={self.issue} uid={self.uid} "
                f"sha={self.digest} approval={approval_id} event={event} -->")


def parse_issue(issue: dict[str, Any]) -> Mission | None:
    if not isinstance(issue, dict) or "pull_request" in issue:
        return None
    if issue.get("state") != "open" or issue.get("user", {}).get("login") != OWNER:
        return None
    title = issue.get("title", "")
    body = issue.get("body", "")
    number = issue.get("number")
    if not isinstance(title, str) or not title.startswith("[WOLF TASK] "):
        return None
    if not isinstance(body, str) or not 35 <= len(body) <= 3000:
        return None
    if not isinstance(number, int) or not 1 <= number <= 1_000_000_000:
        return None
    project = PROJECT.findall(body)
    mode = MODE.findall(body)
    uid = TASK_ID.findall(body)
    if len(project) != 1 or len(mode) != 1 or len(uid) != 1:
        return None
    if project[0] not in PROJECTS or not UID.fullmatch(uid[0]):
        return None
    if not title.startswith("[WOLF TASK] " + project[0] + ":"):
        return None
    # Only public Issue descriptions are forwarded as untrusted diagnostic
    # context. The goal never becomes shell code or an arbitrary URL.
    description = body.split("### Описание\n\n", 1)
    if len(description) != 2:
        return None
    goal = description[1].split("\n\n> ВАЖНО:", 1)[0].strip()
    if not 3 <= len(goal) <= 600:
        return None
    from wolf_ai.agent_engine import FORBIDDEN
    if FORBIDDEN.search(goal):
        return None
    # The digest binds the approval to the exact text, mode and project.
    return Mission(
        issue=number, project=project[0], mode=mode[0], uid=uid[0],
        digest=hashlib.sha256(body.encode("utf-8")).hexdigest(),
        title=title[:120], goal=goal,
    )


def approval_ids(mission: Mission, comments: list[dict]) -> list[int]:
    result = []
    for c in comments:
        if c.get("user", {}).get("login") != OWNER:
            continue
        if c.get("body", "").strip() != mission.approval_prefix:
            continue
        ident = c.get("id")
        if isinstance(ident, int) and ident > 0:
            result.append(ident)
    return result[:MAX_APPROVALS_PER_ISSUE]


def observed_markers(mission: Mission, comments: list[dict]) -> dict[int, set[str]]:
    found: dict[int, set[str]] = {}
    for c in comments:
        if c.get("user", {}).get("login") != BOT:
            continue
        value = c.get("body") or ""
        for match in RECEIPT.finditer(value):
            issue, uid, sha, approval, event = match.groups()
            if (int(issue) == mission.issue and uid == mission.uid and
                    sha == mission.digest):
                found.setdefault(int(approval), set()).add(event)
    return found


class GitHub:
    def __init__(self, token: str | None):
        self.token = token

    def request(self, method: str, path: str, payload: dict | None = None) -> Any:
        if (not path.startswith("/") or "//" in path or
                "\x00" in path or "://" in path or
                method not in {"GET", "POST"}):
            raise MissionError("Invalid GitHub API operation")
        if method == "POST" and not self.token:
            raise MissionError("GitHub issue/Actions write permission missing")
        data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "WOLF-AI-Missions/4.0",
        }
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if data is not None:
            headers["Content-Type"] = "application/json; charset=utf-8"
        request = urllib.request.Request(BASE + path, headers=headers,
                                         data=data, method=method)
        try:
            with urllib.request.urlopen(request, timeout=18) as response:
                status = response.status
                if method == "POST" and path.endswith("/dispatches") and status != 204:
                    raise MissionError("Unexpected Actions dispatch status")
                if method == "POST" and not path.endswith("/dispatches") and status != 201:
                    raise MissionError("GitHub did not accept the issue comment")
                content = response.read(300_001)
                if len(content) > 300_000:
                    raise MissionError("GitHub response too large")
                if not content:
                    return {}
                return json.loads(content)
        except urllib.error.HTTPError as error:
            # Error bodies can contain private data; emit HTTP code only.
            raise MissionError("GitHub HTTP " + str(error.code)) from None
        except urllib.error.URLError:
            raise MissionError("GitHub network connection failed") from None

    def open_issues(self) -> list[dict]:
        result = []
        for page in (1, 2, 3):
            items = self.request("GET", "/issues?state=open&per_page=50&page=" + str(page))
            if not isinstance(items, list):
                raise MissionError("GitHub Issues response changed")
            result.extend(items)
            if len(items) < 50:
                break
        return result[:MAX_ISSUES]

    def comments(self, issue_number: int) -> list[dict]:
        # Fail closed for huge comment threads instead of mistaking a partial
        # response for proof that the Issue was never dispatched.
        rows = self.request("GET", f"/issues/{issue_number}/comments?per_page=100")
        if not isinstance(rows, list) or len(rows) >= MAX_COMMENTS:
            raise MissionError("Issue comment history too large for safe dedupe")
        return rows

    def comment(self, issue_number: int, text: str) -> None:
        if len(text) > 3000:
            raise MissionError("Issue receipt too long")
        self.request("POST", f"/issues/{issue_number}/comments", {"body": text})

    def dispatch_audit(self, mission: Mission) -> None:
        # Compatibility name for the existing mission state machine.
        # New work goes to the v6 agent, which executes only allowlisted tools.
        self.request("POST", f"/actions/workflows/{WORKFLOW}/dispatches", {
            "ref": "main",
            "inputs": {
                "project": mission.project,
                "mode": mission.mode,
                "client_task_id": mission.uid,
                "goal": mission.goal,
                "safe_fix": "false",
            },
        })

    def recent_audits(self) -> list[dict]:
        # Reconcile both new agent runs and previous v5 audit runs, without
        # stranding reservations made before this upgrade.
        runs = []
        for workflow in (WORKFLOW, LEGACY_AUDIT_WORKFLOW):
            result = self.request(
                "GET", f"/actions/workflows/{workflow}/runs?per_page=100")
            if not isinstance(result, dict) or not isinstance(result.get("workflow_runs"), list):
                raise MissionError("Workflow run list unavailable")
            runs.extend(result["workflow_runs"])
        return sorted(runs, key=lambda item: item.get("created_at", ""), reverse=True)


def _utc_epoch(value: str) -> float:
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
    except (ValueError, AttributeError):
        return 0


def matching_run(mission: Mission, reservation_time: str, runs: list[dict]) -> dict | None:
    start = _utc_epoch(reservation_time)
    for run in runs:
        title = run.get("display_title", "")
        created = _utc_epoch(run.get("created_at", ""))
        if (run.get("event") != "workflow_dispatch" or
                run.get("head_branch") != "main" or
                mission.uid not in title or
                mission.project not in title or
                mission.mode not in title or
                created + 120 < start):
            continue
        return run
    return None


def run_once(api: GitHub, *, allow_dispatch: bool = False,
             now: float | None = None) -> dict:
    """At most 3 dispatches and 12 final updates per invocation.

    External API effects cannot be rolled back. There is no infinite
    retry; each approval comment permits at most one dispatch attempt.
    """
    clock = time.time() if now is None else now
    report = {
        "schema": 1,
        "status": "completed",
        "allowed_dispatch": allow_dispatch,
        "dispatch_count": 0,
        "finished_count": 0,
        "queued_count": 0,
        "blocked_count": 0,
        "error_types": [],
        "missions": [],
        "no_code_changes": True,
        "no_paid_api": True,
        "no_video_publication": True,
    }
    issues = api.open_issues()
    runs = None
    for issue in issues:
        mission = parse_issue(issue)
        if mission is None:
            continue
        try:
            comments = api.comments(mission.issue)
            approvals = approval_ids(mission, comments)
            if not approvals:
                continue
            markers = observed_markers(mission, comments)
            for approval in approvals:
                observed = markers.get(approval, set())
                row = {"issue": mission.issue, "project": mission.project,
                       "mode": mission.mode, "approval": approval}
                if "finished" in observed:
                    row["state"] = "completed_earlier"
                    continue
                if "reserved" in observed:
                    if runs is None:
                        runs = api.recent_audits()
                    reservation = next(
                        (c.get("created_at", "") for c in comments
                         if c.get("user", {}).get("login") == BOT and
                         mission.marker(approval, "reserved") in (c.get("body") or "")),
                        "",
                    )
                    found = matching_run(mission, reservation, runs)
                    if found is None:
                        row["state"] = "dispatch_unconfirmed"
                    elif found.get("status") != "completed":
                        row["state"] = "running"
                        row["run_id"] = found.get("id")
                    else:
                        if report["finished_count"] >= MAX_RECONCILE_PER_RUN:
                            row["state"] = "reconciliation_budget"
                        else:
                            run_id = found.get("id")
                            state = found.get("conclusion") or "unknown"
                            status = "✅" if state == "success" else "⚠️"
                            comment = (
                                mission.marker(approval, "finished") + "\n" +
                                f"{status} WOLF mission {mission.project}/{mission.mode}: "
                                f"GitHub Actions **{state}**.\n" +
                                f"Run: https://github.com/{OWNER}/{REPO}/actions/runs/{run_id}\n" +
                                "Result is a verified diagnostic agent report, not proof that the project is fixed or published."
                            )
                            if allow_dispatch:
                                api.comment(mission.issue, comment)
                            row["state"] = "finished" if allow_dispatch else "would_reconcile"
                            row["run_id"] = run_id
                            report["finished_count"] += 1
                else:
                    if not allow_dispatch:
                        row["state"] = "awaiting_dispatch"
                    elif report["dispatch_count"] >= MAX_DISPATCH_PER_RUN:
                        row["state"] = "batch_budget"
                    else:
                        # DURABLE RESERVATION FIRST. No automatic retry after this
                        # point, even on network timeouts or task-run failures.
                        api.comment(mission.issue,
                                    mission.marker(approval, "reserved") + "\n" +
                                    f"🐺 Выполнение согласовано: {mission.project}/{mission.mode}. " +
                                    "Резервирую одну попытку отправки в GitHub Actions. " +
                                    "Если связь оборвётся, нужна новая явная команда /wolf run.")
                        try:
                            api.dispatch_audit(mission)
                        except MissionError:
                            row["state"] = "dispatch_unknown_or_failed"
                            report["blocked_count"] += 1
                        else:
                            api.comment(mission.issue,
                                        mission.marker(approval, "accepted") + "\n" +
                                        "GitHub Actions принял запрос. Отчёт появится в Actions; " +
                                        "статус будет сверяться автоматически.")
                            row["state"] = "accepted"
                        report["dispatch_count"] += 1
                report["missions"].append(row)
                if row.get("state") in {"awaiting_dispatch", "batch_budget",
                                        "dispatch_unconfirmed", "running"}:
                    report["queued_count"] += 1
        except MissionError as exc:
            report["blocked_count"] += 1
            report["error_types"].append(str(exc)[:80])
            # Do not include user description, tokens or raw GitHub responses.
    report["checked_issues"] = len(issues)
    report["time_utc"] = datetime.fromtimestamp(clock, timezone.utc).isoformat()
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--run", action="store_true",
                        help="Allow bounded dispatch/reconcile writes after explicit Issue approval")
    options = parser.parse_args()
    api = GitHub(os.getenv("GITHUB_TOKEN"))
    try:
        result = run_once(api, allow_dispatch=options.run)
        atomic_json(options.report, result)
        print(json.dumps({
            "issues": result["checked_issues"],
            "dispatched": result["dispatch_count"],
            "finished": result["finished_count"],
            "blocked": result["blocked_count"],
        }))
        return 0 if result["blocked_count"] == 0 else 1
    except (MissionError, OSError, ValueError) as exc:
        print("WOLF mission check refused: " + str(exc)[:120])
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
