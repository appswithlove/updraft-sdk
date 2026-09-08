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
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

/**
 * Reads the composited window content back via [PixelCopy]. Unlike [android.view.View.draw] into a
 * software canvas, this renders hardware-backed content (Compose GraphicsLayers, hardware bitmaps).
 * The copy is awaited without blocking the main thread; PNG encoding runs off the main thread.
 */
private class AndroidScreenshotGrabber : ScreenshotGrabber {
    override suspend fun capturePng(): ByteArray? {
        val activity = CurrentActivityManager.current ?: return null
        val window = activity.window ?: return null
        val view = window.decorView.rootView
        if (view.width == 0 || view.height == 0) return null
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val captured = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                withTimeoutOrNull(PIXEL_COPY_TIMEOUT_MS) { pixelCopy(window, bitmap) } == true
            } else {
                view.draw(Canvas(bitmap))
                true
            }
        }.getOrDefault(false)
        if (!captured) {
            bitmap.recycle()
            return null
        }
        return withContext(Dispatchers.Default) {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                bitmap.recycle()
                stream.toByteArray()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun pixelCopy(window: Window, bitmap: Bitmap): Boolean =
        suspendCancellableCoroutine { continuation ->
            val onResult: (Int) -> Unit = { result ->
                if (continuation.isActive) continuation.resume(result == PixelCopy.SUCCESS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val request = PixelCopy.Request.Builder.ofWindow(window).setDestinationBitmap(bitmap).build()
                PixelCopy.request(request, ContextCompat.getMainExecutor(window.context)) { onResult(it.status) }
            } else {
                PixelCopy.request(window, bitmap, onResult, Handler(Looper.getMainLooper()))
            }
        }

    companion object {
        private const val PIXEL_COPY_TIMEOUT_MS = 2_000L
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
