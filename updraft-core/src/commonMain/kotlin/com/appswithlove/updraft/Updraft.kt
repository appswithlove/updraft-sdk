package com.appswithlove.updraft

import com.appswithlove.updraft.api.UpdraftApi
import com.appswithlove.updraft.api.UpdraftApiContract
import com.appswithlove.updraft.interactor.CheckFeedbackEnabledInteractor
import com.appswithlove.updraft.interactor.CheckFeedbackResultModel
import com.appswithlove.updraft.interactor.CheckUpdateInteractor
import com.appswithlove.updraft.platform.KeyValueStore
import com.appswithlove.updraft.platform.ScreenshotGrabber
import com.appswithlove.updraft.platform.ShakeDetector
import com.appswithlove.updraft.platform.createAppForegroundObserver
import com.appswithlove.updraft.platform.createKeyValueStore
import com.appswithlove.updraft.platform.createScreenshotGrabber
import com.appswithlove.updraft.platform.createShakeDetector
import com.appswithlove.updraft.platform.currentAppInfo
import com.appswithlove.updraft.platform.currentNavigationStack
import com.appswithlove.updraft.platform.openUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

internal class UpdraftController(
    private val settings: UpdraftSettings,
    private val api: UpdraftApiContract,
    store: KeyValueStore,
    private val scope: CoroutineScope,
    private val navigationStackSource: () -> String = { "" },
) {
    private val checkUpdateInteractor = CheckUpdateInteractor(api)
    private val checkFeedbackInteractor = CheckFeedbackEnabledInteractor(api, store)

    private val _events = MutableSharedFlow<UpdraftEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UpdraftEvent> = _events

    private var updateAlertShown = false
    private var feedbackHintShown = false
    private var pendingScreenshot: ByteArray? = null
    private var pendingNavigationStack: String? = null

    var feedbackUiPresenter: FeedbackUiPresenter? = null

    fun onForeground() {
        if (settings.showFeedbackAlert && settings.feedbackEnabled && !feedbackHintShown) {
            feedbackHintShown = true
            _events.tryEmit(UpdraftEvent.ShowFeedbackHint)
        }
        checkForUpdate()
        checkFeedbackEnabled()
    }

    fun checkForUpdate() {
        scope.launch {
            try {
                val result = checkUpdateInteractor.checkUpdate()
                val url = result.url
                if (result.showAlert && url != null && !updateAlertShown) {
                    updateAlertShown = true
                    _events.tryEmit(
                        UpdraftEvent.UpdateAvailable(url, result.version, result.yourVersion, result.createAt),
                    )
                }
            } catch (t: Throwable) {
                _events.tryEmit(UpdraftEvent.Error(t))
            }
        }
    }

    private fun checkFeedbackEnabled() {
        scope.launch {
            try {
                val result = checkFeedbackInteractor.run()
                if (result.showAlert) {
                    when (result.alertType) {
                        CheckFeedbackResultModel.AlertType.FeedbackDisabled ->
                            _events.tryEmit(UpdraftEvent.FeedbackDisabled)
                        CheckFeedbackResultModel.AlertType.HowToGiveFeedback ->
                            if (settings.showFeedbackAlert) _events.tryEmit(UpdraftEvent.ShowFeedbackHint)
                    }
                }
                if (!result.isFeedbackEnabled) {
                    _events.tryEmit(UpdraftEvent.CloseFeedback)
                }
            } catch (t: Throwable) {
                _events.tryEmit(UpdraftEvent.Error(t))
            }
        }
    }

    fun onFeedbackTriggered(screenshotPng: ByteArray?) {
        pendingScreenshot = screenshotPng
        pendingNavigationStack = navigationStackSource()
        val presenter = feedbackUiPresenter
        if (presenter != null) {
            presenter.presentFeedback(screenshotPng)
        } else {
            _events.tryEmit(UpdraftEvent.FeedbackRequested)
        }
    }

    fun takePendingScreenshot(): ByteArray? {
        val screenshot = pendingScreenshot
        pendingScreenshot = null
        return screenshot
    }

    fun sendFeedback(screenshotPng: ByteArray, type: FeedbackType, description: String, email: String): Flow<Double> {
        val navigationStack = pendingNavigationStack ?: navigationStackSource()
        pendingNavigationStack = null
        return api.sendFeedback(screenshotPng, type, description, email, navigationStack)
    }
}

object Updraft {
    private var controller: UpdraftController? = null
    private var currentSettings: UpdraftSettings? = null
    private var shakeDetector: ShakeDetector? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var feedbackJob: Job? = null

    val settings: UpdraftSettings
        get() = checkNotNull(currentSettings) { "Must call Updraft.start() first" }

    val events: SharedFlow<UpdraftEvent>
        get() = requireController().events

    /**
     * Optional hook to report the app's navigation stack with feedback uploads,
     * captured when feedback is triggered (e.g. on shake). Return screen names
     * from your navigation library, ordered root to top. When null, the SDK
     * falls back to the platform default: the activity stack on Android, the
     * view controller chain on iOS.
     */
    var navigationStackProvider: (() -> List<String>)? = null

    /**
     * Optional hook to replace the SDK's screenshot capture. Called on the main
     * thread when feedback is triggered. Return null to open feedback without a
     * screenshot. When null, the SDK uses the platform default (PixelCopy of the
     * current window on Android 7+, View.draw on Android 6, the key window on iOS). Exceptions are caught
     * and treated as "no screenshot".
     */
    var screenshotGrabber: ScreenshotGrabber? = null

    private fun resolveNavigationStack(): String {
        if (currentSettings?.sendNavigationStack != true) return ""
        return navigationStackProvider?.invoke()?.joinToString(", ") ?: currentNavigationStack()
    }

    fun start(settings: UpdraftSettings) {
        if (controller != null) return
        currentSettings = settings
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val api = UpdraftApi(settings, currentAppInfo())
        val store = createKeyValueStore(CheckFeedbackEnabledInteractor.STORE_NAME)
        val newController = UpdraftController(settings, api, store, scope, ::resolveNavigationStack)
        controller = newController

        if (settings.feedbackEnabled) {
            val detector = createShakeDetector { showFeedback() }
            shakeDetector = detector
            detector.start()
        }
        createAppForegroundObserver(
            onForeground = { newController.onForeground() },
            onBackground = { },
        ).start()
    }

    fun checkForUpdate() = requireController().checkForUpdate()

    /**
     * Captures a screenshot and opens the feedback UI. Returns immediately; the
     * capture completes asynchronously on the main thread without blocking it.
     */
    fun showFeedback() {
        val controller = requireController()
        if (feedbackJob?.isActive == true) return
        val grabber = screenshotGrabber ?: createScreenshotGrabber()
        feedbackJob = mainScope.launch {
            val screenshot = captureScreenshotOrNull(grabber) { message ->
                if (currentSettings?.shouldShowErrors() == true) println("Updraft: $message")
            }
            controller.onFeedbackTriggered(screenshot)
        }
    }

    fun sendFeedback(screenshotPng: ByteArray, type: FeedbackType, description: String, email: String): Flow<Double> =
        requireController().sendFeedback(screenshotPng, type, description, email)

    fun openUpdateUrl(url: String) = openUrl(url)

    fun setFeedbackUiPresenter(presenter: FeedbackUiPresenter?) {
        requireController().feedbackUiPresenter = presenter
    }

    fun onFeedbackUiClosed() {
        shakeDetector?.setEnabled(true)
    }

    /**
     * Returns the screenshot captured for the pending feedback request, once.
     * Call from your feedback UI host when handling [UpdraftEvent.FeedbackRequested].
     */
    fun takePendingScreenshot(): ByteArray? = requireController().takePendingScreenshot()

    private fun requireController(): UpdraftController =
        checkNotNull(controller) { "Must call Updraft.start() first" }
}

/** A failed screenshot must never crash the host app: feedback opens without one instead. */
internal suspend fun captureScreenshotOrNull(grabber: ScreenshotGrabber, logError: (String) -> Unit): ByteArray? =
    try {
        grabber.capturePng()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logError("screenshot capture failed, opening feedback without one: $e")
        null
    }
