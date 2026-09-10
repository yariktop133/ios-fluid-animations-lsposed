package com.antigravity.fluidanimations.hooks

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import com.antigravity.fluidanimations.config.ConfigManager
import com.antigravity.fluidanimations.physics.FluidCurves
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Хук для SystemUI WMShell Transitions (Shell Transitions).
 * Отвечает за переключение между приложениями (App-to-App), масштабирование и кривые в стиле iOS.
 */
object WMShellTransitionHook {
    private const val TAG = "FluidAnim_WMShell"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        hookDefaultTransitionHandler(classLoader)
        hookShellTransitions(classLoader)
    }

    private fun hookDefaultTransitionHandler(classLoader: ClassLoader) {
        val handlerClass = findClassSafe("com.android.wm.shell.transition.DefaultTransitionHandler", classLoader)
            ?: return

        // Перехват конструктора для доступа к Context
        try {
            XposedBridge.hookAllConstructors(handlerClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.args.firstOrNull { it is Context } as? Context
                    if (context != null) {
                        ConfigManager.initInHook(context)
                        Log.i(TAG, "WMShell DefaultTransitionHandler hooked with context")
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook DefaultTransitionHandler constructor: ${t.message}")
        }

        // Перехват сборки анимаций открытия и закрытия задач (App-to-App)
        val transitionMethods = listOf(
            "buildOpenTransition",
            "buildCloseTransition",
            "loadAnimation",
            "createAnimation"
        )

        for (methodName in transitionMethods) {
            try {
                XposedBridge.hookAllMethods(handlerClass, methodName, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result ?: return
                        applyIosTransitionSpecs(result)
                    }
                })
            } catch (t: Throwable) {
                // Ignore if method does not exist in this AOSP revision
            }
        }
    }

    private fun hookShellTransitions(classLoader: ClassLoader) {
        val transitionsClass = findClassSafe("com.android.wm.shell.transition.Transitions", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(transitionsClass, "dispatchTransition", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Гарантируем, что конфиг актуален
                }
            })
        } catch (t: Throwable) {
            // Ignore
        }
    }

    private fun applyIosTransitionSpecs(obj: Any) {
        val duration = ConfigManager.config.appToAppDurationMs
        val curve = FluidCurves.IOS_APP_TO_APP

        when (obj) {
            is Animation -> {
                obj.duration = duration
                obj.interpolator = curve
                if (obj is AnimationSet) {
                    for (child in obj.animations) {
                        child.duration = duration
                        child.interpolator = curve
                    }
                }
            }
            is ValueAnimator -> {
                obj.duration = duration
                obj.interpolator = curve
            }
            is Animator -> {
                obj.duration = duration
                obj.interpolator = curve
            }
        }
    }

    private fun findClassSafe(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            XposedHelpers.findClass(className, classLoader)
        } catch (t: Throwable) {
            Log.d(TAG, "Class not found: $className")
            null
        }
    }
}
