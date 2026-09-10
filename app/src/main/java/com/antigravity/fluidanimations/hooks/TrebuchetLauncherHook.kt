package com.antigravity.fluidanimations.hooks

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
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
 * Хук-контроллер для лаунчера Trebuchet (основанного на AOSP Launcher3 + Quickstep).
 * Управляет анимациями открытия приложений, жестом возврата на рабочий стол (Swipe-to-Home),
 * морфингом иконки (FloatingIconView), точным скруглением углов экрана Poco X6 Pro
 * и аппаратным эффектом отскока иконки (Landing Bounce).
 */
object TrebuchetLauncherHook {
    private const val TAG = "FluidAnim_Trebuchet"

    private val hookedProviderClasses = mutableSetOf<Class<*>>()
    private val activeBounceAnimators = Collections.synchronizedMap(WeakHashMap<View, Pair<SpringAnimation, SpringAnimation>>())

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        hookQuickstepTransitionManager(classLoader)
        hookQuickStepContract(classLoader)
        hookAbsSwipeUpHandler(classLoader)
        hookRectFSpringAnim(classLoader)
        hookDynamicResource(classLoader)
    }

    /**
     * Перехват QuickstepTransitionManager — центрального класса анимаций запуска и закрытия.
     */
    private fun hookQuickstepTransitionManager(classLoader: ClassLoader) {
        val qtmClass = findClassSafe("com.android.launcher3.QuickstepTransitionManager", classLoader)
            ?: return

        // Перехват конструктора для инициализации ConfigManager и подмены кривых открытия
        try {
            XposedBridge.hookAllConstructors(qtmClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = (param.args.firstOrNull { it is Context } as? Context)
                        ?: XposedHelpers.getObjectField(param.thisObject, "mLauncher") as? Context
                    if (context != null) {
                        ConfigManager.initInHook(context)
                        Log.i(TAG, "QuickstepTransitionManager initialized with context: ${context.packageName}")
                    }
                    setOpeningInterpolators(param.thisObject)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook QuickstepTransitionManager constructor: ${t.message}")
        }

        // Перехват аниматоров открытия приложений: getOpeningWindowAnimators
        // appAnimator ОБЯЗАТЕЛЬНО остается LINEAR для сохранения точных таймингов clampToDuration
        try {
            XposedBridge.hookAllMethods(qtmClass, "getOpeningWindowAnimators", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    setOpeningInterpolators(param.thisObject)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    applyOpenWindowDuration(result, ConfigManager.config.openDurationMs)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook getOpeningWindowAnimators: ${t.message}")
        }

        // Перехват открытия приложений из виджетов: getOpeningWindowAnimatorsForWidget
        try {
            XposedBridge.hookAllMethods(qtmClass, "getOpeningWindowAnimatorsForWidget", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    setOpeningInterpolators(param.thisObject)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    applyOpenWindowDuration(result, ConfigManager.config.openDurationMs)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook getOpeningWindowAnimatorsForWidget: ${t.message}")
        }

        // Синхронизация времени скрытия содержимого лаунчера с длительностью открытия окна
        try {
            XposedBridge.hookAllMethods(qtmClass, "composeIconLaunchAnimator", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Отменяем отскок, если иконка была нажата во время посадки
                    val launcherView = param.args.getOrNull(1) as? View
                    if (launcherView != null) {
                        cancelLandingBounce(launcherView)
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val anim = param.args.firstOrNull() as? AnimatorSet ?: return
                    synchronizeLauncherContentAnimator(anim, ConfigManager.config.openDurationMs)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook composeIconLaunchAnimator: ${t.message}")
        }

        // Синхронизация длительности анимации содержимого лаунчера (getLauncherContentAnimator)
        try {
            XposedBridge.hookAllMethods(qtmClass, "getLauncherContentAnimator", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val isAppOpening = param.args.firstOrNull() as? Boolean ?: true
                    val pair = param.result ?: return
                    try {
                        val launcherAnimator = XposedHelpers.getObjectField(pair, "first") as? AnimatorSet
                        val duration = if (isAppOpening) ConfigManager.config.openDurationMs else ConfigManager.config.closeDurationMs
                        launcherAnimator?.duration = duration
                        launcherAnimator?.childAnimations?.forEach { it.duration = duration }
                    } catch (t: Throwable) {
                        // Non-fatal
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook getLauncherContentAnimator: ${t.message}")
        }

        // Перехват анимации открытия из недавних (Recents): composeRecentsLaunchAnimator
        try {
            XposedBridge.hookAllMethods(qtmClass, "composeRecentsLaunchAnimator", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val anim = param.args.firstOrNull() as? AnimatorSet ?: return
                    val duration = ConfigManager.config.openDurationMs
                    anim.duration = duration
                    anim.childAnimations.forEach { it.duration = duration }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook composeRecentsLaunchAnimator: ${t.message}")
        }

        // Перехват закрытия окна на рабочий стол: getClosingWindowAnimators
        try {
            XposedBridge.hookAllMethods(qtmClass, "getClosingWindowAnimators", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val anim = param.result ?: return
                    applySpringPhysicsToRectFAnim(anim)

                    if (ConfigManager.config.landingBounce) {
                        val launcherView = param.args.getOrNull(2) as? View
                        if (launcherView != null) {
                            attachLandingBounce(anim, launcherView)
                        } else {
                            attachLandingBounceFromSpringAnim(anim)
                        }
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook getClosingWindowAnimators: ${t.message}")
        }

        // Перехват возврата на рабочий стол (совместимость с ревизиями Launcher3): createWindowAnimationToHome
        try {
            XposedBridge.hookAllMethods(qtmClass, "createWindowAnimationToHome", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    if (ConfigManager.config.landingBounce) {
                        try {
                            val floatingIconView = XposedHelpers.getObjectField(param.thisObject, "mFloatingIconView")
                            val originalIcon = floatingIconView?.let { XposedHelpers.getObjectField(it, "mOriginalIcon") as? View }
                            if (originalIcon != null) {
                                attachLandingBounce(result, originalIcon)
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
    }

    /**
     * Хук свайпа домой в Quickstep (AbsSwipeUpHandler.createWindowAnimationToHome).
     * Сохраняет нативную кинематику и скорость пальца пользователя v0.
     */
    private fun hookAbsSwipeUpHandler(classLoader: ClassLoader) {
        val handlerClass = findClassSafe("com.android.quickstep.AbsSwipeUpHandler", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(handlerClass, "createWindowAnimationToHome", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result ?: return
                    val animList = when (result) {
                        is Array<*> -> result.toList()
                        is Iterable<*> -> result.toList()
                        else -> listOf(result)
                    }

                    // Извлекаем иконку из HomeAnimationFactory (arg 1), если доступна
                    val homeAnimFactory = param.args.getOrNull(1)
                    val factoryIconView = homeAnimFactory?.let {
                        runCatching {
                            XposedHelpers.callMethod(it, "getIconView") as? View
                        }.getOrNull()
                    }

                    for (anim in animList) {
                        if (anim == null) continue
                        applySpringPhysicsToRectFAnim(anim)
                        if (ConfigManager.config.landingBounce) {
                            if (factoryIconView != null) {
                                attachLandingBounce(anim, factoryIconView)
                            } else {
                                attachLandingBounceFromSpringAnim(anim)
                            }
                        }
                    }
                }
            })
            Log.i(TAG, "AbsSwipeUpHandler.createWindowAnimationToHome hooked successfully")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook AbsSwipeUpHandler.createWindowAnimationToHome: ${t.message}")
        }
    }

    /**
     * Конфигурация параметров нативной пружины RectFSpringAnim и DefaultSpringConfig.
     */
    private fun hookRectFSpringAnim(classLoader: ClassLoader) {
        // Конфигурация DefaultSpringConfig
        val defaultConfigClass = findClassSafe("com.android.quickstep.util.RectFSpringAnim\$DefaultSpringConfig", classLoader)
        if (defaultConfigClass != null) {
            try {
                XposedBridge.hookAllConstructors(defaultConfigClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val stiffness = ConfigManager.config.stiffness
                        val damping = ConfigManager.config.dampingRatio
                        XposedHelpers.setFloatField(param.thisObject, "stiffnessX", stiffness)
                        XposedHelpers.setFloatField(param.thisObject, "stiffnessY", stiffness)
                        XposedHelpers.setFloatField(param.thisObject, "dampingX", damping)
                        XposedHelpers.setFloatField(param.thisObject, "dampingY", damping)
                        XposedHelpers.setFloatField(param.thisObject, "rectStiffness", stiffness)
                    }
                })
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to hook DefaultSpringConfig constructor: ${t.message}")
            }
        }

        // Конфигурация TaskbarHotseatSpringConfig
        val taskbarConfigClass = findClassSafe("com.android.quickstep.util.RectFSpringAnim\$TaskbarHotseatSpringConfig", classLoader)
        if (taskbarConfigClass != null) {
            try {
                XposedBridge.hookAllConstructors(taskbarConfigClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val stiffness = ConfigManager.config.stiffness
                        val damping = ConfigManager.config.dampingRatio
                        XposedHelpers.setFloatField(param.thisObject, "stiffnessX", stiffness)
                        XposedHelpers.setFloatField(param.thisObject, "stiffnessY", stiffness)
                        XposedHelpers.setFloatField(param.thisObject, "dampingX", damping)
                        XposedHelpers.setFloatField(param.thisObject, "dampingY", damping)
                        XposedHelpers.setFloatField(param.thisObject, "rectStiffness", stiffness)
                    }
                })
            } catch (t: Throwable) {
                // Non-fatal
            }
        }

        // Конструктор RectFSpringAnim
        val springAnimClass = findClassSafe("com.android.quickstep.util.RectFSpringAnim", classLoader)
        if (springAnimClass != null) {
            try {
                XposedBridge.hookAllConstructors(springAnimClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        applySpringPhysicsToRectFAnim(param.thisObject)
                    }
                })
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to hook RectFSpringAnim constructor: ${t.message}")
            }

            // Перехват start() для настройки запущенных SpringAnimation с сохранением начальной скорости v0
            try {
                XposedBridge.hookAllMethods(springAnimClass, "start", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val anim = param.thisObject
                        val stiffness = ConfigManager.config.stiffness
                        val damping = ConfigManager.config.dampingRatio

                        try {
                            val springX = (XposedHelpers.getObjectField(anim, "mRectXSpring") as? SpringAnimation)?.spring
                            springX?.stiffness = stiffness
                            springX?.dampingRatio = damping

                            val springY = (XposedHelpers.getObjectField(anim, "mRectYSpring") as? SpringAnimation)?.spring
                            springY?.stiffness = stiffness
                            springY?.dampingRatio = damping

                            val springScale = (XposedHelpers.getObjectField(anim, "mRectScaleAnim") as? SpringAnimation)?.spring
                            springScale?.stiffness = stiffness
                            springScale?.dampingRatio = damping
                        } catch (t: Throwable) {
                            // Non-fatal
                        }
                    }
                })
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to hook RectFSpringAnim.start: ${t.message}")
            }
        }
    }

    /**
     * Хук DynamicResource: обеспечивает автоматическую подстановку жесткости (340f) и демпфирования (0.84f)
     * для любых динамических ресурсов пружин Trebuchet / Launcher3.
     */
    private fun hookDynamicResource(classLoader: ClassLoader) {
        val dynResClass = findClassSafe("com.android.launcher3.util.DynamicResource", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(dynResClass, "provider", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val provider = param.result ?: return
                    hookResourceProvider(provider.javaClass, param.args.firstOrNull() as? Context)
                }
            })
            Log.i(TAG, "DynamicResource.provider hooked successfully")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook DynamicResource.provider: ${t.message}")
        }
    }

    private fun hookResourceProvider(providerClass: Class<*>, context: Context?) {
        if (!hookedProviderClasses.add(providerClass)) return
        try {
            XposedBridge.hookAllMethods(providerClass, "getFloat", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val resId = param.args.firstOrNull() as? Int ?: return
                    try {
                        val ctx = context ?: return
                        val entryName = ctx.resources.getResourceEntryName(resId)
                        if (entryName.contains("stiffness", ignoreCase = true)) {
                            param.result = ConfigManager.config.stiffness
                        } else if (entryName.contains("damping", ignoreCase = true)) {
                            param.result = ConfigManager.config.dampingRatio
                        }
                    } catch (t: Throwable) {
                        // Non-fatal
                    }
                }
            })
            Log.i(TAG, "ResourceProvider.getFloat hooked on ${providerClass.name}")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook ResourceProvider: ${t.message}")
        }
    }

    /**
     * Точный радиус скругления дисплея Poco X6 Pro (~38dp / ~105px).
     * Поддерживает как вызовы с Context, так и с Resources.
     */
    private fun hookQuickStepContract(classLoader: ClassLoader) {
        val quickStepContractClass = findClassSafe("com.android.systemui.shared.system.QuickStepContract", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(quickStepContractClass, "getWindowCornerRadius", object : XC_MethodHook() {
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
            Log.w(TAG, "Failed to hook QuickStepContract.getWindowCornerRadius: ${t.message}")
        }
    }

    /**
     * Подмена кривых открытия приложения в QuickstepTransitionManager.
     */
    private fun setOpeningInterpolators(qtmInstance: Any) {
        try {
            XposedHelpers.setObjectField(qtmInstance, "mOpeningInterpolator", FluidCurves.HYPEROS_OPEN)
            XposedHelpers.setObjectField(qtmInstance, "mOpeningXInterpolator", FluidCurves.HYPEROS_OPEN_X)
        } catch (t: Throwable) {
            // Field names might differ in custom ROMs
        }
    }

    /**
     * Установка длительности анимации открытия окна.
     * ValueAnimator ОБЯЗАТЕЛЬНО остается LinearInterpolator() во избежание двойной интерполяции.
     */
    private fun applyOpenWindowDuration(obj: Any, duration: Long) {
        when (obj) {
            is ValueAnimator -> {
                obj.duration = duration
                obj.interpolator = LinearInterpolator()
            }
            is AnimatorSet -> {
                obj.duration = duration
                for (child in obj.childAnimations) {
                    applyOpenWindowDuration(child, duration)
                }
            }
            else -> {
                try {
                    val second = XposedHelpers.getObjectField(obj, "second")
                    if (second != null) {
                        applyOpenWindowDuration(second, duration)
                    }
                } catch (t: Throwable) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Синхронизация времени скрытия содержимого рабочего стола с длительностью открытия окна.
     */
    private fun synchronizeLauncherContentAnimator(animSet: AnimatorSet, duration: Long) {
        try {
            for (child in animSet.childAnimations) {
                if (child is AnimatorSet) {
                    child.duration = duration
                    for (subChild in child.childAnimations) {
                        subChild.duration = duration
                    }
                } else if (child is Animator) {
                    child.duration = duration
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to synchronize launcher content animator: ${t.message}")
        }
    }

    /**
     * Применение физических параметров пружины к RectFSpringAnim.
     */
    private fun applySpringPhysicsToRectFAnim(anim: Any) {
        try {
            val stiffness = ConfigManager.config.stiffness
            val damping = ConfigManager.config.dampingRatio
            XposedHelpers.setFloatField(anim, "mStiffnessX", stiffness)
            XposedHelpers.setFloatField(anim, "mStiffnessY", stiffness)
            XposedHelpers.setFloatField(anim, "mDampingX", damping)
            XposedHelpers.setFloatField(anim, "mDampingY", damping)
            XposedHelpers.setFloatField(anim, "mRectStiffness", stiffness)
        } catch (t: Throwable) {
            // Non-fatal
        }
    }

    /**
     * Присоединение слушателя Landing Bounce к анимации закрытия с известной View иконки.
     */
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
            Log.w(TAG, "Failed to attach landing bounce listener: ${t.message}")
        }
    }

    /**
     * Присоединение слушателя Landing Bounce к RectFSpringAnim.
     * Захватывает ссылку на mOriginalIcon СРАЗУ, до того как FloatingIconView.recycle() обнулит mOriginalIcon!
     */
    private fun attachLandingBounceFromSpringAnim(anim: Any) {
        try {
            val initialListeners = runCatching {
                XposedHelpers.getObjectField(anim, "mAnimatorListeners") as? List<*>
            }.getOrNull()
            val initialFiv = initialListeners?.firstOrNull { it?.javaClass?.name?.contains("FloatingIconView") == true }
            val capturedIcon = initialFiv?.let {
                runCatching { XposedHelpers.getObjectField(it, "mOriginalIcon") as? View }.getOrNull()
            }

            val listener = object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        val icon = capturedIcon ?: run {
                            val listeners = XposedHelpers.getObjectField(anim, "mAnimatorListeners") as? List<*>
                            val fiv = listeners?.firstOrNull { it?.javaClass?.name?.contains("FloatingIconView") == true }
                            fiv?.let { XposedHelpers.getObjectField(it, "mOriginalIcon") as? View }
                        }
                        if (icon != null) {
                            runLandingBounce(icon)
                        }
                    } catch (t: Throwable) {
                        // Non-fatal
                    }
                }
            }
            XposedHelpers.callMethod(anim, "addAnimatorListener", listener)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to attach spring anim landing bounce listener: ${t.message}")
        }
    }

    /**
     * Отмена текущего отскока для предотвращения конфликтов при быстром прерывании жестов.
     */
    fun cancelLandingBounce(iconView: View) {
        activeBounceAnimators.remove(iconView)?.let { (animX, animY) ->
            try {
                animX.cancel()
                animY.cancel()
                iconView.setLayerType(View.LAYER_TYPE_NONE, null)
                iconView.scaleX = 1.0f
                iconView.scaleY = 1.0f
            } catch (ignored: Throwable) {
            }
        }
    }

    /**
     * Аппаратный (Hardware-Layer) отскок иконки (Landing Bounce) через нативный SpringAnimation.
     * Без postDelayed: срабатывает строго по завершении анимации возврата на рабочий стол.
     */
    private fun runLandingBounce(iconView: View) {
        iconView.post {
            if (!iconView.isAttachedToWindow) return@post
            try {
                cancelLandingBounce(iconView)

                iconView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                iconView.pivotX = iconView.width / 2f
                iconView.pivotY = iconView.height / 2f
                iconView.scaleX = 0.91f
                iconView.scaleY = 0.91f

                val springForce = SpringForce(1.0f).apply {
                    stiffness = 600f
                    dampingRatio = 0.62f
                }

                val animX = SpringAnimation(iconView, DynamicAnimation.SCALE_X).apply {
                    spring = springForce
                    setStartValue(0.91f)
                }

                val animY = SpringAnimation(iconView, DynamicAnimation.SCALE_Y).apply {
                    spring = springForce
                    setStartValue(0.91f)
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
                Log.w(TAG, "Landing bounce spring animation error: ${t.message}")
                try {
                    activeBounceAnimators.remove(iconView)
                    iconView.setLayerType(View.LAYER_TYPE_NONE, null)
                    iconView.scaleX = 1.0f
                    iconView.scaleY = 1.0f
                } catch (ignored: Throwable) {
                }
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
