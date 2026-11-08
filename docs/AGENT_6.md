# 🐺 WOLF AI 6.0 — Autonomous Agent (bounded and verified)

## Two agent loops that actually execute

**On-device Tool Agent.** With one explicit approval, Android may read
saved local notes, WOLF-AI's real GitHub Actions history and Wikimedia
search results. The offline Qwen GGUF explains the evidence and includes
a trace of which read-only tools were called. User notes, tokens and chat
history never become Wikimedia search queries. No network writes are
performed by this mode.

**GitHub Project Agent.** After a separate approval in a task card, a
workflow performs a finite observe → plan → execute → diagnose → recover
transient failures once → verify → report cycle. It writes JSON
checkpoints into GitHub Actions Artifacts; its budget is 750 seconds.
It does not construct shell commands from user text, and it never merges,
trades or publishes social media.

## Android instructions

1. Install WOLF AI 6.0 and connect a fine-grained GitHub PAT for WOLF-AI
   with Actions: Read and write (other projects may need read access).
2. For a referenced answer, open the Qwen section and tap
   «🤖 Спросить агента (память + источники + GitHub)».
   Approve the potentially external READ-ONLY queries in the dialog.
3. For a project task, create a task with an explicit project and mode
   in WOLF's existing task panel. Tap
   «🤖 Запустить Agent 6.0: план → тесты → диагноз» in its card.
4. Review exactly what is sent to GitHub. This includes the task's
   description, project, mode and opaque task ID. WOLF-AI is public:
   do not put secrets or personal information in this workflow input.
5. Optionally, for wolf/test ONLY, enable a checkbox to allow **safe
   Python Ruff auto-fixes on a temporary runner copy**. No code is
   pushed or merged. Download the verified patch artifact for review.
6. After a confirmed GitHub HTTP 204, use «Обновить запуски GitHub»
   to view the real agent run and its artifact. If the connection is
   lost after submission, WOLF preserves the reservation and blocks
   blind retries. Check GitHub before another attempt.

The new SQLite v4 migration preserves previous tasks, statuses and
Issue numbers. Agent status and run IDs are stored separately.

## What the engine actually does

| Stage | Allowed action | Evidence |
| --- | --- | --- |
| Inventory | Count files/extensions and get checked-out Git SHA | Repo hash/metadata |
| Plan | Select fixed recipe from project and audit/test mode | SHA-256 goal digest |
| Execute | Run developer-allowlisted commands, time-bounded | Actual return codes and bounded redacted logs |
| Diagnose | Categorize observed failures | Error type and next steps |
| Recovery | Repeat only timeouts/unavailable checks once at most | Retry receipt |
| Safe repair | Optional WOLF Python-only Ruff fixes on disposable checkout | Verified patch SHA-256; no remote write |
| Verify | Inspect actual test results and rerun after patch | Pass/fail, not guessed success |
| Report | Save each stage atomically outside source tree | agent_report.json and audit JSON |

The workflow requires only contents: read. It cannot automatically
create branches, PRs or publications; the existing independent
user-reviewed Python repair workflow handles optional PR proposals.

## Existing approved Issue missions

The WOLF Mission Control worker (every two hours) now routes new
owner-approved Issue missions into the v6 agent workflow, with
safe_fix=false. The owner approval remains bound to the exact public
Issue body SHA-256. Both old audit and new agent workflows are read
for status reconciliation, so earlier reservations remain traceable.
Other project repositories stay read-only.

## What the AI itself can and cannot do

The Android chat agent uses the user's installed, entirely local Qwen
model (or imported compatible GGUF). Project checks and recovery are a
deterministic, finite engine, not a general-purpose LLM coder. The
local assistant may explain a plan or the observed tool data but does
not receive authority to invent or execute arbitrary new tools.

The read-only web source is Wikipedia; this is **not** a universal
browser. ChatGPT Work cloud apps, GPT-6 weights, unlimited compute and
full autonomous TikTok publishing are not reproduced. Existing
Metricool MCP OAuth still requires user-side authorization and runtime
verification.

## Code and verification

- Agent: wolf_ai/agent_engine.py
- Fixed tool registry and tests: wolf_ai/orchestrator.py
- Agent workflow: .github/workflows/wolf-ai-agent.yml
- Owner-approved mission routing: wolf_ai/missions.py
- Android local tool agent: LocalToolAgent.java
- Android task runner: GithubApi.java / MainActivity.java / TaskStore.java
- Unit tests: tests/test_wolf_agent.py and tests/test_wolf_agent_android.py
- GitHub: https://github.com/19wolfalone92/WOLF-AI

A release is verified only after Python CI, agent smoke and Android
builds are green, and after a real workflow_dispatch of the merged
main-branch agent produces an artifact. App behavior on the user's
phone, GH token permissions, Metricool OAuth and public social
publishing cannot be proven by CI alone.
