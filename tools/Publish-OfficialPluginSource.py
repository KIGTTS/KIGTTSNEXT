"""Publish an explicitly prepared directory, artifacts first and index last. No persisted credentials."""
import argparse
import getpass
import hashlib
import json
from pathlib import Path
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urlparse, unquote

import requests
import jsonschema
from modelscope.hub.api import HubApi

REPO = "LHTSTUDIO/KIGTTS-Official-Plugins"
BASE = f"https://modelscope.cn/datasets/{REPO}/resolve/master/"


def public_catalog():
    for attempt in range(4):
        base = BASE if attempt % 2 == 0 else BASE.replace("https://modelscope.cn/", "https://www.modelscope.cn/")
        try:
            response = requests.get(base + "index.json?view=false&preflight=" + str(time.time_ns()), timeout=(15, 25))
            response.raise_for_status()
            return response.json()
        except requests.RequestException:
            if attempt == 3:
                raise
    raise ValueError("Published catalog unavailable")


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def remote_hash(path, revision="master"):
    # Fresh public session: no token, authorization header or SDK cookies.
    for attempt in range(5):
        result = hashlib.sha256()
        size = 0
        try:
            with requests.Session() as public:
                base = BASE if attempt % 2 == 0 else BASE.replace("https://modelscope.cn/", "https://www.modelscope.cn/")
                base = base.replace("/resolve/master/", f"/resolve/{revision}/")
                with public.get(base + path + "?view=false&verify=" + str(time.time_ns()), stream=True, timeout=(15, 30)) as response:
                    if response.status_code in (404, 429, 502, 503, 504) and attempt < 4:
                        time.sleep(2 ** attempt)
                        continue
                    response.raise_for_status()
                    if urlparse(response.url).scheme != "https":
                        raise ValueError("Non-HTTPS public download")
                    for block in response.iter_content(1024 * 1024):
                        result.update(block)
                        size += len(block)
        except requests.RequestException:
            if attempt == 4:
                raise
            time.sleep(2 ** attempt)
            continue
        return result.hexdigest(), size
    raise ValueError("Public download did not become available")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", default=".tools/official-plugin-source")
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--resume-artifacts", action="store_true", help="Artifacts already committed; verify them and publish only the catalog")
    parser.add_argument("--catalog-only", action="store_true", help="Update presentation metadata for exactly the existing immutable releases")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    folder = (root / args.folder).resolve()
    if not folder.is_relative_to(root):
        raise ValueError("Prepared folder must stay in the workspace")
    catalog = json.loads((folder / "index.json").read_text(encoding="utf-8"))
    schema = json.loads((root / "distribution/kpm-catalog.schema.json").read_text(encoding="utf-8"))
    jsonschema.Draft202012Validator(schema).validate(catalog)
    files = {}
    for package in catalog["packages"]:
        for release in package["releases"]:
            url = release["mirrors"][0]["url"]
            if not url.startswith(BASE):
                raise ValueError("Unexpected artifact destination")
            path = unquote(urlparse(url).path.rsplit("/", 1)[-1])
            local = folder / path
            if not local.is_file() or digest(local) != release["sha256"] or local.stat().st_size != release["bytes"]:
                raise ValueError(f"Prepared artifact failed integrity check: {path}")
            files[path] = (release["sha256"], release["bytes"])
    for artwork in catalog.get("assets", {}).values():
        url = artwork["url"]
        if not url.startswith(BASE):
            raise ValueError("Unexpected artwork destination")
        path = unquote(urlparse(url).path.removeprefix(urlparse(BASE).path))
        local = (folder / path).resolve()
        if not path.startswith("artwork/") or not local.is_relative_to(folder) or not local.is_file() or digest(local) != artwork["sha256"] or local.stat().st_size != artwork["bytes"]:
            raise ValueError("Prepared artwork failed integrity check")
        files[path] = (artwork["sha256"], artwork["bytes"])
    android = json.loads((folder / "android/release.json").read_text(encoding="utf-8"))
    apk = folder / android["path"]
    if not apk.resolve().is_relative_to(folder) or digest(apk) != android["sha256"]:
        raise ValueError("Prepared Android APK hash mismatch")
    files[android["path"]] = (android["sha256"], apk.stat().st_size)
    files["android/release.json"] = (digest(folder / "android/release.json"), (folder / "android/release.json").stat().st_size)
    token = None
    try:
        if not args.verify_only:
            previous = public_catalog()
            old_files = {}
            for package in previous["packages"]:
                for release in package["releases"]:
                    name = urlparse(release["mirrors"][0]["url"]).path.rsplit("/", 1)[-1]
                    old_files[name] = (release["sha256"], release["bytes"])
            for name, expected in old_files.items():
                if files.get(name) != expected:
                    raise ValueError(f"Published immutable release changed or missing: {name}")
            if args.catalog_only:
                if set(old_files) != {name for name in files if name.endswith(".kigplugin")}:
                    raise ValueError("Catalog-only publishing cannot add package versions")
                if remote_hash("android/release.json") != files["android/release.json"]:
                    raise ValueError("Catalog-only publishing cannot change Android metadata")
            upload = [name for name in files if name not in old_files]
            if args.catalog_only:
                upload = [name for name in files if name.startswith("artwork/")]
            token = getpass.getpass("ModelScope token (not saved): ")
            api = HubApi(token=token)
            if not args.resume_artifacts and upload:
                api.upload_folder(repo_id=REPO, repo_type="dataset", folder_path=str(folder),
                    allow_patterns=upload, ignore_patterns=["*.tmp"], max_workers=2, use_cache=False,
                    token=token, commit_message=f"Add signed API{android['apiVersion']} plugins and Android artifacts")
            with ThreadPoolExecutor(max_workers=3) as pool:
                for name, actual in zip(upload, pool.map(remote_hash, upload)):
                    if actual != files[name]:
                        raise ValueError(f"Public artifact verification failed: {name}")
                    print(f"Verified new artifact: {name}", flush=True)
            # Only reference artifacts after their public bytes have been verified.
            api.upload_folder(repo_id=REPO, repo_type="dataset", folder_path=str(folder),
                allow_patterns=["index.json", "README.md", "LICENSE"], max_workers=2, use_cache=False,
                token=token, commit_message="Publish verified official plugin catalog and Android download")
            api.token = None
            token = None
        verify_files = {} if args.catalog_only else files
        with ThreadPoolExecutor(max_workers=3) as pool:
            for name, actual in zip(verify_files, pool.map(remote_hash, verify_files)):
                if actual != files[name]:
                    raise ValueError(f"Anonymous integrity check failed: {name}")
                print(f"Anonymous SHA-256 OK: {name}", flush=True)
        cached = []
        for name in ("index.json", "README.md", "LICENSE"):
            if remote_hash(name)[0] != digest(folder / name):
                # Verify the committed public bytes while the mutable branch URL propagates.
                entries = HubApi().get_dataset_files(REPO, recursive=False)
                entry = next((item for item in entries if item.get("Path") == name), {})
                revision = entry.get("Revision", "")
                if entry.get("Sha256") != digest(folder / name) or len(revision) != 40 or not all(c in "0123456789abcdef" for c in revision):
                    raise ValueError(f"Published catalog differs: {name}")
                if remote_hash(name, revision)[0] != digest(folder / name):
                    raise ValueError(f"Anonymous commit check failed: {name}")
                cached.append(name)
                print(f"Anonymous commit SHA-256 OK: {name}; branch cache pending", flush=True)
        report = {"repo": REPO, "verifiedArtifacts": len(verify_files), "retainedArtifacts": len(files) if args.catalog_only else 0,
                  "catalogOnly": args.catalog_only, "branchCachePending": cached, "apkSha256": android["sha256"], "time": time.time()}
        (root / ".tools/modelscope-verified.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report), flush=True)
    except Exception as error:
        message = str(error)
        if token:
            message = message.replace(token, "<redacted>")
        print(type(error).__name__ + ": " + message, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
