![Updraft: Mobile App Distribution](updraft.png)

[![Maven Central](https://maven-badges.sml.io/sonatype-central/com.appswithlove.updraft/updraft-sdk/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.appswithlove.updraft/updraft-sdk)
[![GitHub license](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)
[![Bluesky](https://img.shields.io/badge/Bluesky-@appswithlove.bsky.social-blue.svg?style=flat)](https://bsky.app/profile/appswithlove.bsky.social)

# Updraft SDK

The Updraft SDK for Android and iOS, built with Kotlin Multiplatform and Compose Multiplatform.

[Updraft](https://getupdraft.com/) distributes your app builds to testers without app store review. The SDK adds two features to apps distributed with Updraft:

- **Auto update**: users get a prompt when a newer build is available on Updraft.
- **Feedback**: users shake the device (or take a screenshot on iOS), annotate a screenshot, and send feedback straight to your Updraft dashboard.

One SDK serves all app types: native Android (Views or Compose), Kotlin/Compose Multiplatform, and pure Swift iOS apps via an XCFramework. Since 2.0.0 this repository replaces [`updraft-sdk-ios`](https://github.com/appswithlove/updraft-sdk-ios).

Updraft is built by [Apps with love](https://appswithlove.com/) and [Moqod](https://moqod.com/).

## Requirements

- Android: minSdk 23
- iOS: 14.0 or newer
- Kotlin, depending on how you consume the SDK:

| Your app | Required Kotlin |
| --- | --- |
| Java only Android app | none |
| Kotlin Android app | 2.1 or newer |
| KMP / Compose Multiplatform app | 2.2 or newer |
| Swift app via XCFramework | none |

With an older Kotlin version the build fails at compile time (never at runtime), for example: `Module 'updraft-core' was compiled with an incompatible version of Kotlin`. Fix: upgrade Kotlin, or stay on Updraft SDK 1.x until you can.

## 1. Setup for all platforms

You always need:

1. **Keys**: create your app on [getupdraft.com](https://getupdraft.com). You get an `appKey` per platform and one `sdkKey` per project.
2. **One artifact choice** from this table:

| Artifact | Pick when |
| --- | --- |
| `updraft-sdk` | Native Android app (Views or Compose). Everything works out of the box, no wiring needed. Android only. |
| `updraft-core` | You want the logic without any UI: your own feedback screen, or a size sensitive app avoiding Compose. Android and iOS. |
| `updraft-core` + `updraft-ui-compose` | KMP / Compose Multiplatform app using the built in feedback UI. Android and iOS. |

3. **Version catalog** entries (all artifacts share one version):

```toml
# libs.versions.toml
[versions]
updraft = "2.0.1"

[libraries]
updraft-sdk = { module = "com.appswithlove.updraft:updraft-sdk", version.ref = "updraft" }
updraft-core = { module = "com.appswithlove.updraft:updraft-core", version.ref = "updraft" }
updraft-ui-compose = { module = "com.appswithlove.updraft:updraft-ui-compose", version.ref = "updraft" }
```

4. **Start the SDK once at app launch** (where exactly, see your platform section below):

```kotlin
Updraft.start(
    UpdraftSettings(
        appKey = APP_KEY,
        sdkKey = SDK_KEY,
    ),
)
```

Optional `UpdraftSettings` parameters:

| Parameter | Default | Meaning |
| --- | --- | --- |
| `logLevel` | `LogLevel.Error` | `None`, `Error`, or `Debug` console logging |
| `showFeedbackAlert` | `true` | show the "shake to give feedback" hint dialog |
| `feedbackEnabled` | `true` | set `false` to force disable feedback |
| `storeRelease` | `false` | set `true` in store builds to disable Updraft features |
| `sendNavigationStack` | `true` | set `false` to never send navigation info with feedback |
| `baseUrl` | production API | override only for self hosted Updraft instances |

That is the whole common part. Now pick your platform.

## 2. Native Android app

```kotlin
// build.gradle.kts
dependencies {
    implementation(libs.updraft.sdk)
}
```

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Updraft.start(UpdraftSettings(appKey = APP_KEY, sdkKey = SDK_KEY))
    }
}
```

Done. Update dialogs, the shake gesture, and the feedback screen are wired automatically via `androidx.startup`. No context parameter, no activity registration.

Size note: `updraft-sdk` pulls in the Compose runtime for its feedback UI. In a Views app without Compose this adds roughly 2 MB after R8. Apps that already use Compose see almost no extra size. If that is too much, use `updraft-core` with your own UI (section 4).

## 3. KMP / Compose Multiplatform app

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.updraft.core)
            implementation(libs.updraft.ui.compose)
        }
    }
}
```

Call `Updraft.start(...)` once from shared code at app launch. The whole API lives in `commonMain` and behaves the same on both platforms.

**iOS side**: call `UpdraftIos.autoWire()` after start. It presents dialogs and the feedback screen on top of the current key window. Nothing else is needed:

```kotlin
// called from your iOS entry point (for example the function iOSApp.swift invokes)
fun startUpdraft() {
    Updraft.start(UpdraftSettings(appKey = APP_KEY, sdkKey = SDK_KEY))
    UpdraftIos.autoWire()
}
```

**Android side**: either add `implementation(libs.updraft.sdk)` to `androidMain` for the same automatic wiring, or host the UI inside your own Compose tree:

```kotlin
@Composable
fun App() {
    UpdraftEventHost(
        events = Updraft.events,
        onFeedbackRequested = {
            val screenshotPng = Updraft.takePendingScreenshot()
            navigateToFeedback(screenshotPng)
        },
    )
    // your app content
}

@Composable
fun FeedbackRoute(screenshotPng: ByteArray?, onClose: () -> Unit) {
    FeedbackScreen(
        screenshotPng = screenshotPng,
        onClose = {
            onClose()
            Updraft.onFeedbackUiClosed() // re-arms shake detection
        },
    )
}
```

## 4. Your own feedback UI (any platform)

Depend on `updraft-core` only. It has no UI and no Compose dependency:

```kotlin
Updraft.start(UpdraftSettings(appKey = APP_KEY, sdkKey = SDK_KEY))

Updraft.setFeedbackUiPresenter { screenshotPng ->
    // show your own feedback UI
}

// from your UI, when the user submits:
Updraft.sendFeedback(screenshot, type, description, email)
    .collect { progress -> /* 0.0 to 1.0 */ }

// when your feedback UI closes (always, also on cancel):
Updraft.onFeedbackUiClosed() // re-arms shake detection
```

Subscribe to `Updraft.events: SharedFlow<UpdraftEvent>` to handle update prompts, feedback hints, and errors in your own UI.

## 5. Pure Swift iOS app

Build the XCFramework and add it to your Xcode project (drag in, or wrap as an SPM binary target):

```
./gradlew :updraft-core:assembleUpdraftCoreXCFramework
# output: updraft-core/build/XCFrameworks/release/UpdraftCore.xcframework
```

```swift
import UpdraftCore

Updraft.shared.start(
    settings: UpdraftSettings(
        appKey: APP_KEY,
        sdkKey: SDK_KEY,
        baseUrl: UpdraftSettings.companion.BASE_URL_PROD,
        logLevel: .error,
        showFeedbackAlert: true,
        feedbackEnabled: true,
        storeRelease: false,
        sendNavigationStack: true
    )
)
```

Kotlin default parameter values are not visible from Swift, so pass every argument explicitly.

The XCFramework wraps `updraft-core` only, without the Compose feedback UI. Build your own feedback screen and drive it with `Updraft.setFeedbackUiPresenter` and `Updraft.sendFeedback` as in section 4.

## Features

### Auto update

Enable it on getupdraft.com in your app edit menu. The SDK compares the installed build number with the newest build uploaded to Updraft and prompts the user when a newer one exists. Increment the build number on every upload. Micro versions compare correctly, for example 1.2.3.20180804 is newer than 1.2.3.20180803.

### Feedback

Enable it on getupdraft.com in your app edit menu. Users trigger feedback by shaking the device (Android and iOS) or taking a screenshot (iOS). They annotate the captured screenshot, fill a short form, and send. The upload lands on your Updraft dashboard with device info and the navigation stack.

### Navigation stack

Feedback uploads include the screens the user had open, captured at trigger time. Default: the Android activity stack or the iOS view controller chain. Single activity apps should plug in their navigation library:

```kotlin
// Jetpack Compose Navigation
Updraft.navigationStackProvider = {
    listOfNotNull(navController.currentBackStackEntry?.destination?.route)
}

// Fragments (Navigation Component)
Updraft.navigationStackProvider = {
    navController.currentBackStack.value.mapNotNull { it.destination.label?.toString() }
}
```

Return screen names ordered root to top. Setting the provider to `null` restores the platform default. Updraft's own screens are always excluded. To send nothing at all, use `UpdraftSettings(sendNavigationStack = false)`.

### Logging

`UpdraftSettings(logLevel = LogLevel.Debug)` prints requests and responses to the console. Default is `LogLevel.Error`.

## Migrating from 1.x

Version 2.0.0 rebuilds the SDK on Kotlin Multiplatform. `updraft-sdk` stays a drop in Android dependency with the same runtime behavior, but the setup API changed. Check the Kotlin requirements table first; projects on Kotlin below 2.1 should stay on 1.x until they upgrade.

| 1.x | 2.0.0 |
| --- | --- |
| `Settings().apply { appKey = ...; sdkKey = ... }` | `UpdraftSettings(appKey = ..., sdkKey = ...)` constructor |
| `Settings.LOG_LEVEL_DEBUG` | `LogLevel.Debug` (also `Error`, `None`) |
| `Updraft.initialize(this, settings)` + `Updraft.getInstance()?.start()` | `Updraft.start(settings)`, one call, no context argument |
| `settings.isStoreRelease` | `UpdraftSettings(..., storeRelease = ...)` |
| `ScreenshotProvider` | not supported yet, screenshots are captured automatically; a custom hook is planned |

## Local development

Use the `sample` project for testing. `./gradlew publishToMavenLocal` installs the current version to Maven Local. Sample keys go into `local.properties` (`updraft.appKey.android`, `updraft.appKey.ios`, `updraft.sdkKey`).

### Strings (Loco)

UI strings live in `updraft-ui-compose/src/commonMain/composeResources/values*/strings.xml` (en, de) and are shared by both platforms. They are managed on [Loco](https://localise.biz). `./gradlew :updraft-ui-compose:locoFetch` re-fetches them and **overwrites local edits**, so change strings in Loco first, then pull. `locoPush` pushes local strings to Loco and needs a full-access key. Both tasks need `locoApiKey=<key>` in `local.properties`. Plurals live in `plurals.xml`, which Loco does not touch.

## Release

Pushing to the `production` branch triggers the [publish workflow](.github/workflows/publish.yml). It runs all tests, publishes `updraft-core`, `updraft-ui-compose`, and `updraft-sdk` to Maven Central, and creates a GitHub release. Bump `VERSION_NAME` in `updraft-sdk/gradle.properties` first; publishing an already tagged version fails fast.
