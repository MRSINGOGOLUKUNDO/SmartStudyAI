# SmartStudy AI (BSE 3340 – Group Project)

Kotlin · Jetpack Compose · Room · WorkManager · PdfBox-Android

## Project Team

| # | Full Name | Student ID | Assigned Role |
|---|---|---|---|
| 1 | Lukundo Singogo | 2200173 | File Access & Text Parsing |
| 2 | Fortune Jere | 2410050 | Notifications & Reminders |
| 3 | Davies Mubambe | 2410052 | UI & Lifecycle Management |
| 4 | Prince Zulu | 2410237 | Local SQLite Storage / Room |
| 5 | Ernest Chiwala | 2410170 | Data Logic & QA Testing |

## Open & run
1. Android Studio (Koala / 2024.1+) → **File ▸ Open** → select this `SmartStudyAI` folder.
2. Let Gradle sync (Android Studio generates the Gradle wrapper jar automatically; JDK 17).
3. Run on a device/emulator with API 26+. Allow notifications when prompted (Android 13+).

## Structure (clean separation of UI and logic)
| Layer | Package | Files | Owner |
|---|---|---|---|
| File access & parsing | `domain` | `TextExtractor`, `KeyPointGenerator` | Lukundo Singogo (Member 1) |
| Notifications | `notifications` | `ReminderScheduler`, `ReminderWorker`, `NotificationHelper` | Fortune Jere (Member 2) |
| UI & lifecycle | `ui` | `AppNav`, `DashboardScreen`, `PickerScreen`, `SummaryScreen`, `StudyViewModel` | Davies Mubambe (Member 3) |
| Room / SQLite | `data` | `Entities`, `Daos`, `AppDatabase`, `StudyRepository` | Prince Zulu (Member 4) |
| Logic & QA | `src/test` | `KeyPointGeneratorTest` | Ernest Chiwala (Member 5) |

## How "key points" work
Fully offline extractive summarisation: sentences are scored by topic keyword/stem matches,
exact phrase match and word importance, and the top 8 are shown in document order.
(To upgrade later, swap `KeyPointGenerator.generate` for a call to an LLM API.)

## Limits
Scanned/image-only PDFs have no text layer; legacy `.doc` is not supported (use `.docx`).
