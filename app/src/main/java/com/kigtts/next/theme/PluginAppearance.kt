package com.kigtts.next.theme

import android.content.Context
import android.net.Uri
import com.kigtts.plugin.api.AppearanceBackground
import com.kigtts.plugin.api.AppearancePatch
import com.kigtts.plugin.api.KigPlugin
import com.kigtts.plugin.api.PluginResource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ResolvedAppearance(
    val patch: AppearancePatch,
    val background: String? = null,
    val backgroundSeed: Long? = null,
) {
    fun applyTo(base: ThemeSettings): ThemeSettings {
        val settings = if (patch.themeColor != null) base.withColorSource(ThemeColorSource.CUSTOM) else base
        return settings.copy(
            seed = patch.themeColor ?: settings.seed,
            mode = patch.mode?.let { ThemeMode.valueOf(it.name) } ?: settings.mode,
            background = if (patch.background == null) settings.background else background,
            backgroundSeed = if (patch.background == null) settings.backgroundSeed else backgroundSeed,
            backgroundDim = patch.backgroundDim ?: settings.backgroundDim,
            deeperDark = patch.pureBlackDark ?: settings.deeperDark,
            contrast = patch.contrast?.let { ContrastLevel.valueOf(it.name) } ?: settings.contrast,
        )
    }
}

internal data class PageAppearance(val plugin: KigPlugin, val page: Long, val appearance: ResolvedAppearance)
internal data class SavedAppearanceRequest(val plugin: KigPlugin, val appearance: ResolvedAppearance)

internal suspend fun resolveAppearance(context: Context, patch: AppearancePatch,
    resolve: (PluginResource) -> File?): ResolvedAppearance {
    val source = (patch.background as? AppearanceBackground.Image)?.source ?: return ResolvedAppearance(patch)
    return withContext(Dispatchers.IO) {
        val file = requireNotNull(resolve(source)) { "Appearance background is unavailable" }
        require(file.length() in 1..32L * 1024 * 1024) { "Appearance background size is outside limits" }
        val image = AppearanceAssets(context).importBackground(Uri.fromFile(file))
        val seed = AppearanceBackgrounds.load(context, image)?.let(::backgroundThemeSeed)
        ResolvedAppearance(patch, image, seed)
    }
}
