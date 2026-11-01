"""Check shipped originals and artwork, not an imitation of the importer implementation."""
import hashlib
import json
from pathlib import Path
import unittest
from PIL import Image
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[2]


class InformationResourceAssetsTest(unittest.TestCase):
    def test_remi_retains_all_22_original_1024_images(self):
        root = ROOT / "distribution/resources/remi-stickers"
        origins = json.loads((root / "origins.json").read_text(encoding="utf-8"))
        content = json.loads((root / "content.json").read_text(encoding="utf-8"))
        self.assertEqual(22, len(origins["items"]))
        self.assertEqual(22, len(content["snapshot"]["items"]))
        for item in origins["items"]:
            file = root / item["file"]
            self.assertEqual(item["sha256"], hashlib.sha256(file.read_bytes()).hexdigest())
            with Image.open(file) as image:
                self.assertEqual((1024, 1024), image.size)
                self.assertEqual(tuple(item["alpha"]), image.convert("RGBA").getchannel("A").getextrema())

    def test_modified_font_subsets_keep_ofl_and_new_family_names(self):
        root = ROOT / "distribution/resources/info-stickers"
        for origin in json.loads((root / "font-origins.json").read_text(encoding="utf-8")):
            family = origin["family"]
            file = root / "fonts" / (family + ".ttf")
            notice = root / "licenses" / (family + "-OFL.txt")
            self.assertEqual(origin["subsetSha256"], hashlib.sha256(file.read_bytes()).hexdigest())
            self.assertEqual(origin["licenseSha256"], hashlib.sha256(notice.read_bytes()).hexdigest())
            self.assertIn("SIL OPEN FONT LICENSE", notice.read_text(encoding="utf-8-sig"))
            with TTFont(file) as font:
                self.assertEqual("KIGTTS" + family, font["name"].getDebugName(1))
                self.assertTrue(set(map(ord,"0123456789:Saturday")) <= font.getBestCmap().keys())
                if family != "InfoScript": self.assertTrue(set(map(ord,"天气未知上海星期")) <= font.getBestCmap().keys())

    def test_previews_report_actual_dimensions_and_licensing_is_separate(self):
        config = json.loads((ROOT / "distribution/resource-projects.json").read_text(encoding="utf-8"))
        for entry in config["projects"]:
            if not entry["preview"].endswith(".png"): continue
            with Image.open(ROOT / "distribution/resources/previews" / entry["preview"]) as image:
                self.assertEqual((entry["width"], entry["height"]), image.size)
        remi = next(p for p in config["projects"] if p["name"] == "偶域 Remi")
        self.assertNotIn("CC0", remi["license"])


if __name__ == "__main__": unittest.main()
