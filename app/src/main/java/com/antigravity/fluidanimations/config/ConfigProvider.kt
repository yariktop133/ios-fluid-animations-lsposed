package com.antigravity.fluidanimations.config

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Межпроцессный ContentProvider для безопасной и мгновенной передачи настроек анимации
 * в системные процессы (Trebuchet Launcher и SystemUI).
 */
class ConfigProvider : ContentProvider() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val response = Bundle()
        when (method) {
            METHOD_GET_CONFIG -> {
                val json = prefs.getString(KEY_CONFIG_JSON, null)
                response.putString(KEY_CONFIG_JSON, json ?: AnimationConfig.DEFAULT.toJson())
            }
            METHOD_SET_CONFIG -> {
                val json = extras?.getString(KEY_CONFIG_JSON)
                if (json != null) {
                    prefs.edit().putString(KEY_CONFIG_JSON, json).apply()
                    context?.contentResolver?.notifyChange(CONTENT_URI, null)
                }
                response.putBoolean("success", true)
            }
        }
        return response
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.antigravity.fluidanimations.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/config")

        const val PREFS_NAME = "fluid_animation_settings"
        const val KEY_CONFIG_JSON = "config_json"

        const val METHOD_GET_CONFIG = "getConfig"
        const val METHOD_SET_CONFIG = "setConfig"
    }
}
