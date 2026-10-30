"""Verify signed font packages, original notices and font-derived SVG assets; render a contact sheet."""
import argparse
import hashlib
import io
import json
import math
import zipfile
from pathlib import Path
from urllib.parse import unquote
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont
import resvg_py
from font_source_assets import FONT_MIRROR


def verify(folder, output):
    root = Path(__file__).resolve().parent.parent
    folder, output = Path(folder).resolve(), Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    catalog = json.loads((folder / "kpm/index.json").read_text(encoding="utf-8"))
    legacy = json.loads((folder / "index.json").read_text(encoding="utf-8"))
    locks = {f["id"]: f for f in json.loads((root / "distribution/font-sources.json").read_text(encoding="utf-8"))["fonts"]}
    assert len(locks) == len(legacy["fonts"]) == len(catalog["packages"])
    sheet = Image.new("RGB", (1000, math.ceil(len(locks) / 5) * 172), "#f2f8f8")
    draw = ImageDraw.Draw(sheet)
    digest = lambda data: hashlib.sha256(data).hexdigest()

    def local(url):
        assert url.startswith(FONT_MIRROR)
        path = (folder / unquote(url[len(FONT_MIRROR):])).resolve()
        assert path.is_relative_to(folder)
        return path

    label_face = next((f for f in legacy["fonts"] if f["id"] in ("source-han-sans-cn-regular", "noto-sans-sc")), None)
    label_font = ImageFont.truetype(str(local(label_face["url"])), 16) if label_face else ImageFont.load_default()

    assets = {}
    for key, asset in catalog["assets"].items():
        data = local(asset["url"]).read_bytes()
        assert digest(data) == asset["sha256"] and len(data) == asset["bytes"]
        assets[key] = data
    checked = []
    for i, font in enumerate(legacy["fonts"]):
        key = font["id"]
        locked = locks[key]
        original = local(font["url"]).read_bytes()
        notice = local(font["licenseUrl"]).read_bytes()
        assert digest(original) == font["sha256"] == locked["sha256"]
        assert digest(notice) == locked["licenseSha256"]
        assert "SIL OPEN FONT LICENSE" in notice.decode("utf-8-sig")
        with TTFont(io.BytesIO(original)) as face:
            axes = [dict(tag=a.axisTag, min=a.minValue, default=a.defaultValue, max=a.maxValue)
                    for a in face["fvar"].axes] if "fvar" in face else []
            assert font["axes"] == axes
        package = next(p for p in catalog["packages"] if p["id"] == "fonts." + key)
        assert package["icon"]["monochrome"]
        icon = assets[package["icon"]["image"]]
        assert b"<text" not in icon and b"<path" in icon and b"<rect" not in icon
        rendered = Image.open(io.BytesIO(resvg_py.svg_to_bytes(svg_string=icon.decode().replace("#ffffff", "#145B60"), width=110, height=110))).convert("RGBA")
        x, y = (i % 5) * 200, (i // 5) * 172
        sheet.paste(rendered, (x + 45, y + 8), rendered)
        label = font["name"].replace("（西文）", "").replace(" 简体中文", " SC").replace(" GB 屏幕阅读版", "屏幕版")
        draw.text((x + 100, y + 124), label, fill="#145B60", font=label_font, anchor="mt")
        release = package["releases"][0]
        archive = local(release["mirrors"][0]["url"])
        assert digest(archive.read_bytes()) == release["sha256"]
        with zipfile.ZipFile(archive) as z:
            raw, public = z.read("resource.json"), z.read("signature/public.der")
            assert digest(public) == release["expectedPublisher"]
            serialization.load_der_public_key(public).verify(z.read("signature/manifest.sig"), raw, padding.PKCS1v15(), hashes.SHA256())
            manifest = json.loads(raw)
            assert manifest["id"] == package["id"] and manifest["resourceType"] == "FONT"
            assert set(z.namelist()) == set(manifest["files"]) | {"resource.json", "signature/public.der", "signature/manifest.sig"}
            for path, expected in manifest["files"].items():
                assert digest(z.read(path)) == expected
            assert z.read("OFL.txt") == notice
            content = json.loads(z.read("content.json"))
            assert z.read(content["payload"]) == original
        checked.append(dict(id=package["id"], name=font["name"], sha256=release["sha256"], axes=axes))
    sheet.save(output / "font-icons.png")
    (output / "verification.json").write_text(json.dumps(dict(packages=checked, assets=len(assets)), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(packages=len(checked), assets=len(assets), output=str(output)), ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", default=".tools/font-source")
    parser.add_argument("--output", default="build/font-source-verification")
    args = parser.parse_args()
    verify(args.folder, args.output)
