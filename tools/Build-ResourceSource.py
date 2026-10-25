"""Prepare the independent official resource source from original, redistributable content."""
import argparse
import importlib.util
import json
from pathlib import Path
import jsonschema

ROOT = Path(__file__).resolve().parent.parent
BASE = "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Resources/resolve/master/"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", default=".tools/resource-source")
    parser.add_argument("--private-key", required=True)
    args = parser.parse_args()
    folder = (ROOT / args.folder).resolve()
    if not folder.is_relative_to(ROOT): raise ValueError("Output must stay in the workspace")
    folder.mkdir(parents=True, exist_ok=True)
    spec = importlib.util.spec_from_file_location("resource_builder", ROOT / "tools/Build-ResourcePackage.py")
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    projects = [(ROOT / "distribution/resources/classic-frames", "经典相框", "三种简洁的相纸配色，为照片换一种心情。", "crop_square")]
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
    projects.append((filters, "柔和色彩", "轻柔的暖色调，让日常照片多一点温度。", "filter"))
    packages = []
    for project, name, summary, symbol in projects:
        definition = json.loads((project / "package.json").read_text(encoding="utf-8"))
        filename = f"{definition['id']}-{definition['version']}.kigresource"
        release = module.build(project, folder / filename, args.private_key)
        release.update(mirrors=[dict(name="魔搭", url=BASE + filename + "?view=false")], notes="首次发布。", permissions=[], permissionPurposes={})
        packages.append(dict(id=definition["id"], name=name, summary=summary, resourceType=definition["resourceType"],
            category="外观", icon=dict(symbol=symbol), license="CC0-1.0", releases=[release]))
    assets = {}
    for package, name, width, height in zip(packages, ("classic-frames", "soft-colors"), (1200, 480), (800, 900)):
        local = ROOT / f"distribution/resources/previews/{name}.svg"
        target = folder / f"artwork/{name}.svg"; target.parent.mkdir(exist_ok=True); target.write_bytes(local.read_bytes())
        assets[name] = dict(url=BASE + f"artwork/{name}.svg", sha256=module.digest(local.read_bytes()), bytes=local.stat().st_size, width=width, height=height, alt=package["name"] + "预览")
        package["thumbnails"] = [name]
    catalog = dict(schemaVersion=2, kind="RESOURCES", id="kigtts.resources", name="KIGTTS 资源", description="为字幕与照片添加新的风格。",
        homepage="https://github.com/KIGTTS/KPM", updatedAt="2026-10-25T00:00:00Z", icon=dict(symbol="collections_bookmark"), packages=packages, assets=assets)
    jsonschema.Draft202012Validator(json.loads((ROOT / "distribution/kpm-catalog.schema.json").read_text(encoding="utf-8"))).validate(catalog)
    (folder / "index.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    (folder / "README.md").write_text("# KIGTTS 资源\n\n相框与滤镜，在插件商店的「资源」页面添加。\n\n这里的原创相框与 LUT 按 [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) 提供。\n", encoding="utf-8", newline="\n")
    print(f"Prepared {len(packages)} signed resource packages -> {folder}")

if __name__ == "__main__": main()
