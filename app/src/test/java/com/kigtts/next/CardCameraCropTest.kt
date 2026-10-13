package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.kigtts.next.ui.cards.cropCameraPhoto
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27])
class CardCameraCropTest {
    @Test fun captureIsOrientedBeforeCroppingToPhotoWindowAndOutputDoesNotRelyOnExif() {
        val directory = Files.createTempDirectory("card-camera-crop").toFile()
        val source = java.io.File(directory, "camera.jpg"); val target = java.io.File(directory, "polaroid.jpg")
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        for (y in 0 until 80) for (x in 0 until 120) bitmap.setPixel(x, y, if (y < 40) Color.RED else Color.BLUE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }; bitmap.recycle()
        ExifInterface(source).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        try {
            cropCameraPhoto(source, target)
            val output = requireNotNull(BitmapFactory.decodeFile(target.path))
            try {
                assertEquals(80, output.width); assertEquals(84, output.height)
                assertEquals(1072.0 / 1128.0, output.width.toDouble() / output.height, .02)
                val left = output.getPixel(output.width / 4, output.height / 2)
                val right = output.getPixel(output.width * 3 / 4, output.height / 2)
                assertTrue(Color.blue(left) > Color.red(left)); assertTrue(Color.red(right) > Color.blue(right))
                assertEquals(0, ExifInterface(target).rotationDegrees)
            } finally { output.recycle() }
        } finally { source.delete(); target.delete(); directory.delete() }
    }
}
