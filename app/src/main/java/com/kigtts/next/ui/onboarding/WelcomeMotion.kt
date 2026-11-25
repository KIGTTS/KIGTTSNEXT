package com.kigtts.next.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sin

internal data class WelcomePose(
    val alpha: Float = 0f,
    val entranceY: Float = 0f,
    val aim: Offset = Offset.Zero,
    val bodyRoll: Float = 0f,
    val hairRoll: Float = 0f,
    val armRoll: Float = 0f,
    val leftEar: Float = 0f,
    val rightEar: Float = 0f,
    val zipper: Float = 0f,
    val eyeOpen: Float = 1f,
    val squeeze: Boolean = false,
)

internal class WelcomeMotion {
    var pose by mutableStateOf(WelcomePose())
        private set
    private var held: Offset? = null
    private var hover: Offset? = null
    private var anchor = Offset.Zero
    private var part = WelcomePart.BODY
    private var aim = Offset.Zero
    private var restingGravity = Offset.Zero
    private var age = 0f
    private val left = WelcomeSpring()
    private val right = WelcomeSpring()
    private val zipper = WelcomeSpring()

    fun press(position: Offset): Boolean {
        if (position.x !in 80f..1540f || position.y !in 8f..1140f) return false
        anchor = position
        held = position
        part = when {
            position.x in 510f..830f && position.y in 145f..535f -> WelcomePart.LEFT_EAR
            position.x in 1170f..1510f && position.y in 145f..560f -> WelcomePart.RIGHT_EAR
            position.x in 1048f..1130f && position.y in 731f..850f -> WelcomePart.ZIPPER
            else -> WelcomePart.BODY
        }
        return true
    }
    fun drag(position: Offset) { held = position }
    fun hover(position: Offset?) { hover = position }
    fun release() { held = null }

    fun step(dt: Float, gravity: Offset, reduceMotion: Boolean) {
        age += dt
        val progress = if (reduceMotion) 1f else (age / .52f).coerceIn(0f, 1f)
        val remaining = 1f - progress
        val entrance = remaining * remaining * (3f * remaining - 2f)
        val hand = held
        val position = hand ?: hover
        // Follow changes in posture, then let a held angle become the neutral pose.
        restingGravity = lerp(restingGravity, gravity, 1f - exp(-dt / 1.25f))
        val movement = gravity - restingGravity
        val target = if (position == null) Offset(tiltAim(-movement.x, .32f), tiltAim(movement.y, .22f) + .58f * entrance)
            else Offset(((position.x - 986f) / 520f).coerceIn(-1f, 1f), ((position.y - 392f) / 420f).coerceIn(-1f, 1f))
        fun ease(value: Float, goal: Float): Float {
            val time = if (position != null) .085f else if (kotlin.math.abs(goal) < kotlin.math.abs(value)) .52f else .13f
            return if (reduceMotion) goal else value + (goal - value) * (1f - exp(-dt / time))
        }
        aim = Offset(ease(aim.x, target.x), ease(aim.y, target.y))
        val idle = if (reduceMotion) 0f else sin(age * .87f)
        val leftTarget = if (hand != null && part == WelcomePart.LEFT_EAR) tugAngle(hand, Offset(862f, 86f)) else 0f
        val rightTarget = if (hand != null && part == WelcomePart.RIGHT_EAR) tugAngle(hand, Offset(1098f, 73f)) else 0f
        val zipperTarget = if (hand != null && part == WelcomePart.ZIPPER) ((anchor.x - hand.x) * .46f).coerceIn(-38f, 38f)
            else idle * 1.8f + aim.x * 4.4f
        val blink = (age + .65f) % 3.4f
        val eyeOpen = if (reduceMotion || blink > .20f) 1f else 1f - .95f * sin(blink / .20f * PI.toFloat())
        pose = WelcomePose(
            alpha = (progress * 2.4f).coerceAtMost(1f),
            entranceY = entrance,
            aim = aim,
            bodyRoll = aim.x * 1.02f + idle * .16f,
            hairRoll = aim.x * .32f + idle * .48f,
            armRoll = aim.y * -.6f + idle * .46f,
            leftEar = left.step(leftTarget + idle * .85f, dt, reduceMotion),
            rightEar = right.step(rightTarget - idle * .75f, dt, reduceMotion),
            zipper = zipper.step(zipperTarget, dt, reduceMotion),
            eyeOpen = eyeOpen,
            squeeze = hand != null && (part == WelcomePart.LEFT_EAR || part == WelcomePart.RIGHT_EAR),
        )
    }

    private fun tugAngle(hand: Offset, root: Offset): Float {
        val before = anchor - root
        val after = hand - root
        val cross = before.x * after.y - before.y * after.x
        val dot = before.x * after.x + before.y * after.y
        return (atan2(cross, dot) * (180f / PI.toFloat())).coerceIn(-17f, 17f)
    }

    private fun tiltAim(value: Float, range: Float) =
        kotlin.math.sign(value) * ((kotlin.math.abs(value) - .022f) / range).coerceIn(0f, 1f)
}

private class WelcomeSpring {
    private var value = 0f
    private var velocity = 0f
    fun step(target: Float, dt: Float, reduceMotion: Boolean): Float {
        if (reduceMotion) { value = target; velocity = 0f }
        else {
            velocity += ((target - value) * 120f - 12f * velocity) * dt
            value += velocity * dt
        }
        return value
    }
}
