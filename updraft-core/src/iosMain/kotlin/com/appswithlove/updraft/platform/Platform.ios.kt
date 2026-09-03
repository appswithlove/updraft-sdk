package com.appswithlove.updraft.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationUserDidTakeScreenshotNotification
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIWindow
import platform.posix.memcpy

private class IosShakeDetector(private val onShake: () -> Unit) : ShakeDetector {
    private val motionManager = CMMotionManager()
    private var shakeTimestamp = 0.0
    private var enabled = true
    private var screenshotObserver: Any? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun start() {
        // Shake reaches iOS apps as a responder-chain motion event, not as
        // accelerometer data — hook UIWindow to catch it (simulator + device).
        installMotionShakeHook {
            if (enabled) {
                enabled = false
                onShake()
            }
        }
        // Legacy Updraft iOS trigger: user takes a screenshot. Only fires on
        // physical hardware; the simulator's screenshot commands are host-side.
        if (screenshotObserver == null) {
            screenshotObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                UIApplicationUserDidTakeScreenshotNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) {
                if (enabled) {
                    enabled = false
                    onShake()
                }
            }
        }
        if (!motionManager.accelerometerAvailable) return
        motionManager.accelerometerUpdateInterval = 1.0 / 60.0
        motionManager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            val acceleration = data?.acceleration ?: return@startAccelerometerUpdatesToQueue
            acceleration.useContents {
                if (!isShakeGForce(x.toFloat(), y.toFloat(), z.toFloat(), 1.0f)) return@useContents
                val now = NSDate().timeIntervalSince1970
                if (shakeTimestamp + SHAKE_SLOP_SECONDS > now) return@useContents
                shakeTimestamp = now
                if (enabled) {
                    enabled = false
                    onShake()
                }
            }
        }
    }

    override fun stop() {
        motionManager.stopAccelerometerUpdates()
        screenshotObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        screenshotObserver = null
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    companion object {
        private const val SHAKE_SLOP_SECONDS = 0.5
    }
}

actual fun createShakeDetector(onShake: () -> Unit): ShakeDetector = IosShakeDetector(onShake)

@OptIn(ExperimentalForeignApi::class)
private class IosScreenshotGrabber : ScreenshotGrabber {
    override suspend fun capturePng(): ByteArray? {
        val window = keyWindow() ?: return null
        val bounds = window.bounds
        val renderer = UIGraphicsImageRenderer(bounds = bounds)
        val image = renderer.imageWithActions { _ ->
            window.drawViewHierarchyInRect(bounds, afterScreenUpdates = false)
        }
        val nsData = UIImagePNGRepresentation(image) ?: return null
        val length = nsData.length.toInt()
        if (length == 0) return null
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
        }
        return bytes
    }

    private fun keyWindow(): UIWindow? =
        UIApplication.sharedApplication.windows
            .filterIsInstance<UIWindow>()
            .firstOrNull { it.isKeyWindow() }
}

actual fun createScreenshotGrabber(): ScreenshotGrabber = IosScreenshotGrabber()

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
}

private class IosForegroundObserver(
    private val onForeground: () -> Unit,
    private val onBackground: () -> Unit,
) : AppForegroundObserver {
    override fun start() {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, `object` = null, queue = NSOperationQueue.mainQueue) { onForeground() }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, `object` = null, queue = NSOperationQueue.mainQueue) { onBackground() }
    }
}

actual fun createAppForegroundObserver(onForeground: () -> Unit, onBackground: () -> Unit): AppForegroundObserver =
    IosForegroundObserver(onForeground, onBackground)
