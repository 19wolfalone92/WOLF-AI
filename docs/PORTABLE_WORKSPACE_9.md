# WOLF AI 9.0 — Encrypted portable personal workspace

This update addresses the main remaining data-loss risk when installing
new Android debug APKs: the WOLF 8 encrypted .wolfkb archive included
only indexed documents, NOT chat, locally taught knowledge or task history.

## New .wolfpack complete private-data snapshot

The Android app now provides two additional buttons next to its RAG
document library:

- **Сохранить ВСЮ личную среду (.wolfpack)**
- **Восстановить ВСЮ личную среду (.wolfpack)**

A full .wolfpack archive includes FOUR kinds of locally owned user data:
1. Indexed RAG documents and searchable text passages, including
   imported PDF/DOCX/plaintext excerpts (up to 30 document records).
2. Saved learning facts and task-routing examples (up to 100/120).
3. Local chat history (up to the app's current 40-message retention).
4. Local project task journal (up to 100 records), including GitHub
   Issue IDs, GitHub Actions run IDs and their last known statuses.

Remote jobs are NOT restarted by importing a backup. Users should
refresh GitHub Actions and reconcile the external state after restore.

**Intentionally excluded:** Android SecureStore / GitHub PAT,
Metricool OAuth access tokens, private signing certificates, the
Android APK, downloaded GGUF models, cached multimedia, device
permissions and browser sessions. Do not claim that the archive is
a complete image of the entire Android application.

The document-only .wolfkb format from v8 remains supported through
its existing buttons. It is deliberately NOT confused with .wolfpack
because their authenticated headers differ.

## Security of .wolfpack

WOLF uses AES-256-GCM with a random 16-byte salt and 12-byte IV per
export, PBKDF2-HMAC-SHA256 with 240,000 iterations, and a 128-bit
authentication tag. Passphrases must be 12–256 characters.

The app first stages and authenticates the ENTIRE encrypted archive,
then validates ZIP structure/CRC and bounded JSON schemas for all
components. A malformed archive or wrong password is rejected BEFORE
the local database mutation begins.

Each of the four SQLite databases uses a transaction during import.
SQLite cannot provide one atomic transaction across these four
independent connections; if an import unexpectedly fails after a
previous DB has been changed, WOLF attempts a **best-effort rollback**
using pre-restore snapshots in private temporary files. The app
discloses if rollback also fails. Never uninstall the old app or
delete the working encrypted archive until successful validation.

A snapshot includes user-written chat/task/document text. Such text
might itself contain sensitive details pasted by the user, so the
archive must be protected with a long unique passphrase and stored
in a trusted location. There is no cloud sync or unlimited storage.

## Instructions: move your data across APK installs

1. While the previous WOLF installation is STILL AVAILABLE, open
   **Личная библиотека WOLF — RAG офлайн**.
2. Press **Сохранить ВСЮ личную среду (.wolfpack)**.
3. Enter a unique passphrase (12+ characters), repeat it, then save
   the .wolfpack file with Android Files.
4. Confirm the export succeeded and keep BOTH the archive and
   passphrase somewhere safe outside the app.
5. Only then install the new APK. A debug signing mismatch may require
   uninstalling the previous APK — this erases internal app data.
6. In the new version, press **Восстановить ВСЮ личную среду (.wolfpack)**.
   Enter the passphrase and select the stored file.
7. Explicitly confirm replacement of documents, learning examples,
   chat and task records. Refresh the actual GitHub Actions statuses
   because preserved task statuses may be stale.
8. Reconfigure GitHub/Metricool tokens independently, if needed, and
   re-import any larger GGUF model.

**Migration from v8:** previous .wolfkb files contain ONLY documents.
Import them with the existing document-only restore button.
V8 versions cannot retrospectively create a v9 .wolfpack file.
If you still have v8 installed, preserve separately exported learning
JSON and other needed data before uninstalling. Keeping the same
release signature, once configured, avoids many future reinstallation
problems; the default GitHub debug APK is not guaranteed to share
its predecessor's signature.

## Safety acceptance

- Read-only backup creation must never dispatch a GitHub workflow.
- Portable task imports do not trigger remote actions.
- Authenticated encryption and full ZIP reading occur before any
  SQLite mutation.
- All imported record counts, text lengths, project codes, task UUIDs
  and source names are bounded and validated.
- Main PR Python tests and Android CI must pass before merging.
- An Android-device test is still needed for AES-GCM export/import,
  TaskStore/SQLite migrations and real file-provider behavior.
  A successful compile is not equivalent to a proven device
  migration.

Source: https://github.com/19wolfalone92/WOLF-AI
