# Keep LSPosed hook entry point
-keep class com.antigravity.fluidanimations.MainHook { *; }
-keepclassmembers class com.antigravity.fluidanimations.MainHook { *; }

# Keep physics & config classes
-keep class com.antigravity.fluidanimations.config.** { *; }
-keep class com.antigravity.fluidanimations.physics.** { *; }
-keep class com.antigravity.fluidanimations.hooks.** { *; }
