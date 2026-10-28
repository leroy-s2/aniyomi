# Fix release build

## Objective and scope
Resolve R8's missing optional AndroidX Sidecar classes, avoid duplicate branch/tag builds, and reject missing or empty release APKs. Existing releases are inspected separately; no new release or remote write is authorized here.

## Plan
- [x] T1: Add focused AndroidX Sidecar and Extensions warning rules; attempt release build.
- [x] T2: Run release CI on version tags and manual dispatch; require all five nonempty APKs before uploading or publishing.

Both tasks use delegated direct implementation (build/workflow research and two nontrivial files). TDD is enabled by session AGENTS instructions. Workflow verification uses Python/PyYAML to parse the workflow and execute its validation shell against missing, empty, and populated APK fixtures. Build verification: `./gradlew assembleRelease -Penable-updater`; formatting: `git diff --check`.

Forecast: fewer than 100 authored changed lines. Delivery: ask-on-risk; one feature branch, no push/PR. RDD: on globally; parent owns native review. Rollback boundaries: T1 `app/proguard-rules.pro`; T2 `.github/workflows/build_push.yml`.

## Evidence and progress
- RED: workflow assertion failed because branch pushes were enabled; optional API rule assertions failed before their respective additions.
- GREEN: Python/PyYAML verified tag-only/manual triggers and strict upload/release inputs. Executed the actual workflow validation shell against missing and zero-byte fixtures (exit 1), then five populated fixtures (exit 0).
- ABI names match `app/build.gradle.kts`: universal plus arm64-v8a, armeabi-v7a, x86, x86_64.
- `git diff --check`: passed.
- `./gradlew assembleRelease -Penable-updater`: blocked before compilation by missing Android SDK (`SDK location not found`). No local APK or R8 success is claimed. Java 25 is installed locally; Actions explicitly uses Java 17.
- No refactor needed. PR builds remain enabled; ordinary branch pushes no longer run release CI. Manual dispatch on a version tag can retry an existing release; dispatch on a branch only builds artifacts.
- RDD assessment/review and Engram mirror remain parent-owned pending actions. Parent records commit identities below.

## Next step
Commit local work units; parent runs native review and verifies the existing v0.18.2.5 assets. Remote delivery and a successful Actions build remain pending.
