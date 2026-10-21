package com.kigtts.next

import com.kigtts.next.media.color.filmGrain
import org.junit.Assert.*
import org.junit.Test

class FilmGrainTest {
    @Test fun noiseDoesNotRepeatTheOld101CellTilesAndHasBalancedDistribution() {
        val values = (0 until 256).flatMap { y -> (0 until 256).map { x -> filmGrain(x, y).toDouble() } }
        assertTrue(values.all { it in -.5.. .5 })
        assertEquals(0.0, values.average(), .025)
        val same = (0 until 256).sumOf { y -> (0 until 256).count { x -> filmGrain(x, y) == filmGrain(x + 101, y) } }
        assertTrue("The former horizontal repeat must disappear", same < 100)
        val variance = values.sumOf { it * it } / values.size
        assertTrue(variance in .06.. .11)
    }
}
