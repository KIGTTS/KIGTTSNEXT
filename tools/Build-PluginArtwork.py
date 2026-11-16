"""Create editable floral SVG covers and an isolated native screenshot manifest.

Uses font outlines and Material Symbols; never generates or fabricates product UI.
"""
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen

ROOT = Path(__file__).resolve().parent.parent
NS = "http://www.w3.org/2000/svg"
ET.register_namespace("", NS)


def element(parent, tag, **attributes):
    return ET.SubElement(parent, f"{{{NS}}}{tag}", {key.replace('_', '-'): str(value) for key, value in attributes.items()})


class Lettering:
    def __init__(self, folder, hangul=None):
        self.fonts = [TTFont(folder / name) for name in ("google.ttf", "han.ttf")]
        if hangul:
            self.fonts.append(TTFont(hangul))
        self.maps = [font.getBestCmap() for font in self.fonts]
        self.glyphs = {weight: [font.getGlyphSet(location={"wght": weight}) for font in self.fonts]
                       for weight in (500, 700)}

    def text(self, parent, value, x, y, size, fill, max_width=None, weight=500):
        runs = []
        advance = 0
        for char in value:
            index = next((i for i, cmap in enumerate(self.maps) if ord(char) in cmap), None)
            if index is None:
                raise ValueError(f"No glyph for {char!r}")
            name = self.maps[index][ord(char)]
            glyphs = self.glyphs[weight][index]
            glyph = glyphs[name]
            scale = size / self.fonts[index]["head"].unitsPerEm
            pen = SVGPathPen(glyphs)
            glyph.draw(pen)
            runs.append((pen.getCommands(), advance, scale))
            advance += glyph.width * scale
        factor = min(1, max_width / advance) if max_width and advance else 1
        group = element(parent, "g", transform=f"translate({x} {y}) scale({factor})", fill=fill, aria_label=value)
        for path, offset, scale in runs:
            if path:
                element(group, "path", d=path, transform=f"translate({offset:.3f} 0) scale({scale:.6f} {-scale:.6f})")


def bloom(cx, cy, radius, petals=12, phase=0):
    # Smooth periodic cubic interpolation keeps each petal part of one continuous outline.
    count = petals * 16
    points = []
    for i in range(count):
        angle = math.tau * i / count + phase
        r = radius * (1 + .065 * math.cos(petals * (angle - phase)))
        points.append((cx + math.cos(angle) * r, cy + math.sin(angle) * r))
    commands = [f"M {points[0][0]:.3f} {points[0][1]:.3f}"]
    for i, start in enumerate(points):
        before, end, after = points[i - 1], points[(i + 1) % count], points[(i + 2) % count]
        a = (start[0] + (end[0] - before[0]) / 6, start[1] + (end[1] - before[1]) / 6)
        b = (end[0] - (after[0] - start[0]) / 6, end[1] - (after[1] - start[1]) / 6)
        commands.append(f"C {a[0]:.3f} {a[1]:.3f} {b[0]:.3f} {b[1]:.3f} {end[0]:.3f} {end[1]:.3f}")
    return " ".join(commands) + " Z"


def cover(package, design, lettering, symbols):
    colors = {"mint": "#CAEDE8", "sky": "#D4E7F7", "peach": "#F1DDCA", "lilac": "#E5DDF5", "lime": "#E0EBBC"}
    accent = colors[design["accent"]]
    svg = ET.Element(f"{{{NS}}}svg", {"viewBox": "0 0 900 1600", "role": "img"})
    element(svg, "title").text = package["name"]
    element(svg, "desc").text = design["tagline"]
    defs = element(svg, "defs")
    gradient = element(defs, "linearGradient", id="paper", x1="0", y1="0", x2="1", y2="1")
    element(gradient, "stop", offset="0", stop_color="#FBFEFD")
    element(gradient, "stop", offset="1", stop_color="#EFF9F8")
    flower = element(defs, "linearGradient", id="petals", x1="0", y1="0", x2="1", y2="1")
    element(flower, "stop", offset="0", stop_color="#D1F1F1")
    element(flower, "stop", offset="1", stop_color="#A9DDDE")
    element(svg, "rect", width=900, height=1600, fill="url(#paper)")
    element(svg, "path", d=bloom(865, 167, 180, 8, .12), fill=accent)
    element(svg, "path", d=bloom(166, 1251, 145, 8, .20), fill=accent)
    element(svg, "path", d=bloom(741, 1418, 72, 8, .10), fill="#C9E9E7")
    element(svg, "path", d=bloom(533, 1049, 274, 12, .13), fill="url(#petals)")
    element(svg, "path", d=bloom(533, 1049, 212, 12, .24), fill="#038387")
    element(svg, "path", d=bloom(219, 900, 34, 6, .3), fill="#83CDCE")
    icon = package["icon"]["symbol"]
    icon = {"robot_2": "smart_toy", "gesture": "gesture"}.get(icon, icon)
    source = ET.parse(symbols / (icon + ".svg")).getroot()
    nested = element(svg, "svg", x=435, y=951, width=196, height=196, viewBox=source.attrib["viewBox"], fill="#FFFFFF")
    for node in source:
        if node.tag.rsplit('}', 1)[-1] in ("path", "g", "circle", "rect", "polygon"):
            nested.append(copy.deepcopy(node))
    logo = ET.parse(ROOT / "ARTS/LOGOBlack.svg").getroot()
    logo.attrib.update({"x": "76", "y": "88", "width": "226", "height": "56.5"})
    svg.append(logo)
    lettering.text(svg, "PLUGINS", 79, 205, 25, "#45686A", weight=700)
    top = 544 if len(design["title"]) == 1 else 472
    for i, line in enumerate(design["title"]):
        lettering.text(svg, line, 76, top + i * 114, 100, "#173F40", 748, weight=700)
    lettering.text(svg, design["tagline"], 79, 667 if len(design["title"]) == 1 else 702, 36, "#476667", 740)
    element(svg, "path", d="M 79 1406 H 201", stroke="#038387", stroke_width=8, stroke_linecap="round")
    lettering.text(svg, package["category"], 79, 1476, 30, "#527273", 738, weight=700)
    for node in svg.iter():
        # Export presentation attributes, since the host deliberately rejects embedded CSS.
        for declaration in node.attrib.pop("style", "").split(";"):
            if ":" in declaration:
                key, value = (part.strip() for part in declaration.split(":", 1))
                if key in {"fill", "fill-rule", "clip-rule", "stroke-linejoin", "stroke-miterlimit"}:
                    node.set(key, value)
    return ET.tostring(svg, encoding="utf-8", xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fonts", type=Path, required=True)
    parser.add_argument("--symbols", type=Path, required=True)
    parser.add_argument("--plugin-root", type=Path, required=True)
    parser.add_argument("--catalog", type=Path, required=True)
    args = parser.parse_args()
    designs = json.loads((ROOT / "distribution/plugin-artwork.json").read_text(encoding="utf-8"))
    packages = json.loads(args.catalog.read_text(encoding="utf-8"))["packages"]
    if set(designs) != {item["id"] for item in packages}:
        raise ValueError("Every published plugin must have exactly one artwork design")
    lettering = Lettering(args.fonts)
    output = ROOT / "ARTS/PluginStore"
    output.mkdir(parents=True, exist_ok=True)
    runtime = ROOT / "build/plugin-artwork-runtime"
    runtime.mkdir(parents=True, exist_ok=True)
    configs = json.loads((ROOT / "distribution/official-packages.json").read_text(encoding="utf-8"))
    by_id = {}
    for item in configs:
        with zipfile.ZipFile(ROOT / "build/plugin-packages" / item["file"]) as archive:
            by_id.setdefault(json.loads(archive.read("plugin.json"))["id"], item)
    entries = []
    for package in packages:
        design = designs[package["id"]]
        item = by_id[package["id"]]
        folder = output / package["id"]
        folder.mkdir(exist_ok=True)
        (folder / "cover.svg").write_bytes(cover(package, design, lettering, args.symbols))
        repo = args.plugin_root / item["module"]
        jar = repo / "build/intermediates/aar_main_jar/debug/syncDebugLibJars/classes.jar"
        if not jar.is_file():
            raise FileNotFoundError(f"Build {item['module']} before exporting previews")
        with zipfile.ZipFile(ROOT / "build/plugin-packages" / item["file"]) as archive:
            manifest = json.loads(archive.read("plugin.json"))
            assets = runtime / package["id"] / "assets"
            for name in archive.namelist():
                if name.startswith("assets/") and not name.endswith("/"):
                    target = (assets.parent / name).resolve()
                    if not target.is_relative_to(assets.resolve()):
                        raise ValueError("Invalid packaged asset path")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(archive.read(name))
        jars = [str(jar.resolve())]
        dependencies = repo / "build/plugin-dependencies"
        jars.extend(str(path.resolve()) for path in dependencies.glob("*.jar"))
        for dependency in dependencies.glob("*.aar"):
            with zipfile.ZipFile(dependency) as archive:
                for name in archive.namelist():
                    if name == "classes.jar" or name.startswith("libs/") and name.endswith(".jar"):
                        target = runtime / package["id"] / "dependencies" / (dependency.stem + "-" + Path(name).name)
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_bytes(archive.read(name))
                        jars.append(str(target.resolve()))
        entries.append(dict(id=package["id"], name=package["name"], module=item["module"],
                            catalog=str(args.catalog.resolve()),
                            entryClass=manifest["entryClass"], jars=jars, assets=str(assets.resolve()),
                            output=str(folder.resolve()), design=design,
                            sourceCommit=subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip(),
                            packageSha256=hashlib.sha256((ROOT / "build/plugin-packages" / item["file"]).read_bytes()).hexdigest()))
    for name in ("GoogleSans-OFL.txt", "GoogleSans-TRADEMARKS.md", "SourceHanSans-LICENSE.txt"):
        (output / "licenses").mkdir(exist_ok=True)
        shutil.copyfile(args.fonts / name, output / "licenses" / name)
    (runtime / "manifest.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Created {len(entries)} SVG covers; native capture manifest: {runtime / 'manifest.json'}")


if __name__ == "__main__":
    main()
