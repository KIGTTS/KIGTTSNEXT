"""Publish the independent font dataset using the existing authenticated ModelScope session."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import unquote, urlparse
import requests
import time
from modelscope.hub.api import HubApi, ModelScopeConfig

REPO = "LHTSTUDIO/KIGTTS-Fonts"
BASE = f"https://modelscope.cn/datasets/{REPO}/resolve/master/"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", default=".tools/font-source")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    folder = (root / args.folder).resolve()
    if not folder.is_relative_to(root): raise ValueError("Source must be inside this workspace")
    catalog = json.loads((folder / "index.json").read_text(encoding="utf-8"))
    files = {"index.json", "README.md"}
    for font in catalog["fonts"]:
        for field, hashfield in (("url", "sha256"), ("preview", "previewSha256")):
            if not font[field].startswith(BASE): raise ValueError("Unexpected asset URL")
            path = unquote(font[field].removeprefix(BASE))
            local = (folder / path).resolve()
            if not local.is_relative_to(folder) or hashlib.sha256(local.read_bytes()).hexdigest() != font[hashfield]: raise ValueError("Asset verification failed")
            files.add(path)
        files.add(unquote(font["licenseUrl"].removeprefix(BASE)))
    if not args.verify_only:
        if ModelScopeConfig.get_cookies() is None: raise RuntimeError("ModelScope login required")
        api = HubApi()
        response = requests.get(BASE + "index.json", timeout=30)
        if response.status_code == 404:
            api.create_dataset("KIGTTS-Fonts", "LHTSTUDIO", chinese_name="KIGTTS 字体库",
                license="SIL Open Font License 1.1", description="KIGTTS 字体、SVG 字形预览与字体授权。")
        elif response.status_code != 200: response.raise_for_status()
        api.upload_folder(repo_id=REPO, repo_type="dataset", folder_path=str(folder),
            ignore_patterns=["index.json"], commit_message="Add original OFL fonts and outline SVG previews")
        api.upload_file(repo_id=REPO, repo_type="dataset", path_or_fileobj=str(folder / "index.json"),
            path_in_repo="index.json", commit_message="Publish font catalog")
    verified = []
    for relative in sorted(files):
        expected = hashlib.sha256((folder / relative).read_bytes()).hexdigest()
        for attempt in range(4):
            try:
                base = BASE if attempt % 2 == 0 else BASE.replace("https://modelscope.cn/", "https://www.modelscope.cn/")
                response = requests.get(base + requests.utils.quote(relative) + "?view=false&verify=" + str(time.time_ns()), timeout=(10, 25)); response.raise_for_status()
                if hashlib.sha256(response.content).hexdigest() != expected: raise ValueError(f"Public hash mismatch: {relative}")
                verified.append(dict(path=relative, sha256=expected, bytes=len(response.content)))
                print("Verified " + relative, flush=True)
                break
            except (requests.RequestException, ValueError):
                if attempt == 3: raise
    report = dict(repository=REPO, verified=verified)
    (root / ".tools/font-source-verified.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(dict(repository=REPO, verified=len(verified)), ensure_ascii=False))

if __name__ == "__main__": main()
