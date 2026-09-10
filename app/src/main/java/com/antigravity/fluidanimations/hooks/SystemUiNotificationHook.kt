package com.antigravity.fluidanimations.hooks

import android.animation.ValueAnimator
import android.util.Log
import com.antigravity.fluidanimations.config.ConfigManager
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

        // Перехват таймингов аниматора запуска
        val timingsClass = findClassSafe("com.android.systemui.animation.LaunchAnimator\$Timings", classLoader)
            ?: findClassSafe("com.android.systemui.animation.ActivityTransitionAnimator\$Timings", classLoader)

        if (timingsClass != null) {
            try {
                XposedBridge.hookAllConstructors(timingsClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.setLongField(param.thisObject, "totalDuration", ConfigManager.config.durationMs)
                        } catch (t: Throwable) {
                            // Ignore if field differs
                        }
                    }
                })
                Log.i(TAG, "Notification Launch Timings hooked successfully")
            } catch (t: Throwable) {
                Log.w(TAG, "Notification Launch Timings hook warning: ${t.message}")
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
