package com.antigravity.fluidanimations.config

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.antigravity.fluidanimations.physics.SpringInterpolator

/**
 * Менеджер конфигурации с мгновенным кэшированием в памяти.
 * Гарантирует ZERO-ALLOCATION и ZERO-IPC в циклах анимации (120 Гц).
 */
object ConfigManager {
    private const val TAG = "FluidAnim_Config"

    @Volatile
    var config: AnimationConfig = AnimationConfig.DEFAULT
        private set

    @Volatile
    var openSpringInterpolator: SpringInterpolator = SpringInterpolator(
        stiffness = config.stiffness,
        dampingRatio = config.dampingRatio,
        durationMs = config.openDurationMs
    )
        private set

    @Volatile
    var closeSpringInterpolator: SpringInterpolator = SpringInterpolator(
        stiffness = config.stiffness,
        dampingRatio = config.dampingRatio,
        durationMs = config.closeDurationMs
    )
        private set

    // Обратная совместимость для общего интерполятора
    val springInterpolator: SpringInterpolator
        get() = openSpringInterpolator

    @Volatile
    private var isObserverRegistered = false

    /**
     * Инициализация в хукнутом процессе (Trebuchet / SystemUI).
     */
    fun initInHook(context: Context) {
        loadFromProvider(context)
        registerObserver(context)
    }

    /**
     * Чтение конфигурации из ContentProvider.
     */
    fun loadFromProvider(context: Context) {
        try {
            val resolver = context.contentResolver
            val uri = Uri.parse("content://${ConfigProvider.AUTHORITY}")
            val bundle = resolver.call(uri, ConfigProvider.METHOD_GET_CONFIG, null, null)
            val json = bundle?.getString(ConfigProvider.KEY_CONFIG_JSON)
            if (!json.isNullOrBlank()) {
                updateConfigInMemory(AnimationConfig.fromJson(json))
                Log.d(TAG, "Config loaded successfully: ${config.presetName}, open=${config.openDurationMs}ms, close=${config.closeDurationMs}ms")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load config from provider, using defaults: ${e.message}")
        }
    }

    /**
     * Сохранение новой конфигурации (вызывается из UI настроек).
     */
    fun saveConfig(context: Context, newConfig: AnimationConfig) {
        updateConfigInMemory(newConfig)
        try {
            val resolver = context.contentResolver
            val uri = Uri.parse("content://${ConfigProvider.AUTHORITY}")
            val bundle = Bundle().apply {
                putString(ConfigProvider.KEY_CONFIG_JSON, newConfig.toJson())
            }
            resolver.call(uri, ConfigProvider.METHOD_SET_CONFIG, null, bundle)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save config to provider: ${e.message}")
        }
    }

    private fun updateConfigInMemory(newConfig: AnimationConfig) {
        config = newConfig
        openSpringInterpolator = SpringInterpolator(
            stiffness = newConfig.stiffness,
            dampingRatio = newConfig.dampingRatio,
            durationMs = newConfig.openDurationMs
        )
        closeSpringInterpolator = SpringInterpolator(
            stiffness = newConfig.stiffness,
            dampingRatio = newConfig.dampingRatio,
            durationMs = newConfig.closeDurationMs
        )
    }

    private fun registerObserver(context: Context) {
        if (isObserverRegistered) return
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    Log.d(TAG, "Config change detected via ContentObserver, reloading...")
                    loadFromProvider(context)
                }
            }
            context.contentResolver.registerContentObserver(
                ConfigProvider.CONTENT_URI,
                true,
                observer
            )
            isObserverRegistered = true
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to register content observer: ${e.message}")
        }
    }
}
