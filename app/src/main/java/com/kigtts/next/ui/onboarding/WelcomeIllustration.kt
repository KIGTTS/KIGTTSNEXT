package com.kigtts.next.ui.onboarding

import android.content.Context
import android.graphics.Path
import android.graphics.Picture
import android.util.Log
import androidx.core.graphics.PathParser
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val WELCOME_ART_WIDTH = 1576
internal const val WELCOME_ART_HEIGHT = 1200
internal const val WELCOME_ART_ASPECT = WELCOME_ART_WIDTH.toFloat() / WELCOME_ART_HEIGHT
internal const val WELCOME_ORIGIN_Y = 24f

internal enum class WelcomePart { BODY, HEAD, LEFT_HAIR, RIGHT_HAIR, ARM, LEFT_EAR, RIGHT_EAR, ZIPPER }
internal data class WelcomeLayer(val part: WelcomePart, val picture: Picture, val mesh: WelcomeMesh? = null)
internal data class WelcomeEye(val white: Picture, val iris: Picture, val lash: Picture, val squeezed: Picture,
    val aperture: Path, val x: Float, val y: Float)
internal data class WelcomeDrawing(val still: Picture, val layers: List<WelcomeLayer> = emptyList(),
    val eyes: List<WelcomeEye> = emptyList())

/** Parse once, publish the neutral pose first, then record the independently moving vector layers. */
internal object WelcomeIllustration {
    private val started = AtomicBoolean()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val drawing = MutableStateFlow<WelcomeDrawing?>(null)
    val drawings = drawing.asStateFlow()

    fun prepare(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val assets = context.applicationContext.assets
        scope.launch {
            try {
                val source = assets.open("onboarding/welcome.svg").bufferedReader().use { it.readText() }
                val svg = SVG.getFromString(source)
                val still = svg.renderToPicture(WELCOME_ART_WIDTH, WELCOME_ART_HEIGHT)
                drawing.value = WelcomeDrawing(still)
                drawing.value = recordWelcomeLayers(source, svg, still)
            } catch (error: Exception) {
                Log.w("OnboardingWelcome", "Cannot read the bundled vector illustration", error)
            }
        }
    }
}

private val layerPlan = listOf(
    WelcomePart.LEFT_HAIR to "rear-left",
    WelcomePart.RIGHT_HAIR to "rear-right",
    WelcomePart.LEFT_EAR to "bunny-ear-left",
    WelcomePart.HEAD to "hood",
    WelcomePart.RIGHT_EAR to "bunny-ear-right",
    WelcomePart.HEAD to "hood-clasp left-nape nape face left-cheek-cast right-cheek-cast lid-folds hood-facing ear-strand right-human-ear locks-behind-sleeve",
    WelcomePart.BODY to "coat",
    WelcomePart.ARM to "pointing-arm",
    WelcomePart.BODY to "collar-stand",
    WelcomePart.HEAD to "right-lock",
    WelcomePart.BODY to "lapel-shade",
    WelcomePart.ZIPPER to "zipper-assembly",
    WelcomePart.BODY to "zipper-assembly coat-lapel",
    WelcomePart.HEAD to "fringe-cast bang-right bang-middle bang-left left-lock brows",
)

internal fun recordWelcomeLayers(source: String, svg: SVG, still: Picture): WelcomeDrawing {
    val groups = (layerPlan.flatMap { it.second.split(' ') } + "eyes").distinct()
    fun layer(selected: String, extra: String = ""): Picture {
        val visible = selected.split(' ').toSet()
        val hide = groups.filterNot(visible::contains).joinToString("") { "#$it{display:none}" }
        return svg.renderToPicture(WELCOME_ART_WIDTH, WELCOME_ART_HEIGHT, RenderOptions().css(hide + extra))
    }

    val layers = recordWelcomeMeshes(layerPlan.map { (part, selected) ->
        val extra = when {
            part == WelcomePart.ZIPPER -> "#zipper-slider{display:none}"
            "zipper-assembly" in selected -> "#zipper-pull{display:none}"
            else -> ""
        }
        WelcomeLayer(part, layer(selected, extra))
    })
    fun eye(side: String, x: Float, y: Float): WelcomeEye {
        val other = if (side == "left") "right" else "left"
        val prefix = "#eye-$other{display:none}"
        val normal = "eye-$side-normal"
        val iris = "eye-$side-iris"
        val contents = "eye-$side-contents"
        val lash = "#eye-$side-lash{display:none}#eye-$side-glint{display:none}"
        val pathTag = Regex("""<path\b[^>]*\bid="eye-$side-aperture"[^>]*>""").find(source)?.value.orEmpty()
        val path = requireNotNull(Regex("""\bd="([^"]+)"""").find(pathTag)?.groupValues?.get(1))
        val aperture = requireNotNull(PathParser.createPathFromPathData(path)).apply { offset(0f, WELCOME_ORIGIN_Y) }
        return WelcomeEye(
            layer("eyes", "$prefix#$iris{display:none}$lash"),
            layer("eyes", "$prefix#eye-$side-white{display:none}#eye-$side-shadow{display:none}$lash"),
            layer("eyes", "$prefix#$contents{display:none}"),
            layer("eyes", "$prefix#$normal{display:none}#eye-$side-squeeze{display:inline}"),
            aperture, x, y + WELCOME_ORIGIN_Y,
        )
    }
    return WelcomeDrawing(still, layers, listOf(eye("left", 897f, 412f), eye("right", 1078f, 380f)))
}
