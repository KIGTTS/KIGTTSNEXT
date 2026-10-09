package com.kigtts.next

import com.kigtts.next.ui.media.MediaWaveClock
import org.junit.Assert.*
import org.junit.Test

class MediaWaveClockTest {
    @Test fun slowingDownAndAcceleratingIntegrateVelocityWithoutSnappingPhase() {
        val clock=MediaWaveClock()
        clock.frame(0,1f);clock.frame(100_000_000,.5f)
        assertEquals(.075f,clock.phase,.00001f)
        clock.frame(200_000_000,0f)
        assertEquals(.1f,clock.phase,.00001f)
        clock.frame(300_000_000,0f)
        assertEquals(.1f,clock.phase,.00001f)
        clock.pause();clock.frame(10_000_000_000,0f);clock.frame(10_100_000_000,.5f)
        assertEquals(.125f,clock.phase,.00001f)
        clock.frame(10_200_000_000,1f)
        assertEquals(.2f,clock.phase,.00001f)
    }
    @Test fun pauseKeepsPhaseAndResumeDoesNotCountTimeSpentPaused() {
        val clock=MediaWaveClock()
        clock.frame(0);clock.frame(375_000_000)
        assertEquals(.375f,clock.phase,0f)
        clock.pause();clock.frame(9_000_000_000)
        assertEquals(.375f,clock.phase,0f)
        clock.frame(9_125_000_000)
        assertEquals(.5f,clock.phase,0f)
        clock.frame(9_875_000_000)
        assertEquals(.25f,clock.phase,.00001f)
    }
}
