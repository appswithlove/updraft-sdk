package com.appswithlove.updraft.ui.feedback

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.appswithlove.updraft.ui.drawing.DrawnPath
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Surface

actual fun decodePng(bytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(bytes).toComposeImageBitmap()

actual fun renderAnnotated(base: ByteArray, paths: List<DrawnPath>, canvasSize: IntSize): ByteArray {
    val baseImage = Image.makeFromEncoded(base)
    val surface = Surface.makeRasterN32Premul(baseImage.width, baseImage.height)
    val canvas: Canvas = surface.canvas
    canvas.drawImage(baseImage, 0f, 0f)
    paths.forEach { drawn ->
        val paint = Paint().apply {
            color = drawn.color.toArgb()
            mode = PaintMode.STROKE
            strokeWidth = drawn.strokeWidthPx
            strokeCap = PaintStrokeCap.ROUND
            strokeJoin = PaintStrokeJoin.ROUND
            isAntiAlias = true
        }
        val mappedPoints = drawn.points.map { mapToBitmapSpace(it, canvasSize, baseImage.width, baseImage.height) }
        val path: Path = PathBuilder().apply {
            mappedPoints.firstOrNull()?.let { moveTo(it.x, it.y) }
            mappedPoints.drop(1).forEach { lineTo(it.x, it.y) }
        }.detach()
        canvas.drawPath(path, paint)
    }
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
}
