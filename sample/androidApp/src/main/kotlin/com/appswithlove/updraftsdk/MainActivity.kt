package com.appswithlove.updraftsdk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                SampleApp()
                HardwareBitmapImage(modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp))
            }
        }
    }
}

/**
 * Mimics what Coil produces by default on API 26+: a hardware-backed bitmap in the view tree.
 * Drawing the window into a software Canvas throws
 * "Software rendering doesn't support hardware bitmaps" as soon as this is on screen.
 */
@Composable
private fun HardwareBitmapImage(modifier: Modifier = Modifier) {
    val bitmap = remember { createHardwareBitmap() }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Hardware bitmap", modifier = modifier.size(160.dp))
}

private fun createHardwareBitmap(): Bitmap {
    val software = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    Canvas(software).apply {
        drawColor(Color.MAGENTA)
        drawCircle(128f, 128f, 96f, Paint().apply { color = Color.YELLOW })
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return software
    return software.copy(Bitmap.Config.HARDWARE, false).also { software.recycle() }
}
