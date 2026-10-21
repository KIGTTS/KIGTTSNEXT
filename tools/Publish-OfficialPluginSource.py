"""Publish an explicitly prepared directory, artifacts first and index last. No persisted credentials."""
import argparse
import getpass
import hashlib
import json
from pathlib import Path
import sys
import time
from urllib.parse import urlparse, unquote

import requests
from modelscope.hub.api import HubApi

REPO = "LHTSTUDIO/KIGTTS-Official-Plugins"
BASE = f"https://modelscope.cn/datasets/{REPO}/resolve/master/"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def remote_hash(path):
    # Fresh public session: no token, authorization header or SDK cookies.
    for attempt in range(5):
        result = hashlib.sha256()
        size = 0
        with requests.Session() as public:
            with public.get(BASE + path + "?view=false&verify=" + str(time.time_ns()), stream=True, timeout=(20, 180)) as response:
                if response.status_code in (404, 429, 502, 503, 504) and attempt < 4:
                    time.sleep(2 ** attempt)
                    continue
                response.raise_for_status()
                if urlparse(response.url).scheme != "https":
                    raise ValueError("Non-HTTPS public download")
                for block in response.iter_content(1024 * 1024):
                    result.update(block)
                    size += len(block)
        return result.hexdigest(), size
    raise ValueError("Public download did not become available")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", default=".tools/official-plugin-source")
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--resume-artifacts", action="store_true", help="Artifacts already committed; verify them and publish only the catalog")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    folder = (root / args.folder).resolve()
    if not folder.is_relative_to(root):
        raise ValueError("Prepared folder must stay in the workspace")
    catalog = json.loads((folder / "index.json").read_text(encoding="utf-8"))
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
    android = json.loads((folder / "android/release.json").read_text(encoding="utf-8"))
    apk = folder / android["path"]
    if not apk.resolve().is_relative_to(folder) or digest(apk) != android["sha256"]:
        raise ValueError("Prepared Android APK hash mismatch")
    files[android["path"]] = (android["sha256"], apk.stat().st_size)
    files["android/release.json"] = (digest(folder / "android/release.json"), (folder / "android/release.json").stat().st_size)
    token = None
    try:
        if not args.verify_only:
            previous = requests.get(BASE + "index.json?view=false&preflight=" + str(time.time_ns()), timeout=30)
            previous.raise_for_status()
            old_files = {}
            for package in previous.json()["packages"]:
                for release in package["releases"]:
                    name = urlparse(release["mirrors"][0]["url"]).path.rsplit("/", 1)[-1]
                    old_files[name] = (release["sha256"], release["bytes"])
            for name, expected in old_files.items():
                if files.get(name) != expected:
                    raise ValueError(f"Published immutable release changed or missing: {name}")
            upload = [name for name in files if name not in old_files]
            token = getpass.getpass("ModelScope token (not saved): ")
            api = HubApi(token=token)
            if not args.resume_artifacts:
                api.upload_folder(repo_id=REPO, repo_type="dataset", folder_path=str(folder),
                    allow_patterns=upload, ignore_patterns=["*.tmp"], max_workers=2, use_cache=False,
                    token=token, commit_message=f"Add signed API{android['apiVersion']} plugins and Android preview artifacts")
            for name in upload:
                actual = remote_hash(name)
                if actual != files[name]:
                    raise ValueError(f"Public artifact verification failed: {name}")
                print(f"Verified new artifact: {name}", flush=True)
            # Only reference artifacts after their public bytes have been verified.
            api.upload_folder(repo_id=REPO, repo_type="dataset", folder_path=str(folder),
                allow_patterns=["index.json", "README.md", "LICENSE"], max_workers=2, use_cache=False,
                token=token, commit_message="Publish verified official plugin catalog and Android download")
            api.token = None
            token = None
        for name, expected in files.items():
            if remote_hash(name) != expected:
                raise ValueError(f"Anonymous integrity check failed: {name}")
            print(f"Anonymous SHA-256 OK: {name}", flush=True)
        for name in ("index.json", "README.md", "LICENSE"):
            if remote_hash(name)[0] != digest(folder / name):
                raise ValueError(f"Anonymous catalog check failed: {name}")
        report = {"repo": REPO, "verifiedArtifacts": len(files), "apkSha256": android["sha256"], "time": time.time()}
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
