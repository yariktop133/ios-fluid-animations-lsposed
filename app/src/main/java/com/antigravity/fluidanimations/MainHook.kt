package com.antigravity.fluidanimations

import android.util.Log
import com.antigravity.fluidanimations.hooks.SystemUiNotificationHook
import com.antigravity.fluidanimations.hooks.TrebuchetLauncherHook
import com.antigravity.fluidanimations.hooks.WMShellTransitionHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        Log.i(TAG, "FluidAnimations initZygote started")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName

        // Модуль активен: сообщаем в собственный UI
        if (packageName == MODULE_PACKAGE) {
            hookSelfModule(lpparam)
            return
        }

        // Лаунчер Trebuchet (LineageOS Launcher3 fork)
        if (packageName == PACKAGE_TREBUCHET || packageName == PACKAGE_LAUNCHER3) {
            Log.i(TAG, "Hooking Launcher: $packageName")
            TrebuchetLauncherHook.hook(lpparam)
            return
        }

        // SystemUI (WMShell transitions & notifications)
        if (packageName == PACKAGE_SYSTEMUI) {
            Log.i(TAG, "Hooking SystemUI: $packageName")
            WMShellTransitionHook.hook(lpparam)
            SystemUiNotificationHook.hook(lpparam)
            return
        }
    }

    private fun hookSelfModule(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "$MODULE_PACKAGE.ui.MainActivity",
                lpparam.classLoader,
                "isModuleActive",
                XC_MethodReplacement.returnConstant(true)
            )
            Log.i(TAG, "Self module status hook installed successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook self module: ${t.message}")
        }
    }

    companion object {
        const val TAG = "FluidAnimations_Hook"
        const val MODULE_PACKAGE = "com.antigravity.fluidanimations"
        const val PACKAGE_TREBUCHET = "org.lineageos.trebuchet"
        const val PACKAGE_LAUNCHER3 = "com.android.launcher3"
        const val PACKAGE_SYSTEMUI = "com.android.systemui"
    }
}
