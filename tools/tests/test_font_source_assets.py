import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from font_source_assets import FONT_MIRROR, original_assets, source_urls


class FontSourceAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.font = b"\x00\x01\x00\x00" + b"original font bytes"
        self.notice = b"Copyright Example\nSIL OPEN FONT LICENSE Version 1.1\n"
        (self.root / "font.ttf").write_bytes(self.font)
        (self.root / "OFL.txt").write_bytes(self.notice)
        self.lock = dict(id="legacy", upstream="https://github.com/example/font/releases/tag/v1.0",
            downloadUrl=FONT_MIRROR + "fonts/legacy/font.ttf", licenseDownloadUrl=FONT_MIRROR + "fonts/legacy/OFL.txt",
            sha256=hashlib.sha256(self.font).hexdigest(), licenseSha256=hashlib.sha256(self.notice).hexdigest(),
            legacyFiles=dict(font="font.ttf", license="OFL.txt"))

    def test_legacy_copy_preserves_original_font_and_license(self):
        self.assertEqual((self.font, self.notice, "font.ttf"), original_assets(self.lock, None, self.root))

    def test_changed_font_or_license_is_rejected(self):
        for name in ("font.ttf", "OFL.txt"):
            file = self.root / name
            original = file.read_bytes()
            file.write_bytes(original + b"changed")
            with self.assertRaisesRegex(ValueError, "source lock"):
                original_assets(self.lock, None, self.root)
            file.write_bytes(original)

    def test_missing_or_non_ofl_notice_is_not_accepted(self):
        self.lock["licenseSha256"] = hashlib.sha256(b"free font").hexdigest()
        (self.root / "OFL.txt").write_bytes(b"free font")
        with self.assertRaisesRegex(ValueError, "OFL"):
            original_assets(self.lock, None, self.root)

    def test_legacy_paths_cannot_escape_source(self):
        self.lock["legacyFiles"]["font"] = "../font.ttf"
        with self.assertRaisesRegex(ValueError, "escapes"):
            original_assets(self.lock, None, self.root)

    def test_unexpected_mirror_or_upstream_is_rejected(self):
        self.lock["downloadUrl"] = "https://example.org/font.ttf"
        with self.assertRaisesRegex(ValueError, "mirror"):
            source_urls(self.lock)
        self.lock["upstream"] = "http://github.com/example/font"
        with self.assertRaisesRegex(ValueError, "upstream"):
            source_urls(self.lock)

    def test_existing_google_lock_keeps_its_original_download_contract(self):
        url = "https://raw.githubusercontent.com/google/fonts/abc/ofl/example/Example[wght].ttf"
        self.assertEqual((url, url.rsplit("/", 1)[0] + "/OFL.txt", "Example[wght].ttf"), source_urls(dict(upstream=url)))


if __name__ == "__main__":
    unittest.main()
