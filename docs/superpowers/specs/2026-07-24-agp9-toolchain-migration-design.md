# AGP 9 / Gradle 9 Toolchain Migration — Design

**Date:** 2026-07-24
**Issue:** [#16](https://github.com/appswithlove/updraft-sdk-android/issues/16)
**Branch:** `feature/toolchain-agp9` (worktree `../updraft-sdk-android-agp9`; `main` stays free for 2.0.0 checks)
**Strategy:** single branch, staged commits, one PR.

## Goal

Migrate the toolchain from AGP 8.13 / Gradle 8.13 to the AGP 9 generation: built-in Kotlin, the new KMP Android target plugin, and current plugin versions — with published artifacts provably identical to the pre-migration baseline.

## Decisions

| Decision | Choice | Rationale |
| --- | --- | --- |
| PR strategy | Single branch, staged commits | Toolchain pieces interdependent; verification only meaningful on full stack; commits keep it bisectable |
| Version policy | Latest stable everything, except Kotlin | Issue wording; research confirmed compatibility |
| Kotlin | Keep 2.2.21 | Tracks AGP 9 built-in KGP line; min consumer Kotlin stays 2.1.0 — no consumer-facing change. Kotlin 2.4 is a separate later bump |
| Coveralls | Try 2.12.2, drop if Gradle 9 breaks it | No Gradle 9 compat evidence; issue allows dropping |
| Verification | Automated + user checkpoints | Device pass, iOS simulator, `locoPush` dry-run are manual pre-merge gates |

## Target versions

| Item | Current | Target |
| --- | --- | --- |
| Gradle wrapper | 8.13 | 9.6.1 |
| AGP (`gradle` alias → rename `agp`) | 8.13.0 | 9.3.x latest stable |
| Kotlin | 2.2.21 | 2.2.21 (unchanged) |
| Compose Multiplatform | 1.9.3 | 1.11.1 |
| vanniktech maven-publish | 0.34.0 | 0.36.0+ |
| Dokka | 2.1.0 | 2.2.0 |
| loco | 0.4.1 | 1.2.0 |
| updraft | 2.3.0 | 2.3.0 (verify AGP 9) |
| coveralls | 2.4.0 | 2.12.2 or drop |
| androidx.startup | 1.1.1 | 1.2.0 |

## Stages

Each stage is one or more commits; build must be green before moving on.

### Stage 0 — Baseline capture (on unmodified branch point)

- `./gradlew publishToMavenLocal`; snapshot artifact tree (file list, POMs, `.module` metadata, aar/jar contents listing) outside the repo for the Stage 6 diff.
- `./gradlew build --warning-mode all` on Gradle 8.13; record every deprecation.

### Stage 1 — Pre-cleanup on 8.13

- Fix all Gradle 9 deprecation warnings from Stage 0.
- Rename version-catalog alias `gradle` → `agp`.
- Audit legacy remnants: `fileTree(libs)`, `base` plugin usage.

### Stage 2 — Gradle 9 + AGP 9 core

- Wrapper → 9.6.1 via `./gradlew wrapper --gradle-version 9.6.1`; re-validate wrapper jar.
- AGP → 9.3.x.
- `gradle.properties`: audit every `android.*` flag against the AGP 9 upgrade guide; remove obsoleted ones. Never add `android.disallowKotlinSourceSets=false`.
- Remove `testOptions { targetSdk }` from `:updraft-sdk` (library targetSdk removed in AGP 9).
- CI/publish workflows stay on JDK 21 (within AGP 9 supported range).

### Stage 3 — Built-in Kotlin in `:updraft-sdk`

- Drop `org.jetbrains.kotlin.android`; rely on AGP 9 built-in Kotlin.
- Verify `org.jetbrains.kotlin.plugin.compose` still applies — this module crashes at runtime without it (#14).

### Stage 4 — KMP modules → `com.android.kotlin.multiplatform.library`

One module per commit, in order:

1. `:updraft-core`
2. `:updraft-ui-compose` — additionally set `androidResources { enable = true }` inside `androidLibrary {}`; compose resources are otherwise silently dropped from the Android artifact (CMP-9547) and fail at runtime with `MissingResourceException`.
3. `sample/composeApp` — app-side migration (`com.android.application` stays; KMP DSL changes).

Mechanics per library module: replace `com.android.library` with `com.android.kotlin.multiplatform.library`; delete `androidTarget()` and the root `android {}` block; move namespace/compileSdk/minSdk/compilerOptions into `kotlin { androidLibrary {} }`.

### Stage 5 — Plugin upgrades

- CMP → 1.11.1.
- vanniktech → 0.36+; drop third `KotlinMultiplatform(...)` constructor param (`androidVariantsToPublish`).
- Dokka → 2.2.0.
- loco → 1.2.0: task rename `updateLoco` → `locoFetch`; rename `local.properties` key `updraft.locoApiKey` → `locoApiKey`; delete explicit `apiKey`/`Properties` wiring from `updraft-ui-compose/build.gradle.kts`; update README Strings/Loco section.
- coveralls → 2.12.2; if Gradle 9 incompatible, remove plugin + CI step.
- androidx.startup → 1.2.0.

### Stage 6 — Verification (automated)

- Full build + unit tests, Android and iOS targets; CI green including iOS job.
- `publishToMavenLocal` diff vs Stage 0 baseline: same variants (`-android` artifacts), sources/javadoc jars, POM dependency chain (`updraft-sdk` → core + ui-compose), compose resources present inside the ui-compose Android artifact.
- Consumer smoke test: fresh minimal app depending on `updraft-sdk` from `mavenLocal()` compiles and runs.
- `locoFetch` round-trip clean.
- Docs: update `docs/kmp-migration-m1-status.md` and README task names. Kotlin compatibility matrix unchanged (Kotlin stays 2.2.21 → min consumer Kotlin 2.1.0).

### User checkpoints (manual, pre-merge gates)

1. Android device pass: hint → shake → annotate → send → dashboard; update dialog.
2. iOS simulator pass: shake → feedback UI.
3. `locoPush` dry-run (requires full-access key).

## Error handling

- Red stage → fix within the stage before proceeding; no deferred breakage.
- A plugin with no AGP 9 support = stop and report; no workaround hacks.
- Artifact diff mismatch vs baseline = merge blocker.

## Testing

Existing unit tests are the regression net. No new tests: toolchain-only change, no production code touched.

## Out of scope

- Kotlin 2.4 bump (separate later change; shifts min consumer Kotlin to 2.3.0).
- Issue #18 housekeeping (repo rename etc.).
- Any production-code changes.
