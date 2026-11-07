# WOLF AI 2.0 — автономное Android-приложение

Собственный проект Android, перенесённый из Two-Worlds-Studio. Код: `app/`, сборка из корня репозитория: `gradle --no-daemon -p android :app:assembleDebug` (после установки Android SDK/NDK, лицензированного Qwen GGUF и pinned llama.cpp по инструкциям `../docs/ALL_IN_ONE.md`).

Для загрузки собранной APK: вкладка **Actions** репозитория `19wolfalone92/WOLF-AI` → **WOLF AI Android — Build APK** → **Artifacts**.

Версия 2.0 содержит Qwen и llama.cpp внутри APK, **Termux не требуется**. GitHub Actions использует ограниченные разрешения; для запуска из приложения нужен fine-grained PAT на **WOLF-AI и Two-Worlds-Studio**, только **Actions: Read and write**. Видео/торговлю не публикует.

Ограничения: модель 0.5B слабая; CI доказывает сборку/пакет, а не стабильность на всех Android устройствах.
