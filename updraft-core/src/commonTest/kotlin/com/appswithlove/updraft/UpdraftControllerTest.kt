package com.appswithlove.updraft

import app.cash.turbine.test
import com.appswithlove.updraft.api.UpdraftApiContract
import com.appswithlove.updraft.api.response.CheckLastVersionResponse
import com.appswithlove.updraft.api.response.GetLastVersionResponse
import com.appswithlove.updraft.platform.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeStore : KeyValueStore {
    val map = mutableMapOf<String, Boolean>()
    override fun getBoolean(key: String, default: Boolean) = map[key] ?: default
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
}

private class FakeApi : UpdraftApiContract {
    var check = CheckLastVersionResponse(isNewVersion = false, isAutoupdateEnabled = false)
    var last = GetLastVersionResponse()
    var feedbackEnabled = true
    override suspend fun checkLastVersion() = check
    override suspend fun getLastVersion() = last
    override suspend fun isFeedbackEnabled() = feedbackEnabled
    override fun sendFeedback(screenshotPng: ByteArray, type: FeedbackType, description: String, email: String, navigationStack: String): Flow<Double> = emptyFlow()
}

class UpdraftControllerTest {

    private val settings = UpdraftSettings(appKey = "a", sdkKey = "s", showFeedbackAlert = true)

    @Test
    fun onForeground_updateAvailable_emitsEvent() = runTest {
        val api = FakeApi().apply {
            check = CheckLastVersionResponse(isNewVersion = true, isAutoupdateEnabled = true, version = "7")
            last = GetLastVersionResponse(updateUrl = "https://u")
            feedbackEnabled = true
        }
        val store = FakeStore().apply { map["is_feedback_enabled_property"] = true }
        val controller = UpdraftController(settings, api, store, backgroundScope)

        controller.events.test {
            controller.onForeground()
            assertIs<UpdraftEvent.ShowFeedbackHint>(awaitItem())
            val event = awaitItem()
            val update = assertIs<UpdraftEvent.UpdateAvailable>(event)
            assertEquals("https://u", update.url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onForeground_feedbackBecameDisabled_emitsFeedbackDisabled() = runTest {
        val api = FakeApi().apply { feedbackEnabled = false }
        val store = FakeStore().apply { map["is_feedback_enabled_property"] = true }
        val controller = UpdraftController(settings, api, store, backgroundScope)

        controller.events.test {
            controller.onForeground()
            assertIs<UpdraftEvent.ShowFeedbackHint>(awaitItem())
            assertIs<UpdraftEvent.FeedbackDisabled>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun feedbackRequested_emittedOnShakePath() = runTest {
        val controller = UpdraftController(settings, FakeApi(), FakeStore(), backgroundScope)
        controller.events.test {
            controller.onFeedbackTriggered(screenshotPng = byteArrayOf(1))
            assertIs<UpdraftEvent.FeedbackRequested>(awaitItem())
            assertEquals(1, controller.takePendingScreenshot()!!.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class UpdraftControllerLateSubscriberTest {

    private val settings = UpdraftSettings(appKey = "a", sdkKey = "s", showFeedbackAlert = false)

    @Test
    fun updateAvailable_emittedBeforeAnySubscriber_isDeliveredToFirstSubscriber() = runTest {
        val api = FakeApi().apply {
            check = CheckLastVersionResponse(isNewVersion = true, isAutoupdateEnabled = true, version = "7")
            last = GetLastVersionResponse(updateUrl = "https://u")
        }
        val store = FakeStore().apply { map["is_feedback_enabled_property"] = true }
        val controller = UpdraftController(settings, api, store, this)

        controller.onForeground()
        advanceUntilIdle()

        controller.events.test {
            val update = assertIs<UpdraftEvent.UpdateAvailable>(awaitItem())
            assertEquals("https://u", update.url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun bufferedEvents_areDeliveredOnce() = runTest {
        val api = FakeApi().apply {
            check = CheckLastVersionResponse(isNewVersion = true, isAutoupdateEnabled = true)
            last = GetLastVersionResponse(updateUrl = "https://u")
        }
        val store = FakeStore().apply { map["is_feedback_enabled_property"] = true }
        val controller = UpdraftController(settings, api, store, this)

        controller.onForeground()
        advanceUntilIdle()

        controller.events.test {
            assertIs<UpdraftEvent.UpdateAvailable>(awaitItem())
            expectNoEvents()
            cancel()
        }
        controller.events.test {
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun updateCheckFailure_emitsErrorEvent() = runTest {
        val api = object : UpdraftApiContract by FakeApi() {
            override suspend fun checkLastVersion(): CheckLastVersionResponse = throw IllegalStateException("boom")
        }
        val controller = UpdraftController(settings, api, FakeStore(), this)
        controller.events.test {
            controller.checkForUpdate()
            val error = assertIs<UpdraftEvent.Error>(awaitItem())
            assertEquals("boom", error.cause.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
