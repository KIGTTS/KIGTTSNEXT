"""Declare localized browse categories on official catalogs while keeping legacy package fields."""

import argparse
import hashlib
import json
from pathlib import Path
import unicodedata

ROOT = Path(__file__).resolve().parent.parent
LOCALES = ROOT / "market/src/main/assets/market/i18n"
PLUGIN_KEYS = {
    "音频": ("audio", "graphic_eq"),
    "外观": ("appearance", "palette"),
    "内容": ("content", "library_books"),
    "输入": ("input", "keyboard"),
    "开发": ("development", "code"),
    "扩展": ("extensions", "extension"),
}


def default_category(item):
    if item.get("resourceType"):
        key = "resource." + item["resourceType"].lower()
        return key.replace("_", "-"), key, "RESOURCES", item["resourceType"]
    name = item.get("category", "扩展")
    if name in PLUGIN_KEYS:
        group, symbol = PLUGIN_KEYS[name]
        return "plugin." + group, "category." + group, "PLUGINS", name, symbol
    normalized = unicodedata.normalize("NFKC", name.strip()).lower()
    digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:16]
    return "plugin.legacy." + digest, "", "PLUGINS", name


def declare_categories(catalog):
    definitions = {category["id"]: category for category in catalog.get("categories", [])}
    texts = {
        file.stem: json.loads(file.read_text(encoding="utf-8"))
        for file in LOCALES.glob("*.json")
    }
    for item in catalog["packages"]:
        if item.get("categoryId"):
            if item["categoryId"] not in definitions:
                raise ValueError(f"Missing category: {item['categoryId']}")
            continue
        key, translation, kind, default, *symbol = default_category(item)
        if key not in definitions:
            names = {locale: strings[translation] for locale, strings in texts.items() if translation in strings}
            name = names.get("zh-CN", default)
            definitions[key] = {"id": key, "name": name, "kind": kind, "localizations": names}
            if not symbol and kind == "RESOURCES":
                node = item.get("icon") or {}
                symbol = [node["symbol"]] if node.get("symbol") else []
            if symbol:
                definitions[key]["symbol"] = symbol[0]
        item["categoryId"] = key
    catalog["categories"] = list(definitions.values())
    return catalog


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    path = parser.parse_args().catalog
    data = declare_categories(json.loads(path.read_text(encoding="utf-8")))
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
