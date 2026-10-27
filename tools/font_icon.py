"""Font-derived square SVG icons. All lettering is stored as outlines."""
import io
from fontTools.ttLib import TTFont
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen


def font_icon(data, text):
    with TTFont(io.BytesIO(data)) as font:
        glyphs = font.getGlyphSet(location={"wght": 400} if "fvar" in font else None)
        cmap = font.getBestCmap()
        bounds = BoundsPen(glyphs)
        paths, x = [], 0
        for char in text:
            if ord(char) not in cmap:
                raise ValueError(f"Icon glyph missing: {char}")
            glyph = glyphs[cmap[ord(char)]]
            glyph.draw(TransformPen(bounds, (1, 0, 0, 1, x, 0)))
            pen = SVGPathPen(glyphs)
            glyph.draw(TransformPen(pen, (1, 0, 0, 1, x, 0)))
            paths.append(f'<path d="{pen.getCommands()}"/>')
            x += glyph.width
        if bounds.bounds is None:
            raise ValueError("Empty font icon")
        left, bottom, right, top = bounds.bounds
        scale = min(92 / (right - left), 88 / (top - bottom))
        tx, ty = 64 - (left + right) * scale / 2, 64 + (bottom + top) * scale / 2
        return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128">'
                '<rect width="128" height="128" rx="24" fill="#E0EFF0"/>'
                f'<g fill="#145B60" transform="matrix({scale:.8f} 0 0 {-scale:.8f} {tx:.6f} {ty:.6f})">'
                + "".join(paths) + '</g></svg>\n')
