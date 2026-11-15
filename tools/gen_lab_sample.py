# -*- coding: utf-8 -*-
"""生成一张仿真化验单图片，用于测试「化验单解读」的 OCR + 数值核对流程。

版式刻意做得紧凑：项目名 / 结果 / 单位 / 参考范围 四列间距小，
离线 OCR 才会把一行拼成一句话（而不是按列各读一堆碎片）。
"""
import os
from PIL import Image, ImageDraw, ImageFont

W, H = 1360, 1680
BG = (252, 252, 250)
INK = (26, 26, 28)
GRAY = (108, 108, 112)
RED = (198, 40, 40)
LINE = (168, 170, 174)
SOFT = (226, 227, 229)

F_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
F_REG = "C:/Windows/Fonts/msyh.ttc"

COL_NAME = 80
COL_VALUE_R = 560      # 结果列右对齐边界
COL_UNIT = 590
COL_REF = 760
COL_FLAG = 1010

ROWS = [
    ("白细胞计数 WBC", "6.8", "10^9/L", "3.5 ~ 9.5", ""),
    ("血红蛋白 HGB", "152", "g/L", "130 ~ 175", ""),
    ("血小板计数 PLT", "218", "10^9/L", "125 ~ 350", ""),
    ("空腹血糖 GLU", "7.8", "mmol/L", "3.9 ~ 6.1", "↑"),
    ("糖化血红蛋白 HbA1c", "6.4", "%", "4.0 ~ 6.0", "↑"),
    ("总胆固醇 TC", "5.62", "mmol/L", "2.8 ~ 5.2", "↑"),
    ("甘油三酯 TG", "3.42", "mmol/L", "0.45 ~ 1.70", "↑"),
    ("低密度脂蛋白 LDL-C", "3.96", "mmol/L", "0 ~ 3.4", "↑"),
    ("高密度脂蛋白 HDL-C", "0.92", "mmol/L", "1.0 ~ 1.9", "↓"),
    ("谷丙转氨酶 ALT", "62", "U/L", "9 ~ 50", "↑"),
    ("谷草转氨酶 AST", "45", "U/L", "15 ~ 40", "↑"),
    ("总胆红素 TBIL", "14.2", "umol/L", "3.4 ~ 20.5", ""),
    ("肌酐 CREA", "128", "umol/L", "57 ~ 97", "↑"),
    ("尿素 UREA", "6.9", "mmol/L", "3.1 ~ 8.0", ""),
    ("尿酸 UA", "486", "umol/L", "208 ~ 428", "↑"),
    ("eGFR 估算肾小球滤过率", "62", "mL/min", "90 ~ 120", "↓"),
]


def f(path, size):
    return ImageFont.truetype(path, size)


img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.text((72, 62), "仁和市第一人民医院", font=f(F_BOLD, 34), fill=INK)
d.text((72, 106), "RENHE FIRST PEOPLE'S HOSPITAL", font=f(F_REG, 15), fill=GRAY)
d.text((W - 72, 66), "门诊 · 检验科", font=f(F_REG, 20), fill=GRAY, anchor="ra")
d.line([(72, 140), (W - 72, 140)], fill=INK, width=3)

d.text((W // 2, 164), "生化检验报告单", font=f(F_BOLD, 38), fill=INK, anchor="ma")
d.text((W // 2, 216), "Biochemistry Test Report", font=f(F_REG, 16), fill=GRAY, anchor="ma")

info = [
    [("姓名：", "陈志远"), ("性别：", "男"), ("年龄：", "45 岁")],
    [("科室：", "体检中心"), ("标本类型：", "血清"), ("病历号：", "MZ20261102037")],
    [("临床诊断：", "健康体检"), ("送检医生：", "周敏"), ("采集时间：", "2026-11-02 08:12")],
]
COL_X = (78, 470, 860)
y = 262
for row in info:
    for x, (label, value) in zip(COL_X, row):
        d.text((x, y), label, font=f(F_REG, 21), fill=GRAY)
        d.text((x + 108, y), value, font=f(F_REG, 21), fill=INK)
    y += 36

d.line([(72, y + 12), (W - 72, y + 12)], fill=LINE, width=2)

top = y + 30
rowh = 60
d.rectangle([72, top, W - 72, top + rowh], fill=(242, 243, 245), outline=LINE, width=2)
for text, x in [("项目名称", COL_NAME + 10), ("结果", 470), ("单位", COL_UNIT), ("参考范围", COL_REF), ("提示", COL_FLAG)]:
    d.text((x, top + 14), text, font=f(F_BOLD, 22), fill=INK)

yy = top + rowh
for i, (name, value, unit, ref, flag) in enumerate(ROWS):
    if i % 2 == 1:
        d.rectangle([73, yy + 1, W - 73, yy + rowh - 1], fill=(248, 249, 250))
    d.line([(72, yy), (W - 72, yy)], fill=SOFT, width=1)
    d.text((COL_NAME + 10, yy + 14), name, font=f(F_REG, 24), fill=INK)
    d.text((COL_VALUE_R, yy + 13), value, font=f(F_BOLD, 25), fill=RED if flag else INK, anchor="ra")
    d.text((COL_UNIT, yy + 14), unit, font=f(F_REG, 24), fill=INK)
    d.text((COL_REF, yy + 14), ref, font=f(F_REG, 24), fill=INK)
    if flag:
        d.text((COL_FLAG, yy + 12), flag, font=f(F_BOLD, 24), fill=RED)
    yy += rowh

d.line([(72, yy), (W - 72, yy)], fill=LINE, width=2)

d.text((80, yy + 24), "检验者：李珊", font=f(F_REG, 21), fill=INK)
d.text((330, yy + 24), "审核者：王海涛", font=f(F_REG, 21), fill=INK)
d.text((620, yy + 24), "报告状态：已审核", font=f(F_REG, 21), fill=INK)

note_y = yy + 76
d.line([(72, note_y), (W - 72, note_y)], fill=SOFT, width=1)
d.text((80, note_y + 12), "备注：本报告仅对所检标本负责，如有疑问请于 7 日内与检验科联系（0571-88886120）。",
       font=f(F_REG, 16), fill=GRAY)
d.text((80, note_y + 38), "↑ 高于参考范围    ↓ 低于参考范围   参考范围仅适用于本实验室检测系统。",
       font=f(F_REG, 16), fill=GRAY)

out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "samples")
os.makedirs(out_dir, exist_ok=True)
out = os.path.join(out_dir, "lab_report_sample.png")
img.save(out, "PNG")
print(out, img.size)
