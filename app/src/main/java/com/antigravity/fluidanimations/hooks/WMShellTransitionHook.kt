package com.antigravity.fluidanimations.hooks

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.antigravity.fluidanimations.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Легковесный хук для SystemUI.
 * Обеспечивает синхронизацию конфигурации и адаптивное скругление углов без вмешательства
 * в нативные SurfaceControl транзакции системы.
 */
object WMShellTransitionHook {
    private const val TAG = "FluidAnim_WMShell"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        hookScreenDecorationsUtils(classLoader)
    }

    /**
     * Точный радиус скругления дисплея Poco X6 Pro для переходов SystemUI.
     */
    private fun hookScreenDecorationsUtils(classLoader: ClassLoader) {
        val sduClass = findClassSafe("com.android.internal.policy.ScreenDecorationsUtils", classLoader)
            ?: findClassSafe("com.android.systemui.util.ScreenDecorationsUtils", classLoader)
            ?: return

        try {
            XposedBridge.hookAllMethods(sduClass, "getWindowCornerRadius", object : XC_MethodHook() {
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
            Log.i(TAG, "ScreenDecorationsUtils.getWindowCornerRadius hooked")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook ScreenDecorationsUtils: ${t.message}")
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
