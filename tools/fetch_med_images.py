# -*- coding: utf-8 -*-
"""
抓取真实药品包装照片（Bing 图片搜索 → 本地保存）。
用法：python tools/fetch_med_images.py
输出：images/medicines/<序号>.jpg + images/medicines/index.json（药名 -> 相对路径）
可重复执行：已存在的图片会跳过，只补抓缺失的。
"""
import io
import json
import os
import re
import sys
import time
import html
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

from PIL import Image

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(BASE, "images", "medicines")
MANIFEST = os.path.join(OUT_DIR, "index.json")
API = "http://localhost:8080/api/medicines"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

SKIP_WORDS = ("logo", "icon", "qrcode", "二维码", "sprite", "banner", "loading", "default")


def http_get(url, timeout=12, referer=None):
    req = urllib.request.Request(url)
    req.add_header("User-Agent", UA)
    req.add_header("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
    if referer:
        req.add_header("Referer", referer)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def bing_candidates(name, brand, limit=6):
    q = name if not brand else f"{name} 药盒"
    url = "https://www.bing.com/images/search?" + urllib.parse.urlencode(
        {"q": q, "form": "HDRSC2", "first": "1", "count": "20"}
    )
    try:
        page = http_get(url, timeout=15).decode("utf-8", "ignore")
    except Exception:
        return []
    urls = re.findall(r'murl&quot;:&quot;(.*?)&quot;', page)
    if not urls:
        urls = [html.unescape(u) for u in re.findall(r'"murl":"(.*?)"', page)]
    seen, out = set(), []
    for u in urls:
        u = html.unescape(u)
        low = u.lower()
        if u in seen:
            continue
        seen.add(u)
        if not low.startswith("http"):
            continue
        if any(w in low for w in SKIP_WORDS):
            continue
        if not re.search(r"\.(jpe?g|png|webp|bmp)", low):
            continue
        out.append(u)
        if len(out) >= limit:
            break
    return out


def to_square(raw, path, size=420):
    img = Image.open(io.BytesIO(raw))
    img.load()
    if img.width < 180 or img.height < 120:
        return False
    if img.mode in ("RGBA", "LA", "P"):
        img = img.convert("RGBA")
        bg = Image.new("RGB", img.size, (255, 255, 255))
        bg.paste(img, mask=img.split()[-1])
        img = bg
    else:
        img = img.convert("RGB")
    img.thumbnail((size, size), Image.LANCZOS)
    canvas = Image.new("RGB", (size, size), (255, 255, 255))
    canvas.paste(img, ((size - img.width) // 2, (size - img.height) // 2))
    canvas.save(path, "JPEG", quality=84, optimize=True)
    return True


def fetch_one(idx, item):
    name = item.get("name") or ""
    brand = (item.get("brand") or "").replace("牌", "")
    fname = f"{idx:03d}.jpg"
    path = os.path.join(OUT_DIR, fname)
    if os.path.exists(path) and os.path.getsize(path) > 8000:
        return idx, name, fname, "cached"

    for url in bing_candidates(name, brand):
        try:
            raw = http_get(url, referer="https://www.bing.com/")
            if len(raw) < 8000:
                continue
            if to_square(raw, path):
                return idx, name, fname, "ok"
        except Exception:
            continue
    return idx, name, None, "fail"


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    with urllib.request.urlopen(API, timeout=15) as r:
        items = json.load(r)
    print(f"共 {len(items)} 条药品", flush=True)

    manifest = {}
    if os.path.exists(MANIFEST):
        try:
            with open(MANIFEST, encoding="utf-8") as f:
                manifest = json.load(f)
        except Exception:
            manifest = {}

    results = []
    with ThreadPoolExecutor(max_workers=5) as pool:
        futures = {pool.submit(fetch_one, i + 1, it): it for i, it in enumerate(items)}
        done = 0
        for fut in as_completed(futures):
            try:
                idx, name, fname, status = fut.result()
            except Exception as e:
                done += 1
                print("ERR", e, flush=True)
                continue
            done += 1
            if fname:
                manifest[name] = f"images/medicines/{fname}"
            results.append((idx, name, status))
            if done % 10 == 0:
                print(f"进度 {done}/{len(items)}", flush=True)

    ok = sum(1 for _, _, s in results if s in ("ok", "cached"))
    fail = [n for _, n, s in results if s == "fail"]
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)

    print(f"完成：成功 {ok} / 失败 {len(fail)}", flush=True)
    if fail:
        print("失败清单：" + "、".join(fail[:40]), flush=True)


if __name__ == "__main__":
    main()
