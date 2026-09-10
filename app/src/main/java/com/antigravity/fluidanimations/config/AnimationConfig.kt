package com.antigravity.fluidanimations.config

import org.json.JSONObject

data class AnimationConfig(
    val presetName: String = PRESET_IOS_FLUID,
    val stiffness: Float = 340f,
    val dampingRatio: Float = 0.84f,
    val durationMs: Long = 380L,
    val appToAppDurationMs: Long = 320L,
    val cornerRadiusMorph: Boolean = true,
    val landingBounce: Boolean = true,
    val wallpaperDepthZoom: Boolean = true,
    val displayCornerRadiusDp: Float = 38f,
    val appScaleDownRatio: Float = 0.93f
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("presetName", presetName)
        obj.put("stiffness", stiffness.toDouble())
        obj.put("dampingRatio", dampingRatio.toDouble())
        obj.put("durationMs", durationMs)
        obj.put("appToAppDurationMs", appToAppDurationMs)
        obj.put("cornerRadiusMorph", cornerRadiusMorph)
        obj.put("landingBounce", landingBounce)
        obj.put("wallpaperDepthZoom", wallpaperDepthZoom)
        obj.put("displayCornerRadiusDp", displayCornerRadiusDp.toDouble())
        obj.put("appScaleDownRatio", appScaleDownRatio.toDouble())
        return obj.toString()
    }

    companion object {
        const val PRESET_IOS_FLUID = "iOS Fluid (Default)"
        const val PRESET_IOS_BOUNCY = "iOS Bouncy"
        const val PRESET_SNAPPY_120HZ = "Snappy 120Hz"
        const val PRESET_CUSTOM = "Custom"

        val DEFAULT = AnimationConfig()

        val PRESET_MAP = mapOf(
            PRESET_IOS_FLUID to AnimationConfig(
                presetName = PRESET_IOS_FLUID,
                stiffness = 340f,
                dampingRatio = 0.84f,
                durationMs = 380L,
                appToAppDurationMs = 320L,
                cornerRadiusMorph = true,
                landingBounce = true,
                wallpaperDepthZoom = true,
                displayCornerRadiusDp = 38f,
                appScaleDownRatio = 0.93f
            ),
            PRESET_IOS_BOUNCY to AnimationConfig(
                presetName = PRESET_IOS_BOUNCY,
                stiffness = 290f,
                dampingRatio = 0.78f,
                durationMs = 420L,
                appToAppDurationMs = 340L,
                cornerRadiusMorph = true,
                landingBounce = true,
                wallpaperDepthZoom = true,
                displayCornerRadiusDp = 38f,
                appScaleDownRatio = 0.91f
            ),
            PRESET_SNAPPY_120HZ to AnimationConfig(
                presetName = PRESET_SNAPPY_120HZ,
                stiffness = 450f,
                dampingRatio = 0.88f,
                durationMs = 280L,
                appToAppDurationMs = 260L,
                cornerRadiusMorph = true,
                landingBounce = true,
                wallpaperDepthZoom = true,
                displayCornerRadiusDp = 38f,
                appScaleDownRatio = 0.94f
            )
        )

        fun fromJson(jsonStr: String?): AnimationConfig {
            if (jsonStr.isNullOrBlank()) return DEFAULT
            return try {
                val obj = JSONObject(jsonStr)
                AnimationConfig(
                    presetName = obj.optString("presetName", PRESET_IOS_FLUID),
                    stiffness = obj.optDouble("stiffness", 340.0).toFloat(),
                    dampingRatio = obj.optDouble("dampingRatio", 0.84).toFloat(),
                    durationMs = obj.optLong("durationMs", 380L),
                    appToAppDurationMs = obj.optLong("appToAppDurationMs", 320L),
                    cornerRadiusMorph = obj.optBoolean("cornerRadiusMorph", true),
                    landingBounce = obj.optBoolean("landingBounce", true),
                    wallpaperDepthZoom = obj.optBoolean("wallpaperDepthZoom", true),
                    displayCornerRadiusDp = obj.optDouble("displayCornerRadiusDp", 38.0).toFloat(),
                    appScaleDownRatio = obj.optDouble("appScaleDownRatio", 0.93).toFloat()
                )
            } catch (e: Exception) {
                DEFAULT
            }
        }
    }
}
