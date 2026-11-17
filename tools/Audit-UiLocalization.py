"""Validate host/module catalogs and report Han literals for manual UI review."""
import argparse
from collections import Counter
import csv
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
LOCALES = ('zh-CN', 'en', 'ko', 'ja')
LITERAL = re.compile(r'"(?:\\.|[^"\\\n])+"')
HAN = re.compile(r'[\u3400-\u9fff]')
ARG = re.compile(r'\{([a-zA-Z0-9_.-]+)\}')


def verify(directory):
    data = {tag: json.loads((directory / f'{tag}.json').read_text(encoding='utf-8')) for tag in LOCALES}
    base = data['zh-CN']
    for tag, strings in data.items():
        if set(strings) != set(base):
            raise SystemExit(f'{directory} ({tag}): missing={base.keys() - strings.keys()} extra={strings.keys() - base.keys()}')
        for key, value in strings.items():
            if not isinstance(value, str) or not value.strip() or set(ARG.findall(value)) != set(ARG.findall(base[key])):
                raise SystemExit(f'{directory} ({tag}): invalid text or placeholders for {key}')
    print(f'{directory.name}: {len(base)} keys in each of {len(LOCALES)} locales')


def plugin_roots(platform):
    return sorted(path for path in platform.iterdir() if (path / 'plugin-spec.json').is_file()) if platform.is_dir() else []


def scan(platform):
    for root in ('app/src/main/java', 'plugin-runtime/src/main/java', 'reading/src/main/kotlin', 'speech-recognition/src/main/kotlin'):
        for path in sorted((ROOT / root).rglob('*.kt')):
            rel = path.relative_to(ROOT).as_posix()
            for number, line in enumerate(path.read_text(encoding='utf-8-sig').splitlines(), 1):
                for match in LITERAL.finditer(line):
                    if HAN.search(match[0]):
                        yield root.split('/')[0], rel, number, match[0][1:-1]
    for module in plugin_roots(platform):
        for path in sorted((module / 'src/main').rglob('*')):
            if not path.is_file() or path.suffix not in ('.kt', '.java', '.lua', '.js'):
                continue
            rel = path.relative_to(platform).as_posix()
            for number, line in enumerate(path.read_text(encoding='utf-8-sig').splitlines(), 1):
                for match in LITERAL.finditer(line):
                    if HAN.search(match[0]):
                        yield module.name, rel, number, match[0][1:-1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plugin-sources', type=Path, default=ROOT.parent / 'KIGTTS-Plugins')
    parser.add_argument('--inventory', type=Path, help='Optional UTF-8 CSV. Matches also include presets, aliases, prompts and diagnostic strings.')
    args = parser.parse_args()
    for directory in ('app/src/main/assets/i18n', 'reading/src/main/resources/localization/reading',
                      'speech-recognition/src/main/resources/localization/recognition'):
        verify(ROOT / directory)
    for module in plugin_roots(args.plugin_sources):
        directory = module / 'src/main/assets/i18n'
        missing = [tag for tag in LOCALES if not (directory / f'{tag}.json').exists()]
        if not missing:
            print(f'{module.name}: ', end='')
            try:
                verify(directory)
            except (SystemExit, ValueError) as error:
                print(error)
        else:
            print(f'{module.name}: no asset catalog for {", ".join(missing)} (check alternate resources)')
    inventory = list(scan(args.plugin_sources))
    print('Han literal candidates (manual classification is required):', dict(Counter(r[0] for r in inventory)))
    if args.inventory:
        args.inventory.parent.mkdir(parents=True, exist_ok=True)
        with args.inventory.open('w', encoding='utf-8', newline='') as output:
            writer = csv.writer(output)
            writer.writerow(('module', 'file', 'line', 'literal'))
            writer.writerows(inventory)


if __name__ == '__main__':
    main()
