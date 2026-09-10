package com.antigravity.fluidanimations

import com.antigravity.fluidanimations.config.AnimationConfig
import com.antigravity.fluidanimations.physics.FluidCurves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidAnimationsTest {

    @Test
    fun testDefaultAnimationConfig() {
        val config = AnimationConfig.DEFAULT
        assertEquals(340f, config.stiffness, 0.001f)
        assertEquals(0.84f, config.dampingRatio, 0.001f)
        assertEquals(360L, config.openDurationMs)
        assertEquals(410L, config.closeDurationMs)
        assertEquals(320L, config.appToAppDurationMs)
        assertEquals(38f, config.displayCornerRadiusDp, 0.001f)
        assertTrue(config.cornerRadiusMorph)
        assertTrue(config.landingBounce)
        assertTrue(config.wallpaperDepthZoom)
    }

    @Test
    fun testJsonSerializationRoundtrip() {
        val original = AnimationConfig(
            presetName = AnimationConfig.PRESET_CUSTOM,
            stiffness = 350f,
            dampingRatio = 0.82f,
            openDurationMs = 340L,
            closeDurationMs = 390L,
            appToAppDurationMs = 300L,
            cornerRadiusMorph = true,
            landingBounce = true,
            wallpaperDepthZoom = false,
            displayCornerRadiusDp = 38f,
            appScaleDownRatio = 0.92f
        )
        val jsonStr = original.toJson()
        val parsed = AnimationConfig.fromJson(jsonStr)

        assertEquals(original.presetName, parsed.presetName)
        assertEquals(original.stiffness, parsed.stiffness, 0.001f)
        assertEquals(original.dampingRatio, parsed.dampingRatio, 0.001f)
        assertEquals(original.openDurationMs, parsed.openDurationMs)
        assertEquals(original.closeDurationMs, parsed.closeDurationMs)
        assertEquals(original.appToAppDurationMs, parsed.appToAppDurationMs)
        assertEquals(original.cornerRadiusMorph, parsed.cornerRadiusMorph)
        assertEquals(original.landingBounce, parsed.landingBounce)
        assertEquals(original.wallpaperDepthZoom, parsed.wallpaperDepthZoom)
        assertEquals(original.displayCornerRadiusDp, parsed.displayCornerRadiusDp, 0.001f)
        assertEquals(original.appScaleDownRatio, parsed.appScaleDownRatio, 0.001f)
    }

    @Test
    fun testLegacyJsonFallback() {
        val legacyJson = """{"durationMs": 350, "stiffness": 320.0, "dampingRatio": 0.8}"""
        val parsed = AnimationConfig.fromJson(legacyJson)

        assertEquals(350L, parsed.openDurationMs)
        assertEquals(390L, parsed.closeDurationMs)
        assertEquals(320f, parsed.stiffness, 0.001f)
        assertEquals(0.8f, parsed.dampingRatio, 0.001f)
    }

    @Test
    fun testNullAndEmptyJson() {
        val fromNull = AnimationConfig.fromJson(null)
        val fromBlank = AnimationConfig.fromJson("   ")

        assertEquals(AnimationConfig.DEFAULT, fromNull)
        assertEquals(AnimationConfig.DEFAULT, fromBlank)
    }

    @Test
    fun testPresetsMap() {
        val fluid = AnimationConfig.PRESET_MAP[AnimationConfig.PRESET_IOS_FLUID]
        assertNotNull(fluid)
        assertEquals(340f, fluid!!.stiffness, 0.001f)
        assertEquals(0.84f, fluid.dampingRatio, 0.001f)
        assertEquals(360L, fluid.openDurationMs)
        assertEquals(410L, fluid.closeDurationMs)

        val snappy = AnimationConfig.PRESET_MAP[AnimationConfig.PRESET_SNAPPY_120HZ]
        assertNotNull(snappy)
        assertEquals(450f, snappy!!.stiffness, 0.001f)
        assertEquals(0.88f, snappy.dampingRatio, 0.001f)
        assertEquals(260L, snappy.openDurationMs)
        assertEquals(290L, snappy.closeDurationMs)
    }

    @Test
    fun testFluidCurvesInitialization() {
        assertNotNull(FluidCurves.HYPEROS_OPEN)
        assertNotNull(FluidCurves.HYPEROS_OPEN_X)
        assertNotNull(FluidCurves.IOS_APP_TO_APP)
        assertNotNull(FluidCurves.IOS_EASE_IN_OUT)
        assertNotNull(FluidCurves.IOS_RAPID_DECEL)
        assertNotNull(FluidCurves.IOS_LANDING_BOUNCE)
    }
}
