"""Build a data-only KPM archive. Existing publishers can continue signing the resource manifest."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

TYPES = ("QUICK_TEXT", "PHOTOS", "FILTER", "VOICE", "FRAME", "STICKERS", "CARD_FRAME", "FONT", "EXPRESSION")

def digest(data):
    return hashlib.sha256(data).hexdigest()

def safe(path):
    return 0 < len(path) <= 240 and not any(x in path for x in ("\\", ":")) and all(x not in ("", ".", "..") for x in path.split("/")) and all(ord(x) >= 32 for x in path)

def build(project, output, private_key=None):
    project = Path(project).resolve()
    definition = json.loads((project / "package.json").read_text(encoding="utf-8"))
    assert re.fullmatch(r"[a-z][a-z0-9.-]{0,63}", definition["id"])
    assert definition["resourceType"] in TYPES and definition["versionCode"] > 0
    assert re.fullmatch(r"\d+\.\d+\.\d+(?:[-+][a-zA-Z0-9.-]+)?", definition["version"])
    assert 1 <= len(definition["name"]) <= 128
    content = json.loads((project / "content.json").read_text(encoding="utf-8"))
    payload = {"content.json": (project / "content.json").read_bytes()}
    paths = list(content.get("imageFiles", {}).values()) + [x["path"] for x in content.get("filters", [])]
    if content.get("payload"): paths.append(content["payload"])
    generators = project / "generators.json"
    if generators.is_file():
        if definition["resourceType"] != "STICKERS": raise ValueError("Generators require STICKERS")
        descriptor = json.loads(generators.read_text(encoding="utf-8"))
        assert descriptor.get("schemaVersion", 1) == 1 and 1 <= len(descriptor["items"]) <= 32
        payload["generators.json"] = generators.read_bytes()
        for item in descriptor["items"]:
            assert 32 <= item.get("width", 1024) <= 2048 and 32 <= item.get("height", 512) <= 2048
            assert len(item.get("parameters", [])) <= 16 and len(item.get("fonts", {})) <= 8
            paths.append(item["template"])
            paths.extend(item.get("fonts", {}).values())
    paths.extend(definition.get("licenses", []))
    photo_defaults = project / "photo-defaults.json"
    if photo_defaults.is_file():
        if not generators.is_file(): raise ValueError("Photo defaults require sticker generators")
        paths.append("photo-defaults.json")
    card_frames = project / "card-frames.json"
    if definition["resourceType"] == "CARD_FRAME":
        descriptor = json.loads(card_frames.read_text(encoding="utf-8"))
        assert 1 <= len(descriptor["items"]) <= 64
        payload["card-frames.json"] = card_frames.read_bytes()
        paths.extend(item["artwork"] for item in descriptor["items"])
    api_version = 30 if generators.is_file() or definition["resourceType"] == "CARD_FRAME" else 29
    if definition["resourceType"] == "FRAME" and content.get("payload"):
        api_version = 34
        watermark = content["payload"]
        if not safe(watermark) or not watermark.lower().endswith(".xml") or any(content.get(k) for k in ("frames", "filters", "snapshot", "imageFiles")):
            raise ValueError("Camera watermark packages require a single XML payload")
        watermark_file = (project / watermark).resolve()
        if not watermark_file.is_relative_to(project) or not 1 <= watermark_file.stat().st_size <= 4096:
            raise ValueError("Camera watermark XML is too large")
    if photo_defaults.is_file(): api_version = 34
    if definition["resourceType"] == "EXPRESSION":
        api_version = 36
        expression = content.get("payload", "")
        if not safe(expression) or not expression.endswith(".kigexpression") or any(content.get(k) for k in ("filters", "frames", "snapshot", "imageFiles")):
            raise ValueError("Face effects require a single .kigexpression payload")
        source = (project / expression).resolve()
        if not source.is_relative_to(project) or not 1 <= source.stat().st_size <= 3 * 1024 * 1024:
            raise ValueError("Invalid face effect archive")
        pack = json.loads(source.read_text(encoding="utf-8"))
        if pack.get("version", 1) != 1 or not 1 <= len(pack.get("effects", [])) <= 24:
            raise ValueError("Unsupported face effect descriptor")
    if definition["resourceType"] == "FONT":
        api_version = 32
        assert content.get("payload") and not any(content.get(k) for k in ("filters", "frames", "snapshot", "imageFiles"))
        font_path = (project / content["payload"]).resolve()
        if not font_path.is_relative_to(project) or font_path.suffix.lower() not in (".ttf", ".otf"):
            raise ValueError("Font payload must be a local TTF or OTF")
        if not 12 <= font_path.stat().st_size <= 40 * 1024 * 1024:
            raise ValueError("Font must be smaller than 40 MB")
        with font_path.open("rb") as font_file:
            if font_file.read(4) not in (b"\x00\x01\x00\x00", b"OTTO"):
                raise ValueError("Invalid font signature")
    for name in paths:
        if not safe(name) or name in ("package.json", "resource.json") or name.startswith("signature/"): raise ValueError("Invalid payload path")
        file = (project / name).resolve()
        if not file.is_relative_to(project) or not file.is_file(): raise ValueError("Missing resource")
        payload[name] = file.read_bytes()
    assert len(payload) <= 4093 and sum(map(len, payload.values())) <= 768 * 1024 * 1024
    manifest = {k: definition[k] for k in ("id", "name", "version", "versionCode", "resourceType")}
    author = definition.get("author")
    if author is not None:
        if not isinstance(author, str) or len(author) > 120: raise ValueError("Resource author must be at most 120 characters")
        if author.strip(): manifest["author"] = author.strip()
    manifest.update(schemaVersion=1, apiVersion=api_version, minSdk=26, content="content.json", dependencies=definition.get("dependencies", {}), files={p: digest(b) for p, b in sorted(payload.items())})
    raw = json.dumps(manifest, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    contents = {**payload, "resource.json": raw}
    publisher = ""
    if private_key:
        key = serialization.load_der_private_key(Path(private_key).read_bytes(), password=None)
        if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < 2048: raise ValueError("RSA 2048+ required")
        public = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        signature = key.sign(raw, padding.PKCS1v15(), hashes.SHA256())
        key.public_key().verify(signature, raw, padding.PKCS1v15(), hashes.SHA256())
        contents.update({"signature/public.der": public, "signature/manifest.sig": signature})
        publisher = digest(public)
    output = Path(output).resolve(); output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        with zipfile.ZipFile(output) as existing:
            if set(existing.namelist()) != set(contents) or any(existing.read(p) != b for p, b in contents.items()):
                raise ValueError("Existing release differs. Use a new version and filename.")
    else:
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
            for path, data in sorted(contents.items()):
                info = zipfile.ZipInfo(path, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data)
    if output.stat().st_size > 256 * 1024 * 1024: raise ValueError("Archive too large")
    release = dict(version=manifest["version"], versionCode=manifest["versionCode"], format="RESOURCE", apiVersion=api_version, minSdk=26,
        sha256=digest(output.read_bytes()), bytes=output.stat().st_size, dependencies=manifest["dependencies"])
    if publisher:
        release["expectedPublisher"] = publisher
    return release

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project"); parser.add_argument("--output", required=True); parser.add_argument("--private-key")
    args = parser.parse_args()
    print(json.dumps(build(args.project, args.output, args.private_key), ensure_ascii=False, indent=2))
