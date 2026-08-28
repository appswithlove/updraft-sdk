# AGP 9 / Gradle 9 Toolchain Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the toolchain from Gradle 8.13 / AGP 8.13 to Gradle 9.6.1 / AGP 9.3.x with built-in Kotlin, the new KMP Android target plugin, and current plugin versions — with published artifacts provably equivalent to the pre-migration baseline.

**Architecture:** Staged lockstep on branch `feature/toolchain-agp9` (worktree `/Users/miggi/awlsrc/projects/updraft-sdk-android-agp9`). Each task = one logical commit, build green before the next (exceptions called out explicitly in Task 3). Baseline artifact snapshot before any change; byte-structure diff at the end is a merge blocker.

**Tech Stack:** Gradle 9.6.1, AGP 9.3.x, Kotlin 2.2.21 (unchanged), Compose Multiplatform 1.11.1, vanniktech maven-publish 0.36.0, Dokka 2.2.0, loco 1.2.0.

**Spec:** `docs/superpowers/specs/2026-07-24-agp9-toolchain-migration-design.md`

## Global Constraints

- All work in worktree `/Users/miggi/awlsrc/projects/updraft-sdk-android-agp9`, branch `feature/toolchain-agp9`. Never touch the main worktree.
- Kotlin stays `2.2.21`. Min consumer Kotlin stays `2.1.0`. Do NOT bump Kotlin.
- Never add `android.disallowKotlinSourceSets=false` to any `gradle.properties`.
- No production source code changes (`src/**`) — build/config/docs only. If a source change seems required, stop and report.
- No `git rebase`. No `Co-Authored-By`/AI hints in commit messages.
- A plugin with no AGP 9 support = stop and report; no workaround hacks.
- Artifact diff mismatch vs baseline (Task 11) = merge blocker.
- Baseline dir: `/Users/miggi/awlsrc/projects/agp9-baseline` (outside any repo).
- Version catalog: `gradle/libs.versions.toml`. After Task 2 the AGP version key/alias is `agp`.
- Reference docs (consult when a step says so):
  - AGP 9 upgrade guide + release notes: https://developer.android.com/build/agp-upgrade-guide , https://developer.android.com/build/releases/agp-9-0-0-release-notes
  - Built-in Kotlin: https://developer.android.com/kotlin/builtin-kotlin
  - KMP → AGP 9 migration (new android library target): https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
  - Compose resources under new target: https://youtrack.jetbrains.com/issue/CMP-9547

---

### Task 1: Baseline capture

No repo changes; produces the comparison baseline every later verification depends on.

**Files:**
- Create (outside repo): `/Users/miggi/awlsrc/projects/agp9-baseline/*`

**Interfaces:**
- Produces: `$BASE/files.txt`, `$BASE/poms/`, `$BASE/modules/`, `$BASE/aar-contents/`, `$BASE/warnings.txt` — consumed by Task 11 diff.

- [ ] **Step 1: Publish baseline artifacts to a clean mavenLocal group**

```bash
cd /Users/miggi/awlsrc/projects/updraft-sdk-android-agp9
rm -rf ~/.m2/repository/com/appswithlove/updraft
./gradlew publishToMavenLocal
```

Expected: BUILD SUCCESSFUL; `~/.m2/repository/com/appswithlove/updraft/{updraft-sdk,updraft-core,updraft-ui-compose,...}/2.0.0/` populated (KMP modules also publish `-android`, `-iosarm64`, `-iossimulatorarm64`, `-iosx64` artifacts).

- [ ] **Step 2: Snapshot the artifact tree**

```bash
BASE=/Users/miggi/awlsrc/projects/agp9-baseline
mkdir -p "$BASE/poms" "$BASE/modules" "$BASE/aar-contents"
REPO=~/.m2/repository/com/appswithlove/updraft
( cd "$REPO" && find . -type f ! -name "*.sha1" ! -name "*.md5" ! -name "maven-metadata*" | sort ) > "$BASE/files.txt"
find "$REPO" -name "*.pom" -exec sh -c 'cp "$1" "$0/poms/$(basename $(dirname $(dirname $1)))-$(basename $1)"' "$BASE" {} \;
find "$REPO" -name "*.module" -exec sh -c 'cp "$1" "$0/modules/$(basename $(dirname $(dirname $1)))-$(basename $1)"' "$BASE" {} \;
for f in $(find "$REPO" \( -name "*.aar" -o -name "*.jar" \)); do
  unzip -l "$f" | awk '{print $4}' | grep -v '^$' | sort > "$BASE/aar-contents/$(basename "$f").txt"
done
ls "$BASE"/poms "$BASE"/modules | head -30
```

Expected: `files.txt` non-empty; one pom+module per published artifact; content listings for every aar/jar (check `updraft-ui-compose-*-android*.aar` listing contains compose-resources entries, e.g. paths with `composeResources` or `values/strings` — note the exact resource paths, Task 11 re-checks them).

- [ ] **Step 3: Record Gradle 9 deprecation warnings on current toolchain**

```bash
cd /Users/miggi/awlsrc/projects/updraft-sdk-android-agp9
./gradlew build --warning-mode all 2>&1 | tee /Users/miggi/awlsrc/projects/agp9-baseline/warnings.txt
grep -n "deprecat" /Users/miggi/awlsrc/projects/agp9-baseline/warnings.txt | head -50
```

Expected: BUILD SUCCESSFUL. Note every deprecation line — Task 2 fixes each one.

---

### Task 2: Pre-cleanup on Gradle 8.13

**Files:**
- Modify: `gradle/libs.versions.toml` (rename `gradle` key → `agp`)
- Modify: `updraft-sdk/build.gradle.kts` (drop `id("base")`)
- Modify: `gradle.properties`, `updraft-sdk/gradle.properties` (drop jetifier)
- Modify: whatever `warnings.txt` from Task 1 flags

**Interfaces:**
- Produces: catalog version key `agp` + unchanged plugin aliases `android-application`, `android-library` now referencing `version.ref = "agp"`. Tasks 3+ rely on this name.

- [ ] **Step 1: Rename catalog key `gradle` → `agp`**

In `gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.13.0"
```

and update the two references:

```toml
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
```

- [ ] **Step 2: Remove `id("base")` from `updraft-sdk/build.gradle.kts` plugins block**

The `base` plugin is a legacy remnant; AGP applies its own lifecycle tasks.

- [ ] **Step 3: Remove jetifier**

Delete `android.enableJetifier=true` from BOTH `gradle.properties` (root) and `updraft-sdk/gradle.properties`. All deps are AndroidX; jetifier is removed in AGP 9 anyway.

- [ ] **Step 4: Fix every deprecation from `$BASE/warnings.txt`**

Address each `deprecat*` line recorded in Task 1 Step 3. These are unknown until run; typical suspects in this repo: `Properties`-based `local.properties` reads at configuration time, `fileTree` usages, task eager-configuration. For each: apply the replacement the warning message names. If a warning originates inside a third-party plugin (not our build scripts), record it in the commit message and leave it — plugin upgrades in Tasks 8–10 handle those.

- [ ] **Step 5: Verify clean build, no warnings from our scripts**

```bash
./gradlew build --warning-mode all 2>&1 | grep -i "deprecat" || echo "CLEAN"
```

Expected: `CLEAN`, or only lines attributable to third-party plugins (verify by stack trace/plugin name in the full output).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Pre-cleanup for Gradle 9: rename gradle->agp alias, drop base plugin, jetifier, fix deprecations"
```

---

### Task 3: Gradle 9.6.1 + AGP 9.3.x core bump

AGP 8.13 does not run on Gradle 9, so wrapper + AGP move in ONE commit.

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties` (via `./gradlew wrapper`)
- Modify: `gradle/libs.versions.toml` (`agp` version)
- Modify: `gradle.properties` (flag audit)
- Modify: `updraft-sdk/build.gradle.kts` (drop `testOptions { targetSdk }`)

**Interfaces:**
- Produces: toolchain Gradle 9.6.1 + AGP 9.3.x that Tasks 4–7 build on.
- Known-broken window: the two KMP modules (`updraft-core`, `updraft-ui-compose`) may fail to configure under AGP 9 while still on `com.android.library` + `androidTarget()`. That is EXPECTED and resolved by Tasks 5–6. Verification in this task is therefore targeted, not `./gradlew build`.

- [ ] **Step 1: Pin exact AGP version**

Check latest stable 9.3.x at https://developer.android.com/build/releases/about-agp (or `curl -s https://dl.google.com/dl/android/maven2/com/android/application/com.android.application.gradle.plugin/maven-metadata.xml | grep -o '<release>[^<]*'`). Use that exact version below (written as `9.3.x` — substitute everywhere).

- [ ] **Step 2: Bump wrapper**

```bash
./gradlew wrapper --gradle-version 9.6.1 && ./gradlew wrapper --gradle-version 9.6.1
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```

Expected: `distributionUrl=...gradle-9.6.1-bin.zip` (run twice so the new wrapper regenerates its own scripts/jar).

- [ ] **Step 3: Bump AGP in catalog**

```toml
[versions]
agp = "9.3.x"
```

- [ ] **Step 4: Audit `android.*` flags in root `gradle.properties`**

Per the AGP 9 upgrade guide: `android.useAndroidX` is default/required in AGP 9 — remove the line. Keep `org.gradle.caching`, `org.gradle.configuration-cache`, jvmargs, `kotlin.code.style` as-is. Do NOT add `android.disallowKotlinSourceSets=false`. Also check `updraft-sdk/gradle.properties` for the same `android.useAndroidX` line and remove it.

- [ ] **Step 5: Remove library `targetSdk` from `updraft-sdk/build.gradle.kts`**

Delete the block (library targetSdk removed in AGP 9):

```kotlin
    testOptions {
        targetSdk = 36
    }
```

- [ ] **Step 6: Targeted verification**

```bash
./gradlew :updraft-sdk:assembleRelease :updraft-sdk:testReleaseUnitTest 2>&1 | tail -20
./gradlew :sample:composeApp:assembleDebug 2>&1 | tail -5 || echo "EXPECTED-IF-KMP-BLOCKED"
./gradlew help 2>&1 | tail -5 || echo "CONFIG-FAILURE - inspect"
```

Expected: `:updraft-sdk` tasks succeed (it may pull KMP module deps — if configuration of the KMP modules hard-fails under AGP 9 with an error pointing at `com.android.library`+KMP, note the exact error and proceed: Tasks 5–6 fix it; in that case even `./gradlew help` may fail, which is acceptable ONLY with that specific error). If failure is anything else (e.g. coveralls plugin incompatible with Gradle 9), handle: for coveralls specifically, temporarily comment the `alias(libs.plugins.coveralls)` line in root `build.gradle.kts` with `// TEMP disabled, resolved in Task 10` and note it; any other failure → stop and report.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Bump to Gradle 9.6.1 + AGP 9.3.x, audit android.* flags, drop library targetSdk"
```

(If the KMP modules are hard-broken at this point, say so in the commit body: "KMP modules migrate to the new target plugin in follow-up commits.")

---

### Task 4: Built-in Kotlin in `:updraft-sdk`

**Files:**
- Modify: `updraft-sdk/build.gradle.kts`
- Modify: `gradle/libs.versions.toml` + root `build.gradle.kts` (drop `kotlin-android` alias if nothing else uses it)

**Interfaces:**
- Consumes: AGP 9 toolchain from Task 3.
- Produces: `:updraft-sdk` compiled by AGP's built-in Kotlin; `org.jetbrains.kotlin.android` gone from the build.

- [ ] **Step 1: Read the built-in Kotlin guide**

Read https://developer.android.com/kotlin/builtin-kotlin — specifically: how compiler options are declared, and how Kotlin compiler plugins (`org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`) are applied on a built-in-Kotlin module. The DSL below is the expected shape; if the guide differs, follow the guide.

- [ ] **Step 2: Migrate `updraft-sdk/build.gradle.kts`**

Remove `alias(libs.plugins.kotlin.android)` from the plugins block. Replace the `tasks.withType<KotlinJvmCompile>` block inside `android {}` with the built-in Kotlin DSL:

```kotlin
android {
    // ... existing namespace/compileSdk/defaultConfig/buildTypes/buildFeatures/compileOptions ...
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
        }
    }
}
```

Drop the now-unused imports `org.gradle.kotlin.dsl.withType` and `org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile` (keep `JvmTarget` if the DSL still uses it). Keep `alias(libs.plugins.compose.compiler)` and `alias(libs.plugins.kotlin.serialization)` — verify per Step 1 how they attach under built-in Kotlin; #14 showed this module CRASHES AT RUNTIME without the compose compiler plugin, so if the compose plugin cannot apply, stop and report.

- [ ] **Step 3: Clean up unused `kotlin-android` alias**

```bash
grep -rn "kotlin.android\|kotlin-android" --include="*.kts" gradle/libs.versions.toml build.gradle.kts updraft-* sample/
```

If `:updraft-sdk` was the only user: delete `alias(libs.plugins.kotlin.android) apply false` from root `build.gradle.kts` and the `kotlin-android` entry from the catalog.

- [ ] **Step 4: Verify**

```bash
./gradlew :updraft-sdk:assembleRelease :updraft-sdk:testReleaseUnitTest
```

Expected: BUILD SUCCESSFUL. Confirm compose compiler ran: `find updraft-sdk/build -name "*.class" | head -1` exists and build output shows no "Compose compiler" warnings/errors.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Migrate :updraft-sdk to AGP 9 built-in Kotlin"
```

---

### Task 5: `:updraft-core` → `com.android.kotlin.multiplatform.library`

**Files:**
- Modify: `gradle/libs.versions.toml` (new plugin alias)
- Modify: root `build.gradle.kts` (register alias `apply false`)
- Modify: `updraft-core/build.gradle.kts`

**Interfaces:**
- Consumes: AGP 9 toolchain.
- Produces: catalog alias `android-kotlin-multiplatform-library` (used again by Task 6); `:updraft-core` on the new target plugin with identical published coordinates (`com.appswithlove.updraft:updraft-core` + `-android`/`-ios*` variants).

- [ ] **Step 1: Read the KMP AGP 9 migration guide**

Read https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html . The steps below encode its expected shape; guide wins on conflicts.

- [ ] **Step 2: Add plugin alias**

`gradle/libs.versions.toml`:

```toml
android-kotlin-multiplatform-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
```

Root `build.gradle.kts` plugins block:

```kotlin
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
```

- [ ] **Step 3: Migrate `updraft-core/build.gradle.kts`**

Plugins block: replace `alias(libs.plugins.android.library)` with `alias(libs.plugins.android.kotlin.multiplatform.library)`.

Inside `kotlin {}`: replace

```kotlin
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
```

with

```kotlin
    androidLibrary {
        namespace = "com.appswithlove.updraft.core"
        compileSdk = 36
        minSdk = 23
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
```

Delete the entire root `android { ... }` block (namespace/compileSdk/minSdk/compileOptions all moved or obsolete; Java `compileOptions` has no equivalent needed — jvmTarget covers bytecode level).

- [ ] **Step 4: Verify build + tests + publication**

```bash
./gradlew :updraft-core:build :updraft-core:testDebugUnitTest 2>&1 | tail -5
./gradlew :updraft-core:publishToMavenLocal 2>&1 | tail -5
ls ~/.m2/repository/com/appswithlove/updraft/updraft-core-android/2.0.0/
```

Expected: BUILD SUCCESSFUL; `updraft-core-android` dir contains `.aar`, `-sources.jar`, `.pom`, `.module` as in `$BASE/files.txt`. (Unit test task name may differ under the new plugin — list with `./gradlew :updraft-core:tasks --all | grep -i test` and run the android unit test task it offers.) If vanniktech 0.34.0 cannot publish the new plugin's variants (error mentioning unsupported plugin), bump `maven-publish = "0.36.0"` in the catalog NOW instead of waiting for Task 8, and note it in the commit.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Migrate :updraft-core to com.android.kotlin.multiplatform.library"
```

---

### Task 6: `:updraft-ui-compose` → new target plugin (+ compose resources)

**Files:**
- Modify: `updraft-ui-compose/build.gradle.kts`

**Interfaces:**
- Consumes: catalog alias from Task 5.
- Produces: `:updraft-ui-compose` on new plugin WITH compose resources packaged in the Android artifact (CMP-9547 guard).

- [ ] **Step 1: Migrate plugins + DSL**

Same mechanics as Task 5: swap `alias(libs.plugins.android.library)` → `alias(libs.plugins.android.kotlin.multiplatform.library)`; replace `androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }` with:

```kotlin
    androidLibrary {
        namespace = "com.appswithlove.updraft.ui"
        compileSdk = 36
        minSdk = 23
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        androidResources {
            enable = true
        }
    }
```

Delete the root `android { ... }` block. Keep `Loco {}`, `compose.resources {}` blocks untouched (loco changes come in Task 9).

`androidResources { enable = true }` is REQUIRED: without it the new plugin drops compose resources from the Android artifact and consumers crash with `MissingResourceException` (CMP-9547).

- [ ] **Step 2: Verify build + resources actually packaged**

```bash
./gradlew :updraft-ui-compose:build 2>&1 | tail -5
./gradlew :updraft-ui-compose:publishToMavenLocal 2>&1 | tail -5
unzip -l ~/.m2/repository/com/appswithlove/updraft/updraft-ui-compose-android/2.0.0/*.aar | grep -i -E "composeResources|strings|values" | head
diff <(unzip -l ~/.m2/repository/com/appswithlove/updraft/updraft-ui-compose-android/2.0.0/updraft-ui-compose-android-2.0.0.aar | awk '{print $4}' | grep -v '^$' | sort) /Users/miggi/awlsrc/projects/agp9-baseline/aar-contents/updraft-ui-compose-android-2.0.0.aar.txt
```

Expected: BUILD SUCCESSFUL; resource entries present matching the baseline listing (diff empty or only inconsequential ordering/metadata lines — any MISSING resource path = failure, fix before commit).

- [ ] **Step 3: Verify iOS target unaffected**

```bash
./gradlew :updraft-ui-compose:compileKotlinIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Migrate :updraft-ui-compose to com.android.kotlin.multiplatform.library with androidResources enabled"
```

---

### Task 7: `sample/composeApp` app-side migration + full build green

**Files:**
- Modify: `sample/composeApp/build.gradle.kts` (only if the guide/build errors require it)
- Modify: root `build.gradle.kts` (re-enable coveralls if Task 3 disabled it — else leave for Task 10)

**Interfaces:**
- Consumes: migrated library modules.
- Produces: FULL `./gradlew build` green — the gate for all remaining tasks.

- [ ] **Step 1: Check what the app side needs**

Per the KMP AGP 9 migration guide (Task 5 Step 1): `com.android.kotlin.multiplatform.library` is library-only; app modules stay on `com.android.application` + `kotlin.multiplatform` with `androidTarget()`. Attempt the build first:

```bash
./gradlew :sample:composeApp:assembleDebug 2>&1 | tail -20
```

If green: no app-side change; skip to Step 3. If AGP 9 rejects the current app DSL, apply exactly what the error/guide instructs (typical: `androidTarget()` DSL adjustments, removed `android {}` options). Keep `targetSdk = 36` (valid for apps), signing config, `generateSampleKeys` task, and the `updraft {}` block untouched. If the `com.appswithlove.updraft` plugin 2.3.0 itself fails under AGP 9 → stop and report (own-org plugin needs a release first).

- [ ] **Step 2: Fix and re-run until green**

```bash
./gradlew :sample:composeApp:assembleDebug :sample:composeApp:assembleRelease
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Full multiplatform build + tests**

```bash
./gradlew build 2>&1 | tail -10
./gradlew :updraft-core:iosSimulatorArm64Test :updraft-ui-compose:iosSimulatorArm64Test 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL for both. This is the first required-fully-green point since Task 3.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Adapt sample app for AGP 9; full build green on new toolchain"
```

---

### Task 8: Plugin/dependency upgrades (CMP, vanniktech, dokka, startup)

**Files:**
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: green build from Task 7.
- Produces: current plugin set; publishing still works.

- [ ] **Step 1: Bump versions in catalog**

```toml
composeMultiplatform = "1.11.1"
maven-publish = "0.36.0"
dokka = "2.2.0"
androidxStartup = "1.2.0"
```

(If Task 5 already bumped `maven-publish`, only the rest change. Before writing, confirm each is still the latest stable: CMP via https://github.com/JetBrains/compose-multiplatform/releases , vanniktech via https://github.com/vanniktech/gradle-maven-publish-plugin/releases , dokka via https://github.com/Kotlin/dokka/releases , startup via https://developer.android.com/jetpack/androidx/releases/startup — use newer stable if available.)

- [ ] **Step 2: Verify build + publish**

```bash
./gradlew build 2>&1 | tail -5
./gradlew publishToMavenLocal 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL both. Any CMP 1.11.x API breakage in build scripts → fix per its release notes; production `src/**` breakage → stop and report (violates no-source-change constraint).

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "Upgrade CMP 1.11.1, maven-publish 0.36.0, dokka 2.2.0, androidx.startup 1.2.0"
```

---

### Task 9: loco 0.4.1 → 1.2.0

**Files:**
- Modify: `gradle/libs.versions.toml` (`loco = "1.2.0"`)
- Modify: `updraft-ui-compose/build.gradle.kts` (drop apiKey wiring)
- Modify: `README.md:261` (task rename)
- Modify (user machine, uncommitted): `local.properties` key rename

**Interfaces:**
- Consumes: green build.
- Produces: `locoFetch`/`locoPush` tasks; apiKey via `local.properties` key `locoApiKey`.

- [ ] **Step 1: Bump version**

```toml
loco = "1.2.0"
```

- [ ] **Step 2: Simplify `updraft-ui-compose/build.gradle.kts`**

Delete the `import java.util.Properties` line, the whole `val localProperties = Properties().apply { ... }` block, and the `apiKey = ...` line inside `Loco { config { ... } }`. Plugin 1.2.0 reads `locoApiKey` from `local.properties` itself. Keep the rest of the config:

```kotlin
Loco {
    config {
        lang = listOf("en", "de")
        defLang = "en"
        resDir = "$projectDir/src/commonMain/composeResources"
        fallbackLang = "en"
        orderByAssetId = true
        hideComments = true
    }
}
```

If 1.2.0 changed the config DSL shape (build error on `Loco {}`), check https://github.com/appswithlove/loco-android for the 1.x DSL and adapt while keeping these exact values.

- [ ] **Step 3: Rename key in `local.properties`**

In `/Users/miggi/awlsrc/projects/updraft-sdk-android-agp9/local.properties` (git-ignored; worktree has its own copy — copy from main worktree if missing): rename `updraft.locoApiKey=` → `locoApiKey=` keeping the value.

- [ ] **Step 4: Update README**

`README.md` Strings/Loco paragraph (~line 261): replace `./gradlew :updraft-ui-compose:updateLoco` with `./gradlew :updraft-ui-compose:locoFetch`, and `updraft.locoApiKey=<key>` with `locoApiKey=<key>`. Mention `locoPush` pushes local strings to Loco and needs a full-access key.

- [ ] **Step 5: Verify**

```bash
./gradlew :updraft-ui-compose:tasks --all | grep -i loco
./gradlew build 2>&1 | tail -3
```

Expected: `locoFetch` and `locoPush` listed (no `updateLoco`); build green. (Actual `locoFetch` round-trip happens in Task 13 — needs the API key.)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Upgrade loco plugin to 1.2.0: locoFetch/locoPush, apiKey via local.properties"
```

---

### Task 10: Coveralls — try 2.12.2, drop if broken

**Files:**
- Modify: `gradle/libs.versions.toml`, root `build.gradle.kts`

**Interfaces:**
- Produces: either working coveralls 2.12.2 or plugin fully removed.

- [ ] **Step 1: Assess usage**

Coveralls is applied at root with zero configuration, no jacoco setup anywhere, and neither CI workflow invokes a coveralls task — it is effectively dead weight.

- [ ] **Step 2: Try upgrade**

Set `coveralls = "2.12.2"` in the catalog (re-enable the root alias if Task 3 commented it). Run:

```bash
./gradlew help 2>&1 | tail -3
```

- [ ] **Step 3: Decide**

If `help` succeeds → keep at 2.12.2. If it fails on Gradle 9 incompatibility → remove entirely: delete `alias(libs.plugins.coveralls)` from root `build.gradle.kts`, delete the `coveralls` entries from `[versions]` and `[plugins]` in the catalog. Either way:

```bash
./gradlew build 2>&1 | tail -3
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Coveralls: <upgrade to 2.12.2 | drop unused plugin (no Gradle 9 support)>"
```

---

### Task 11: Artifact diff vs baseline (merge blocker)

**Files:**
- Create (outside repo): `/Users/miggi/awlsrc/projects/agp9-after/*`

**Interfaces:**
- Consumes: `$BASE` snapshot from Task 1; fully upgraded toolchain.
- Produces: verified artifact equivalence — REQUIRED for merge.

- [ ] **Step 1: Publish from new toolchain into clean mavenLocal**

```bash
cd /Users/miggi/awlsrc/projects/updraft-sdk-android-agp9
rm -rf ~/.m2/repository/com/appswithlove/updraft
./gradlew publishToMavenLocal
```

- [ ] **Step 2: Snapshot with the SAME script as Task 1 Step 2**, with `BASE=/Users/miggi/awlsrc/projects/agp9-after`.

- [ ] **Step 3: Diff**

```bash
BASE=/Users/miggi/awlsrc/projects/agp9-baseline
AFTER=/Users/miggi/awlsrc/projects/agp9-after
diff "$BASE/files.txt" "$AFTER/files.txt"
for f in "$BASE"/poms/*; do diff -u "$f" "$AFTER/poms/$(basename "$f")" | head -40; done
for f in "$BASE"/modules/*; do
  b=$(basename "$f")
  diff <(python3 -c "import json,sys; d=json.load(open('$f')); print(json.dumps({'variants':[{ 'name':v['name'], 'attributes':v.get('attributes',{}), 'dependencies':sorted([x['module'] for x in v.get('dependencies',[])]) } for v in d['variants']]}, indent=1, sort_keys=True))") \
       <(python3 -c "import json,sys; d=json.load(open('$AFTER/modules/$b')); print(json.dumps({'variants':[{ 'name':v['name'], 'attributes':v.get('attributes',{}), 'dependencies':sorted([x['module'] for x in v.get('dependencies',[])]) } for v in d['variants']]}, indent=1, sort_keys=True))")
done
for f in "$BASE"/aar-contents/*; do diff "$f" "$AFTER/aar-contents/$(basename "$f")"; done
```

- [ ] **Step 4: Judge the diff**

MUST be identical: file set (`files.txt`), POM dependency chains (`updraft-sdk` POM depends on core + ui-compose), `.module` variant names/attributes/dependencies, compose-resource entries in the ui-compose android aar.
ACCEPTABLE diffs (document each in the task log): dependency version bumps we made deliberately (androidx.startup 1.2.0), toolchain metadata (e.g. `org.gradle.jvm.version` attribute changes, Kotlin/AGP version stamps inside `.module` `createdBy`), added/removed non-semantic files like `.module` formatting. ANY other difference — missing variant, missing sources/javadoc jar, changed artifactId, lost resources — is a merge blocker: fix, or stop and report.

- [ ] **Step 5: Record result**

Append a short "Artifact diff: <clean | acceptable diffs listed>" note to the PR description draft (no repo file needed). No commit.

---

### Task 12: Consumer smoke test from mavenLocal

**Files:**
- Create (outside repo): `/Users/miggi/awlsrc/projects/agp9-consumer-test/` — throwaway Android app

**Interfaces:**
- Consumes: artifacts in mavenLocal from Task 11.
- Produces: proof a plain AGP 8.13 / Kotlin 2.1.0 consumer compiles against the new artifacts (validates the metadata floor).

- [ ] **Step 1: Scaffold minimal consumer**

Create `/Users/miggi/awlsrc/projects/agp9-consumer-test/` with:

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { google(); gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { mavenLocal(); google(); mavenCentral() }
}
rootProject.name = "consumer-test"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}
```

`gradle.properties`:

```properties
android.useAndroidX=true
```

`app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.consumer"
    compileSdk = 36
    defaultConfig { minSdk = 23; targetSdk = 36; applicationId = "com.example.consumer" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}
dependencies {
    implementation("com.appswithlove.updraft:updraft-sdk:2.0.0")
}
```

`app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="consumer" />
</manifest>
```

`app/src/main/kotlin/com/example/consumer/Probe.kt` — reference the SDK's public entry point exactly as the README "Getting started" section shows (read `README.md` in the repo for the init snippet; the point is to force Kotlin-metadata resolution of a published symbol):

```kotlin
package com.example.consumer

// Replace with the actual initialization/entry-point call from README "Getting started".
// Example shape — adjust to the real API surface found in the README:
import com.appswithlove.updraft.Updraft

fun probe() {
    val ref = Updraft::class
    println(ref)
}
```

Copy the Gradle 8.13 wrapper from the SDK repo's git history or run `gradle wrapper --gradle-version 8.13` (any local Gradle) inside the consumer dir.

- [ ] **Step 2: Build**

```bash
cd /Users/miggi/awlsrc/projects/agp9-consumer-test && ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. A Kotlin-metadata error ("compiled with a newer Kotlin") = FAIL → means Kotlin floor moved; stop and report (should be impossible with Kotlin unchanged at 2.2.21).

- [ ] **Step 3: No commit** — throwaway project stays outside the repo. Note result for PR description.

---

### Task 13: Docs, locoFetch round-trip, CI

**Files:**
- Modify: `docs/kmp-migration-m1-status.md`
- Modify: `README.md` (verify only — loco part done in Task 9)

**Interfaces:**
- Consumes: everything prior.
- Produces: docs current; CI green on the branch.

- [ ] **Step 1: Update `docs/kmp-migration-m1-status.md`**

The "AGP 9 / Gradle 9 upgrade" bullet (~line 133): mark as implemented on branch `feature/toolchain-agp9` with the final version set (Gradle 9.6.1, AGP <exact>, CMP 1.11.1, loco 1.2.0, vanniktech 0.36.0, dokka 2.2.0), noting device re-verification pending.

- [ ] **Step 2: Verify README Kotlin compatibility matrix**

Kotlin stayed 2.2.21 → min consumer Kotlin 2.1.0 unchanged. Read the README matrix section; confirm no change needed. If the matrix states toolchain versions (AGP/Gradle used to build), update those lines.

- [ ] **Step 3: locoFetch round-trip**

```bash
cd /Users/miggi/awlsrc/projects/updraft-sdk-android-agp9
./gradlew :updraft-ui-compose:locoFetch
git diff --stat updraft-ui-compose/src/commonMain/composeResources
```

Expected: task succeeds; diff empty or only cosmetic reordering (strings are managed in Loco — a large diff means fetch changed content: revert with `git checkout -- updraft-ui-compose/src/commonMain/composeResources` and report). Requires `locoApiKey` in `local.properties` (Task 9 Step 3); if missing, flag as user checkpoint instead.

- [ ] **Step 4: Commit docs**

```bash
git add docs/kmp-migration-m1-status.md README.md
git commit -m "Update docs for AGP 9 toolchain migration"
```

- [ ] **Step 5: Push branch, watch CI**

```bash
git push -u origin feature/toolchain-agp9
gh run watch $(gh run list --branch feature/toolchain-agp9 --limit 1 --json databaseId --jq '.[0].databaseId')
```

Expected: CI workflow (android + ios jobs) green. konan cache key auto-invalidates via `libs.versions.toml` hash. If a job fails: fix in a follow-up commit on the branch, re-push, repeat until green.

---

### Task 14: User checkpoints + PR

**Interfaces:**
- Consumes: green CI, clean artifact diff.
- Produces: PR ready for merge after manual gates.

- [ ] **Step 1: Open draft PR**

```bash
gh pr create --draft --title "Toolchain migration: AGP 9, Gradle 9, built-in Kotlin, plugin upgrades" \
  --body "$(cat <<'EOF'
Closes #16.

- Gradle 9.6.1, AGP <exact version>, built-in Kotlin (:updraft-sdk)
- KMP modules on com.android.kotlin.multiplatform.library (androidResources enabled for compose resources)
- CMP 1.11.1, vanniktech 0.36.0, dokka 2.2.0, loco 1.2.0 (updateLoco -> locoFetch), startup 1.2.0, coveralls <kept|dropped>
- Kotlin unchanged at 2.2.21 -> min consumer Kotlin stays 2.1.0
- Artifact diff vs pre-migration baseline: <result from Task 11>
- Consumer smoke test (AGP 8.13 / Kotlin 2.1.0 from mavenLocal): <result from Task 12>

Manual verification pending (merge gates):
- [ ] Android device pass: hint -> shake -> annotate -> send -> dashboard; update dialog
- [ ] iOS simulator pass: shake -> feedback UI
- [ ] locoPush dry-run (full-access key)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: Hand off to user**

Report to the user: PR URL, artifact-diff summary, and the three manual gates above. Do NOT merge; user marks PR ready after device verification.

---

## Self-review notes

- Spec coverage: Stage 0→Task 1, Stage 1→Task 2, Stage 2→Task 3, Stage 3→Task 4, Stage 4→Tasks 5–7, Stage 5→Tasks 8–10, Stage 6→Tasks 11–13, user checkpoints→Task 14. Complete.
- Known deliberate deviations from "green before next stage": Task 3 allows the KMP modules to be broken until Task 7 (AGP 9 may hard-reject `com.android.library`+KMP); called out explicitly in Task 3/7.
- Steps that depend on live docs (built-in Kotlin DSL, KMP guide, loco 1.x DSL) name the exact URL and give the expected code shape; guide wins on conflict.
