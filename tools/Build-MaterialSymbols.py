"""Build the bundled static icon font from the pinned Google source; fonttools==4.56.0."""
import argparse
import hashlib
from pathlib import Path

import fontTools
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

SOURCE_SHA256 = "32e4011709e055596eb8d2dd0f7cb547b47c1d40ac9ecb8fad54f7f7eac202e7"
SOURCE_URL = "https://raw.githubusercontent.com/google/material-design-icons/master/variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Downloaded original Google variable font")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "app/src/main/assets/icons/material-symbols-rounded.ttf")
    args = parser.parse_args()
    if fontTools.__version__ != "4.56.0":
        raise SystemExit("Use fonttools==4.56.0 for the documented build")
    if hashlib.sha256(args.source.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise SystemExit("Source digest differs from the pinned Google font; review before updating")
    font = instantiateVariableFont(TTFont(args.source), {"FILL": 0, "GRAD": 0, "opsz": 24, "wght": 400}, inplace=True)
    options = subset.Options()
    options.layout_features = []
    options.drop_tables += ["GSUB", "GPOS", "GDEF"]
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=list(font.getBestCmap()))
    subsetter.subset(font)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    font.save(args.output)
    print(f"{args.output}: {args.output.stat().st_size} bytes")


if __name__ == "__main__":
    main()
