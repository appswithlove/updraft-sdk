# KMP Migration M1 — Status & Follow-ups

Date: 2026-07-16 · Branch: `feature/kmp-migration-m1` (21 commits, unmerged) · Issue: [#13](https://github.com/appswithlove/updraft-sdk-android/issues/13)
Spec: [2026-07-16-kmp-migration-design.md](superpowers/specs/2026-07-16-kmp-migration-design.md) · Plan: [2026-07-16-kmp-migration-m1.md](superpowers/plans/2026-07-16-kmp-migration-m1.md)

## What was built

| Module | Coords | Content |
|---|---|---|
| `:updraft-core` | `com.appswithlove.updraft:updraft-core:2.0.0` | KMP (androidTarget in M1). Ktor client, coroutines/Flow, `Updraft.start(UpdraftSettings)`, `Updraft.events: SharedFlow<UpdraftEvent>`, interactors, expect/actual seams (KeyValueStore, AppInfo, shake, screenshot, url, foreground observer). Replaces Retrofit + RxJava2. |
| `:updraft-ui-compose` | `com.appswithlove.updraft:updraft-ui-compose:2.0.0` | Compose Multiplatform feedback UI: `FeedbackScreen` (annotate + form), `DrawingCanvas` (FreeDrawView port), dialogs, `UpdraftEventHost`. Strings en/de via compose-resources. |
| `:updraft-sdk` | `com.appswithlove.updraft:updraft-sdk:2.0.0` (coords unchanged) | Thin Android wrapper: 2 host activities + androidx.startup auto-wire. Entire legacy Views/Fragments/FreeDrawView/Retrofit stack deleted (~3500 lines). |

Breaking change: 2.0.0. `Settings` → `UpdraftSettings`, `Updraft.initialize(context, settings)` + `getInstance()?.start()` → `Updraft.start(settings)`. Migration table in README.

Full build + all unit tests green. Final adversarial whole-branch review: READY TO MERGE. Each task individually spec- and quality-reviewed with fix loops (ledger: `.superpowers/sdd/progress.md`, gitignored).

## Release checklist (merge ≠ release — these BLOCK a 2.0.0 release)

- [x] **Fix `.github/workflows/publish.yml`** — DONE in M2: now publishes all three modules from a macOS runner. (Was: published only `:updraft-sdk`, whose POM references the other two via `api(project(...))`, so a one-module publish would ship a broken 2.0.0.)
- [ ] **Manual device verification** (needs real APP_KEY/SDK_KEY — currently empty strings in `app/App.kt`, real keys stripped from repo in 2024):
  1. Launch → feedback hint dialog on first start
  2. Shake → annotate screen; **draw near screen corners**; undo/redo; OK → form; select type; send → progress → closes
  3. Inspect uploaded image on staging dashboard — verify annotation positions align (coordinate mapping was the critical fix; stroke *width* scaling consciously skipped, see follow-ups)
  4. Rotate mid-form — screenshot must survive, no stacked activities
  5. Lower build number installed → update dialog; "Open" opens browser
  6. Toggle feedback-enabled on dashboard → disabled/how-to dialog on next foreground
  7. Shake twice in a row (second after closing feedback UI) — must work both times
- [x] README `Release` section reconciled with the `production`-branch push trigger — DONE in M2.

## Follow-ups (non-blocking, ordered roughly by impact)

1. **Stroke width not rescaled in `renderAnnotated`** — positions map correctly to bitmap space, but a 12px screen stroke stays 12px on the full-res bitmap → annotations look thinner in uploads. Fix: divide `strokeWidthPx` by the fit scale.
2. **Rotation loses form/drawing state** — `remember` not `rememberSaveable` in `FeedbackScreen`; typed text + drawn paths gone, in-flight upload cancelled silently. Screenshot itself survives (fixed).
3. **`UpdraftOverlayActivity.pendingEvent` static race** — two dialog events in quick succession can overwrite before first `onCreate` consumes; one dialog dropped. Proper fix: intent extras instead of static holder.
4. **Double feedback hint on fresh install** — sync `onForeground` hint + async state-transition hint can both fire in one cycle (legacy-inherited); dedupe via `feedbackHintShown` on the async path.
5. **`updateAlertShown` race** — two rapid foregrounds → concurrent update checks both pass the once-guard → double dialog. Window = network RTT.
6. **Late `Updraft.start()` drops events** — if called after first foreground (not in `Application.onCreate`), the auto-wire collector isn't subscribed yet (`replay=0`) and the hint is permanently lost. Document the `Application.onCreate` requirement prominently, or add replay.
7. **`decodePng` NPE on invalid bytes** — `BitmapFactory.decodeByteArray` returns null on garbage; `FeedbackScreen(screenshotPng, ...)` is public API. Add null handling.
8. **Spec's `FeedbackSent` event unimplemented** — consumers can't observe successful sends via `Updraft.events`.
9. **Empty-screenshot upload** — `screenshotPng == null` path uploads `ByteArray(0)` as image part; server acceptance untested.
10. **Unbounded 100ms poll** in `UpdraftAndroid.autoWire()` if `start()` never called. Cosmetic.
11. **Stale Retrofit keep rule** in `updraft-sdk/proguard-rules.pro` (inert, minify off).
12. **Compose UI tests missing** — spec called for Robolectric-based UI tests (form validation, drawing); only state/controller unit tests exist. An annotate→render UI test would have caught the coordinate-mapping bug.
13. **Feedback hint copy** — form labels localized (en/de) in final fix wave; verify German copy with a native reader before release.

## Deferred to M2 (per spec)

- iOS targets + `iosMain` actuals (motionEnded shake, UIWindow screenshot, itms-services install, NSUserDefaults)
- XCFramework export (SPM/CocoaPods), iosApp sample, deprecate [updraft-sdk-ios](https://github.com/appswithlove/updraft-sdk-ios)
- `app/` → `sample/` rename (CMP structure with androidApp + iosApp)

## Post-migration reminder (from spec)

- ~~Revisit options for Views-based Android apps~~ — **decided 2026-07-22: Compose ships for Views apps too.** `updraft-sdk` stays Compose-based regardless of the host app's UI stack; no `updraft-ui-views` artifact will be built. Rationale: one UI codebase across four app types (Android Views, Android Compose, CMP, iOS) is the point of the migration; the ~2 MB cost is acceptable (R8 strips unused parts, and most active apps already ship Compose); host activities isolate Compose from the app's own view hierarchy, so nothing leaks into it. Escape hatch for size-sensitive apps stays: depend on `updraft-core` only and bring your own feedback UI (documented in the README).

## Dev environment note

Git commits are SSH-signed via 1Password; the signing agent intermittently fails (`1Password: failed to fill whole buffer`). Unlock/approve 1Password and retry — no config change needed.

## M2 status (2026-07-21)

Branch: `feature/kmp-migration-m2` (unmerged).

### What M2 delivered

- **iOS targets + actuals** on `:updraft-core` (`iosArm64`, `iosSimulatorArm64`, `iosX64`): `IosShakeDetector` (`CMMotionManager` accelerometer, no permission entry needed), `IosScreenshotGrabber` (key-window render to PNG), `IosForegroundObserver` (`UIApplicationDidBecomeActive`/`DidEnterBackground`), `KeyValueStore`/`AppInfo` actuals.
- **`:updraft-ui-compose` iOS target**: same Compose feedback UI (`FeedbackScreen`, dialogs) compiled for iOS via Compose Multiplatform; `com.appswithlove.updraft.ui.ios.UpdraftIos.autoWire()` auto-presents dialogs/feedback screen on the topmost view controller, and `UpdraftFeedbackViewController(screenshotPng, onClose)` is exposed for manual hosting.
- **Sample**: `app/` renamed to `sample/` — `sample/composeApp` (shared Kotlin, `MainViewController()` + `startUpdraft()`) and `sample/iosApp` (xcodegen `project.yml`, `iOSApp.swift` calling `MainViewControllerKt.startUpdraft()`, iOS 14.0 deployment target).
- **XCFramework**: `:updraft-core:assembleUpdraftCoreXCFramework` builds `UpdraftCore.xcframework` (logic-only; does not include `updraft-ui-compose`).
- **CI**: `.github/workflows/build.yml` adds an `ios` job (macos-15) running both modules' `iosSimulatorArm64Test`, the XCFramework assemble task, and an `xcodebuild` of `sample/iosApp`. Triggers on `main` and `feature/**` branches. **Verified green on CI** (both android + ios jobs) on branch HEAD.
- README: real iOS setup replacing the "M2, not yet available" placeholder — Installation, iOS quickstart, and extended Swift integration sections; `updraft-sdk-ios` superseded note added.

### Verification findings (fixed on-branch)

- **Runner bumped macos-14 → macos-15**: the xcodegen-produced `project.pbxproj` uses `objectVersion 77` (Xcode 16 format); macos-14 ships Xcode 15 and fails with `Unable to read project 'iosApp.xcodeproj'` (exit 74). Fixed in both `build.yml` and `publish.yml`.
- **`CADisableMinimumFrameDurationOnPhone` added to `sample/iosApp/iosApp/Info.plist`**: without it, Compose Multiplatform's `PlistSanityCheck` throws `IllegalStateException` and the sample **crashes on launch**. Any CMP-hosting iOS app (including consumer apps) needs this key — documented as a requirement.
- **End-to-end confirmed**: sample launched on an iPhone 16 simulator; `startUpdraft()` → iOS foreground observer → controller event → `UpdraftIos.autoWire()` → `ComposeUIViewController` renders the CMP feedback hint dialog ("Feedback geben"). Full shared chain works on iOS.

### Remains release-time (blocks a 2.0.0 release, not this branch)

- [ ] Run the publish workflow once (`.github/workflows/publish.yml`, all three modules, macos-15, triggered by push to `production`) and verify all three land on Maven Central.
- [ ] Manual device verification on **both** platforms with real `APP_KEY`/`SDK_KEY` (still empty strings in the sample) — Android checklist above, plus iOS: launch → hint dialog; shake → annotate → form → send; rotate mid-form; update dialog against staging; feedback-enabled toggle.
- [ ] Archive [`updraft-sdk-ios`](https://github.com/appswithlove/updraft-sdk-ios) and add its final README deprecation banner.

### M2-specific follow-ups (non-blocking, ordered roughly by impact)

1. **iOS shake has no auto-pause on background** — `Updraft.start`'s `createAppForegroundObserver(... onBackground = { })` is a no-op; unlike a real pause, `IosShakeDetector` keeps listening to the accelerometer while backgrounded (Android's equivalent stops the sensor). Low impact (CoreMotion is cheap while backgrounded, and `presentViewController` would just no-op with no key window), but inconsistent with Android behavior.
2. **`UpdraftIos.presentDialog`/`presentFeedback` can silently drop events** — `topmostViewController() ?: return` and the `presentViewController` call have no retry/queueing; an event that arrives while `rootViewController` is nil (e.g. very early launch) or while another `presentViewController` transition is still in flight is dropped rather than deferred.
3. **`keyWindow()` helper duplicated** — near-identical `UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }` exists in both `updraft-core`'s `IosScreenshotGrabber` (`Platform.ios.kt`) and `updraft-ui-compose`'s `UpdraftIos.kt`. No shared internal utility between the two iOS source sets yet.
4. **Sample `UpdraftSettings` construction duplicated** — Android (`sample/composeApp` androidMain/App) and iOS (`MainViewController.kt#startUpdraft`) each hardcode their own `UpdraftSettings(...)` call; no shared `commonMain` config object.
5. **CI runner memory tuning** — `ios` job on `macos-15` (7 GB total) overrides Gradle/Kotlin daemon heap to `-Xmx3g` each (`GRADLE_OPTS` in `build.yml`), vs. defaults elsewhere; worth revisiting if iOS builds get memory-constrained as the project grows.
6. **`sample/iosApp/iosApp/Info.plist` has a stale `UIRequiredDeviceCapabilities: [armv7]`** — meaningless for an arm64-only/simulator build target (armv7 was 32-bit); harmless but should be dropped in a cleanup pass.

## Device verification round (2026-07-22)

Manual verification with real keys (Pixel 8 Pro + iPhone 16 simulator, production API). Keys live in `local.properties` (gitignored): `updraft.appKey.android`, `updraft.appKey.ios`, `updraft.sdkKey` — generated into `SampleKeys.kt` at build time.

### Release-blocking bugs found & fixed

1. **`:updraft-sdk` crashed on any dialog** — module never applied the Compose compiler plugin; `setContent` lambdas compiled as plain `Function0` → `NoSuchMethodError` at runtime. Compiles fine, only explodes on device — exactly what the manual-verification blocker existed for.
2. **Sample had no Android event collector** — `sample/composeApp` didn't depend on `:updraft-sdk`, so hint/feedback/update dialogs were silently dropped on Android (M2's `app/`→`sample/` rewrite lost the dep).
3. **`BASE_URL_STAGING` (u2.mqd.me) is dead** — host unreachable; every network call failed. Sample now uses the prod default; the constant is removed from `UpdraftSettings` (baseUrl stays overridable).
4. **`device_uuid` never arrived on the dashboard** — server field is `device_uudid` (legacy iOS SDK typo, load-bearing). Fixed.
5. **iOS shake never fired** — shake is a responder-chain `motionEnded:` event, not accelerometer data; `CMMotionManager` can't see it on the simulator and nothing forwarded it. SDK now swizzles `UIWindow.motionEnded:withEvent:` (IMP is a `staticCFunction` — Kotlin block bridging marshals the `NSInteger` arg as an object and segfaults). Screenshot trigger (legacy iOS behavior) also added; fires on hardware only.

### Features added during verification

- **Feedback UI restyled to the legacy design** (charcoal chrome, Updraft logo header, color swatches + halo, dashed undo, draw-hint overlay, white form fields, yellow buttons; safeDrawing insets). Assets recovered from `dd72439^`.
- **Navigation stack now sent with feedback**: platform default (activity stack / VC chain, Updraft screens excluded) + `Updraft.navigationStackProvider` for single-activity apps (Compose Navigation etc.); opt out via `UpdraftSettings(sendNavigationStack = false)`. README has per-library examples.

### Verified end-to-end

- Android (Pixel 8 Pro, prod API): hint dialog, shake → annotate (draw/colors/undo) → form → send ✅ received on dashboard incl. UUID + navigation stack.
- iOS (iPhone 16 simulator): hint dialog, shake (⌃⌘Z) → feedback UI, manual button flow ✅.
- `publishToMavenLocal` (signing skipped locally): all 3 modules + iOS klibs land with correct POM chain; fresh consumer app resolves `updraft-sdk:2.0.0` from mavenLocal and compiles ✅. Consumers need `android.useAndroidX=true` (document in README).

### Update feature verification (2026-07-22, later)

- **Android: fully verified E2E** — versionCode-6 release APK uploaded via cURL (`app_upload`), server reports `is_new_version=true` for the installed v5, update dialog shown, "Open" lands on the Updraft install page. Both builds release-signed, so install-over works.
- **iOS: verified by construction, not E2E.** Updraft's server rejects unsigned IPAs (HTTP 500), and no Apple signing team was available. Audit against the legacy `updraft-sdk-ios`: request/response models field-identical for `check_last_version/` + `get_last_version/`, same version source (CFBundleVersion), same flow order, same `UIApplication.open` call; endpoints empirically proven via the Android E2E on the same account; `UpdraftIos.autoWire` handles `UpdateAvailable`, and dialog presentation is proven live (hint dialog). Remaining untested surface is server-side signed-IPA handling + itms-services install — do once with a provisioned Ad Hoc build at release time.

### Still open before 2.0.0

- Real-iPhone pass: hardware shake, screenshot trigger, send from iOS (simulator send still unverified end-to-end on dashboard), and one signed-IPA update-dialog run (see above).
- Publish workflow run + Maven Central verification; archive `updraft-sdk-ios` (unchanged from above).
- Follow-up: SDK logging is a silent no-op on Android (Ktor default logger → SLF4J with no provider).

## Deferred to M3 (per spec, unchanged from M1)

- ~~Fresh-repo/rename decision~~ — **decided 2026-07-22**: keep this repository and rebrand it as the multiplatform SDK. Code-side prep done (rootProject.name, POM URLs/name, README repositioning → `updraft-sdk`). Manual GitHub steps remain: rename the repo `updraft-sdk-android` → `updraft-sdk` (Settings → General; old URLs redirect), update repo description/topics, archive `updraft-sdk-ios` at release.
- SPM packaging polish, further platform targets.
- **AGP 9 / Gradle 9 upgrade** — tracked in [#16](https://github.com/appswithlove/updraft-sdk-android/issues/16) (decided 2026-07-22: do it, but as its own milestone — not in M2, which was device-verified on AGP 8.13): **implemented on branch `feature/toolchain-agp9`** with the final version set — Gradle 9.6.1, AGP 9.3.1, Kotlin 2.2.21 (unchanged), Compose Multiplatform 1.10.3 (not 1.11.1 — 1.11.x requires Kotlin >2.2.21, deferred), vanniktech maven-publish 0.37.0, dokka 2.2.0, loco 1.2.0 (`locoFetch`/`locoPush`), androidx.startup 1.2.0, coveralls dropped (applied at root with no jacoco or CI wiring — dead weight). Built-in Kotlin migration done for `:updraft-sdk`; KMP modules migrated to the `com.android.kotlin.multiplatform.library` target plugin. `sample` split into a shared KMP library module + `androidApp` (AGP 9 forbids a single module being both an Android app and a KMP target). The updraft gradle plugin was bumped to 3.0.0 (the AGP 9-compatible line; 2.x crashes under AGP 9). Android host tests now run via `testAndroidHostTest` (`withHostTestBuilder`). Requires a re-verification round on device afterwards.
