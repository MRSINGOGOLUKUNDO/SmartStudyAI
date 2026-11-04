# SmartStudy AI (BSE 3340 – Group Project)

Kotlin · Jetpack Compose · Room · WorkManager · PdfBox-Android

## Open & run
1. Android Studio (Koala / 2024.1+) → **File ▸ Open** → select this `SmartStudyAI` folder.
2. Let Gradle sync (Android Studio generates the Gradle wrapper jar automatically; JDK 17).
3. Run on a device/emulator with API 26+. Allow notifications when prompted (Android 13+).

## Structure (clean separation of UI and logic)
| Layer | Package | Files | Suggested owner |
|---|---|---|---|
| File access & parsing | `domain` | `TextExtractor`, `KeyPointGenerator` | Member 1 |
| Notifications | `notifications` | `ReminderScheduler`, `ReminderWorker`, `NotificationHelper` | Member 2 |
| UI & lifecycle | `ui` | `AppNav`, `DashboardScreen`, `PickerScreen`, `SummaryScreen`, `StudyViewModel` | Member 3 |
| Room / SQLite | `data` | `Entities`, `Daos`, `AppDatabase`, `StudyRepository` | Member 4 |
| Logic & QA | `src/test` | `KeyPointGeneratorTest` | Member 5 |

## How "key points" work
Fully offline extractive summarisation: sentences are scored by topic keyword/stem matches,
exact phrase match and word importance, and the top 8 are shown in document order.
(To upgrade later, swap `KeyPointGenerator.generate` for a call to an LLM API.)

## Limits
Scanned/image-only PDFs have no text layer; legacy `.doc` is not supported (use `.docx`).
