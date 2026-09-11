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
     * Эталонная кривая iOS / HyperOS 3 Fluid Ease-Out для открытия приложений.
     * Мгновенный отклик на касание (v0 > 0) и бархатное монотонное затухание.
     * Cubic Bezier (0.15, 0.85, 0.25, 1.0)
     */
    val IOS_OPEN: Interpolator = PathInterpolator(0.15f, 0.85f, 0.25f, 1.0f)

    /**
     * Координированная кривая для анимации масштабирования рабочего стола и затемнения.
     * Cubic Bezier (0.20, 0.90, 0.30, 1.0)
     */
    val IOS_OPEN_CONTENT: Interpolator = PathInterpolator(0.20f, 0.90f, 0.30f, 1.0f)

    /**
     * Выделенная кривая HyperOS 3 / iOS Fluid Quintic для вертикальной оси (Y), масштабирования и скругления при открытии.
     */
    val HYPEROS_OPEN: Interpolator = IOS_OPEN

    /**
     * Выделенная кривая HyperOS 3 / iOS Fluid Quintic для горизонтальной оси (X) при открытии.
     * Синхронизирована с вертикальной осью для предотвращения асимметричных перекосов окна.
     */
    val HYPEROS_OPEN_X: Interpolator = IOS_OPEN
}

