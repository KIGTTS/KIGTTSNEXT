"""Load hash-pinned originals, including an explicitly selected legacy font export."""
import hashlib
import re
from pathlib import Path
from urllib.parse import unquote, urlparse

FONT_MIRROR = "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Fonts/resolve/master/"


def source_urls(locked):
    upstream = locked["upstream"]
    parsed = urlparse(upstream)
    if parsed.scheme != "https" or parsed.hostname not in ("github.com", "raw.githubusercontent.com"):
        raise ValueError("Unexpected upstream")
    if "legacyFiles" in locked:
        font_url = locked["downloadUrl"]
        license_url = locked["licenseDownloadUrl"]
        if not all(url.startswith(FONT_MIRROR + "fonts/" + locked["id"] + "/") for url in (font_url, license_url)):
            raise ValueError("Unexpected legacy font mirror")
    elif "licenseUpstream" in locked:
        license_url = locked["licenseUpstream"]
        license_parsed = urlparse(license_url)
        parts = parsed.path.strip("/").split("/")
        license_parts = license_parsed.path.strip("/").split("/")
        if (parsed.hostname != "raw.githubusercontent.com" or len(parts) < 4
                or not re.fullmatch(r"[a-f0-9]{40}", parts[2])
                or license_parsed.scheme != "https" or license_parsed.hostname != parsed.hostname
                or license_parts[:3] != parts[:3] or len(license_parts) < 4):
            raise ValueError("Font and license must use the same pinned upstream revision")
        font_url = upstream
    else:
        if not upstream.startswith("https://raw.githubusercontent.com/google/fonts/"):
            raise ValueError("Unexpected upstream")
        font_url, license_url = upstream, upstream.rsplit("/", 1)[0] + "/OFL.txt"
    filename = unquote(urlparse(font_url).path.rsplit("/", 1)[1])
    if Path(filename).name != filename or "\\" in filename or Path(filename).suffix.lower() not in (".ttf", ".otf"):
        raise ValueError("Unexpected font filename")
    return font_url, license_url, filename


def original_assets(locked, session, legacy_folder=None):
    font_url, license_url, filename = source_urls(locked)
    if legacy_folder is not None and "legacyFiles" in locked:
        root = Path(legacy_folder).resolve()
        def read(field):
            path = (root / locked["legacyFiles"][field]).resolve()
            if not path.is_relative_to(root):
                raise ValueError("Legacy font path escapes source")
            return path.read_bytes()
        font, license_data = read("font"), read("license")
    else:
        def download(url):
            response = session.get(url, timeout=(15, 120))
            response.raise_for_status()
            return response.content
        font, license_data = download(font_url), download(license_url)
    for data, expected in ((font, locked["sha256"]), (license_data, locked["licenseSha256"])):
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError("Font or license differs from source lock")
    if font[:4] not in (b"\x00\x01\x00\x00", b"OTTO") or len(font) > 40 * 1024 * 1024:
        raise ValueError("Invalid font payload")
    notice = license_data.decode("utf-8-sig")
    if "SIL OPEN FONT LICENSE" not in notice or "Version 1.1" not in notice:
        raise ValueError("Expected original OFL 1.1 notice")
    return font, license_data, filename
