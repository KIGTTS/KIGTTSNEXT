# Separately built .kigplugin DEX files link against these host-provided APIs.
# R8 cannot discover references made by plugins that are installed later.
-keep class com.kigtts.plugin.api.** { *; }
-keep class com.kigtts.plugin.android.** { *; }

# Plugins intentionally do not bundle their own Kotlin/coroutine/serialization runtime.
# Preserve the runtime ABI as well as API classes; otherwise even a successfully
# loaded plugin can fail later with NoSuchMethodError while invoking a Flow or SDK.
# Method bodies may still be optimized, while names and callable members remain stable.
-keep,allowoptimization class kotlin.** { *; }
-keep,allowoptimization class kotlinx.coroutines.** { *; }
-keep,allowoptimization class kotlinx.serialization.** { *; }
