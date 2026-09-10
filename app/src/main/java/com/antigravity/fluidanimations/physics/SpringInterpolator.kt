package com.antigravity.fluidanimations.physics

import android.view.animation.Interpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Высокопроизводительный интерполятор пружинной физики в стиле iOS (Damped Harmonic Oscillator).
 *
 * Математическая модель соответствует поведению CASpringAnimation в iOS:
 * - omega0 = sqrt(stiffness)
 * - zeta = dampingRatio
 * - omegad = omega0 * sqrt(1 - zeta^2)
 *
 * Для достижения максимального FPS (120 Гц на Poco X6 Pro) без нагрузки на сборщик мусора (GC)
 * значения предрассчитываются в Look-Up Table (LUT) размером 1024 сэмпла с линейной аппроксимацией.
 */
class SpringInterpolator(
    val stiffness: Float = 340f,
    val dampingRatio: Float = 0.84f,
    val durationMs: Long = 380L
) : Interpolator {

    private val samples = FloatArray(LUT_SIZE)

    init {
        precomputeLut()
    }

    private fun precomputeLut() {
        val durationSec = durationMs / 1000.0
        val omega0 = sqrt(stiffness.toDouble())
        val zeta = dampingRatio.toDouble().coerceIn(0.01, 0.999)
        val omegaD = omega0 * sqrt(1.0 - zeta * zeta)

        for (i in 0 until LUT_SIZE) {
            val progress = i.toDouble() / (LUT_SIZE - 1)
            val t = progress * durationSec

            // Уравнение затухающих колебаний
            // x(t) = 1 - e^(-zeta * omega0 * t) * (cos(omegaD * t) + (zeta * omega0 / omegaD) * sin(omegaD * t))
            val envelope = exp(-zeta * omega0 * t)
            val oscillation = cos(omegaD * t) + (zeta * omega0 / omegaD) * sin(omegaD * t)
            val value = 1.0 - envelope * oscillation

            samples[i] = value.toFloat()
        }

        // Нормализация границ
        samples[0] = 0f
        val finalVal = samples[LUT_SIZE - 1]
        if (finalVal != 0f && finalVal != 1f) {
            // Мягкое сведение последнего сэмпла ровно к 1.0f
            for (i in 0 until LUT_SIZE) {
                val factor = i.toFloat() / (LUT_SIZE - 1)
                samples[i] = samples[i] / (1f - factor * (1f - finalVal))
            }
        }
        samples[LUT_SIZE - 1] = 1f
    }

    override fun getInterpolation(input: Float): Float {
        if (input <= 0f) return 0f
        if (input >= 1f) return 1f

        val indexFloat = input * (LUT_SIZE - 1)
        val indexLow = indexFloat.toInt()
        val fraction = indexFloat - indexLow

        if (indexLow >= LUT_SIZE - 1) {
            return samples[LUT_SIZE - 1]
        }

        val y0 = samples[indexLow]
        val y1 = samples[indexLow + 1]
        return y0 + fraction * (y1 - y0)
    }

    companion object {
        private const val LUT_SIZE = 1024
    }
}
