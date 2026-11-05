"""Use the update-poster skill with an explicit camera and gallery illustration."""
import sys
import json
import hashlib
from pathlib import Path
from PIL import Image

SKILL = Path("C:/Users/LHT02/.codex/skills/kigtts-update-poster")
sys.path.insert(0, str(SKILL / "scripts"))
import render_poster as renderer
from vector_art import node, NS, TEAL

original_render = renderer.render


def camera_art(p):
    p.flower(887, 575, 123, "#BFE3D6", petals=7, opacity=.68)
    node(p.svg, "rect", x=776, y=450, width=204, height=265, rx=29, fill="#C5DFD5", opacity=.45)
    g = node(p.svg, "g", transform="rotate(4 872 579)")
    node(g, "rect", x=764, y=440, width=204, height=265, rx=29, fill="#FFFFFF", stroke="#A2C8BE", stroke_width=2)
    node(g, "rect", x=774, y=478, width=184, height=157, rx=15, fill="#CBE9E5")
    node(g, "circle", cx=909, cy=511, r=19, fill="#F7D8A0")
    node(g, "path", d="M774 594Q810 536 850 575Q900 542 958 594V620Q958 635 943 635H789Q774 635 774 620Z", fill="#91C7B7")
    node(g, "path", d="M774 615Q819 581 864 606Q914 570 958 611V620Q958 635 943 635H789Q774 635 774 620Z", fill="#4E9D91")
    # In-viewfinder guide, mode and shutter: a real feature diagram, not just decoration.
    grid = node(g, "g", fill="none", stroke="#FFFFFF", stroke_width=1, opacity=.55)
    for x in (835, 897):
        node(grid, "path", d=f"M{x} 481V632")
    for y in (530, 583):
        node(grid, "path", d=f"M777 {y}H955")
    node(g, "rect", x=827, y=611, width=78, height=20, rx=10, fill="#155A58", opacity=.75)
    p.letters.text(g, "0.6   1×   2", 866, 625, 12, "#FFFFFF", "medium", anchor="middle")
    node(g, "circle", cx=789, cy=459, r=6, fill=TEAL)
    node(g, "path", d="M938 454h10m-10 5h10m-10 5h10", fill="none", stroke=TEAL, stroke_width=2, stroke_linecap="round")
    node(g, "rect", x=842, y=645, width=48, height=17, rx=8.5, fill="#C3E5D9")
    p.letters.text(g, "拍照", 866, 657, 11, TEAL, "medium", anchor="middle")
    node(g, "circle", cx=866, cy=681, r=16, fill="none", stroke=TEAL, stroke_width=2)
    node(g, "circle", cx=866, cy=681, r=12, fill=TEAL)
    node(g, "rect", x=791, y=670, width=24, height=22, rx=5, fill="#91C7B7")
    node(g, "path", d="M923 674h14v14h-14z M927 670l-4 4 4 4 M933 684l4 4-4 4", fill="none", stroke=TEAL, stroke_width=1.8)


def render(content, assets, letters):
    if content["feature"].get("art") != "camera-album":
        raise ValueError("Explicit camera illustration required")
    p = original_render(content, assets, letters)
    for child in list(p.svg):
        if child.tag == f"{{{NS}}}path" and child.get("fill") in ("#B7DFD4", "#87C6C4"):
            p.svg.remove(child)
    camera_art(p)
    return p


if __name__ == "__main__":
    renderer.render = render
    renderer.main()
    output = Path(sys.argv[sys.argv.index("--output") + 1])
    manifest_file = output / "manifest.json"
    manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    stem = f"KIGTTS-update-{manifest['date']}"
    share = output / f"{stem}-share.jpg"
    with Image.open(output / f"{stem}-preview.jpg") as preview:
        assert preview.size == (1080, 1920)
        preview.convert("RGB").save(share, quality=95, subsampling=0, optimize=True)
    manifest["outputs"].append({"file": share.name, "bytes": share.stat().st_size,
        "sha256": hashlib.sha256(share.read_bytes()).hexdigest()})
    manifest_file.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
