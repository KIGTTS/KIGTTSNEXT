"""Prepare the standalone KIGTTS font catalog, outline SVG previews and original OFL notices."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import requests
from urllib.parse import quote
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

BASE = "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Fonts/resolve/master/"
FONTS = [
    ("noto-sans-sc", "Noto Sans 简体中文", "notosanssc", "NotoSansSC[wght].ttf"),
    ("ma-shan-zheng", "马善政毛笔楷书", "mashanzheng", "MaShanZheng-Regular.ttf"),
]

def digest(data):
    return hashlib.sha256(data).hexdigest()

def outline(data):
    font = TTFont(io.BytesIO(data))
    glyphs = font.getGlyphSet(location={"wght": 400} if "fvar" in font else None)
    cmap = font.getBestCmap()
    scale = 100 / font["head"].unitsPerEm
    paths = []
    x = 16
    for char in "你好，KIGTTS":
        name = cmap.get(ord(char), ".notdef")
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
    r = session.get("https://api.github.com/repos/google/fonts/commits/main", timeout=30); r.raise_for_status()
    commit = r.json()["sha"]
    entries = []
    for key, name, folder, filename in FONTS:
        relative = f"fonts/{key}/{filename}"
        target = out / relative; target.parent.mkdir(parents=True, exist_ok=True)
        upstream = f"https://raw.githubusercontent.com/google/fonts/{commit}/ofl/{folder}/"
        r = session.get(upstream + filename, timeout=(20, 120)); r.raise_for_status(); data = r.content
        target.write_bytes(data)
        r = session.get(upstream + "OFL.txt", timeout=30); r.raise_for_status()
        target.with_name("OFL.txt").write_bytes(r.content)
        svg = outline(data); preview = f"previews/{key}.svg"
        (out / "previews").mkdir(exist_ok=True); (out / preview).write_bytes(svg)
        entries.append(dict(id=key, name=name, url=BASE + quote(relative), sha256=digest(data), bytes=len(data),
                            preview=BASE + preview, previewSha256=digest(svg), license="SIL OFL 1.1",
                            licenseUrl=BASE + f"fonts/{key}/OFL.txt", upstream=upstream + filename))
    (out / "index.json").write_text(json.dumps(dict(schemaVersion=1, name="KIGTTS 字体库", fonts=entries), ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    (out / "README.md").write_text("---\nlicense: other\n---\n# KIGTTS 字体库\n\n为 KIGTTS 提供字体与 SVG 字形预览。字体遵循各目录中的 SIL Open Font License 1.1；原始字体文件保持不变。\n\n"
        +"\n".join(f"- {e['name']}：[{e['license']}]({e['licenseUrl']})" for e in entries)+"\n", encoding="utf-8")
    print(json.dumps(dict(fonts=len(entries), upstreamCommit=commit, output=str(out)), ensure_ascii=False))

if __name__ == "__main__":
    main()
