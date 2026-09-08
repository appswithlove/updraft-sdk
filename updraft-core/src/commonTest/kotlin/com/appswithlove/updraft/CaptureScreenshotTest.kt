package com.appswithlove.updraft

import com.appswithlove.updraft.platform.ScreenshotGrabber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureScreenshotTest {

    @Test
    fun captureScreenshotOrNull_grabberReturnsBytes_returnsBytes() = runTest {
        val grabber = ScreenshotGrabber { byteArrayOf(1, 2, 3) }
        assertContentEquals(byteArrayOf(1, 2, 3), captureScreenshotOrNull(grabber) {})
    }

    @Test
    fun captureScreenshotOrNull_grabberThrows_returnsNullAndLogs() = runTest {
        val logged = mutableListOf<String>()
        val grabber = ScreenshotGrabber { throw IllegalArgumentException("Software rendering doesn't support hardware bitmaps") }

        assertNull(captureScreenshotOrNull(grabber) { logged += it })
        assertEquals(1, logged.size)
        assertTrue(logged.single().contains("hardware bitmaps"))
    }

    @Test
    fun captureScreenshotOrNull_grabberThrowsError_returnsNull() = runTest {
        val grabber = ScreenshotGrabber { throw OutOfMemoryError("bitmap") }
        assertNull(captureScreenshotOrNull(grabber) {})
    }

    @Test
    fun captureScreenshotOrNull_grabberReturnsNull_returnsNullWithoutLog() = runTest {
        val logged = mutableListOf<String>()
        assertNull(captureScreenshotOrNull(ScreenshotGrabber { null }) { logged += it })
        assertTrue(logged.isEmpty())
    }

    @Test
    fun captureScreenshotOrNull_cancellation_propagates() = runTest {
        val grabber = ScreenshotGrabber { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { captureScreenshotOrNull(grabber) {} }
    }
}
