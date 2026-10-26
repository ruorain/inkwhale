#!/usr/bin/env python3
"""把 preview/index.html 打成单文件预览（内联图标），输出 预览.html。"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
src = ROOT / "preview" / "index.html"
icons_js = (ROOT / "assets" / "icons.js").read_text(encoding="utf-8")

html = src.read_text(encoding="utf-8")
tag = '<script src="../assets/icons.js"></script>'
assert tag in html, "找不到图标脚本引用"
html = html.replace(tag, "<script>\n" + icons_js + "</script>")

out = ROOT / "预览.html"
out.write_text(html, encoding="utf-8")
print(f"written {out}  {out.stat().st_size/1024:.1f} KB")
