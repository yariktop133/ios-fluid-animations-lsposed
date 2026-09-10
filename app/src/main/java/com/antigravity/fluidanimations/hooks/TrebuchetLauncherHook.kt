package com.antigravity.fluidanimations.hooks

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.antigravity.fluidanimations.config.ConfigManager
import com.antigravity.fluidanimations.physics.FluidCurves
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Collections
import java.util.WeakHashMap

/**
 * Высокооптимизированный хук-контроллер для Trebuchet (Launcher3 + Quickstep).
 *
 * Ключевые принципы стабильности:
 * 1. ZERO JNI Overhead: Никаких хуков на частые методы (getFloat, onUpdate, setDepth).
 * 2. ZERO Black Screens: Не ломаем внутренние таймеры RemoteAnimationTargets и openingTargets.release().
 * 3. Плавность HyperOS 3 / iOS: Управляется через кинематические кривые интерполяции mOpeningInterpolator / mOpeningXInterpolator.
 * 4. Натуральный Landing Bounce на аппаратном слое.
 */
object TrebuchetLauncherHook {
    private const val TAG = "FluidAnim_Trebuchet"

    private val activeBounceAnimators = Collections.synchronizedMap(WeakHashMap<View, Pair<SpringAnimation, SpringAnimation>>())

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        hookQuickstepTransitionManager(classLoader)
        hookQuickStepContract(classLoader)
        hookAbsSwipeUpHandler(classLoader)
    }

    /**
     * Перехват QuickstepTransitionManager: плавная кинематика открытия без черных экранов.
     */
    private fun hookQuickstepTransitionManager(classLoader: ClassLoader) {
        val qtmClass = findClassSafe("com.android.launcher3.QuickstepTransitionManager", classLoader)
            ?: return

        // Инициализация конфигурации в процессе лаунчера
        try {
            XposedBridge.hookAllConstructors(qtmClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = (param.args.firstOrNull { it is Context } as? Context)
                        ?: XposedHelpers.getObjectField(param.thisObject, "mLauncher") as? Context
                    if (context != null) {
                        ConfigManager.initInHook(context)
                        Log.i(TAG, "QuickstepTransitionManager initialized with context: ${context.packageName}")
                    }
                    applyFluidOpeningInterpolators(param.thisObject)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook QuickstepTransitionManager constructor: ${t.message}")
        }

        // Подмена кривых открытия приложения по тапу на иконку
        try {
            XposedBridge.hookAllMethods(qtmClass, "getOpeningWindowAnimators", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    applyFluidOpeningInterpolators(param.thisObject)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook getOpeningWindowAnimators: ${t.message}")
        }

        // Подмена кривых открытия из виджетов
        try {
            XposedBridge.hookAllMethods(qtmClass, "getOpeningWindowAnimatorsForWidget", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    applyFluidOpeningInterpolators(param.thisObject)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook getOpeningWindowAnimatorsForWidget: ${t.message}")
        }

        // Отмена отскока иконки, если по ней нажали повторно
        try {
            XposedBridge.hookAllMethods(qtmClass, "composeIconLaunchAnimator", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val launcherView = param.args.getOrNull(1) as? View
                    if (launcherView != null) {
                        cancelLandingBounce(launcherView)
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook composeIconLaunchAnimator: ${t.message}")
        }

        // Подключение Landing Bounce к закрытию окна
        try {
            XposedBridge.hookAllMethods(qtmClass, "getClosingWindowAnimators", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.config.landingBounce) return
                    val anim = param.result ?: return
                    val launcherView = param.args.getOrNull(2) as? View
                    if (launcherView != null) {
                        attachLandingBounce(anim, launcherView)
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook getClosingWindowAnimators: ${t.message}")
        }
    }

    /**
     * Свайп-возврат на рабочий стол: перехватываем завершение жеста для запуска Landing Bounce.
     */
    private fun hookAbsSwipeUpHandler(classLoader: ClassLoader) {
        val handlerClass = findClassSafe("com.android.quickstep.AbsSwipeUpHandler", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(handlerClass, "createWindowAnimationToHome", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.config.landingBounce) return
                    val result = param.result ?: return
                    val homeAnimFactory = param.args.getOrNull(1)
                    val iconView = homeAnimFactory?.let {
                        runCatching { XposedHelpers.callMethod(it, "getIconView") as? View }.getOrNull()
                    } ?: return

                    val animList = when (result) {
                        is Array<*> -> result.toList()
                        is Iterable<*> -> result.toList()
                        else -> listOf(result)
                    }
                    for (anim in animList) {
                        if (anim != null) {
                            attachLandingBounce(anim, iconView)
                        }
                    }
                }
            })
            Log.i(TAG, "AbsSwipeUpHandler.createWindowAnimationToHome hooked")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook AbsSwipeUpHandler: ${t.message}")
        }
    }

    /**
     * Точный радиус скругления дисплея Poco X6 Pro (~38dp / ~105px).
     */
    private fun hookQuickStepContract(classLoader: ClassLoader) {
        val contractClass = findClassSafe("com.android.systemui.shared.system.QuickStepContract", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(contractClass, "getWindowCornerRadius", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.config.cornerRadiusMorph) return
                    val resources = when (val first = param.args.firstOrNull()) {
                        is Context -> first.resources
                        is Resources -> first
                        else -> null
                    }
                    val density = resources?.displayMetrics?.density ?: 2.75f
                    param.result = ConfigManager.config.displayCornerRadiusDp * density
                }
            })
            Log.i(TAG, "QuickStepContract.getWindowCornerRadius hooked")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook QuickStepContract: ${t.message}")
        }
    }

    /**
     * Установка эталонных кривых HyperOS 3 / iOS Fluid в QuickstepTransitionManager.
     * Без изменения базовых таймеров системы, гарантируя отсутствие черных экранов!
     */
    private fun applyFluidOpeningInterpolators(qtmInstance: Any) {
        try {
            XposedHelpers.setObjectField(qtmInstance, "mOpeningInterpolator", FluidCurves.HYPEROS_OPEN)
            XposedHelpers.setObjectField(qtmInstance, "mOpeningXInterpolator", FluidCurves.HYPEROS_OPEN_X)
        } catch (t: Throwable) {
            // Non-fatal
        }
    }

    private fun attachLandingBounce(anim: Any, iconView: View) {
        try {
            val listener = object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runLandingBounce(iconView)
                }
            }
            when (anim) {
                is Animator -> anim.addListener(listener)
                else -> XposedHelpers.callMethod(anim, "addAnimatorListener", listener)
            }
        } catch (t: Throwable) {
            // Non-fatal
        }
    }

    private fun cancelLandingBounce(iconView: View) {
        activeBounceAnimators.remove(iconView)?.let { (x, y) ->
            runCatching { x.cancel() }
            runCatching { y.cancel() }
            iconView.setLayerType(View.LAYER_TYPE_NONE, null)
            iconView.scaleX = 1.0f
            iconView.scaleY = 1.0f
        }
    }

    private fun runLandingBounce(iconView: View) {
        iconView.post {
            if (!iconView.isAttachedToWindow) return@post
            try {
                cancelLandingBounce(iconView)

                iconView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                iconView.pivotX = iconView.width / 2f
                iconView.pivotY = iconView.height / 2f
                iconView.scaleX = 0.92f
                iconView.scaleY = 0.92f

                val springForce = SpringForce(1.0f).apply {
                    stiffness = 550f
                    dampingRatio = 0.68f
                }

                val animX = SpringAnimation(iconView, DynamicAnimation.SCALE_X).apply {
                    spring = springForce
                    setStartValue(0.92f)
                }

                val animY = SpringAnimation(iconView, DynamicAnimation.SCALE_Y).apply {
                    spring = springForce
                    setStartValue(0.92f)
                    addEndListener { _, canceled, _, _ ->
                        activeBounceAnimators.remove(iconView)
                        iconView.setLayerType(View.LAYER_TYPE_NONE, null)
                        if (!canceled) {
                            iconView.scaleX = 1.0f
                            iconView.scaleY = 1.0f
                        }
                    }
                }

                activeBounceAnimators[iconView] = Pair(animX, animY)
                animX.start()
                animY.start()
            } catch (t: Throwable) {
                activeBounceAnimators.remove(iconView)
                iconView.setLayerType(View.LAYER_TYPE_NONE, null)
                iconView.scaleX = 1.0f
                iconView.scaleY = 1.0f
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
