"""Prepare the independent official resource source from original, redistributable content."""
import argparse
import importlib.util
import json
from pathlib import Path
import jsonschema
from datetime import datetime, timezone
from kpm_catalog_categories import declare_categories

ROOT = Path(__file__).resolve().parent.parent
BASE = "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Resources/resolve/master/"

def add_artwork(folder, assets, module, name, local, width, height, alt):
    if not local.is_relative_to(ROOT) or not local.is_file(): raise ValueError("Artwork must exist in the workspace")
    content = local.read_bytes()
    if local.suffix.lower() == ".svg":
        content = content.replace(b"\r\n", b"\n")
    digest = module.digest(content)
    relative = f"artwork/{name}-{digest[:12]}{local.suffix}"
    target = folder / relative
    target.parent.mkdir(exist_ok=True)
    target.write_bytes(content)
    assets[name] = dict(url=BASE + relative, sha256=digest, bytes=len(content), width=width, height=height, alt=alt)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", default=".tools/resource-source")
    parser.add_argument("--private-key", help="Preserve signatures when rebuilding older signed resource releases")
    parser.add_argument("--expressions", action="store_true")
    args = parser.parse_args()
    folder = (ROOT / args.folder).resolve()
    if not folder.is_relative_to(ROOT): raise ValueError("Output must stay in the workspace")
    folder.mkdir(parents=True, exist_ok=True)
    spec = importlib.util.spec_from_file_location("resource_builder", ROOT / "tools/Build-ResourcePackage.py")
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    config = json.loads((ROOT / "distribution/resource-projects.json").read_text(encoding="utf-8"))
    assert config["schemaVersion"] == 1
    # Original 3D LUT. Blue-major, green, red order follows the .cube specification.
    filters = ROOT / ".tools/resource-projects/soft-colors"; filters.mkdir(parents=True, exist_ok=True)
    (filters / "package.json").write_text(json.dumps(dict(id="resources.soft-colors", name="柔和色彩", version="1.0.0", versionCode=1, resourceType="FILTER"), ensure_ascii=False), encoding="utf-8")
    (filters / "content.json").write_text(json.dumps(dict(filters=[dict(path="soft-warm.cube", name="柔和暖色")])), encoding="utf-8")
    lines = ['TITLE "柔和暖色"', 'LUT_3D_SIZE 5', 'DOMAIN_MIN 0 0 0', 'DOMAIN_MAX 1 1 1']
    for b in range(5):
        for g in range(5):
            for r in range(5):
                lines.append(f"{r/4*.96+.04:.6f} {g/4*.97+.02:.6f} {b/4*.95+.01:.6f}")
    (filters / "soft-warm.cube").write_text("\n".join(lines)+"\n", encoding="utf-8")
    index_name = "expressions.json" if args.expressions else "index.json"
    packages = []
    assets = {}
    previous = json.loads((folder / index_name).read_text(encoding="utf-8")) if (folder / index_name).is_file() else {}
    previous_packages = {p["id"]: p for p in previous.get("packages", [])}
    for entry in config["projects"]:
        project = (ROOT / entry["project"]).resolve()
        if not project.is_relative_to(ROOT): raise ValueError("Project escapes workspace")
        definition = json.loads((project / "package.json").read_text(encoding="utf-8"))
        if args.expressions != (definition["resourceType"] == "EXPRESSION"):
            continue
        filename = f"{definition['id']}-{definition['version']}.kigresource"
        release = module.build(project, folder / filename, args.private_key)
        release.update(mirrors=[dict(name="魔搭", url=BASE + filename + "?view=false")], notes="首次发布。", permissions=[], permissionPurposes={})
        history = previous_packages.get(definition["id"], {}).get("releases", [])
        for old in history:
            if old["version"] == release["version"] and old["sha256"] != release["sha256"]: raise ValueError("Published release differs")
        releases = [release] + [old for old in history if old["version"] != release["version"]]
        package = dict(id=definition["id"], name=entry["name"], summary=entry["summary"], resourceType=definition["resourceType"],
            category="外观", icon=dict(symbol=entry["symbol"]), license=entry["license"], releases=releases)
        if icon := entry.get("icon"):
            name = definition["id"].removeprefix("resources.") + "-icon"
            add_artwork(folder, assets, module, name, (ROOT / icon["file"]).resolve(), icon["width"], icon["height"], icon["alt"])
            package["icon"] = dict(image=name)
        if entry.get("localizations"): package["localizations"] = entry["localizations"]
        if entry.get("description"): package["description"] = entry["description"]
        packages.append(package)
        local = (ROOT / "distribution/resources/previews" / entry["preview"]).resolve()
        if not local.is_relative_to(ROOT / "distribution/resources/previews"): raise ValueError("Preview escapes artwork folder")
        name = local.stem
        add_artwork(folder, assets, module, name, local, entry["width"], entry["height"], package["name"] + "预览")
        package["thumbnails"] = [name]
    catalog = dict(schemaVersion=2, kind="RESOURCES", id="kigtts.resources", name="KIGTTS 资源", description="为字幕与照片添加新的风格。",
        homepage="https://github.com/KIGTTS/KPM", updatedAt=datetime.now(timezone.utc).isoformat(timespec="seconds"), linkPolicy=dict(recommendAfterInstall=True), icon=dict(symbol="collections_bookmark"), packages=packages, assets=assets)
    if args.expressions:
        catalog.update(id="kigtts.expressions", name="KIGTTS 面部贴纸", description="让贴纸跟随真人脸与娃脸。",
            localizations={
                "en": {"name": "KIGTTS Face Stickers", "description": "Tracking effects for faces and dolls."},
                "ja": {"name": "KIGTTS 顔ステッカー", "description": "顔やドールの顔を追尾するエフェクト"},
                "ko": {"name": "KIGTTS 얼굴 스티커", "description": "사람과 인형 얼굴을 따라 움직이는 효과"},
            })
    declare_categories(catalog)
    jsonschema.Draft202012Validator(json.loads((ROOT / "distribution/kpm-catalog.schema.json").read_text(encoding="utf-8"))).validate(catalog)
    (folder / index_name).write_bytes((json.dumps(catalog, ensure_ascii=False, indent=2)+"\n").encode("utf-8"))
    if not args.expressions:
        (folder / "README.md").write_text("---\nlicense: other\n---\n# KIGTTS 资源\n\n在商店添加相框、滤镜、名片边框与贴纸。每个包按自己的许可提供；字体和第三方素材不统一套用 CC0。\n\n" + "\n".join(f"- {p['name']}：{p['license']}" for p in packages) + "\n\nRemi 来源：[原作者文档](https://jafq11jkbyg.feishu.cn/docx/DPIxd0bRRoLxrixSuDccNvUYnbd)，权利归原作者。\n", encoding="utf-8", newline="\n")
    print(f"Prepared {len(packages)} resource packages -> {folder}")

if __name__ == "__main__": main()
