"""Index and package the official covers and native interface previews."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import zipfile

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    folder = ROOT / "ARTS/PluginStore"
    packages = json.loads(args.catalog.read_text(encoding="utf-8"))["packages"]
    captures = json.loads((ROOT / "build/plugin-artwork-runtime/capture-report.json").read_text(encoding="utf-8"))
    if {p["id"] for p in packages} != {p["id"] for p in captures}:
        raise ValueError("Capture report must cover every published plugin")
    entries = []
    for package in packages:
        plugin = package["id"]
        specs = [("cover.svg", "花型封面", (1600, 800)), ("preview-light.png", "界面预览 · 浅色", None),
                 ("preview-dark.png", "界面预览 · 深色", None)]
        if plugin == "display.led":
            specs.insert(1, ("preview-led.png", "LED 字幕效果", None))
        images = []
        for name, label, dimensions in specs:
            path = folder / plugin / name
            if dimensions is None:
                with Image.open(path) as bitmap:
                    bitmap.verify()
                with Image.open(path) as bitmap:
                    dimensions = bitmap.size
            data = path.read_bytes()
            images.append(dict(file=f"{plugin}/{name}", alt=f"{package['name']} · {label}",
                               width=dimensions[0], height=dimensions[1], bytes=len(data),
                               sha256=hashlib.sha256(data).hexdigest()))
        entries.append(dict(id=plugin, images=images))
    metadata = dict(schemaVersion=1, capturedAt=datetime.now(timezone.utc).isoformat(), packages=entries,
                    provenance=captures, environment="Robolectric API 35, 393×852 dp, xxhdpi; LED: Chromium 1280×720",
                    sampleData="AI conversation and LED text are illustrative, offline data; no account credentials or real conversations.")
    (folder / "index.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for mode in ("cover", "preview-light", "preview-dark"):
        width, height = (480, 240) if mode == "cover" else (236, 512)
        overview = Image.new("RGB", (5 * (width + 20), 3 * (height + 44)), "#DCE8E7")
        draw = ImageDraw.Draw(overview)
        for i, package in enumerate(packages):
            with Image.open(folder / package["id"] / (mode + ".png")) as original:
                bitmap = original.convert("RGB")
            bitmap.thumbnail((width, height), Image.Resampling.LANCZOS)
            x, y = (i % 5) * (width + 20) + 10, (i // 5) * (height + 44) + 10
            overview.paste(bitmap, (x + (width - bitmap.width) // 2, y))
            draw.text((x, y + height + 7), package["id"], fill="#173F40")
        overview.save(folder / (mode + "-overview.jpg"), quality=94)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(folder.rglob("*")):
            if path.is_file():
                archive.write(path, "PluginStore/" + path.relative_to(folder).as_posix())
    print(f"Packaged {len(entries)} covers and {sum(len(p['images']) - 1 for p in entries)} interface previews: {args.output}")
    print(f"SHA-256: {hashlib.sha256(args.output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
