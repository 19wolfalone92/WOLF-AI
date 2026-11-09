# WOLF-AI 17.2 — границы архитектуры, безопасность и CI

## Реальная архитектура (а не один универсальный Python-сервер)

```text
Android UI HomeActivity
    ├── WolfCommandRouter / CommandParser (намерения, allowlist)
    ├── EmbeddedAi → JNI wolf_native.cpp → локальный llama.cpp / GGUF (CPU)
    ├── DocumentAssistant / KnowledgeBase / LearningStore (локальная память)
    ├── DeviceActions / WolfAccessibilityService (только Android, согласие)
    ├── BrowserActivity / TikTokStudioActivity (локальный UI)
    └── GithubApi / GithubOperations (HTTPS, разрешённый workflow)
                               │
                               ▼
                     GitHub Actions dispatch
                               │
                               ▼
               Python wolf_ai/agent_engine.py
               Python wolf_ai/orchestrator.py
               Python wolf_ai/missions.py
                    │
                    └── проверка, ограниченный план,
                        результаты и отчёты в GitHub Artifacts
```

### Разделение ответственности

| Зона | Исходники | Назначение | Не должна делать |
| --- | --- | --- | --- |
| Android UI | HomeActivity, MainActivity, GuideActivity | Диалог и ручные подтверждения | Выполнять произвольные shell-команды |
| Локальная логика | WolfCommandRouter, CommandParser, LocalToolAgent | Выбрать безопасное действие; собрать read-only данные | Публиковать данные без согласия или выдавать модельный текст за команду |
| Локальная нейросеть | EmbeddedAi, wolf_native.cpp, DocumentAssistant | CPU GGUF, память и анализ | Запускать GitHub или сеть непосредственно по «мысли» модели |
| Android системная интеграция | DeviceActions, WolfAccessibilityService | Однократное действие в пользовательском UI | Скрытый мониторинг, обход прав, управление банковскими приложениями |
| Приватный браузер | BrowserActivity | HTTPS, локальная ручная авторизация | Экспорт cookies в GitHub Actions |
| Python Actions worker | wolf_ai/agent_engine.py, orchestrator.py, missions.py | Контролируемые чтение/тесты/отчёты | Получать Android Keystore, cookies, системный UI |
| CI | .github/workflows | Сборка/проверка и долговременные отчёты | Имитировать успешную реальную публикацию TikTok |

**Почему Java и Python нельзя бездумно объединить:** Java нужен Android SDK,
Keystore, WebView, UI и нативное GGUF; Python запускается в отдельной
GitHub Actions Linux-среде с другой моделью доверия и ресурсами.
`LocalToolAgent.java` обслуживает ограниченный **read-only** путь на
телефоне; `agent_engine.py` исполняет ограниченные проектные
проверки **в Actions**. Это разные границы выполнения, не две
взаимозаменяемые реализации одного метода. Контракт между ними —
`GithubApi.dispatch()` → GitHub Actions `workflow_dispatch` с
`project`, `mode`, `client_task_id` и необязательным `goal`.
Коды проекта и названия workflow должны проверяться регрессионными тестами
при каждом изменении, без динамической передачи shell-команд.

## Легаси

Устаревшие `legacy/LocalAi.java.txt`, `legacy/termux/wolf-local.sh`
и `docs/archive/ANDROID_README_V13.md` удалены из актуального дерева;
их исходники не уничтожены и остаются в истории Git.
Удалено HTTP-исключение для `127.0.0.1` /`localhost`, которое
использовалось старым локальным Termux-сервером. Теперь Android
`network_security_config.xml` запрещает HTTP без исключений.
Миграция старой Termux-версии автоматически не выполняется.

## Почему 7 GitHub workflows пока остаются отдельными

| Workflow | Привилегии | Назначение |
| --- | --- | --- |
| wolf-ai-android.yml | contents: read | Gradle/NDK, встроенная Qwen, тесты, APK |
| wolf-ai-audit.yml | contents: read | Плановые read-only проверки |
| wolf-ai-agent.yml | contents: read | Контролируемое Plan/Act/Verify |
| wolf-ai-cloud-browser.yml | contents: read | Публичный безлогинный Chromium |
| wolf-ai-train.yml | contents: read | CPU обучение классификатора намерений, не дообучение GGUF |
| wolf-ai-missions.yml | issues: write, actions: write | Подтверждённые Issues и запуск ограниченных задач |
| wolf-ai-repair.yml | contents: write, pull-requests: write | Только безопасные предложения PR, без автослияния |

Их безусловное объединение дало бы одной работе лишние
`issues:write` и `contents:write` права, а `matrix` мог бы
удвоить тяжёлые 428-МБ сборки APK. Поэтому **сохраняем
раздельные привилегии**, но объединяем повторяющийся Python
набор регрессий в один проход внутри Android CI. Критические Java
JVM-тесты сохраняются отдельными с указанием их области.

## Безопасность Android / Google Play AccessibilityService

WOLF — **универсальный ассистент**, а не специально разработанный
скринридер для людей с инвалидностью. Нельзя указывать
`isAccessibilityTool=true` только для обхода требований Play.

Новый `AccessibilityConsent` отображает **отдельное** раскрытие
перед обращением к системному разрешению: служба *потенциально*
может получать доступ к содержимому интерфейсов других приложений.
Пользователь вручную подтверждает это, затем отдельно вручную
разрешает службу в Android и подтверждает каждый маршрут.
Согласие можно отозвать через меню WOLF, что также отменяет
незавершённую команду. Проверка согласия выполняется повторно
непосредственно в `WolfAccessibilityService`, а не только в UI.

Ограничения: один запрос ≤45 секунд, одно приложение,
до четырёх заранее указанных безопасных шагов, запрещены
системные/финансовые пакеты и кнопки отправки, удаления,
покупки, публикации. Проверяются также **кликабельные
родительские контейнеры**, а не только текст дочерней кнопки.
Не записываются screenshots, пароли, полный текст окон или
облачные журналы. Нельзя гарантировать, что Android вообще
даст службе разрешение.

**Перед публикацией в Google Play вручную необходимо:**
оформить декларацию AccessibilityService в Play Console,
показать реальный сценарий в demo-video, указать доступ и
использование данных в описании приложения, проверить соответствие
декларации реальной сборке и получить одобрение Google.
Прохождение CI **не является** официальной сертификацией.
Запрещено использовать Accessibility как автономного агента,
принимающего решения без отдельного согласия.

Официальный источник:
https://support.google.com/googleplay/android-developer/answer/10964491

## Тесты и приёмка

* JVM: маршрутизатор, политика Accessibility, криптография,
  проверка GGUF и голосового UI (локальные алгоритмы).
* Python unittest: полный `test_wolf*.py` набор (включая
  исторические регрессии) одним запуском вместо множества
  повторных процессов в Android CI.
* `tests/test_wolf_architecture_security.py`:
  границы UI/Python, защита секретов, сетевой конфиг, законное
  согласие, запрет legacy, изоляция прав CI.
* Android Gradle: реальная сборка debug APK (включает 428-МБ
  SHA256-проверенный локальный GGUF).

**Неподтверждённое:** ручная установка новой APK на Samsung,
фактический inference Qwen3.5, получение Play Console approval.
Без физического устройства и аккаунта Play это не проверено.
