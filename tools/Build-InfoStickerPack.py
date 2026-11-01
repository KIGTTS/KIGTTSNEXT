"""Build original editable photo-information stickers, subset OFL fonts and outlined store artwork."""
import hashlib
import io
import json
from pathlib import Path
from urllib.parse import unquote
import xml.etree.ElementTree as ET

import requests
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "distribution/resources/info-stickers"
FONT_BASE = "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Fonts/resolve/master/"


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def prepare_fonts():
    catalog = json.loads((ROOT / ".tools/font-source/index.json").read_text(encoding="utf-8"))
    keys = {f["id"]: f for f in catalog["fonts"]}
    sources = []
    for family, key in (("InfoSans", "noto-sans-sc"), ("InfoSerif", "noto-serif-sc")):
        entry = keys[key]
        file = ROOT / ".tools/font-source" / unquote(entry["url"].removeprefix(FONT_BASE))
        assert hashlib.sha256(file.read_bytes()).hexdigest() == entry["sha256"]
        sources.append((family, file, file.with_name("OFL.txt"), entry["upstream"]))
    base = "https://raw.githubusercontent.com/google/fonts/6c1dbe27dcf7d817d4b62cec033ba77fa8e2a5ec/ofl/pacifico/"
    cache = ROOT / ".tools/info-sticker-fonts"; cache.mkdir(exist_ok=True)
    for name in ("Pacifico-Regular.ttf", "OFL.txt"):
        target = cache / name
        if not target.exists():
            response = requests.get(base + name, timeout=(15, 90)); response.raise_for_status(); target.write_bytes(response.content)
    assert b"SIL OPEN FONT LICENSE" in (cache / "OFL.txt").read_bytes()
    sources.append(("InfoScript", cache / "Pacifico-Regular.ttf", cache / "OFL.txt", base + "Pacifico-Regular.ttf"))
    locks = []
    for family, original, license_file, upstream in sources:
        font = TTFont(original, recalcTimestamp=False)
        if "fvar" in font:
            location = {a.axisTag: (600 if a.axisTag == "wght" else a.defaultValue) for a in font["fvar"].axes}
            font = instantiateVariableFont(font, location, inplace=True)
        # Common Chinese characters remain editable; use original family names only for unmodified store fonts.
        def needed(c):
            if c < 0x300 or 0x2000 <= c <= 0x206F or 0x3000 <= c < 0x3100 or 0xFF00 <= c <= 0xFFEF: return True
            if 0x3400 <= c <= 0x9FFF:
                try: chr(c).encode("gb2312"); return True
                except UnicodeEncodeError: pass
            return False
        chars = [c for c in font.getBestCmap() if needed(c)]
        options = subset.Options(); options.hinting = False; options.name_IDs = ["*"]; options.name_legacy = True
        worker = subset.Subsetter(options=options); worker.populate(unicodes=chars); worker.subset(font)
        # A subset is a modified font: use our own family names, preserving copyright and OFL notices.
        for record in font["name"].names:
            if record.nameID in (1, 3, 4, 6, 16): record.string = ("KIGTTS" + family).encode(record.getEncoding(), errors="replace")
        output = PROJECT / "fonts" / (family + ".ttf"); output.parent.mkdir(parents=True, exist_ok=True)
        font.save(output); font.close(); assert output.stat().st_size <= 12 * 1024 * 1024
        notice = PROJECT / "licenses" / (family + "-OFL.txt"); notice.parent.mkdir(exist_ok=True)
        notice.write_bytes(license_file.read_bytes())
        locks.append(dict(family=family, upstream=upstream, originalSha256=hashlib.sha256(original.read_bytes()).hexdigest(),
                          licenseSha256=hashlib.sha256(license_file.read_bytes()).hexdigest(), subsetSha256=hashlib.sha256(output.read_bytes()).hexdigest()))
    write_json(PROJECT / "font-origins.json", locks)


def text(value, x, y, size, family="InfoSans", anchor="start", fill="{{ink}}", transform=""):
    # Explicit text bounds also keep Unknown, long place names and manual edits inside their layout.
    fit = max(180, min(1024, 2*x-80 if anchor=="middle" else x-80 if anchor=="end" else 1100-x))
    if "{{weather}}" in value: fit = min(fit, 620)
    if "{{place}}" in value: fit = min(fit, 850)
    return f'<text x="{x}" y="{y}" font-family="{family}" font-size="{size}" data-fit-width="{fit}" text-anchor="{anchor}" fill="{fill}"' + (f' transform="{transform}"' if transform else '') + f'>{value}</text>'


def line(x1, y1, x2, y2, width=3, color="{{ink}}"):
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="{width}"/>'


def build_art():
    items, bindings = [], {}
    fonts = {f: f"fonts/{f}.ttf" for f in ("InfoSans", "InfoSerif", "InfoScript")}
    def add(key, name, size, art, fields, extra=()):
        params = [dict(id=id, label=label, kind="TEXT", default="Unknown" if source.endswith("_EN") else "未知", maxLength=limit) for id, label, source, limit in fields]
        params += [dict(id="ink", label="文字颜色", kind="COLOR", default="#FFFFFF"),
                   dict(id="accent", label="点缀颜色", kind="COLOR", default="#E2BA60")] + list(extra)
        w, h = size; relative = f"art/{key}.svg"
        data = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">{art}</svg>\n'
        target = PROJECT / relative; target.parent.mkdir(exist_ok=True); target.write_text(data, encoding="utf-8", newline="\n")
        used = {f: p for f, p in fonts.items() if f'font-family="{f}"' in data}
        items.append(dict(id=key, name=name, template=relative, width=w, height=h, parameters=params, fonts=used))
        if fields: bindings[key] = {id: source for id, _, source, _ in fields}
    time = ("time", "拍摄时间", "TIME", 12); date = ("date", "拍摄日期", "DATE", 16)
    place = ("place", "拍摄地点", "LOCATION", 16); weather = ("weather", "拍摄天气", "WEATHER", 20)
    day = ("day", "日", "DAY", 4); month = ("month", "月", "MONTH", 4); year = ("year", "年", "YEAR", 8)
    add("clock", "简约时刻", (1024, 400), text("{{time}}",512,245,220,anchor="middle") + line(110,280,914,280,4) + text("{{date}}",512,350,54,anchor="middle"), [time,date])
    add("calendar", "日历方章", (512,768), '<rect x="72" y="24" width="368" height="720" fill="none" stroke="{{ink}}" stroke-width="4"/>' + text("{{day}}",256,360,238,anchor="middle") + line(115,410,397,410) + text("{{month}}",256,540,96,anchor="middle") + text("{{year}}",256,670,76,anchor="middle"), [day,month,year])
    add("time-place", "时地竖线", (1024,360), line(55,45,55,310,8,"{{accent}}") + text("{{time}}",100,200,176) + text("{{place}}",103,287,48), [time,place])
    pin = '<path d="M512 36c-42 0-66 30-66 65 0 48 66 101 66 101s66-53 66-101c0-35-24-65-66-65Z" fill="{{accent}}"/><circle cx="512" cy="100" r="23" fill="{{ink}}"/>'
    add("location", "定位横章", (1024,470), pin + text("{{place}}",512,290,54,anchor="middle") + line(130,330,894,330) + text("{{date}}  {{time}}",512,412,45,anchor="middle"), [place,date,time])
    rays = ''.join(f'<g transform="rotate({a} 180 165)">{line(180,55,180,83,8)}</g>' for a in range(0,360,45))
    add("weather", "天气一刻", (1024,400), '<circle cx="180" cy="165" r="57" fill="none" stroke="{{ink}}" stroke-width="7"/>'+rays+text("{{weather}}",340,195,54)+line(340,233,965,233)+text("{{date}}",342,300,45), [weather,date])
    add("weekday", "星期徽章", (900,600), '<ellipse cx="450" cy="300" rx="288" ry="230" fill="none" stroke="{{ink}}" stroke-width="3" stroke-dasharray="9 10"/>'+text("{{weekday}}",450,340,124,"InfoSerif","middle")+text("{{date}}",450,440,43,anchor="middle"), [("weekday","星期","WEEKDAY",8),date])
    add("weekday-script", "手写星期", (1200,450), text("{{weekday}}",600,250,152,"InfoScript","middle")+line(200,310,1000,310,7)+text("{{date}}",600,395,48,anchor="middle"), [("weekday","星期（英文）","WEEKDAY_EN",12),date])
    add("vertical-date", "竖排日期", (440,1024), '<path d="M75 30H365L410 75V949L365 994H75L30 949V75Z" fill="none" stroke="{{ink}}" stroke-width="3"/><rect x="49" y="49" width="342" height="926" rx="8" fill="none" stroke="{{ink}}" stroke-width="2"/>'+text("{{month}}",220,335,160,"InfoSerif","middle")+text("月",220,435,64,"InfoSerif","middle")+text("{{day}}",220,660,160,"InfoSerif","middle")+text("日",220,760,64,"InfoSerif","middle")+line(100,815,340,815)+text("{{year}}",220,904,65,"InfoSerif","middle"), [month,day,year])
    add("film", "胶片铭牌", (1200,430), line(70,48,1130,48,5)+text("{{date}}",74,165,86)+text("{{time}}",1130,165,86,anchor="end")+line(70,212,1130,212,2)+text("{{place}}",75,325,56)+text("REC",1120,325,48,anchor="end",fill="{{accent}}"), [date,time,place])
    add("record-strip", "记录长条", (1536,300), line(50,45,50,250,6,"{{accent}}")+text("{{date}}  {{time}}",94,114,64)+text("{{place}}",94,220,52)+text("{{weather}}",1470,220,40,anchor="end"), [date,time,place,weather])
    add("memorial", "纪念日期", (1024,700), text("{{day}}",512,365,320,"InfoSerif","middle")+line(145,425,879,425,4)+text("{{year}} / {{month}}",512,565,88,"InfoSerif","middle"), [day,month,year])
    add("report", "拍摄简报", (1200,700), '<rect x="40" y="40" width="1120" height="620" rx="24" fill="none" stroke="{{ink}}" stroke-width="3"/>'+text("{{date}}",90,180,80)+text("{{time}}",1110,180,80,anchor="end")+line(90,235,1110,235)+text("{{place}}",90,360,60)+text("{{weather}}",90,500,52)+text("A LITTLE MOMENT",90,600,32,fill="{{accent}}"), [date,time,place,weather])
    add("greeting", "你好世界", (1200,550), text("{{greeting}}",600,280,120,"InfoSerif","middle")+text("{{date}}",600,410,48,anchor="middle"), [date], [dict(id="greeting",label="问候",kind="TEXT",default="你好世界",maxLength=8)])
    add("date-dash", "日期刻度", (1200,320), line(80,70,1120,70,3)+text("{{year}}",95,215,120)+text("{{month}}",640,215,120,anchor="middle")+text("{{day}}",1105,215,120,anchor="end")+line(480,115,480,225,3,"{{accent}}")+line(800,115,800,225,3,"{{accent}}"), [year,month,day])
    write_json(PROJECT / "generators.json", dict(schemaVersion=1,items=items))
    write_json(PROJECT / "photo-defaults.json", dict(schemaVersion=1,generators=bindings))
    write_json(PROJECT / "content.json", {})
    licenses = ["LICENSE.txt"] + [f"licenses/{f}-OFL.txt" for f in fonts]
    write_json(PROJECT / "package.json", dict(id="resources.info-stickers",name="照片信息贴纸",version="1.0.0",versionCode=1,resourceType="STICKERS",licenses=licenses))
    (PROJECT / "LICENSE.txt").write_text("Original sticker layouts and SVG artwork: CC0 1.0 Universal.\nhttps://creativecommons.org/publicdomain/zero/1.0/\n\nEmbedded font subsets retain their individual SIL OFL 1.1 licenses in licenses/. Font software is not CC0.\n",encoding="utf-8",newline="\n")
    return items


def outlined_preview(item, samples):
    source = (PROJECT / item["template"]).read_text(encoding="utf-8")
    values = {p["id"]: p["default"] for p in item["parameters"]} | samples
    for key, value in values.items(): source = source.replace("{{" + key + "}}", value)
    root = ET.fromstring(source); ns = "{http://www.w3.org/2000/svg}"
    for parent in root.iter():
        for element in list(parent):
            if element.tag != ns+"text": continue
            face = TTFont(PROJECT / item["fonts"][element.attrib["font-family"]]); glyphs = face.getGlyphSet(); cmap = face.getBestCmap()
            size=float(element.attrib["font-size"]); scale=size/face["head"].unitsPerEm
            value=element.text or ""; assert all(ord(c) in cmap for c in value),value
            width=sum(glyphs[cmap[ord(c)]].width*scale for c in value)
            fit=float(element.attrib.get("data-fit-width",width or 1))
            if width>fit: scale*=fit/width; width=fit
            x=float(element.attrib["x"]); y=float(element.attrib["y"])
            anchor=element.attrib.get("text-anchor"); x-=width/2 if anchor=="middle" else width if anchor=="end" else 0
            paths=[]
            for char in value:
                glyph=glyphs[cmap[ord(char)]]; pen=SVGPathPen(glyphs); glyph.draw(TransformPen(pen,(scale,0,0,-scale,x,y)))
                paths.append(pen.getCommands());x+=glyph.width*scale
            attrs={k:v for k,v in element.attrib.items() if k not in ("x","y","font-family","font-size","text-anchor","data-fit-width")}
            attrs["d"]=" ".join(paths);replacement=ET.Element(ns+"path",attrs);parent.insert(list(parent).index(element),replacement);parent.remove(element);face.close()
    return ET.tostring(root,encoding="unicode").replace("ns0:","").replace(":ns0","")


def preview(items):
    from PIL import Image, ImageDraw
    import resvg_py
    samples=dict(time="12:00",date="2026.10.31",place="上海 · 漫展现场",weather="晴 22°C",day="31",month="10",year="2026",weekday="星期六",ink="#FFFFFF",accent="#E2BA60")
    out=ROOT/"distribution/resources/previews";canvas=Image.new("RGB",(1600,1800),"#142D30")
    for i,item in enumerate(items):
        local=dict(samples)
        if item["id"]=="weekday-script":local["weekday"]="Saturday"
        svg=outlined_preview(item,local)
        p=out/("info-"+item['id']+".svg");p.write_text(svg,encoding="utf-8",newline="\n")
        png=resvg_py.svg_to_bytes(svg_string=svg,width=700,height=330)
        im=Image.open(io.BytesIO(png)).convert("RGBA");im.thumbnail((700,195),Image.Resampling.LANCZOS)
        x=50+(i%2)*790;y=20+(i//2)*250;canvas.paste(im,(x+(700-im.width)//2,y+(210-im.height)//2),im)
        ImageDraw.Draw(canvas).text((x+10,y+212),str(i+1),fill="#A3D6D5")
        # Individual preview files are intermediate; the pack ships transparent templates, not these example values.
        p.unlink()
    canvas.save(out/"info-stickers.png")


if __name__ == "__main__":
    PROJECT.mkdir(parents=True,exist_ok=True)
    prepare_fonts();items=build_art();preview(items)
    print(json.dumps(dict(stickers=len(items),project=str(PROJECT)),ensure_ascii=False))
