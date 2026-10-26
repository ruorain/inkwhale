#!/usr/bin/env python3
"""把 Lucide 原始 SVG 转成两样东西：
   1) assets/icons.js  —— HTML 视觉稿用的内联片段
   2) assets/drawable/*.xml —— Android VectorDrawable
Lucide 只用到 path/circle/rect/line/polyline/polygon，VectorDrawable 只认 <path>，
所以这里做一次几何等价转换。所有图标都是 24x24 viewBox、stroke 风格。
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "assets" / "icons"
OUT_DRAWABLE = ROOT / "assets" / "drawable"
OUT_JS = ROOT / "assets" / "icons.js"

TB = r'<(path|circle|rect|line|polyline|polygon|ellipse)\b([^>]*?)/?>'
ATTR = re.compile(r'([\w:-]+)="([^"]*)"')
NUM = re.compile(r"-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?")


def attrs(s):
    return dict(ATTR.findall(s))


def num(v, d=0.0):
    return float(v) if v not in (None, "") else d


def fmt(v):
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    return s if s not in ("", "-0") else "0"


def circle_path(cx, cy, r):
    return (
        f"M {fmt(cx - r)} {fmt(cy)} "
        f"a {fmt(r)} {fmt(r)} 0 1 0 {fmt(2 * r)} 0 "
        f"a {fmt(r)} {fmt(r)} 0 1 0 {fmt(-2 * r)} 0"
    )


def rect_path(x, y, w, h, rx, ry):
    if rx <= 0 and ry <= 0:
        return f"M {fmt(x)} {fmt(y)} H {fmt(x + w)} V {fmt(y + h)} H {fmt(x)} Z"
    rx = rx or ry
    ry = ry or rx
    return (
        f"M {fmt(x + rx)} {fmt(y)} "
        f"H {fmt(x + w - rx)} A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + w)} {fmt(y + ry)} "
        f"V {fmt(y + h - ry)} A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + w - rx)} {fmt(y + h)} "
        f"H {fmt(x + rx)} A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x)} {fmt(y + h - ry)} "
        f"V {fmt(y + ry)} A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + rx)} {fmt(y)} Z"
    )


def points_path(raw, close=False):
    n = [float(x) for x in NUM.findall(raw)]
    if len(n) < 4:
        return ""
    parts = [f"M {fmt(n[0])} {fmt(n[1])}"]
    for i in range(2, len(n) - 1, 2):
        parts.append(f"L {fmt(n[i])} {fmt(n[i + 1])}")
    return " ".join(parts) + (" Z" if close else "")


def element_to_path(tag, body):
    a = attrs(body)
    if tag == "path":
        return a.get("d", "").strip(), a
    if tag == "circle":
        return circle_path(num(a.get("cx")), num(a.get("cy")), num(a.get("r"))), a
    if tag == "ellipse":
        cx, cy = num(a.get("cx")), num(a.get("cy"))
        rx, ry = num(a.get("rx")), num(a.get("ry"))
        return (
            f"M {fmt(cx - rx)} {fmt(cy)} "
            f"a {fmt(rx)} {fmt(ry)} 0 1 0 {fmt(2 * rx)} 0 "
            f"a {fmt(rx)} {fmt(ry)} 0 1 0 {fmt(-2 * rx)} 0"
        ), a
    if tag == "rect":
        return rect_path(
            num(a.get("x")), num(a.get("y")), num(a.get("width")),
            num(a.get("height")), num(a.get("rx")), num(a.get("ry")),
        ), a
    if tag == "line":
        return f"M {fmt(num(a.get('x1')))} {fmt(num(a.get('y1')))} L {fmt(num(a.get('x2')))} {fmt(num(a.get('y2')))}", a
    if tag == "polyline":
        return points_path(a.get("points", "")), a
    if tag == "polygon":
        return points_path(a.get("points", ""), close=True), a
    return "", a


def xml_escape(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def main():
    OUT_DRAWABLE.mkdir(parents=True, exist_ok=True)
    js = {}
    widths = {}
    report = []
    for svg_file in sorted(SRC.glob("*.svg")):
        name = svg_file.stem
        raw = svg_file.read_text(encoding="utf-8")
        root = re.search(r"<svg([^>]*)>", raw, re.S)
        ra = attrs(root.group(1)) if root else {}
        sw = ra.get("stroke-width", "2")
        widths[sw] = widths.get(sw, 0) + 1
        paths, fills = [], []
        for m in re.finditer(TB, raw):
            tag, body = m.group(1), m.group(2)
            d, a = element_to_path(tag, body)
            if not d:
                continue
            paths.append(d)
            fill = (a.get("fill") or "").strip()
            # VectorDrawable 只认 #RRGGBB / #AARRGGBB；currentColor 交给运行时 setTint
            if fill and fill != "none" and not fill.startswith("#") and not fill.startswith("@"):
                fill = "#FF000000"
            fills.append(fill if fill and fill != "none" else None)
        js[name] = "".join(f'<path d="{xml_escape(d)}"/>' for d in paths)
        # VectorDrawable
        body = []
        for d, fill in zip(paths, fills):
            stroke = 'android:strokeColor="#FF000000" android:strokeWidth="%s"' % sw
            stroke += ' android:strokeLineCap="round" android:strokeLineJoin="round"'
            if fill:
                body.append(
                    '    <path\n        android:pathData="%s"\n        android:fillColor="%s"\n        %s />'
                    % (xml_escape(d), fill, stroke)
                )
            else:
                body.append(
                    '    <path\n        android:pathData="%s"\n        %s />' % (xml_escape(d), stroke)
                )
        xml = (
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n'
            '    android:height="24dp"\n'
            '    android:viewportWidth="24"\n'
            '    android:viewportHeight="24">\n'
            + "\n".join(body)
            + "\n</vector>\n"
        )
        (OUT_DRAWABLE / f"ic_{name.replace('-', '_')}.xml").write_text(xml, encoding="utf-8")
        report.append((name, len(paths)))

    OUT_JS.write_text(
        "/* Lucide Icons v1.61.0 — ISC License — https://lucide.dev */\n"
        "window.ICONS = " + json.dumps(js, ensure_ascii=False, indent=0) + ";\n",
        encoding="utf-8",
    )
    print(f"icons: {len(js)}   vector drawables: {len(list(OUT_DRAWABLE.glob('*.xml')))}")
    print("stroke-width distribution:", widths)
    thin = [r for r in report if r[1] == 0]
    if thin:
        print("WARNING empty:", thin)
    print("sizes: icons.js %.1f KB" % (OUT_JS.stat().st_size / 1024))


if __name__ == "__main__":
    main()
