package com.antigravity.fluidanimations.physics

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Аутентичные кривые интерполяции iOS (Fluid Motion Curves).
 */
object FluidCurves {
    /**
     * Стандартный переход App-to-App в iOS (плавный старт, быстрый разгон и затяжное мягкое торможение).
     * Cubic Bezier (0.32, 0.72, 0.0, 1.0)
     */
    val IOS_APP_TO_APP: Interpolator = PathInterpolator(0.32f, 0.72f, 0.0f, 1.0f)

    /**
     * Классическая кривая затухания iOS для вторичных слоев и затемнения (Dimming overlay).
     */
    val IOS_EASE_IN_OUT: Interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f)

    /**
     * Быстрое замедление (Fast-Decel) для масштабирования фонового окна.
     */
    val IOS_RAPID_DECEL: Interpolator = PathInterpolator(0.16f, 1.0f, 0.3f, 1.0f)

    /**
     * Пружинящий отскок при возврате на рабочий стол (Icon landing bounce).
     * Легкий overshoot (выход за пределы 1.0 с последующим возвратом).
     */
    val IOS_LANDING_BOUNCE: Interpolator = PathInterpolator(0.175f, 0.885f, 0.32f, 1.15f)

    /**
     * Выделенная кривая HyperOS 3 / iOS Fluid Quintic для вертикальной оси (Y), масштабирования и скругления при открытии.
     * Quintic Bezier (0.20, 0.0, 0.0, 1.0)
     */
    val HYPEROS_OPEN: Interpolator = PathInterpolator(0.20f, 0.0f, 0.0f, 1.0f)

    /**
     * Выделенная кривая HyperOS 3 / iOS Fluid Quintic для горизонтальной оси (X) при открытии.
     * Quintic Bezier (0.22, 0.0, 0.10, 1.0)
     */
    val HYPEROS_OPEN_X: Interpolator = PathInterpolator(0.22f, 0.0f, 0.10f, 1.0f)
}

