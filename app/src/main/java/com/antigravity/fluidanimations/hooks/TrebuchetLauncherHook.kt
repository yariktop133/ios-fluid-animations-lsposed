package com.antigravity.fluidanimations.hooks

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import android.view.View
import com.antigravity.fluidanimations.config.ConfigManager
import com.antigravity.fluidanimations.physics.FluidCurves
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Хук-контроллер для лаунчера Trebuchet (основанного на AOSP Launcher3 + Quickstep).
 * Управляет анимациями открытия приложений, жестом возврата на рабочий стол (Swipe-to-Home),
 * морфингом иконки (FloatingIconView) и эффектом отскока иконки (Landing Bounce).
 */
object TrebuchetLauncherHook {
    private const val TAG = "FluidAnim_Trebuchet"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        hookQuickstepTransitionManager(classLoader)
        hookInterpolators(classLoader)
        hookFloatingIconView(classLoader)
        hookDepthController(classLoader)
    }

    /**
     * Перехват QuickstepTransitionManager — центрального класса анимаций запуска и закрытия.
     */
    private fun hookQuickstepTransitionManager(classLoader: ClassLoader) {
        val qtmClass = findClassSafe("com.android.launcher3.QuickstepTransitionManager", classLoader)
            ?: return

        // Перехват конструктора для инициализации ConfigManager через контекст лаунчера
        try {
            XposedBridge.hookAllConstructors(qtmClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = (param.args.firstOrNull { it is Context } as? Context)
                        ?: XposedHelpers.getObjectField(param.thisObject, "mLauncher") as? Context
                    if (context != null) {
                        ConfigManager.initInHook(context)
                        Log.i(TAG, "QuickstepTransitionManager initialized with context: ${context.packageName}")
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook QuickstepTransitionManager constructor: ${t.message}")
        }

        // Перехват аниматоров открытия приложений: getOpeningWindowAnimators
        try {
            XposedBridge.hookAllMethods(qtmClass, "getOpeningWindowAnimators", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    applySpringToAnimator(result, isOpening = true)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook getOpeningWindowAnimators: ${t.message}")
        }

        // Перехват анимации возврата на рабочий стол: createWindowAnimationToHome
        try {
            XposedBridge.hookAllMethods(qtmClass, "createWindowAnimationToHome", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    applySpringToAnimator(result, isOpening = false)

                    // Применение эффекта Landing Bounce к целевой иконке
                    if (ConfigManager.config.landingBounce) {
                        try {
                            val launcher = XposedHelpers.getObjectField(param.thisObject, "mLauncher")
                            val floatingIconView = XposedHelpers.getObjectField(param.thisObject, "mFloatingIconView")
                            val originalIcon = if (floatingIconView != null) {
                                XposedHelpers.getObjectField(floatingIconView, "mOriginalIcon") as? View
                            } else null

                            if (originalIcon != null) {
                                scheduleLandingBounce(originalIcon)
                            }
                        } catch (t: Throwable) {
                            // Non-fatal
                        }
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook createWindowAnimationToHome: ${t.message}")
        }

        // Перехват анимации открытия из недавних (Recents): composeRecentsLaunchAnimator
        try {
            XposedBridge.hookAllMethods(qtmClass, "composeRecentsLaunchAnimator", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    applySpringToAnimator(result, isOpening = true)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook composeRecentsLaunchAnimator: ${t.message}")
        }
    }

    /**
     * Замена глобальных интерполяторов Launcher3 на пружинные iOS-интерполяторы.
     */
    private fun hookInterpolators(classLoader: ClassLoader) {
        val interpolatorsClass = findClassSafe("com.android.launcher3.anim.Interpolators", classLoader)
            ?: return

        try {
            // Подменяем статические поля интерполяторов
            setFinalStatic(interpolatorsClass, "EMPHASIZED", ConfigManager.springInterpolator)
            setFinalStatic(interpolatorsClass, "EMPHASIZED_DECELERATE", ConfigManager.springInterpolator)
            setFinalStatic(interpolatorsClass, "APP_CLOSE_SPRING", ConfigManager.springInterpolator)
            setFinalStatic(interpolatorsClass, "AGGRESSIVE_EASE", FluidCurves.IOS_RAPID_DECEL)
            Log.i(TAG, "Launcher3 Interpolators replaced with iOS spring curves")
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to replace static Interpolators: ${t.message}")
        }
    }

    /**
     * Хук FloatingIconView для аутентичного скругления углов при переходе окна в иконку.
     */
    private fun hookFloatingIconView(classLoader: ClassLoader) {
        val fivClass = findClassSafe("com.android.launcher3.views.FloatingIconView", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(fivClass, "getFloatingIconView", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val fiv = param.result ?: return
                    if (ConfigManager.config.cornerRadiusMorph) {
                        try {
                            // Оптимизация радиуса скругления окна под экран Poco X6 Pro
                            val displayRadiusPx = dpToPx(fiv as View, ConfigManager.config.displayCornerRadiusDp)
                            XposedHelpers.setFloatField(fiv, "mStartRadius", displayRadiusPx)
                        } catch (t: Throwable) {
                            // Ignore if field has different name in this LineageOS fork
                        }
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "FloatingIconView hook warning: ${t.message}")
        }
    }

    /**
     * Хук DepthController для эффекта глубины и размытия обоев при открытии/закрытии приложения.
     */
    private fun hookDepthController(classLoader: ClassLoader) {
        val depthControllerClass = findClassSafe("com.android.launcher3.statehandlers.DepthController", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(depthControllerClass, "setDepth", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.config.wallpaperDepthZoom) return
                    val currentDepth = (param.args.firstOrNull() as? Float) ?: return
                    // Усиливаем мягкий параллакс обоев на 15%
                    param.args[0] = (currentDepth * 1.15f).coerceIn(0f, 1f)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "DepthController hook warning: ${t.message}")
        }
    }

    /**
     * Рекурсивное применение пружинного интерполятора и обновленной длительности к аниматорам.
     */
    private fun applySpringToAnimator(obj: Any, isOpening: Boolean) {
        val spring = ConfigManager.springInterpolator
        val duration = ConfigManager.config.durationMs

        when (obj) {
            is ValueAnimator -> {
                obj.interpolator = spring
                obj.duration = duration
            }
            is AnimatorSet -> {
                obj.duration = duration
                for (anim in obj.childAnimations) {
                    applySpringToAnimator(anim, isOpening)
                }
            }
            else -> {
                // Если метод вернул Pair<RectF, AnimatorSet> или другую обертку
                try {
                    val second = XposedHelpers.getObjectField(obj, "second")
                    if (second is Animator) {
                        applySpringToAnimator(second, isOpening)
                    }
                } catch (t: Throwable) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Эффект отскока иконки (Landing Bounce) в стиле iOS при возврате в лаунчер.
     */
    private fun scheduleLandingBounce(view: View) {
        view.postDelayed({
            view.animate()
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(90)
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(260)
                        .setInterpolator(FluidCurves.IOS_LANDING_BOUNCE)
                        .start()
                }
                .start()
        }, ConfigManager.config.durationMs - 120)
    }

    private fun findClassSafe(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            XposedHelpers.findClass(className, classLoader)
        } catch (t: Throwable) {
            Log.d(TAG, "Class not found: $className")
            null
        }
    }

    private fun setFinalStatic(clazz: Class<*>, fieldName: String, value: Any) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            val modifiersField = Field::class.java.getDeclaredField("modifiers")
            modifiersField.isAccessible = true
            modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
            field.set(null, value)
        } catch (t: Throwable) {
            try {
                XposedHelpers.setStaticObjectField(clazz, fieldName, value)
            } catch (t2: Throwable) {
                // Ignore
            }
        }
    }

    private fun dpToPx(view: View, dp: Float): Float {
        return dp * view.resources.displayMetrics.density
    }
}
