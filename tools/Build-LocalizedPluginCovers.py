"""Update official gallery covers in all supported languages, retaining native screenshots."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
from textwrap import wrap

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "ARTS/PluginStore"


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, obj):
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--fonts", type=Path, required=True, help="Maintainer font directory: google.ttf, han.ttf")
    parser.add_argument("--hangul", type=Path, required=True)
    parser.add_argument("--symbols", type=Path, required=True)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("cover_art", Path(__file__).with_name("Build-PluginArtwork.py"))
    art = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(art)
    lettering = art.Lettering(args.fonts, args.hangul)
    catalog = read(args.catalog)
    designs = read(ROOT / "distribution/plugin-artwork.json")
    translations = read(ROOT / "distribution/plugin-artwork-locales.json")
    manifest_path = ART / "index.json"
    manifest = read(manifest_path)
    previous = {record["id"]: record for record in manifest["packages"]}
    categories = {"内容": ("Content", "콘텐츠"), "开发": ("Development", "개발"),
                  "外观": ("Appearance", "모양"), "音频": ("Audio", "오디오"),
                  "输入": ("Input", "입력"), "扩展": ("Extensions", "확장 기능")}
    new_records = []
    for package in catalog["packages"]:
        key = package["id"]
        design = designs[key]
        existing = previous.get(key, {"id": key, "images": []})
        # Keep native previews and photos byte-for-byte; only the title cover changes.
        screenshots = [image for image in existing["images"] if Path(image["file"]).stem != "cover"]

        def image(suffix, title, design, category):
            path = ART / key / ("cover" + suffix + ".svg")
            path.parent.mkdir(parents=True, exist_ok=True)
            entry = {**package, "name": title, "category": category}
            data = art.cover(entry, design, lettering, args.symbols)
            path.write_bytes(data)
            return dict(file=path.relative_to(ART).as_posix(), alt=title, width=900, height=1600,
                        bytes=len(data), sha256=hashlib.sha256(data).hexdigest())

        entry = {"id": key, "images": [image("", package["name"], design, package["category"]), *screenshots],
                 "localizations": {}}
        for i, (tag, wording) in enumerate(translations[key].items()):
            lines = wrap(wording["name"], width=19, break_long_words=False)
            layout = {**design, "title": lines if len(lines) <= 2 else [wording["name"]], "tagline": wording["summary"]}
            cover = image("-" + tag, wording["name"], layout, categories[package["category"]][i])
            entry["localizations"][tag] = {**wording, "cover": cover}
        new_records.append(entry)
    manifest["packages"] = new_records
    write(manifest_path, manifest)
    print(f"Wrote {len(new_records)} × 3 localized 9:16 cover SVGs")


if __name__ == "__main__":
    main()
