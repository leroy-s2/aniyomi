# Fix release build

## Objective and scope
Resolve R8's missing optional AndroidX Sidecar classes, avoid duplicate branch/tag builds, and reject missing or empty release APKs. The user subsequently authorized pushing to `main` and dispatching Actions using the existing GitHub CLI session. Preserve the existing fifth release; do not create a duplicate.

## Plan
- [x] T1: Add focused AndroidX Sidecar and Extensions warning rules; attempt release build.
- [x] T2: Run release CI on version tags and manual dispatch; require all five nonempty APKs before uploading or publishing.

Both tasks use delegated direct implementation (build/workflow research and two nontrivial files). TDD is enabled by session AGENTS instructions. Workflow verification uses Python/PyYAML to parse the workflow and execute its validation shell against missing, empty, and populated APK fixtures. Build verification: `./gradlew assembleRelease -Penable-updater`; formatting: `git diff --check`.

Forecast: fewer than 100 authored changed lines. Delivery: ask-on-risk; local feature branch followed by the authorized push to `main`, without a PR. RDD: on globally; parent owns native review. Rollback boundaries: T1 `app/proguard-rules.pro`; T2 `.github/workflows/build_push.yml`.

## Evidence and progress
- RED: workflow assertion failed because branch pushes were enabled; optional API rule assertions failed before their respective additions.
- GREEN: Python/PyYAML verified tag-only/manual triggers and strict upload/release inputs. Executed the actual workflow validation shell against missing and zero-byte fixtures (exit 1), then five populated fixtures (exit 0).
- ABI names match `app/build.gradle.kts`: universal plus arm64-v8a, armeabi-v7a, x86, x86_64.
- `git diff --check`: passed.
- `./gradlew assembleRelease -Penable-updater`: blocked before compilation by missing Android SDK (`SDK location not found`). No local APK or R8 success is claimed. Java 25 is installed locally; Actions explicitly uses Java 17.
- Independent verifier passed the workflow shell fixtures, YAML configuration, and Gradle ABI/output-name checks.
- Parent pushed `43a35cd6c` to `origin/main` and manually dispatched [Actions run 40713504669](https://github.com/leroy-s2/aniyomi/actions/runs/40713504669). Parent verified the completed run concluded **SUCCESS**. Build app, unit tests, validation of all five APKs, APK upload, and mapping upload passed. The APK artifact ZIP is 42,744,242 bytes. This confirms remote release compilation despite the missing local SDK. No duplicate release was created; downloaded-artifact integrity is pending parent confirmation.
- No refactor needed. PR builds remain enabled; ordinary branch pushes no longer run release CI. Manual dispatch on a version tag can retry an existing release; dispatch on a branch only builds artifacts.
- RDD assessment and preflight refused the runtime with `immutable_review_transport_unsupported` before starting a transaction. Review is unavailable; no approval is claimed. Engram mirror is parent-owned.
- Work units: T1 `b20c166c3` (`fix(build): handle optional WindowManager device APIs`); T2 `ae93d06ad` (`fix(ci): build tags once and require release APKs`). Any subsequent bookkeeping commit does not change these implementation boundaries.

## Next step
Existing v0.18.2.5 has five uploaded nonzero APKs; its downloaded ARM64 APK passed ZIP integrity and matched GitHub's SHA-256. Parent checks the newly generated APK's integrity. Do not create a duplicate release. Native review remains unavailable and must not be retried or represented as approved.
