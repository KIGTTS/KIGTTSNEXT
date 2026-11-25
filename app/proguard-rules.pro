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


# Media3 carries nullable API 31 identifiers across its transformer and decoder boundaries.
# Keep those boundaries so Release compilation cannot add unconditional casts on API 26–30.
-keep,allowobfuscation,allowshrinking class androidx.media3.transformer.** { *; }
-keep,allowobfuscation,allowshrinking class androidx.media3.exoplayer.analytics.PlayerId** { *; }
-keep,allowobfuscation,allowshrinking class androidx.media3.exoplayer.mediacodec.MediaCodecRenderer** { *; }
-keep,allowobfuscation,allowshrinking class androidx.media3.exoplayer.audio.AudioTrackAudioOutput { *; }
