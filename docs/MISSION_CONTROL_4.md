# 🐺 WOLF AI 4.0 — Mission Control

WOLF AI now has **durable, explicitly approved multi-project audit missions**.
Unlike a temporary ChatGPT/Work conversation, the queue and receipts live in
GitHub Issues. When Android is offline or closed, a finite scheduled GitHub
Actions job can process owner-approved tasks and reconcile results.

## What it really does

1. User writes a task in WOLF Android; it is saved locally.
2. User explicitly selects **Send to GitHub Issues**. The text becomes a
   **public** Issue in WOLF-AI, with project, audit/test mode and opaque task ID.
   This step alone **never** executes the task.
3. User taps **Approve autonomous mission** on that published task.
   WOLF Android fetches the current Issue, verifies owner/project/mode/task ID,
   calculates SHA-256 of the exact Issue body, then after confirmation posts
   the exact authorization command:
   /wolf run w<32 hex chars> <64-character SHA-256>
4. The worker at .github/workflows/wolf-ai-missions.yml runs every 2 hours
   (17 minutes past even UTC hours), or immediately when triggered from Android.
   It checks open Issues; only **owner-authored Issues** containing an exact
   **owner-authored matching approval comment** are eligible.
5. For each approved Issue, the worker writes a durable reservation
   **before** any workflow dispatch and then invokes exactly the existing
   allowlisted workflow wolf-ai-audit.yml, with project/mode/task ID.
   It never executes task text as shell commands or code.
6. The same worker later reconciles real GitHub Actions run status and posts
   the result and run link in the corresponding Issue. The user can inspect
   it from the app even after restarting it or turning the phone off.

The server state is **not** a private app database or ephemeral CI disk.
It persists in normal GitHub Issues and Actions history.

## Allowed actions & strict restrictions

- Project choices: wolf, studio, shaira, williams, williams-ai,
  english-child (fixed repo whitelist in orchestrator.py).
- Modes: audit or test; recipes are hardcoded, not generated from Issue text.
- For Shaira and other projects with non-public repositories, GitHub checkout
  may require separately granted read access. A failed checkout is reported
  as failure, never silently treated as success.
- The worker can create **at most 3 audit/test dispatches** per invocation.
- At most 12 finished jobs are reconciled per invocation.
- At most 3 explicit approvals per Issue.
- GitHub may delay scheduled jobs and has plan/runtime/concurrency limits.
- Unknown network outcomes are *never automatically retried*. The user
  must explicitly approve another attempt after verifying prior state.
- **No arbitrary repository writes, app purchases, publishing social posts,
  real/demo trades, paid LLM requests or secret transmission.**
- Even a passed audit is not a proof that the entire project is finished,
  fixed, or bug-free.

### GitHub token permissions

For Android controls, use a **fine-grained GitHub PAT** scoped to WOLF-AI
with Actions: Read and write, Issues: Read and write. Existing Studio controls
may need separately authorized Actions permissions for Two-Worlds-Studio.
GitHub Code editor needs Contents and Pull requests: Read and write ONLY if
the user elects to create code PRs from the phone.

GitHub Actions uses its own short-lived built-in GITHUB_TOKEN with
contents: read, issues: write and actions: write for the mission workflow.
It does NOT use Android's PAT or write secrets into Issues/Artifacts.

## No duplicate or cancelled jobs

- Each approval has its own GitHub comment ID.
- A matching machine receipt reserves exactly one dispatch attempt.
- The worker looks only at GitHub-generated receipts when deduplicating.
- A changed Issue body invalidates the prior approval digest.
- WOLF audit workflow concurrency now separates **different task IDs**,
  preventing an independent mission from cancelling another.
- A failed reservation/dispatch is visible; there is no invisible retry loop.

## Scheduled self-improvement

Alongside Mission Control:
- Every day at 03:15 UTC: finite WOLF self-audit (existing).
- Every Monday at 06:30 UTC: Studio audit (existing).
- Every Monday at 04:20 UTC: retrain/evaluate CPU task classifier.
- Every Sunday at 04:35 UTC: run safe deterministic Python auto-fixes on
  WOLF source and try to open a **human-review-only Pull Request** if tests
  pass (never automatically merge). If repository policy blocks PR creation,
  the verified patch is left as an artifact.
- Mission queue poll: at minute 17 of even UTC hours, only owner-approved
  jobs are eligible.

This is an **incremental automation system**, NOT a background GPT-6 instance.

## Optional stable APK signing

By default GitHub builds a fresh Android **debug APK**. Debug signatures can
change between GitHub runners, which prevents installation over an existing
debug APK without uninstalling the old one (and losing local databases).

WOLF 4.0 now supports an **opt-in persistent release keystore**:
- In WOLF-AI GitHub Settings → Secrets and variables → Actions, configure
  WOLF_RELEASE_KEYSTORE_B64 (base64 of your private JKS),
  WOLF_STORE_PASSWORD, WOLF_KEY_ALIAS, and WOLF_KEY_PASSWORD.
- The **main-branch-only** Android workflow restores the JKS in temporary
  runner storage, builds an Android release APK, validates its signature,
  and uploads a separate signed-release artifact.
- The same private JKS must be reused for all later releases. Keep a secure
  offline backup of the JKS and passwords. Losing them means future updates
  with the same package signature may be impossible.
- No key or password is committed to GitHub, sent to ChatGPT, or placed in an
  Issue. An incomplete set of secrets results in debug-only APK.
- Existing older debug-installed apps generally cannot be upgraded with a
  differently signed release. **Export learning data first** (WOLF 3.0+);
  other local data must be backed up separately before uninstalling.

CI has not verified optional signing without the owner's secret keystore.

## How to verify

GitHub repo: https://github.com/19wolfalone92/WOLF-AI

- Actions → WOLF AI — Approved Mission Worker → latest run → mission receipt.
- Issues → the task's owner approval + WOLF reservation + real run link.
- Actions → WOLF AI — free-first project audit → independent audit report.
- Actions → WOLF AI — Safe Self-Repair → verified patch/PR.
- Actions → WOLF AI Android — Build APK → debug or optional signed release.

Trust the **actual GitHub run conclusions**, not just a button or AI statement.
