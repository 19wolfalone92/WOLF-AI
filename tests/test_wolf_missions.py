"""Contract tests for WOLF durable, consent-gated mission queue."""
from __future__ import annotations

import re
import unittest

from wolf_ai.missions import (
    MissionError,
    approval_ids,
    matching_run,
    observed_markers,
    parse_issue,
    run_once,
)


def sample_issue(number=123, uid="w" + "a" * 32, project="wolf", mode="test"):
    return {
        "number": number,
        "state": "open",
        "user": {"login": "19wolfalone92"},
        "title": "[WOLF TASK] " + project + ": Проверь проект!",
        "body": (
            "Задача, отправленная пользователем из WOLF AI Android.\n\n" +
            "**Проект:** " + project + "\n" +
            "**Режим:** " + mode + "\n" +
            "**ID задания:** " + uid + "\n\n" +
            "### Описание\n\nПроверь все ошибки и подготовь отчёт.\n"
        ),
    }


class FakeGitHub:
    def __init__(self, issues):
        self.issues = issues
        self.data = {issue["number"]: [] for issue in issues}
        self.dispatches = []
        self.runs = []
        self.fail_dispatch = False

    def open_issues(self):
        return self.issues

    def comments(self, number):
        return list(self.data[number])

    def comment(self, number, value):
        self.data[number].append({
            "id": 1000 + sum(len(x) for x in self.data.values()),
            "body": value,
            "user": {"login": "github-actions[bot]"},
            "created_at": "2026-11-08T01:00:00Z",
        })

    def dispatch_audit(self, mission):
        if self.fail_dispatch:
            raise MissionError("Simulated network loss")
        self.dispatches.append((mission.issue, mission.project, mission.mode, mission.uid))

    def recent_audits(self):
        return self.runs

    def add_user_approval(self, number, text, author="19wolfalone92"):
        self.data[number].append({
            "id": 100 + len(self.data[number]),
            "body": text,
            "user": {"login": author},
            "created_at": "2026-11-08T00:59:55Z",
        })


class MissionWorkerTests(unittest.TestCase):
    def test_parse_issue_must_be_owned_and_consistent(self):
        issue = sample_issue()
        m = parse_issue(issue)
        self.assertIsNotNone(m)
        self.assertEqual(m.project, "wolf")
        self.assertTrue(re.fullmatch(r"[0-9a-f]{64}", m.digest))
        for mutation in (
            lambda r: r["user"].update(login="unknown-user"),
            lambda r: r.update(title="[PR] bogus"),
            lambda r: r.update(state="closed"),
            lambda r: r.update(number=0),
            lambda r: r.update(body=r["body"].replace("**Режим:** test", "**Режим:** shell")),
            lambda r: r.update(body=r["body"] + "\n**Проект:** studio"),
        ):
            bad = sample_issue()
            mutation(bad)
            self.assertIsNone(parse_issue(bad))
        pr = sample_issue()
        pr["pull_request"] = {"url": "http://example.invalid"}
        self.assertIsNone(parse_issue(pr))

    def test_no_user_approval_no_run_and_spoof_rejected(self):
        api = FakeGitHub([sample_issue()])
        mission = parse_issue(api.issues[0])
        self.assertEqual(run_once(api, allow_dispatch=True)["dispatch_count"], 0)
        api.add_user_approval(123, mission.approval_prefix, "attacker")
        self.assertEqual(run_once(api, allow_dispatch=True)["dispatch_count"], 0)
        api.add_user_approval(123, "/wolf run " + mission.uid + " " + "b" * 64)
        self.assertEqual(run_once(api, allow_dispatch=True)["dispatch_count"], 0)
        self.assertEqual(api.dispatches, [])

    def test_exact_approval_one_dispatch_only_even_if_polled_again(self):
        api = FakeGitHub([sample_issue()])
        mission = parse_issue(api.issues[0])
        api.add_user_approval(123, mission.approval_prefix)
        result = run_once(api, allow_dispatch=True)
        self.assertEqual(result["dispatch_count"], 1)
        self.assertEqual(len(api.dispatches), 1)
        self.assertEqual(result["missions"][0]["state"], "accepted")
        second = run_once(api, allow_dispatch=True)
        self.assertEqual(second["dispatch_count"], 0)
        self.assertEqual(len(api.dispatches), 1)

    def test_receipt_then_real_workflow_reconciliation(self):
        api = FakeGitHub([sample_issue()])
        mission = parse_issue(api.issues[0])
        api.add_user_approval(123, mission.approval_prefix)
        run_once(api, allow_dispatch=True)
        api.runs = [{
            "id": 42424242,
            "event": "workflow_dispatch",
            "head_branch": "main",
            "created_at": "2026-11-08T01:00:01Z",
            "display_title": "WOLF wolf test " + mission.uid,
            "status": "completed",
            "conclusion": "success",
        }]
        done = run_once(api, allow_dispatch=True)
        self.assertEqual(done["finished_count"], 1)
        self.assertEqual(done["dispatch_count"], 0)
        self.assertEqual(done["missions"][0]["state"], "finished")
        final = run_once(api, allow_dispatch=True)
        self.assertEqual(final["finished_count"], 0)
        self.assertEqual(len(api.dispatches), 1)
        self.assertTrue(any("42424242" in c["body"] for c in api.comments(123)))

    def test_changed_issue_requires_new_sha_approval(self):
        api = FakeGitHub([sample_issue()])
        original = parse_issue(api.issues[0])
        api.add_user_approval(123, original.approval_prefix)
        api.issues[0]["body"] += "\nExtra instructions injected after consent."
        self.assertEqual(run_once(api, allow_dispatch=True)["dispatch_count"], 0)

    def test_dispatch_failure_reserved_and_does_not_auto_retry(self):
        api = FakeGitHub([sample_issue()])
        m = parse_issue(api.issues[0])
        api.add_user_approval(123, m.approval_prefix)
        api.fail_dispatch = True
        first = run_once(api, allow_dispatch=True)
        self.assertEqual(first["blocked_count"], 1)
        self.assertEqual(first["missions"][0]["state"], "dispatch_unknown_or_failed")
        api.fail_dispatch = False
        second = run_once(api, allow_dispatch=True)
        self.assertEqual(second["dispatch_count"], 0)
        self.assertEqual(api.dispatches, [])

    def test_explicit_second_approval_allows_a_new_attempt(self):
        api = FakeGitHub([sample_issue()])
        m = parse_issue(api.issues[0])
        api.add_user_approval(123, m.approval_prefix)
        run_once(api, allow_dispatch=True)
        api.add_user_approval(123, m.approval_prefix)
        second = run_once(api, allow_dispatch=True)
        self.assertEqual(second["dispatch_count"], 1)
        self.assertEqual(len(api.dispatches), 2)

    def test_readonly_inspection_cannot_write_or_dispatch(self):
        api = FakeGitHub([sample_issue()])
        m = parse_issue(api.issues[0])
        api.add_user_approval(123, m.approval_prefix)
        report = run_once(api, allow_dispatch=False)
        self.assertEqual(report["dispatch_count"], 0)
        self.assertEqual(report["missions"][0]["state"], "awaiting_dispatch")
        self.assertEqual(len(api.data[123]), 1)

    def test_limits_dispatches_per_batch(self):
        issues = [sample_issue(i + 1, uid="w" + ("%032x" % (i + 1)))
                  for i in range(8)]
        api = FakeGitHub(issues)
        for issue in issues:
            api.add_user_approval(issue["number"], parse_issue(issue).approval_prefix)
        report = run_once(api, allow_dispatch=True)
        self.assertEqual(report["dispatch_count"], 3)
        self.assertEqual(len(api.dispatches), 3)
        self.assertTrue(report["no_video_publication"])
        self.assertTrue(report["no_code_changes"])

    def test_max_three_owner_approvals(self):
        issue = sample_issue()
        m = parse_issue(issue)
        comments = [{"id": i, "body": m.approval_prefix,
                     "user": {"login": "19wolfalone92"}} for i in range(1, 9)]
        self.assertEqual(approval_ids(m, comments), [1, 2, 3])

    def test_only_github_actions_bot_can_sign_completion(self):
        m = parse_issue(sample_issue())
        forged = [{
            "body": m.marker(123, "finished"),
            "user": {"login": "attacker"},
        }]
        self.assertEqual(observed_markers(m, forged), {})
        valid = [dict(forged[0], user={"login": "github-actions[bot]"})]
        self.assertEqual(observed_markers(m, valid), {123: {"finished"}})

    def test_stale_or_foreign_workflow_run_cannot_match(self):
        m = parse_issue(sample_issue())
        run = {
            "id": 12,
            "event": "push",
            "head_branch": "main",
            "created_at": "2026-11-08T01:01:00Z",
            "display_title": "WOLF wolf test " + m.uid,
            "status": "success",
        }
        self.assertIsNone(matching_run(m, "2026-11-08T01:00:00Z", [run]))
        run["event"] = "workflow_dispatch"
        run["created_at"] = "2026-11-06T01:00:00Z"
        self.assertIsNone(matching_run(m, "2026-11-08T01:00:00Z", [run]))


if __name__ == "__main__":
    unittest.main()
