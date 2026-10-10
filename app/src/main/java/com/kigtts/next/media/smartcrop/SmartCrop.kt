/* Port of smartcrop.js, Copyright (c) Jonas Wagner. MIT; see assets/licenses/smartcrop-MIT.txt. */
package com.kigtts.next.media.smartcrop

import kotlin.math.*

/** Straight ARGB pixels. The analysis does not depend on Android or a face-detection service. */
class CropImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong()) }
}

data class CropBoost(val x: Int, val y: Int, val width: Int, val height: Int, val weight: Double)
data class CropScore(val detail: Double, val skin: Double, val saturation: Double, val boost: Double, val total: Double)
data class SmartCropResult(val x: Int, val y: Int, val width: Int, val height: Int, val score: CropScore)

data class SmartCropOptions(
    val detailWeight: Double = .2, val skinWeight: Double = 1.8, val saturationWeight: Double = .1,
    val skinColor: List<Double> = listOf(.78, .57, .44), val skinBias: Double = .01,
    val skinBrightnessMin: Double = .2, val skinBrightnessMax: Double = 1.0, val skinThreshold: Double = .8,
    val saturationBrightnessMin: Double = .05, val saturationBrightnessMax: Double = .9,
    val saturationThreshold: Double = .4, val saturationBias: Double = .2,
    val scoreDownSample: Int = 8, val step: Int = 8, val scaleStep: Double = .1,
    val minScale: Double = 1.0, val maxScale: Double = 1.0,
    val edgeRadius: Double = .4, val edgeWeight: Double = -20.0, val outsideImportance: Double = -.5,
    val boostWeight: Double = 100.0, val ruleOfThirds: Boolean = true, val prescale: Boolean = true,
    /** .5 preserves upstream scoring; cards use a rightward focus inside their visible image region. */
    val focusX: Double = .5, val focusY: Double = .5, val boosts: List<CropBoost> = emptyList()
) {
    init {
        require(step > 0 && scoreDownSample > 0 && scaleStep.isFinite() && scaleStep > 0)
        require(minScale.isFinite() && maxScale.isFinite() && minScale > 0 && minScale <= maxScale && maxScale <= 1)
        require(focusX in .05.. .95 && focusY in .05.. .95)
        require(skinColor.size == 3 && skinColor.all { it.isFinite() })
        require(skinThreshold in 0.0..<1.0 && saturationThreshold in 0.0..<1.0)
        require(listOf(detailWeight, skinWeight, saturationWeight, skinBias, saturationBias,
            skinBrightnessMin, skinBrightnessMax, saturationBrightnessMin, saturationBrightnessMax,
            edgeRadius, edgeWeight, outsideImportance, boostWeight).all { it.isFinite() })
        require(boosts.all { it.width >= 0 && it.height >= 0 && it.weight.isFinite() })
    }
}

/** Original edge/skin/saturation analysis, block scoring and crop traversal, with cancellable work. */
object SmartCrop {
    fun crop(image: CropImage, width: Int, height: Int, options: SmartCropOptions = SmartCropOptions(),
        checkCancelled: () -> Unit = {}): SmartCropResult {
        require(width > 0 && height > 0)
        val scale = min(image.width.toDouble() / width, image.height.toDouble() / height)
        val originalWidth = (width * scale).toInt().coerceAtLeast(1)
        val originalHeight = (height * scale).toInt().coerceAtLeast(1)
        val minimumScale = min(options.maxScale, max(1 / scale, options.minScale))
        val prescale = if (options.prescale) min(max(256.0 / image.width, 256.0 / image.height), 1.0) else 1.0
        val input = if (prescale < 1) CropResample.resize(image,
            (image.width * prescale).toInt().coerceAtLeast(1), (image.height * prescale).toInt().coerceAtLeast(1)) else image
        val cropWidth = (originalWidth * prescale).toInt().coerceIn(1, input.width)
        val cropHeight = (originalHeight * prescale).toInt().coerceIn(1, input.height)
        val scaledOptions = options.copy(boosts = options.boosts.map {
            CropBoost((it.x * prescale).toInt(), (it.y * prescale).toInt(),
                (it.width * prescale).toInt(), (it.height * prescale).toInt(), it.weight)
        })
        checkCancelled()
        val features = SmartCropFeatures.detect(input, scaledOptions, checkCancelled)
        // Upstream gives empty scores on very small images. Keep a real analysis for native thumbnails.
        val factor = min(options.scoreDownSample, min(input.width, input.height))
        val sampled = SmartCropFeatures.downSample(features, factor)
        var best: SmartCropResult? = null
        var currentScale = options.maxScale
        while (currentScale >= minimumScale) {
            val w = (cropWidth * currentScale).coerceAtLeast(1.0)
            val h = (cropHeight * currentScale).coerceAtLeast(1.0)
            var y = 0
            while (y + h <= input.height) {
                checkCancelled()
                var x = 0
                while (x + w <= input.width) {
                    val score = SmartCropScoring.score(sampled, factor, x, y, w, h, scaledOptions)
                    if (best == null || score.total > best.score.total) best = SmartCropResult(
                        (x / prescale).toInt(), (y / prescale).toInt(), (w / prescale).toInt().coerceAtLeast(1),
                        (h / prescale).toInt().coerceAtLeast(1), score)
                    x += options.step
                }
                y += options.step
            }
            val nextScale = currentScale - options.scaleStep
            if (nextScale == currentScale) break // Subnormal steps must not trap a caller in an infinite loop.
            currentScale = nextScale
        }
        return requireNotNull(best)
    }
}
