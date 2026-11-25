package com.kigtts.next.camera.expressions

import android.content.Context
import com.kigtts.next.i18n.uiCopy

/** KIGARFACE StickerArt shapes, expressed as editable SVG layers. */
internal object ExpressionPresets {
    private fun layer(path: String, anchor: ExpressionAnchor, scale: Float, x: Float = 0f, y: Float = 0f,
        rotate: Float = 0f, blur: Boolean = false, underFringe: Boolean = false) =
        ExpressionLayer("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="-1.25 -1.25 2.5 2.5">$path</svg>""",
            anchor, scale, x, y, rotate, hideUnderFringe = underFringe, blurBase = blur, pivotY = .5f)

    private val blush = """<ellipse rx="1" ry=".47" fill="#ff788e" fill-opacity=".67"/>
        <path d="M-.47 .13l.16-.27m.23 .27l.16-.27m.23 .27l.16-.27" stroke="#fff0f2" stroke-width=".09" stroke-linecap="round" fill="none"/>"""
    private val grin = """<path d="M-.87-.26H.87Q1.02-.26 .96-.10C.83 .24 .53 .42 0 .42S-.83 .24-.96-.10Q-1.02-.26-.87-.26Z" fill="#fffdf8" stroke="#35262d" stroke-width=".066"/>
        <path d="M-.55-.23v.49m.275-.49v.59m.275-.59v.63m.275-.63v.59m.275-.59v.49" stroke="#816d75" stroke-width=".04"/>"""
    private val pupils = """<circle r=".95" fill="#fffefc" stroke="#201923" stroke-width=".071"/><circle cx=".36" cy="-.23" r=".34" fill="#161318"/>"""
    private val moustache = """<path d="M0-.12C.38-.62 .65 .48 1.13-.23C1.08 .46 .54 .61 0 .22C-.54 .61-1.08 .46-1.13-.23C-.65 .48-.38-.62 0-.12Z"
        fill="#332323" stroke="#d1956b" stroke-width=".045"/>"""
    private val anger = """<path d="M-.76-.30Q-.27-.24-.29-.76M.76-.30Q.27-.24 .29-.76M-.76 .30Q-.27 .24-.29 .76M.76 .30Q.27 .24 .29 .76"
        stroke="#471728" stroke-width=".30" stroke-linecap="round" fill="none"/>
        <path d="M-.76-.30Q-.27-.24-.29-.76M.76-.30Q.27-.24 .29-.76M-.76 .30Q-.27 .24-.29 .76M.76 .30Q.27 .24 .29 .76"
        stroke="#ff475b" stroke-width=".19" stroke-linecap="round" fill="none"/>"""
    private val drop = """<defs><linearGradient id="drop" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop stop-color="#56c9fd"/><stop offset="1" stop-color="#075792"/></linearGradient></defs>
        <path d="M-.09-1.18C-.25-.82-.66-.13-.65 .30S-.42 .99-.02 1 .66 .73 .65 .31C.61-.12 .20-.90 .02-1.18Z"
        fill="url(#drop)" stroke="#135985" stroke-width=".073"/>
        <path d="M-.32-.42Q-.49-.08-.45 .18" fill="none" stroke="#d3f9ff" stroke-width=".10" stroke-linecap="round"/>"""
    private val glasses = """<g fill="none" stroke="#161a27" stroke-width=".10">
        <circle cx="-.65" r=".49"/><circle cx=".65" r=".49"/><path d="M-.17 0Q0-.18 .17 0"/></g>"""
    private val flare = """<defs><radialGradient id="flare"><stop stop-color="#fff4d5"/><stop offset=".36" stop-color="#ff3a22"/>
        <stop offset="1" stop-color="#ff150e" stop-opacity="0"/></radialGradient></defs>
        <ellipse rx="1.23" ry=".22" fill="url(#flare)"/><ellipse rx=".52" ry=".57" fill="url(#flare)"/>"""
    private val presets = linkedMapOf(
        "blush" to listOf(layer(blush, ExpressionAnchor.EYES, .46f, -.23f, .18f), layer(blush, ExpressionAnchor.EYES, .46f, .23f, .18f)),
        "glasses" to listOf(layer(glasses, ExpressionAnchor.EYES, .97f, underFringe = true)),
        "giggle" to listOf(layer(grin, ExpressionAnchor.MOUTH, .92f, y = .045f)),
        "not_giggle" to listOf(layer(grin, ExpressionAnchor.MOUTH, .92f, y = .045f, rotate = 180f)),
        "wise" to listOf(layer(pupils, ExpressionAnchor.LEFT_EYE, 1.18f, underFringe = true),
            layer(pupils, ExpressionAnchor.RIGHT_EYE, 1.18f, rotate = 180f, underFringe = true)),
        "moustache" to listOf(layer(moustache, ExpressionAnchor.MOUTH, 1f, y = -.09f)),
        "anger" to listOf(layer(anger, ExpressionAnchor.FOREHEAD, .42f, -.29f, -.085f)),
        "hunt" to listOf(layer(flare, ExpressionAnchor.LEFT_EYE, 2.4f, underFringe = true)),
        "sweat" to listOf(layer(drop, ExpressionAnchor.FOREHEAD, .32f, .28f, -.025f)),
        "erase" to listOf(layer("""<circle r="1" fill="none"/>""", ExpressionAnchor.FACE, 1f,
            blur = true, underFringe = true)),
    )

    val names get() = presets.keys

    fun read(context: Context, name: String): ExpressionEffect? = presets[name]?.let {
        ExpressionEffect("builtin.$name", context.uiCopy("expression.preset.$name"), it)
    }
}
