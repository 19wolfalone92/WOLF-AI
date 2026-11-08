# 🐺 WOLF AI 8.0 — Offline PDF/DOCX + portable encrypted document memory

Version 8 addresses two major limitations of WOLF 7:
real PDFs/Word documents were not imported, and reinstalling a differently
signed Android APK could delete the local RAG document database.

## Features

**PDF**: Local selectable-text extraction using Apache-2.0
PdfBox-Android 2.0.27.0; input ≤4 MB and ≤45 pages; extracted text
≤1.5 MB. Scanned/image-only PDFs have no OCR in this release.
Encrypted/password-protected PDFs are not supported.

**DOCX**: Offline extraction via Android ZipInputStream and XML parser,
reading ONLY word/document.xml. No embedded media, macros, remote
resources or code are executed. 4 MB input limit, bounded ZIP entry
count/decompression and 1.5 MB maximum extracted text.

**Private RAG**: Extracted text joins TXT, Markdown, JSON, CSV, SRT
and code inside private SQLite FTS4. Search and local Qwen cite actual
file/fragment identifiers. No document is uploaded to an external LLM.

**Encrypted backups**: Create portable .wolfkb encrypted snapshots
of the complete indexed DOCUMENT LIBRARY using user passphrase and
AES-256-GCM + PBKDF2-HMAC-SHA256, 240,000 iterations, random salt/IV,
and 128-bit authentication tag. The passphrase is not persisted.

**Restore integrity**: The whole AES-GCM archive is staged and
authenticated, then the ZIP CRC and content schema are validated,
BEFORE any existing document is replaced. SQLite replacement uses
a single transaction; wrong passwords and corrupt archives do not
silently erase existing document data.

## Android usage

To import, open "Личная библиотека WOLF — RAG офлайн", tap
"Добавить файл", choose one PDF/DOCX/text file using Android Files,
and confirm local indexing. PDF text must be selectable; image-only
scans need a separate OCR system.

Before uninstalling a debug APK, open the same library and tap
"Сохранить зашифрованную библиотеку (.wolfkb)". Enter and confirm
a passphrase of at least 12 characters, choose the destination file,
and confirm export success. Store the passphrase separately.

After installing the new APK, tap "Восстановить библиотеку из .wolfkb",
type the passphrase, choose the archive and explicitly confirm that
the current DOCUMENT LIBRARY should be replaced. Authentication and
CRC validation happen BEFORE the SQLite transaction.

**Important:** This encrypted archive backs up ONLY indexed documents.
It does NOT include chat history, GitHub PAT, Metricool OAuth,
task queue, personal note/training examples, or user-imported GGUF
model weights. The existing "Экспорт изученного в JSON" separately
exports facts/routing examples; that JSON is NOT encrypted.
The owner should keep original files because the SQLite index is
local and may be removed when uninstalling an app. Android backup
is disabled and CI debug signing may change between versions.

## Security/technical acceptance

- PDF inputs capped at 4 MB and 45 pages; DOCX ZIP contents tightly bounded.
- Up to 30 indexed documents and ≤1.5 MB extracted text/document.
- Backup uses randomly generated salt/IV for each export, AES-GCM
  integrity verification before any DB mutation, and a 55 MB
  compressed/uncompressed staging size bound.
- Password minimum 12 characters; losing it loses access to backup.
- Android UI always requires owner selection and explicit confirmation.
- CI Java/Android compile and Python security-contract tests must pass.
- Actual decryption round trips and PDF/DOCX extraction MUST still be
  verified on real Android hardware; CI compilation alone cannot prove
  universal file compatibility or model quality.

Source: https://github.com/19wolfalone92/WOLF-AI
PdfBox-Android Apache-2.0: https://github.com/TomRoush/PdfBox-Android

This release does not create a GPT-6 clone, unlimited inference compute
or unrestricted ChatGPT Work computer-use automation.
