package com.appswithlove.updraft.platform

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private class AndroidShakeDetector(private val onShake: () -> Unit) :
    ShakeDetector, SensorEventListener, DefaultLifecycleObserver {

    private val sensorManager =
        UpdraftContext.application.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var shakeTimestamp = 0L
    private var enabled = true

    override fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun stop() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        sensorManager.unregisterListener(this)
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun onStart(owner: LifecycleOwner) {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onStop(owner: LifecycleOwner) {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        if (!isShakeGForce(x, y, z, SensorManager.GRAVITY_EARTH)) return

        val now = System.currentTimeMillis()
        if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) return
        shakeTimestamp = now

        if (enabled) {
            enabled = false
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val SHAKE_SLOP_TIME_MS = 500
    }
}

actual fun createShakeDetector(onShake: () -> Unit): ShakeDetector = AndroidShakeDetector(onShake)

private class AndroidScreenshotGrabber : ScreenshotGrabber {
    override fun capturePng(): ByteArray? {
        val activity = CurrentActivityManager.current ?: return null
        val window = activity.window ?: return null
        val view = window.decorView.rootView
        if (view.width == 0 || view.height == 0) return null
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val captured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pixelCopy(window, bitmap)
        } else {
            runCatching { view.draw(Canvas(bitmap)) }.isSuccess
        }
        if (!captured) {
            bitmap.recycle()
            return null
        }
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }

    /**
     * Copies the window's rendered content via [PixelCopy], which supports hardware-accelerated
     * content (Compose GraphicsLayers, hardware bitmaps) that [android.view.View.draw] into a software
     * canvas cannot render. Runs on the main thread, so the result callback must not be posted to the
     * main looper while we block on it.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun pixelCopy(window: Window, bitmap: Bitmap): Boolean {
        val thread = HandlerThread("updraft-pixelcopy").apply { start() }
        return try {
            val latch = CountDownLatch(1)
            var success = false
            PixelCopy.request(window, bitmap, { result ->
                success = result == PixelCopy.SUCCESS
                latch.countDown()
            }, Handler(thread.looper))
            latch.await(PIXEL_COPY_TIMEOUT_SECONDS, TimeUnit.SECONDS) && success
        } catch (e: Exception) {
            false
        } finally {
            thread.quitSafely()
        }
    }

    companion object {
        private const val PIXEL_COPY_TIMEOUT_SECONDS = 2L
    }
}

actual fun createScreenshotGrabber(): ScreenshotGrabber = AndroidScreenshotGrabber()

actual fun openUrl(url: String) {
    val activity = CurrentActivityManager.current ?: return
    activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

private class AndroidForegroundObserver(
    private val onForeground: () -> Unit,
    private val onBackground: () -> Unit,
) : AppForegroundObserver, DefaultLifecycleObserver {
    override fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) = onForeground()
    override fun onStop(owner: LifecycleOwner) = onBackground()
}

actual fun createAppForegroundObserver(onForeground: () -> Unit, onBackground: () -> Unit): AppForegroundObserver =
    AndroidForegroundObserver(onForeground, onBackground)
