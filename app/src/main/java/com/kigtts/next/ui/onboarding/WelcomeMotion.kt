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
import kotlin.math.abs

internal data class WelcomePose(
    val alpha: Float = 0f,
    val entranceY: Float = 0f,
    val aim: Offset = Offset.Zero,
    val head: Offset = Offset.Zero,
    val gaze: Offset = Offset.Zero,
    val bodyShift: Offset = Offset.Zero,
    val bodyRoll: Float = 0f,
    val leftHair: Float = 0f,
    val rightHair: Float = 0f,
    val armRoll: Float = 0f,
    val armBend: Float = 0f,
    val leftEar: Float = 0f,
    val rightEar: Float = 0f,
    val leftStretch: Float = 0f,
    val rightStretch: Float = 0f,
    val zipper: Float = 0f,
    val eyeOpen: Float = 1f,
    val rightEyeOpen: Float = 1f,
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
    private var stableSample = Offset.Zero
    private var stillTime = 0f
    private var age = 0f
    private val headX = WelcomeSpring(185f, 20f)
    private val headY = WelcomeSpring(150f, 17f)
    private val torso = WelcomeSpring(52f, 12f)
    private val left = WelcomeSpring(116f, 10f)
    private val right = WelcomeSpring(95f, 9f)
    private val leftStretch = WelcomeSpring(114f, 12f)
    private val rightStretch = WelcomeSpring(98f, 11f)
    private val rearLeft = WelcomeSpring(63f, 8f)
    private val rearRight = WelcomeSpring(74f, 9f)
    private val arm = WelcomeSpring(77f, 12f)
    private val sleeve = WelcomeSpring(96f, 10f)
    private val zipper = WelcomeSpring(142f, 8f)

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
        if ((gravity - stableSample).getDistanceSquared() > .045f * .045f) {
            stableSample = gravity
            stillTime = 0f
        } else stillTime = (stillTime + dt).coerceAtMost(3.1f)
        val settled = stillTime >= 3f
        // Recenter while the phone is tilted, then settle more quickly after three quiet seconds.
        restingGravity = lerp(restingGravity, gravity, 1f - exp(-dt / if (settled) .52f else 2.1f))
        val movement = gravity - restingGravity
        val target = if (position == null) Offset(tiltAim(movement.x, .32f), tiltAim(-movement.y, .22f) + .58f * entrance)
            else Offset(((position.x - 986f) / 520f).coerceIn(-1f, 1f), ((position.y - 392f) / 420f).coerceIn(-1f, 1f))
        fun ease(value: Float, goal: Float): Float {
            val time = when {
                position != null -> .065f
                abs(goal) >= abs(value) && value * goal >= 0f -> .13f
                settled -> .24f
                else -> .52f
            }
            return if (reduceMotion) goal else value + (goal - value) * (1f - exp(-dt / time))
        }
        aim = Offset(ease(aim.x, target.x), ease(aim.y, target.y))
        fun wave(rate: Float, phase: Float = 0f) = if (reduceMotion) 0f else sin(age * rate + phase)
        val sway = wave(.77f)
        val breath = .85f * wave(1.42f) + .15f * wave(2.84f, -.9f)
        val head = Offset(headX.step(aim.x + .045f * wave(.73f), dt, reduceMotion),
            headY.step(aim.y + .038f * wave(.89f), dt, reduceMotion))
        val gaze = aim + Offset(.052f * wave(1.65f), .045f * wave(1.14f, .8f))
        val headVelocity = headX.velocity
        val leftGrip = hand?.takeIf { part == WelcomePart.LEFT_EAR }
        val rightGrip = hand?.takeIf { part == WelcomePart.RIGHT_EAR }
        val leftRoot = Offset(862f, 86f)
        val rightRoot = Offset(1098f, 73f)
        val leftTarget = leftGrip?.let { tugAngle(it, leftRoot) } ?: 0f
        val rightTarget = rightGrip?.let { tugAngle(it, rightRoot) } ?: 0f
        val zipperTarget = if (hand != null && part == WelcomePart.ZIPPER) ((anchor.x - hand.x) * .46f).coerceIn(-38f, 38f)
            else wave(1.7f, .34f) * 2.5f + head.x * 4.4f - headVelocity * 1.5f
        val armRoll = arm.step(-.85f * head.y + .28f * head.x + .94f * wave(1.12f) + .28f * wave(.54f), dt, reduceMotion)
        pose = WelcomePose(
            alpha = (progress * 2.4f).coerceAtMost(1f),
            entranceY = entrance,
            aim = aim,
            head = head,
            gaze = gaze,
            bodyShift = Offset(2.9f * sway + .45f * breath, -5.3f * breath + 2.4f * head.y),
            bodyRoll = torso.step(head.x * 1.15f + .42f * sway, dt, reduceMotion),
            leftHair = rearLeft.step(19f * wave(.94f, -.85f) + 8f * wave(1.57f) - headVelocity * 4.6f, dt, reduceMotion),
            rightHair = rearRight.step(21f * wave(1.04f, 1.16f) + 6f * wave(1.83f) - headVelocity * 4f, dt, reduceMotion),
            armRoll = armRoll,
            armBend = sleeve.step(13f * wave(1.43f, .28f) - arm.velocity * 3.2f + 7f * head.y, dt, reduceMotion),
            leftEar = left.step(leftTarget + 1.62f * wave(1.16f, .1f) - headVelocity * .72f, dt, reduceMotion),
            rightEar = right.step(rightTarget + 1.48f * wave(1.04f, 1.10f) - headVelocity * .83f, dt, reduceMotion),
            leftStretch = leftStretch.step(leftGrip?.let { tugLength(it, leftRoot) } ?: 0f, dt, reduceMotion),
            rightStretch = rightStretch.step(rightGrip?.let { tugLength(it, rightRoot) } ?: 0f, dt, reduceMotion),
            zipper = zipper.step(zipperTarget, dt, reduceMotion),
            eyeOpen = if (reduceMotion) 1f else openness(age),
            rightEyeOpen = if (reduceMotion) 1f else openness(age - .012f),
            squeeze = hand != null && (part == WelcomePart.LEFT_EAR || part == WelcomePart.RIGHT_EAR),
        )
    }

    private fun tugLength(hand: Offset, root: Offset): Float =
        ((hand - root).getDistance() - (anchor - root).getDistance()).coerceIn(-35f, 56f)

    private fun tugAngle(hand: Offset, root: Offset): Float {
        val before = anchor - root
        val after = hand - root
        val cross = before.x * after.y - before.y * after.x
        val dot = before.x * after.x + before.y * after.y
        return (atan2(cross, dot) * (180f / PI.toFloat())).coerceIn(-17f, 17f)
    }

    private fun tiltAim(value: Float, range: Float) =
        kotlin.math.sign(value) * ((kotlin.math.abs(value) - .022f) / range).coerceIn(0f, 1f)

    private fun openness(time: Float): Float {
        val phase = time % 16f
        val start = blinkStarts.firstOrNull { phase >= it && phase <= it + .24f } ?: return 1f
        val elapsed = phase - start
        val closed = if (elapsed < .084f) elapsed / .084f else (1f - (elapsed - .084f) / .156f)
        return 1f - closed * closed * (3f - 2f * closed) * .97f
    }

    private companion object {
        val blinkStarts = floatArrayOf(1.85f, 5.32f, 5.76f, 9.42f, 13.18f)
    }
}

private class WelcomeSpring(private val stiffness: Float, private val damping: Float) {
    private var value = 0f
    var velocity = 0f
        private set
    fun step(target: Float, dt: Float, reduceMotion: Boolean): Float {
        if (reduceMotion) { value = target; velocity = 0f }
        else {
            velocity += ((target - value) * stiffness - damping * velocity) * dt
            value += velocity * dt
        }
        return value
    }
}
