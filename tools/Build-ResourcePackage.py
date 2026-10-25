"""Build a signed data-only KPM archive from a resource project. Never copies the signing key."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

TYPES = ("QUICK_TEXT", "PHOTOS", "FILTER", "VOICE", "FRAME", "STICKERS")

def digest(data):
    return hashlib.sha256(data).hexdigest()

def safe(path):
    return 0 < len(path) <= 240 and not any(x in path for x in ("\\", ":")) and all(x not in ("", ".", "..") for x in path.split("/")) and all(ord(x) >= 32 for x in path)

def build(project, output, private_key):
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
    for name in paths:
        if not safe(name) or name in ("package.json", "resource.json") or name.startswith("signature/"): raise ValueError("Invalid payload path")
        file = (project / name).resolve()
        if not file.is_relative_to(project) or not file.is_file(): raise ValueError("Missing resource")
        payload[name] = file.read_bytes()
    assert len(payload) <= 4093 and sum(map(len, payload.values())) <= 768 * 1024 * 1024
    manifest = {k: definition[k] for k in ("id", "name", "version", "versionCode", "resourceType")}
    manifest.update(schemaVersion=1, apiVersion=29, minSdk=26, content="content.json", dependencies=definition.get("dependencies", {}), files={p: digest(b) for p, b in sorted(payload.items())})
    raw = json.dumps(manifest, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    key = serialization.load_der_private_key(Path(private_key).read_bytes(), password=None)
    if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < 2048: raise ValueError("RSA 2048+ required")
    public = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    signature = key.sign(raw, padding.PKCS1v15(), hashes.SHA256())
    key.public_key().verify(signature, raw, padding.PKCS1v15(), hashes.SHA256())
    output = Path(output).resolve(); output.parent.mkdir(parents=True, exist_ok=True)
    contents = {**payload, "resource.json": raw, "signature/public.der": public, "signature/manifest.sig": signature}
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
    return dict(version=manifest["version"], versionCode=manifest["versionCode"], format="RESOURCE", apiVersion=29, minSdk=26,
        sha256=digest(output.read_bytes()), bytes=output.stat().st_size, expectedPublisher=digest(public), dependencies=manifest["dependencies"])

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project"); parser.add_argument("--output", required=True); parser.add_argument("--private-key", required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.project, args.output, args.private_key), ensure_ascii=False, indent=2))
