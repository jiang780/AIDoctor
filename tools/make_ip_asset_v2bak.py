# -*- coding: utf-8 -*-
"""把 AI医生动漫IP形象设计 (2).jpeg 处理成悬浮挂件素材。

原图是「角色 + 无人机 + HUD 装饰」的完整插画，近白渐变底 + 右下角水印。
挂件只保留角色本体，处理四步：

1. 裁到角色范围（光环 → 鞋底），丢掉水印；
2. 轻微模糊后从四边洪泛填充取 alpha（背景近白带噪点，直接键控会漏白斑）；
3. 清掉角色以外的装饰：无人机区段按大衣轮廓收边（无人机在角色身后，清掉不留残影），
   其余区段只保留经过画面中线的连通段（HUD 面板、光圈都在两侧）；
4. 收边羽化、裁掉透明边，导出两档。

坐标为「工作尺寸」下的像素（WORK_W × WORK_H 由裁切框等比缩放得到）。
"""
import os

from PIL import Image, ImageDraw, ImageFilter

SRC = 'AI医生动漫IP形象设计 (2).jpeg'
OUT1 = 'images/aiip.png'
OUT2 = 'images/aiip@2x.png'

CROP = (478, 24, 1032, 1505)   # 角色 + 光环 + 鞋底
WORK_H = 778                  # 保持 0.5256 的缩放比，下面的坐标都按这个比例标定
MARK = (255, 0, 255)
THRESH = 21                    # 洪泛容差：吃掉近白渐变，不钻白大褂

# 无人机纵向区间（工作坐标），只在这一段里做颜色判定
BAND = (278, 520)
# 无人机机身上的取样点（工作坐标）
SEEDS = [(34, 372), (44, 350), (22, 402), (252, 372), (246, 350), (262, 402)]
HUD_TOP = (60, 292, 50, 230)   # 顶部两块 HUD 面板：y 范围 + 左/右安全 x


def cut_background(rgb):
    w, h = rgb.size
    work = rgb.filter(ImageFilter.GaussianBlur(1.4))
    seeds = ([(x, 0) for x in range(0, w, 4)] + [(x, h - 1) for x in range(0, w, 4)] +
             [(0, y) for y in range(0, h, 4)] + [(w - 1, y) for y in range(0, h, 4)])
    for seed in seeds:
        if work.getpixel(seed) != MARK:
            ImageDraw.floodfill(work, seed, MARK, thresh=THRESH)
    wp = work.load()

    alpha = Image.new('L', (w, h), 255)
    ap = alpha.load()
    for y in range(h):
        for x in range(w):
            if wp[x, y] == MARK:
                ap[x, y] = 0
    return alpha


def keep_center_run(alpha, y_from, y_to):
    """只保留经过画面中线的连通段，两侧的装饰与光晕一并清掉。"""
    w, h = alpha.size
    ap = alpha.load()
    cx = w // 2
    for y in range(max(0, y_from), min(h, y_to)):
        row = [ap[x, y] > 0 for x in range(w)]
        if not any(row):
            continue
        seed = cx if row[cx] else max((x for x in range(w) if row[x]),
                                      key=lambda x: -abs(x - cx))
        lo = seed
        while lo > 0 and row[lo - 1]:
            lo -= 1
        hi = seed
        while hi < w - 1 and row[hi + 1]:
            hi += 1
        for x in range(0, lo):
            ap[x, y] = 0
        for x in range(hi + 1, w):
            ap[x, y] = 0
    return alpha


def trim_drone(alpha):
    w, h = alpha.size
    ap = alpha.load()
    for y in range(max(0, BAND[0]), min(h, BAND[1])):
        left = int(round(interp(LEFT_EDGE, y)))
        right = int(round(interp(RIGHT_EDGE, y)))
        for x in range(0, min(w, max(0, left))):
            ap[x, y] = 0
        for x in range(max(0, right), w):
            ap[x, y] = 0
    return alpha


def erase_hud(alpha):
    """顶部左右两侧的 HUD 面板：人物头部只占中间，两侧一律清空。"""
    w, h = alpha.size
    ap = alpha.load()
    y0, y1, xl, xr = HUD_TOP
    for y in range(y0, min(h, y1)):
        for x in range(0, min(w, xl)):
            ap[x, y] = 0
        for x in range(max(0, xr), w):
            ap[x, y] = 0
    return alpha


def drop_mint(alpha, rgb):
    """把无人机机身的薄荷色抹掉，只抹「从画面左右边缘蔓延进来」的那一片。

    角色身上也有薄荷色（头发、马甲、袖章），但它们被大衣包住、碰不到边缘，
    所以不会误伤；抹掉之后机身与大衣断开，后续按行取中段时自然丢弃。
    """
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()
    y0, y1 = BAND

    def mint(x, y):
        r, g, b = px[x, y]
        return g - r >= 16 and g >= 110 and b >= g - 45

    seen = [[False] * w for _ in range(h)]
    stack = []
    for y in range(max(0, y0), min(h, y1)):
        for x in (0, w - 1):
            if not seen[y][x] and mint(x, y):
                seen[y][x] = True
                stack.append((x, y))
    while stack:
        x, y = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and y0 <= ny < y1 and not seen[ny][nx] and mint(nx, ny):
                seen[ny][nx] = True
                stack.append((nx, ny))
    for y in range(h):
        for x in range(w):
            if seen[y][x]:
                ap[x, y] = 0
    return alpha


def drop_pale_from_border(alpha, rgb):
    """从画面四边向内扩散，凡是很浅、几乎无彩的像素都算背景。

    大衣内部同样是浅色，但它被深色描边围住，扩散进不去；能进去的只有角色外圈的
    浅色光晕和 HUD 底板的浅色面——正是要去掉的部分。
    """
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()

    def pale(x, y):
        r, g, b = px[x, y]
        return (r + g + b) / 3.0 >= 222 and (max(r, g, b) - min(r, g, b)) <= 18

    seen = [[False] * w for _ in range(h)]
    stack = []
    for x in range(w):
        for y in (0, h - 1):
            if not seen[y][x] and pale(x, y):
                seen[y][x] = True
                stack.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if not seen[y][x] and pale(x, y):
                seen[y][x] = True
                stack.append((x, y))
    while stack:
        x, y = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and pale(nx, ny):
                seen[ny][nx] = True
                stack.append((nx, ny))
    for y in range(h):
        for x in range(w):
            if seen[y][x]:
                ap[x, y] = 0
    return alpha


def manual_clean(alpha, rgb):
    """两处局部收尾：左侧还挂着无人机的一段导管，右上角是 HUD 底板的浅色面。"""
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()
    # 左：只清薄荷偏青的像素，大衣的深色描边（蓝多于绿）不会被误伤
    for y in range(318, min(h, 412)):
        for x in range(0, min(w, 88)):
            r, g, b = px[x, y]
            if g - r >= 18 and g >= 60 and b >= r + 8:
                ap[x, y] = 0
    # 右上：只清浅色的 HUD 底板，头发（薄荷色）不受影响
    # 右上：整块清掉。头发是"绿多于蓝"（b < g），HUD 面板的青色描边与底板是"蓝 ≥ 绿"，
    # 按这个差别把面板连边带面一起带走，发丝一根不动。
    for y in range(20, min(h, 172)):
        for x in range(218, w):
            r, g, b = px[x, y]
            pale = (r + g + b) / 3.0 >= 232 and (max(r, g, b) - min(r, g, b)) <= 16
            if b - g > -10 or pale:
                ap[x, y] = 0
    return alpha


def fill_holes(alpha):
    """把被不透明像素包围的空洞补回来（袖章、纽扣这些细节被上面的步骤误伤过）。"""
    w, h = alpha.size
    ap = alpha.load()
    seen = [[False] * w for _ in range(h)]
    stack = []
    for x in range(w):
        for y in (0, h - 1):
            if ap[x, y] == 0 and not seen[y][x]:
                seen[y][x] = True
                stack.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if ap[x, y] == 0 and not seen[y][x]:
                seen[y][x] = True
                stack.append((x, y))
    while stack:
        x, y = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and ap[nx, ny] == 0:
                seen[ny][nx] = True
                stack.append((nx, ny))
    for y in range(h):
        for x in range(w):
            if ap[x, y] == 0 and not seen[y][x]:
                ap[x, y] = 255
    return alpha


def drop_specks(alpha, min_area=700):
    """清掉零碎的浅色小岛（HUD 面板残留的细线、光点）。"""
    w, h = alpha.size
    ap = alpha.load()
    seen = [[False] * w for _ in range(h)]
    for y0 in range(h):
        for x0 in range(w):
            if ap[x0, y0] == 0 or seen[y0][x0]:
                continue
            comp = [(x0, y0)]
            seen[y0][x0] = True
            head = 0
            while head < len(comp):
                x, y = comp[head]
                head += 1
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and ap[nx, ny] > 0:
                        seen[ny][nx] = True
                        comp.append((nx, ny))
            if len(comp) < min_area:
                for x, y in comp:
                    ap[x, y] = 0
    return alpha


def tidy(alpha, erode=1, blur=1.0):
    """先把小凹口填平再收边羽化，免得轮廓上留下锯齿。"""
    alpha = alpha.filter(ImageFilter.MaxFilter(3))
    if erode:
        alpha = alpha.filter(ImageFilter.MinFilter(3))
    if blur:
        alpha = alpha.filter(ImageFilter.GaussianBlur(blur))
    return alpha


def trim(rgba, pad_ratio=0.02):
    bbox = rgba.split()[-1].getbbox()
    if not bbox:
        return rgba
    x0, y0, x1, y1 = bbox
    pad = int(round(max(x1 - x0, y1 - y0) * pad_ratio))
    return rgba.crop((max(0, x0 - pad), max(0, y0 - pad),
                      min(rgba.size[0], x1 + pad), min(rgba.size[1], y1 + pad)))


def export(rgba, path, target_h):
    ratio = rgba.size[0] / float(rgba.size[1])
    w = max(1, int(round(target_h * ratio)))
    img = rgba.resize((w, target_h), Image.LANCZOS)
    img.save(path, optimize=True)
    print('%-22s %dx%d  %.0fKB' % (path, img.size[0], img.size[1],
                                   os.path.getsize(path) / 1024.0))


def build():
    im = Image.open(SRC).convert('RGB').crop(CROP)
    cw, ch = im.size
    scale = WORK_H / float(ch)
    im = im.resize((int(round(cw * scale)), WORK_H), Image.LANCZOS)

    alpha = cut_background(im)
    alpha = drop_pale_from_border(alpha, im)
    alpha = drop_mint(alpha, im)
    alpha = keep_center_run(alpha, 0, alpha.size[1])
    alpha = fill_holes(alpha)
    alpha = manual_clean(alpha, im)
    alpha = drop_specks(alpha)
    alpha = tidy(alpha, erode=1, blur=0.6)

    rgba = im.convert('RGBA')
    rgba.putalpha(alpha)
    return trim(rgba)


def main():
    rgba = build()
    os.makedirs('images', exist_ok=True)
    export(rgba, OUT1, 230)
    export(rgba, OUT2, 460)

    for name, color in (('_chk_light', (242, 250, 246)), ('_chk_dark', (30, 40, 55))):
        canvas = Image.new('RGBA', rgba.size, color + (255,))
        canvas.alpha_composite(rgba)
        canvas.convert('RGB').save('images/%s.png' % name)
    print('master', rgba.size, 'ratio %.2f' % (rgba.size[0] / float(rgba.size[1])))


if __name__ == '__main__':
    main()
