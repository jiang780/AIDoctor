# -*- coding: utf-8 -*-
"""把「AI医生动漫IP形象设计 (3).jpeg」处理成悬浮挂件素材（透明 PNG，两档尺寸）。

原图是「角色 + 无人机 + HUD 面板 + 光圈」的完整插画，底色近白带薄荷光晕，右下角有生成水印。
挂件只需要角色本体（外加头顶那道 AI 光环），处理顺序：

1. 裁到角色范围，右下角水印落在框外；
2. 轻微模糊后从四边洪泛取 alpha（底色带噪点，直接键控会漏白斑）；
3. 再从四边吃掉「很浅且几乎无彩」的像素：光晕、光圈细线、HUD 半透明底板；
4. 无人机：肩部区间抹掉「绿多蓝少」的像素，机身是薄荷绿，白大褂偏中性白，分得开；
5. 肩部区间再按行收中段（容忍行内空洞），把前后伸出的机臂、旋翼残片一起丢掉；
6. 两块 HUD 面板按矩形清掉，右侧面板左缘压在头发上，只清非发色像素；
7. 左侧小圆环、心电波形用矩形清掉；
8. 只保留躯干连通块 + 头顶光环，补洞收边，裁掉透明边，导出两档。

矩形都写成原图坐标，用 rel() 换算成裁剪内坐标。
"""
import os

from PIL import Image, ImageDraw, ImageFilter

SRC = 'AI医生动漫IP形象设计 (3).jpeg'
OUT1 = 'images/aiip.png'
OUT2 = 'images/aiip@2x.png'

CROP = (440, 40, 1090, 1470)     # 角色 + 光环 + 鞋底；圆环、心电、水印都落在框外
MARK = (255, 0, 255)
THRESH = 21


def rel(box):
    """把原图坐标的框换算成 CROP 内坐标。"""
    x0, x1, y0, y1 = box
    return (x0 - CROP[0], x1 - CROP[0], y0 - CROP[1], y1 - CROP[1])


DRONE_BAND = (525, 768)                      # 无人机纵向区间（原图 y 565~808）
LEFT_PANEL = rel((396, 578, 126, 258))       # 左 HUD 面板（右下角压到头发）
RIGHT_PANEL = rel((892, 1058, 170, 298))     # 右 HUD 面板（左下角压到头发）
PANEL_BOXES = (LEFT_PANEL, RIGHT_PANEL)
HALO_BOX = rel((640, 890, 40, 170))          # 头顶光环，保留
SCRUB_ZONE = (175, 465)                      # 胸口薄荷色内搭的 x 范围，抹无人机时避开
CORRIDOR_GAP = 26                            # 肩部区间行内允许合并的空洞宽度
TEAL_BOXES = (rel((672, 716, 590, 700)), rel((952, 1010, 575, 700)))
TEAL_STRIPS = ((55, 180, 540, 860), (460, 725, 540, 860))
HAIR_FLOOR = 150                             # 修轮廓时只看这个 y 以下（避开光环）


def cut_background(rgb):
    w, h = rgb.size
    work = rgb.filter(ImageFilter.GaussianBlur(1.2))
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


def drop_pale_from_border(alpha, rgb, bright=228, sat=20):
    """从四边向内吃掉很浅且无彩的像素（光晕/光圈/HUD 半透明底板）。"""
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()

    def pale(x, y):
        r, g, b = px[x, y]
        return (r + g + b) / 3.0 >= bright and (max(r, g, b) - min(r, g, b)) <= sat

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


def drop_drone(alpha, rgb):
    """肩部区间抹掉无人机机身：薄荷绿（红低、绿≥蓝），深色描边与蓝色头发都不满足。

    胸口那件薄荷色内搭在这个区间里也露着，用 SCRUB_ZONE 单独放过。
    """
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()
    y0, y1 = DRONE_BAND
    sx0, sx1 = SCRUB_ZONE
    for y in range(max(0, y0), min(h, y1)):
        for x in range(w):
            if ap[x, y] == 0 or sx0 <= x <= sx1:
                continue
            r, g, b = px[x, y]
            if r <= 232 and g - r >= 8 and g >= 170 and g >= b - 10:
                ap[x, y] = 0
    return alpha


def corridor_band(alpha, y0, y1, gap=CORRIDOR_GAP):
    """肩部区间只保留包含画面中线的那一段（允许跨过 <= gap 的行内空洞）。

    无人机的机臂、旋翼与角色之间隔着背景，段落会被这段逻辑切断丢弃。
    """
    w, h = alpha.size
    ap = alpha.load()
    cx = w // 2
    for y in range(max(0, y0), min(h, y1)):
        row = [ap[x, y] > 0 for x in range(w)]
        if not any(row):
            continue
        segs, x = [], 0
        while x < w:
            if row[x]:
                s = x
                while x < w and row[x]:
                    x += 1
                segs.append([s, x - 1])
            else:
                x += 1
        merged = []
        for s in segs:
            if merged and s[0] - merged[-1][1] <= gap:
                merged[-1][1] = s[1]
            else:
                merged.append(list(s))
        target = next((s for s in merged if s[0] <= cx <= s[1]), None)
        if target is None:
            target = min(merged, key=lambda s: min(abs(s[0] - cx), abs(s[1] - cx)))
        for s in merged:
            if s is target:
                continue
            for x in range(s[0], s[1] + 1):
                ap[x, y] = 0
        for x in list(range(0, target[0])) + list(range(target[1] + 1, w)):
            ap[x, y] = 0
    return alpha


def drop_teal_patches(alpha, rgb, boxes, r_max=200, gb_max=22, gr_min=18):
    """肩头还挂着两小块无人机残件：青绿、比白大褂暗、比头发偏绿，按色差定点清掉。"""
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()
    n = 0
    for x0, x1, y0, y1 in boxes:
        for y in range(max(0, y0), min(h, y1)):
            for x in range(max(0, x0), min(w, x1)):
                if ap[x, y] == 0:
                    continue
                r, g, b = px[x, y]
                if r <= r_max and g >= 80 and b - g <= gb_max and g - r >= gr_min:
                    ap[x, y] = 0
                    n += 1
    print('teal patches removed:', n)
    return alpha


def corridor_by_bright(alpha, rgb, y0, y1, thresh=195, gap=30, pad=6):
    """肩部区间按「最长的亮区」收边。

    白大褂/内搭是一大片亮面（每行最长的那段），无人机残件虽偶有亮边，但只是零星小段。
    每行取最长亮段并外扩 pad，切掉走廊外的深色残件；白大褂的深色描边和听诊器都在
    走廊之内，不受影响。
    """
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()
    cx = w // 2
    for y in range(max(0, y0), min(h, y1)):
        bright = [ap[x, y] > 0 and (px[x, y][0] + px[x, y][1] + px[x, y][2]) / 3.0 >= thresh
                  for x in range(w)]
        segs, x = [], 0
        while x < w:
            if bright[x]:
                s0 = x
                while x < w and bright[x]:
                    x += 1
                segs.append([s0, x - 1])
            else:
                x += 1
        if not segs:
            continue
        merged = []
        for seg in segs:
            if merged and seg[0] - merged[-1][1] <= gap:
                merged[-1][1] = seg[1]
            else:
                merged.append(list(seg))
        target = max(merged, key=lambda seg: (seg[1] - seg[0],
                                              -abs((seg[0] + seg[1]) // 2 - cx)))
        lo = max(0, target[0] - pad)
        hi = min(w - 1, target[1] + pad)
        for x in range(0, lo):
            ap[x, y] = 0
        for x in range(hi + 1, w):
            ap[x, y] = 0
    return alpha


def _unused_corridor(alpha, rgb, y0, y1, thresh=210, gap=60, pad=10):
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()
    cx = w // 2
    for y in range(max(0, y0), min(h, y1)):
        bright = [(r + g + b) / 3.0 >= thresh for (r, g, b) in
                  (px[x, y] for x in range(w))]
        segs, x = [], 0
        while x < w:
            if bright[x]:
                s0 = x
                while x < w and bright[x]:
                    x += 1
                segs.append([s0, x - 1])
            else:
                x += 1
        if not segs:
            continue
        merged = []
        for seg in segs:
            if merged and seg[0] - merged[-1][1] <= gap:
                merged[-1][1] = seg[1]
            else:
                merged.append(list(seg))
        target = next((seg for seg in merged if seg[0] <= cx <= seg[1]), None)
        if target is None:
            target = min(merged, key=lambda seg: min(abs(seg[0] - cx), abs(seg[1] - cx)))
        lo = max(0, target[0] - pad)
        hi = min(w - 1, target[1] + pad)
        for x in range(0, lo):
            ap[x, y] = 0
        for x in range(hi + 1, w):
            ap[x, y] = 0
    return alpha


def clear_box(alpha, box):
    w, h = alpha.size
    ap = alpha.load()
    x0, x1, y0, y1 = box
    for y in range(max(0, y0), min(h, y1)):
        for x in range(max(0, x0), min(w, x1)):
            ap[x, y] = 0
    return alpha


def mirror_fill_panels(alpha, rgb, boxes):
    """面板区域整体用「左右镜像」重建。

    头部左右基本对称，把面板覆盖的那块区域按中轴对称从另一侧抄过来：
    轮廓、明暗、发丝走向都是原画的，不会出现色块或直线边；抄到背景的地方就置透明。
    中轴 cx 用不受面板影响的那些行算出来。
    """
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()
    bad = set()
    for box in boxes:
        bad |= set(range(max(0, box[2]), min(h, box[3])))

    def is_hair(x, y):
        r, g, b = px[x, y]
        return b - g >= 55 and b >= 150

    mids = []
    for y in range(h):
        if y in bad:
            continue
        xs = [x for x in range(w) if ap[x, y] > 0 and is_hair(x, y)]
        if len(xs) >= 8:
            mids.append((xs[0] + xs[-1]) / 2.0)
    if not mids:
        return alpha
    mids.sort()
    cx = int(round(mids[len(mids) // 2]))
    print('mirror axis cx =', cx)

    fixed = 0
    for box in boxes:
        for y in range(max(0, box[2]), min(h, box[3])):
            for x in range(max(0, box[0]), min(w, box[1])):
                mx = 2 * cx - x
                if 0 <= mx < w and ap[mx, y] > 0 and is_hair(mx, y):
                    ap[x, y] = 255
                    px[x, y] = px[mx, y]
                    fixed += 1
                else:
                    ap[x, y] = 0
    print('mirror filled px:', fixed)
    return alpha


def prune_side_spikes(alpha, band=(600, 920), k=15):
    """横向开运算（转 90° 复用竖滤波）：把肩膀两侧横向伸出的细刺削平。"""
    w, h = alpha.size
    rot = alpha.transpose(Image.ROTATE_90)
    rot = _vfilter(_vfilter(rot, k, True), k, False).transpose(Image.ROTATE_270)
    out = alpha.copy()
    y0, y1 = max(0, band[0]), min(h, band[1])
    out.paste(rot.crop((0, y0, w, y1)), (0, y0))
    return out


def _vfilter(alpha, k, take_min=True):
    """竖直方向的一维最大/最小值滤波（PIL 只支持方形核，这里按列自己做）。"""
    w, h = alpha.size
    src = alpha.tobytes()
    dst = bytearray(len(src))
    r = k // 2
    for x in range(w):
        col = src[x::w]
        for y in range(h):
            seg = col[max(0, y - r):min(h, y + r + 1)]
            dst[y * w + x] = min(seg) if take_min else max(seg)
    return Image.frombytes('L', (w, h), bytes(dst))


def prune_horizontal_wings(alpha, band=(480, 840), keep=45, base=21):
    """竖直开运算：抹掉细长的水平凸出物（无人机机臂、旋翼残留）。

    肩部这一段用更强的核（45px），其它地方只做轻微清理，避免啃掉发丝和衣角。
    """
    soft = _vfilter(_vfilter(alpha, base, True), base, False)
    hard = _vfilter(_vfilter(alpha, keep, True), keep, False)
    w, h = alpha.size
    y0, y1 = max(0, band[0]), min(h, band[1])
    soft.paste(hard.crop((0, y0, w, y1)), (0, y0))
    return soft


def keep_components(alpha, seed_xy, extra_boxes=()):
    """保留躯干所在连通块，以及中心落在光环框内的连通块，其余一律清掉。"""
    w, h = alpha.size
    ap = alpha.load()
    seen = [[False] * w for _ in range(h)]
    comps = []
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
            comps.append(comp)

    sx, sy = seed_xy
    main = None
    for comp in comps:
        if (sx, sy) in set(comp):
            main = comp
            break
    if main is None:
        raise RuntimeError('seed 不在角色上: %r' % (seed_xy,))

    keep = set(main)
    for box in extra_boxes:
        bx0, bx1, by0, by1 = box
        for comp in comps:
            if comp is main or len(comp) < 200:
                continue
            cx = sum(p[0] for p in comp) / float(len(comp))
            cy = sum(p[1] for p in comp) / float(len(comp))
            if bx0 <= cx <= bx1 and by0 <= cy <= by1:
                keep |= set(comp)

    mask = Image.new('L', (w, h), 0)
    mp = mask.load()
    for x, y in keep:
        mp[x, y] = 255
    print('keep px: %d / comps %d' % (len(keep), len(comps)))
    return mask


def fill_holes(alpha, rgb, max_area=2500):
    """补回被不透明像素包围的小空洞（袖章、纽扣、发丝高光）。

    只补小洞：大洞（比如清掉无人机后留下的空腔）不补，否则会把已经删掉的东西
    连颜色一起「填」回来。补出来的像素用洞壁的中位色，避免留下原图的残色。
    """
    w, h = alpha.size
    ap, px = alpha.load(), rgb.load()
    seen = [[False] * w for _ in range(h)]
    holes = []

    def walk(sx, sy):
        comp = [(sx, sy)]
        seen[sy][sx] = True
        head = 0
        touch = False
        while head < len(comp):
            x, y = comp[head]
            head += 1
            if x in (0, w - 1) or y in (0, h - 1):
                touch = True
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and ap[nx, ny] == 0:
                    seen[ny][nx] = True
                    comp.append((nx, ny))
        if not touch:
            holes.append(comp)

    for y in range(h):
        for x in range(w):
            if ap[x, y] == 0 and not seen[y][x]:
                walk(x, y)

    filled = 0
    for comp in holes:
        if len(comp) > max_area:
            continue
        ring = []
        for x, y in comp:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and ap[nx, ny] > 0:
                    ring.append(px[nx, ny])
        col = None
        if ring:
            col = tuple(sorted(c[k] for c in ring)[len(ring) // 2] for k in range(3))
        for x, y in comp:
            ap[x, y] = 255
            if col:
                px[x, y] = col
            filled += 1
    print('holes filled px:', filled, 'of holes', len(holes))
    return alpha


def drop_specks(alpha, min_area=600):
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


def tidy(alpha, blur=0.8):
    # 先做一次开运算去掉 1~2px 的细毛刺，再闭运算填小凹口，最后轻微羽化
    alpha = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3))
    alpha = alpha.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))
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
    w, h = im.size

    alpha = cut_background(im)
    alpha = drop_pale_from_border(alpha, im)
    alpha = drop_drone(alpha, im)
    alpha = corridor_band(alpha, DRONE_BAND[0], DRONE_BAND[1])
    alpha = drop_teal_patches(alpha, im, TEAL_BOXES, r_max=200, gb_max=22, gr_min=18)
    alpha = drop_teal_patches(alpha, im, TEAL_STRIPS, r_max=168, gb_max=26, gr_min=30)
    alpha = corridor_by_bright(alpha, im, 640, 900)
    alpha = clear_box(alpha, LEFT_PANEL)
    alpha = clear_box(alpha, RIGHT_PANEL)
    alpha = mirror_fill_panels(alpha, im, PANEL_BOXES)
    alpha = drop_specks(alpha, 400)
    alpha = keep_components(alpha, (w // 2, int(h * 0.72)), extra_boxes=(HALO_BOX,))
    alpha = fill_holes(alpha, im)
    alpha = prune_horizontal_wings(alpha)
    alpha = prune_side_spikes(alpha)
    alpha = drop_specks(alpha, 900)
    alpha = tidy(alpha, blur=0.8)

    rgba = im.convert('RGBA')
    rgba.putalpha(alpha)
    return trim(rgba)


def main():
    rgba = build()
    os.makedirs('images', exist_ok=True)
    os.makedirs('.ipwork', exist_ok=True)
    export(rgba, OUT1, 230)
    export(rgba, OUT2, 460)

    for name, color in (('_chk_light', (242, 250, 246)), ('_chk_dark', (30, 40, 55))):
        canvas = Image.new('RGBA', rgba.size, color + (255,))
        canvas.alpha_composite(rgba)
        canvas.convert('RGB').save('.ipwork/%s.png' % name)
    print('master', rgba.size, 'ratio %.2f' % (rgba.size[0] / float(rgba.size[1])))


if __name__ == '__main__':
    main()
