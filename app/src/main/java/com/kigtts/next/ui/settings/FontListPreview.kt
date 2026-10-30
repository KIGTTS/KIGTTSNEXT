package com.kigtts.next.ui.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import com.kigtts.next.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class FontListPreview(val face: AppearanceFont = AppearanceFont(), val axis: FontWeightAxis? = null)

/** Load and prepare a row's actual font before fading it in; damaged fonts remain removable. */
@Composable internal fun rememberFontListPreview(key: String, weight: Int): FontListPreview? {
    val context = LocalContext.current
    val resolver = LocalFontFamilyResolver.current
    val preview by produceState<FontListPreview?>(null, key, weight, resolver) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                AppearanceAssets(context).file(key)?.let { file ->
                    val face = loadAppearanceFont(file, weight)
                    face.family?.let { resolver.preload(it) }
                    FontListPreview(face, readFontWeightAxis(file))
                } ?: FontListPreview()
            }.getOrDefault(FontListPreview())
        }
    }
    return preview
}
