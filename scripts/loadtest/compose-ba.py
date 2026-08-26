#!/usr/bin/env python3
"""Compose a v2-style before/after evidence image from two Grafana stat-row PNGs.

Usage: compose-ba.py <before.png> <after.png> <out.png> "<title>" "<before banner>" "<after banner>"

The raw PNGs are full-page kiosk screenshots (scale 2, 3200x840) whose top band is
the single row of 6 stat panels. We crop that band and stack: title, red BEFORE
banner + row, green AFTER banner + row. Matches the portfolio-v2 dashboard format.
"""
import sys
from PIL import Image, ImageDraw, ImageFont

before_p, after_p, out_p, title, b_txt, a_txt = sys.argv[1:7]
FONT = "/System/Library/Fonts/AppleSDGothicNeo.ttc"

# crop band of the stat-panel row from the raw kiosk screenshot
CROP = (8, 8, 3195, 452)  # x0,y0,x1,y1 in the 3200x840 raw

def row(p):
    im = Image.open(p).convert("RGB")
    return im.crop(CROP)

rb, ra = row(before_p), row(after_p)
W = rb.width
BG = (13, 17, 23)
title_h, banner_h, gap = 92, 78, 26
H = title_h + banner_h + rb.height + gap + banner_h + ra.height + 24

canvas = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(canvas)
f_title = ImageFont.truetype(FONT, 46, index=1)
f_ban = ImageFont.truetype(FONT, 40, index=1)

def banner(y, text, color):
    d.rounded_rectangle((8, y, 8 + int(f_ban.getlength(text)) + 56, y + banner_h - 16),
                        radius=12, fill=color)
    d.text((36, y + 12), text, font=f_ban, fill=(255, 255, 255))

y = 14
d.text((10, y), title, font=f_title, fill=(233, 237, 243))
y += title_h
banner(y, b_txt, (152, 30, 40)); y += banner_h
canvas.paste(rb, (0, y)); y += rb.height + gap
banner(y, a_txt, (25, 104, 66)); y += banner_h
canvas.paste(ra, (0, y))

canvas.save(out_p)
print(f"wrote {out_p} {canvas.size}")
