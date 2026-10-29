"""Build the offline nearby-city fallback from a verified GeoNames extract (requires pypinyin).

Download cities15000.zip from https://download.geonames.org/export/dump/ separately.
Only selected city names and coordinates are retained; the app makes no GeoNames requests.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import unicodedata
import zipfile
from pypinyin import lazy_pinyin

ROOT = Path(__file__).resolve().parent.parent
LOCKED_HASH = "c89ba6601c29c98a717ef8ad021cdebe47f0de3f1c2ebe0a66f5fe7d60cdc352"

def plain(text):
    return re.sub('[^a-z]', '', unicodedata.normalize('NFKD', text).encode('ascii', 'ignore').decode().lower())

def chinese_name(name, alternatives):
    han = [value for value in alternatives.split(',') if re.fullmatch('[\u4e00-\u9fff]+', value)]
    wanted = plain(name)
    matched = [value for value in han if plain(''.join(lazy_pinyin(value))) == wanted or
               len(value) > 1 and value[-1] in '市县區区镇鎮' and plain(''.join(lazy_pinyin(value[:-1]))) == wanted]
    return min(matched, key=len) if matched else name

def build(source):
    raw = source.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == LOCKED_HASH, 'GeoNames extract differs from the reviewed snapshot'
    rows = []
    with zipfile.ZipFile(source) as archive:
        for line in archive.read('cities15000.txt').decode('utf-8').splitlines():
            fields = line.split('\t')
            if fields[8] not in {'CN', 'HK', 'MO', 'TW'}: continue
            name = fields[1]; local = chinese_name(fields[2], fields[3])
            # Established translated names that use non-Mandarin romanisation.
            local = {'1819729': '香港', '1821274': '澳门', '1668341': '台北'}.get(fields[0], local)
            rows.append([fields[0], fields[4], fields[5], local, name])
    folder = ROOT / 'app/src/main/assets/photo-facts'
    folder.mkdir(parents=True, exist_ok=True)
    data = ('# GeoNames cities15000; CC BY 4.0; nearby-city fallback only\n' +
            ''.join('\t'.join(row) + '\n' for row in sorted(rows, key=lambda row: int(row[0])))).encode('utf-8')
    (folder / 'cities.tsv').write_bytes(data)
    (folder / 'source.json').write_text(json.dumps(dict(source='https://download.geonames.org/export/dump/cities15000.zip',
        sourceSha256=LOCKED_HASH, license='CC-BY-4.0', snapshotDate='2026-10-29', records=len(rows),
        outputSha256=hashlib.sha256(data).hexdigest(), changes='Regional subset; names matched to primary romanisation; no address boundaries.'),indent=2)+'\n', encoding='utf-8')
    print('Offline cities:',len(rows),'bytes:',len(data),'Chinese labels:',sum(re.fullmatch('[\u4e00-\u9fff]+',r[3]) is not None for r in rows))

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,default=ROOT/'.tools/photo-place-source/cities15000.zip')
    build(parser.parse_args().source)
