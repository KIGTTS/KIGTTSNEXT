"""Prepare the standalone KIGTTS font catalog, outline SVG previews and original OFL notices."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import requests
from urllib.parse import quote, unquote
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

BASE = "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Fonts/resolve/master/"
LOCK = Path(__file__).resolve().parent.parent / "distribution/font-sources.json"

def digest(data):
    return hashlib.sha256(data).hexdigest()

def outline(data, text="你好，KIGTTS"):
    font = TTFont(io.BytesIO(data))
    glyphs = font.getGlyphSet(location={"wght": 400} if "fvar" in font else None)
    cmap = font.getBestCmap()
    scale = 100 / font["head"].unitsPerEm
    paths = []
    x = 16
    for char in text:
        if ord(char) not in cmap: raise ValueError(f"Preview glyph missing: {char}")
        name = cmap[ord(char)]
        pen = SVGPathPen(glyphs)
        glyphs[name].draw(TransformPen(pen, (scale, 0, 0, -scale, x, 118)))
        paths.append(f'<path d="{pen.getCommands()}"/>')
        x += glyphs[name].width * scale
    font.close()
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {max(x+16, 880):.2f} 160"><g fill="#ffffff">'
            + "".join(paths) + "</g></svg>\n").encode()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default=".tools/font-source")
    args = parser.parse_args()
    out = Path(args.output); out.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    previous = {e["id"]: e for e in json.loads((out / "index.json").read_text(encoding="utf-8"))["fonts"]} if (out / "index.json").exists() else {}
    entries = []
    for locked in json.loads(LOCK.read_text(encoding="utf-8"))["fonts"]:
        key, name = locked["id"], locked["name"]
        url = locked["upstream"]
        if not url.startswith("https://raw.githubusercontent.com/google/fonts/"): raise ValueError("Unexpected upstream")
        upstream, filename = url.rsplit("/", 1)
        upstream += "/"
        # Published 1.0.0 packages are immutable. Keep verified originals pinned to their upstream revision.
        if key in previous:
            entry = previous[key]
            if entry["sha256"] != locked["sha256"] or entry["upstream"] != url: raise ValueError("Published font differs from lock")
            for field, hash_field in (("url", "sha256"), ("preview", "previewSha256")):
                if not entry[field].startswith(BASE): raise ValueError("Unexpected cached URL")
                cached = (out / unquote(entry[field][len(BASE):])).resolve()
                if not cached.is_relative_to(out.resolve()) or digest(cached.read_bytes()) != entry[hash_field]:
                    raise ValueError("Cached font asset mismatch")
            if digest((out / f"fonts/{key}/OFL.txt").read_bytes()) != locked["licenseSha256"]: raise ValueError("Font license mismatch")
            entries.append(entry)
            continue
        relative = f"fonts/{key}/{filename}"
        target = out / relative; target.parent.mkdir(parents=True, exist_ok=True)
        r = session.get(url, timeout=(20, 120)); r.raise_for_status(); data = r.content
        if digest(data) != locked["sha256"]: raise ValueError("Upstream font mismatch")
        target.write_bytes(data)
        r = session.get(upstream + "OFL.txt", timeout=30); r.raise_for_status()
        if "SIL OPEN FONT LICENSE Version 1.1" not in r.text: raise ValueError("Expected OFL 1.1 license")
        if digest(r.content) != locked["licenseSha256"]: raise ValueError("Upstream font license mismatch")
        target.with_name("OFL.txt").write_bytes(r.content)
        text = locked["previewText"]
        svg = outline(data, text); preview = f"previews/{key}.svg"
        (out / "previews").mkdir(exist_ok=True); (out / preview).write_bytes(svg)
        entries.append(dict(id=key, name=name, url=BASE + quote(relative), sha256=digest(data), bytes=len(data),
                            preview=BASE + preview, previewSha256=digest(svg), license="SIL OFL 1.1",
                            licenseUrl=BASE + f"fonts/{key}/OFL.txt", upstream=upstream + filename, previewText=text))
    for entry in entries:
        with TTFont(out / unquote(entry["url"][len(BASE):])) as font:
            entry["axes"] = [dict(tag=a.axisTag, min=a.minValue, default=a.defaultValue, max=a.maxValue) for a in font["fvar"].axes] if "fvar" in font else []
    (out / "index.json").write_text(json.dumps(dict(schemaVersion=1, name="KIGTTS 字体库", fonts=entries), ensure_ascii=False, indent=2)+"\n", encoding="utf-8", newline="\n")
    (out / "README.md").write_text("---\nlicense: other\n---\n# KIGTTS 字体库\n\n为 KIGTTS 提供字体与 SVG 字形预览。字体遵循各目录中的 SIL Open Font License 1.1；原始字体文件保持不变。\n\n"
        +"\n".join(f"- {e['name']}：[{e['license']}]({e['licenseUrl']})" for e in entries)+"\n", encoding="utf-8", newline="\n")
    print(json.dumps(dict(fonts=len(entries), sourceLock=str(LOCK), output=str(out)), ensure_ascii=False))

if __name__ == "__main__":
    main()
