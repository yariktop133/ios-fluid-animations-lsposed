package com.antigravity.fluidanimations.hooks

import android.util.Log
import com.antigravity.fluidanimations.config.ConfigManager
import com.antigravity.fluidanimations.physics.FluidCurves
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Хук анимации открытия приложений из шторки уведомлений и экрана блокировки.
 */
object SystemUiNotificationHook {
    private const val TAG = "FluidAnim_Notification"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        hookActivityTransitionAnimator(classLoader)
    }

    private fun hookActivityTransitionAnimator(classLoader: ClassLoader) {
        val animatorClass = findClassSafe("com.android.systemui.animation.ActivityTransitionAnimator", classLoader)
            ?: findClassSafe("com.android.systemui.animation.TransitionAnimator", classLoader)
            ?: findClassSafe("com.android.systemui.animation.LaunchAnimator", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(animatorClass, "startAnimation", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Перехват параметров запуска
                }
            })
        } catch (t: Throwable) {
            // Ignore
        }

        // Перехват таймингов аниматора запуска (Android 16 / LineageOS 23.2 TransitionAnimator.Timings)
        val timingsClass = findClassSafe("com.android.systemui.animation.TransitionAnimator\$Timings", classLoader)
            ?: findClassSafe("com.android.systemui.animation.LaunchAnimator\$Timings", classLoader)
            ?: findClassSafe("com.android.systemui.animation.ActivityTransitionAnimator\$Timings", classLoader)

        if (timingsClass != null) {
            try {
                XposedBridge.hookAllConstructors(timingsClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.setLongField(param.thisObject, "totalDuration", ConfigManager.config.openDurationMs)
                        } catch (t: Throwable) {
                            // Ignore if field differs
                        }
                    }
                })
                Log.i(TAG, "Notification Launch Timings hooked successfully (${timingsClass.name})")
            } catch (t: Throwable) {
                Log.w(TAG, "Notification Launch Timings hook warning: ${t.message}")
            }
        }

        // Перехват интерполяторов TransitionAnimator
        val interpolatorsClass = findClassSafe("com.android.systemui.animation.TransitionAnimator\$Interpolators", classLoader)
        if (interpolatorsClass != null) {
            try {
                XposedBridge.hookAllConstructors(interpolatorsClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.setObjectField(param.thisObject, "positionInterpolator", FluidCurves.HYPEROS_OPEN)
                            XposedHelpers.setObjectField(param.thisObject, "positionXInterpolator", FluidCurves.HYPEROS_OPEN_X)
                        } catch (t: Throwable) {
                            // Non-fatal
                        }
                    }
                })
                Log.i(TAG, "Notification TransitionAnimator.Interpolators hooked successfully")
            } catch (t: Throwable) {
                Log.w(TAG, "Notification TransitionAnimator.Interpolators hook warning: ${t.message}")
            }
        }

        // Обновление статических синглтонов TIMINGS и INTERPOLATORS (если уже инициализированы)
        val ataClass = findClassSafe("com.android.systemui.animation.ActivityTransitionAnimator", classLoader)
        if (ataClass != null) {
            try {
                val staticTimings = XposedHelpers.getStaticObjectField(ataClass, "TIMINGS")
                if (staticTimings != null) {
                    XposedHelpers.setLongField(staticTimings, "totalDuration", ConfigManager.config.openDurationMs)
                }
                val staticInterpolators = XposedHelpers.getStaticObjectField(ataClass, "INTERPOLATORS")
                if (staticInterpolators != null) {
                    XposedHelpers.setObjectField(staticInterpolators, "positionInterpolator", FluidCurves.HYPEROS_OPEN)
                    XposedHelpers.setObjectField(staticInterpolators, "positionXInterpolator", FluidCurves.HYPEROS_OPEN_X)
                }
                Log.i(TAG, "ActivityTransitionAnimator static TIMINGS and INTERPOLATORS updated")
            } catch (t: Throwable) {
                // Ignore if static fields are not present
            }
        }
    }

    private fun findClassSafe(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            XposedHelpers.findClass(className, classLoader)
        } catch (t: Throwable) {
            null
        }
    }
}
