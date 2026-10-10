package com.kigtts.next

import com.kigtts.next.media.smartcrop.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CancellationException

class SmartCropTest {
    @Test fun matchesPinnedOriginalJsRectanglesAndAllScoreChannels() {
        val lines = requireNotNull(javaClass.getResourceAsStream("/smartcrop-reference.tsv")).bufferedReader().readLines()
        for (line in lines.filterNot { it.startsWith('#') || it.isBlank() }) {
            val cells = line.split('\t'); val name = cells[0]
            val width = if (name == "portrait") 128 else if (name == "odd-size") 259 else 256
            val height = if (name == "portrait") 256 else if (name == "odd-size") 173 else 192
            val pixels = IntArray(width * height) { i ->
                val x = i % width; val y = i / width
                val rgb = when {
                    name == "black" -> intArrayOf(0, 0, 0)
                    name == "skin" -> if (x > 150 && y > 40 && y < 155) intArrayOf(210, 153, 118) else intArrayOf(32, 40, 45)
                    name == "saturation" -> if (x > 145 && y > 25 && y < 145) intArrayOf(220, 10, 35) else intArrayOf(55, 55, 55)
                    else -> intArrayOf((x * 17 + y * 31 + (x xor y) * 7) % 256, (x * 3 + y * 5) % 256, (x * 7 + y * 11) % 256)
                }
                (255 shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
            }
            val options = SmartCropOptions(prescale = false, ruleOfThirds = name != "thirds-off", minScale = if (name == "scales") .75 else 1.0,
                boosts = when (name) {
                    "boost" -> listOf(CropBoost(206, 72, 24, 40, 1.0))
                    "overlap-boost" -> listOf(CropBoost(10, 30, 20, 60, .6), CropBoost(20, 50, 24, 60, .7))
                    else -> emptyList()
                })
            val result = SmartCrop.crop(CropImage(width, height, pixels), 80, if (name == "portrait") 48 else 80, options)
            assertEquals(name, cells.subList(1, 5).map(String::toInt), listOf(result.x, result.y, result.width, result.height))
            val scores = listOf(result.score.detail, result.score.skin, result.score.saturation, result.score.boost, result.score.total)
            scores.forEachIndexed { i, actual -> assertEquals("$name score $i", cells[5 + i].toDouble(), actual, 1e-10) }
        }
    }

    @Test fun tinyPanoramicTransparentAndPrescaledInputsStayWithinSource() {
        for ((w, h) in listOf(1 to 1, 3 to 8, 1024 to 2, 2 to 1024, 600 to 400)) {
            val result = SmartCrop.crop(CropImage(w, h, IntArray(w * h) { 0 }), 400, 160)
            assertTrue(result.x >= 0 && result.y >= 0 && result.width > 0 && result.height > 0)
            assertTrue(result.x + result.width <= w && result.y + result.height <= h)
            assertTrue(result.score.total.isFinite())
        }
    }

    @Test fun rightwardFocusPositionsKnownSubjectRightwardAndWorkCanBeCancelled() {
        val image = CropImage(320, 160, IntArray(320 * 160) { 0xff303030.toInt() })
        val boost = listOf(CropBoost(210, 64, 16, 32, 1.0))
        val center = SmartCrop.crop(image, 80, 80, SmartCropOptions(boosts = boost))
        val right = SmartCrop.crop(image, 80, 80, SmartCropOptions(boosts = boost, focusX = .68, ruleOfThirds = false))
        assertTrue("Subject should move right inside the chosen crop", right.x < center.x)
        assertTrue((218.0 - right.x) / right.width > .58)
        var calls = 0
        assertThrows(CancellationException::class.java) { SmartCrop.crop(image, 80, 80, checkCancelled = { if (++calls > 20) throw CancellationException() }) }
        assertNotNull(SmartCrop.crop(image, 80, 80, SmartCropOptions(scaleStep = 1e-30)))
    }
}
