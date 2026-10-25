"""Publish prepared KPM resource archives, verify anonymous bytes, then publish the catalog."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import unquote, urlparse
import time
import requests
from modelscope.hub.api import HubApi, ModelScopeConfig

REPO = "LHTSTUDIO/KIGTTS-Resources"
BASE = f"https://modelscope.cn/datasets/{REPO}/resolve/master/"
ROOT = Path(__file__).resolve().parent.parent

def public(relative, expected):
    for attempt in range(4):
        try:
            base = BASE if attempt % 2 == 0 else BASE.replace("https://modelscope.cn/", "https://www.modelscope.cn/")
            response = requests.get(base + relative + "?view=false&verify=" + str(time.time_ns()), timeout=(10, 25))
            response.raise_for_status()
            if hashlib.sha256(response.content).hexdigest() != expected: raise ValueError("Published bytes differ: " + relative)
            print("Verified " + relative, flush=True)
            return
        except (requests.RequestException, ValueError):
            if attempt == 3: raise
            time.sleep(2 ** attempt)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", default=".tools/resource-source"); parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    folder = (ROOT / args.folder).resolve()
    if not folder.is_relative_to(ROOT): raise ValueError("Source must stay in the workspace")
    catalog = json.loads((folder / "index.json").read_text(encoding="utf-8"))
    files = {}
    for package in catalog["packages"]:
        for release in package["releases"]:
            url = release["mirrors"][0]["url"]
            if not url.startswith(BASE): raise ValueError("Unexpected asset URL")
            relative = unquote(urlparse(url).path.split("/resolve/master/", 1)[1])
            local = (folder / relative).resolve()
            if not local.is_relative_to(folder): raise ValueError("Invalid asset path")
            if local.stat().st_size != release["bytes"] or hashlib.sha256(local.read_bytes()).hexdigest() != release["sha256"]: raise ValueError("Local artifact differs")
            files[relative] = release["sha256"]
    for asset in catalog.get("assets", {}).values():
        if not asset["url"].startswith(BASE): raise ValueError("Unexpected image URL")
        relative = unquote(asset["url"].removeprefix(BASE))
        file = (folder / relative).resolve()
        if not file.is_relative_to(folder) or file.stat().st_size != asset["bytes"] or hashlib.sha256(file.read_bytes()).hexdigest() != asset["sha256"]: raise ValueError("Image verification failed")
        files[relative] = asset["sha256"]
    api = None
    if not args.verify_only:
        if ModelScopeConfig.get_cookies() is None: raise RuntimeError("ModelScope login required")
        api = HubApi()
        response = requests.get(BASE + "index.json", timeout=(10, 25))
        if response.status_code == 404:
            api.create_dataset("KIGTTS-Resources", "LHTSTUDIO", chinese_name="KIGTTS 资源", license="CC0-1.0", description="KIGTTS 相框、滤镜与其它可添加资源。")
        else:
            response.raise_for_status()
            previous = response.json()
            for package in previous["packages"]:
                for release in package["releases"]:
                    name = unquote(urlparse(release["mirrors"][0]["url"]).path.rsplit("/", 1)[1])
                    if files.get(name) != release["sha256"]: raise ValueError("Published release cannot be replaced or removed")
        api.upload_folder(repo_id=REPO, repo_type="dataset", folder_path=str(folder), allow_patterns=list(files), commit_message="Add signed data resources")
    for path, expected in files.items(): public(path, expected)
    if api:
        api.upload_folder(repo_id=REPO, repo_type="dataset", folder_path=str(folder), allow_patterns=["index.json", "README.md"], commit_message="Publish verified resource catalog")
    files.update({name: hashlib.sha256((folder / name).read_bytes()).hexdigest() for name in ("index.json", "README.md")})
    for name in ("index.json", "README.md"): public(name, files[name])
    (ROOT / ".tools/resource-source-verified.json").write_text(json.dumps(dict(repository=REPO, verified=files), ensure_ascii=False, indent=2), encoding="utf-8")

if __name__ == "__main__": main()
