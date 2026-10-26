"""Wrap the verified OFL font catalog as signed KPM resources; retain the legacy index."""
import argparse
import importlib.util
import json
from pathlib import Path
from urllib.parse import quote, unquote
import shutil
import jsonschema
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
BASE = "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Fonts/resolve/master/"

def build(folder, private_key):
    folder = Path(folder).resolve()
    spec = importlib.util.spec_from_file_location("resource_builder", ROOT / "tools/Build-ResourcePackage.py")
    builder = importlib.util.module_from_spec(spec); spec.loader.exec_module(builder)
    legacy = json.loads((folder / "index.json").read_text(encoding="utf-8"))
    packages, assets = [], {}
    for font in legacy["fonts"]:
        def verified(field, digest_field=None):
            if not font[field].startswith(BASE): raise ValueError("Unexpected font source")
            local = (folder / unquote(font[field][len(BASE):])).resolve()
            if not local.is_relative_to(folder) or not local.is_file(): raise ValueError("Missing font asset")
            if digest_field and builder.digest(local.read_bytes()) != font[digest_field]: raise ValueError("Font asset mismatch")
            return local
        original = verified("url", "sha256")
        preview = verified("preview", "previewSha256")
        license_file = verified("licenseUrl")
        project = ROOT / ".tools/font-kpm-projects" / font["id"]
        project.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, project / original.name)
        shutil.copyfile(license_file, project / "OFL.txt")
        definition = dict(id="fonts." + font["id"], name=font["name"], version="1.0.0", versionCode=1,
                          resourceType="FONT", licenses=["OFL.txt"])
        (project / "package.json").write_text(json.dumps(definition, ensure_ascii=False), encoding="utf-8")
        (project / "content.json").write_text(json.dumps(dict(payload=original.name)), encoding="utf-8")
        relative = "kpm/" + definition["id"] + "-1.0.0.kigresource"
        release = builder.build(project, folder / relative, private_key)
        release.update(mirrors=[dict(name="魔搭", url=BASE + quote(relative))], permissions=[], permissionPurposes={}, notes="添加后可在外观的字体列表中选择。")
        # Use an opaque pale plate so the outlined glyphs remain legible in either theme.
        svg = preview.read_text(encoding="utf-8")
        bounds = ET.fromstring(svg).attrib["viewBox"].split()
        width, height = float(bounds[2]), float(bounds[3])
        svg = svg.replace('<g fill="#ffffff">', f'<rect width="{width}" height="{height}" rx="16" fill="#E0EFF0"/><g fill="#145B60">')
        artwork = folder / "kpm/previews" / (font["id"] + ".svg")
        artwork.parent.mkdir(parents=True, exist_ok=True); artwork.write_text(svg, encoding="utf-8", newline="\n")
        assets[font["id"]] = dict(url=BASE + "kpm/previews/" + font["id"] + ".svg", sha256=builder.digest(artwork.read_bytes()), bytes=artwork.stat().st_size,
                                  width=int(width + .5), height=int(height + .5), alt=font["name"] + "字形预览")
        packages.append(dict(id=definition["id"], name=font["name"], summary="你好，KIGTTS · 添加后在外观中选择", resourceType="FONT",
                             category="外观", license=font["license"], icon=dict(symbol="font_download"),
                             thumbnails=[font["id"]], releases=[release]))
    catalog = dict(schemaVersion=2, kind="RESOURCES", id="kigtts.fonts", name="KIGTTS 字体库", description="给文字换一种表情。",
                   homepage="https://github.com/KIGTTS/KPM", icon=dict(symbol="font_download"), assets=assets, packages=packages)
    jsonschema.Draft202012Validator(json.loads((ROOT / "distribution/kpm-catalog.schema.json").read_text(encoding="utf-8"))).validate(catalog)
    target = folder / "kpm/index.json"
    target.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    return target

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", default=".tools/font-source")
    parser.add_argument("--private-key", required=True)
    args = parser.parse_args()
    print(build(args.folder, args.private_key))
