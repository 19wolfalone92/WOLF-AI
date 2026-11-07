# WOLF AI 3.0 — Work Hub, Video Publishing, Learning

WOLF AI now integrates practical mobile video handoff and real small-model CPU
training into the same Android application. This is **not** a complete copy of
ChatGPT Work's cloud browser, connected identity, unlimited compute or models.

## Added to the APK

- Embedded Qwen2.5-Coder-0.5B (GGUF+llama.cpp), offline ARM64.
- Existing GitHub Actions: audits, APK builds, Studio previews, limited
  verified Python auto-repair, Pull Request/Issue lists and receipts.
- **Opt-in local memory:** user-written facts stored only in app-private
  SQLite; 1–3 relevant records can be retrieved as reference for local Qwen.
  The neural weights remain unchanged.
- **Adaptive routing:** the user adds phrase+project examples; very close
  matches influence project selection on the phone without any cloud.
- **Real CPU training in GitHub:** the new wolf-ai-train.yml uses 96 curated
  public examples, trains a Multinomial Naive Bayes classifier with a
  reproducible holdout split, writes the JSON model and confusion matrix
  to GitHub Actions artifacts. It does NOT train Qwen neural weights.
  The GitHub-trained model is not automatically installed in the APK.

## Video sharing: TikTok, YouTube, Instagram, Facebook and others

User picks an MP4 through the Android document picker. WOLF checks duration,
resolution/orientation, audio track, H.264 codec, and first frame brightness
to warn about black covers. User confirms recipient and presses Share.
Android ACTION_SEND hands the video to an installed application. If that app
is absent, the standard Android share chooser is shown.

**This is user-assisted publishing, not unattended autopublishing.**
Final upload, privacy settings, music and the post button belong to the
social network app. Captions are copied to the clipboard on user's share
action and included as EXTRA_TEXT; receiving applications may ignore it.

### Why not direct autonomous publishing?

TikTok's official Direct Post API requires developer registration, user
OAuth authorization, approved video.publish scope and an audit to allow
general public postings. We have not obtained those approvals and cannot
bypass them. Unaudited applications have limited private-only posting.

Metricool REST API requires Advanced/Custom, not Free/Starter. Metricool
MCP is available in ChatGPT on Free, but a ChatGPT plugin's authorization
cannot automatically be transferred into WOLF Android. WOLF opens the
Metricool planner; it does NOT silently connect a paid API or promise a
scheduled TikTok publication.

Official references:
- https://developers.tiktok.com/docs/en/content-posting-api-get-started
- https://developers.tiktok.com/docs/en/share-kit-android-quickstart-v2
- https://help.metricool.com/basic-guide-for-api-integration-r97af
- https://help.metricool.com/plans-add-ons-and-api-access-explained-xux1u

## Safety and privacy

Personal notes/chat are local app-private SQLite, NOT full-database
encryption. The GitHub PAT uses Android Keystore encryption. WOLF does
not upload chat/notes to GitHub training datasets. Public Issues only
appear after clear user confirmation. No trades, auto-merge, hidden
TikTok actions, paid API usage or ChatGPT Work quota circumvention.

## Artifacts and verification

Android sources:
- android/app/src/main/java/ai/wolf/autopilot/PublicationCenter.java
- android/app/src/main/java/ai/wolf/autopilot/LearningStore.java

Training:
- wolf_ai/train_router.py
- training/tasks.jsonl
- tests/test_wolf_training.py
- .github/workflows/wolf-ai-train.yml

Build: GitHub WOLF-AI -> Actions -> WOLF AI Android — Build APK -> Artifacts.

CI compilation verifies packaging and Python tests; it does not prove
reliability on the user's exact Android phone. GitHub Actions quotas remain.
