#!/usr/bin/env python3
"""T14 icon options contact sheet, pure 1-bit on the S=4 grid.

usage: python3 design/icons/candidates/t14/sheet.py design/icons/candidates/t14 design/captures/t14-icons.png \
       initiative=a,blessing=a,ring=b,day=a
Each family row: option a/b/c, each shown three ways at device size (4x): tile badge (ink on
paper), drawer cell ON (paper on an ink card), drawer cell OFF (ink on a 1-px halo on the
drawer's sparse dither). The pick is underlined. Monarch and night (approved) lead as reference.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.normpath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, ICONS)
from render import FONT, write_png  # noqa: E402

S = 4
src, out, picks = sys.argv[1], sys.argv[2], dict(p.split('=') for p in sys.argv[3].split(','))
families = ['initiative', 'blessing', 'ring', 'day']
ref_dir = ICONS


def load(path):
    with open(path) as f:
        rows = f.read().split('\n')[:16]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), path
    return [[c == '#' for c in r] for r in rows]


opts = {f: [(o, load(os.path.join(src, f'{f}-{o}.txt'))) for o in 'abc' if os.path.exists(os.path.join(src, f'{f}-{o}.txt'))] for f in families}
refs = [('monarch', load(f'{ref_dir}/monarch.txt')), ('night', load(f'{ref_dir}/night.txt'))]

TILE = 20  # 16 icon + 2 px each side
GAP = 2
OPT_W = 3 * TILE + 2 * GAP
NAME_W = 44
M = 4
row_h = 7 + TILE + 6
W = M + NAME_W + 3 * (OPT_W + 8) + M
H = M + (len(families) + 1) * row_h + M
W += (-W) % 2
H += (-H) % 2
px = [[0] * W for _ in range(H)]  # 0 paper, 1 ink


def rect(x, y, w, h, v):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px[yy][xx] = v


def text(s, x, y):
    for ci, ch in enumerate(s):
        g = FONT.get(ch)
        if not g:
            continue
        g = g.ljust(15, '.')
        for i, c in enumerate(g):
            if c == '#':
                px[y + i // 3][x + ci * 4 + i % 3] = 1


def sparse(x, y, w, h):
    # The DRAWER role's sparse dither stand-in: one ink px per 4x4, on the global phase.
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px[yy][xx] = 1 if (xx % 4 == 1 and yy % 4 == 1) or (xx % 4 == 3 and yy % 4 == 3) else 0


def icon(ic, x, y, ink):
    for j in range(16):
        for i in range(16):
            if ic[j][i]:
                px[y + j][x + i] = ink


def halo(ic, x, y):
    for j in range(16):
        for i in range(16):
            if ic[j][i]:
                for dj in (-1, 0, 1):
                    for di in (-1, 0, 1):
                        px[y + j + dj][x + i + di] = 0


def three(ic, x, y):
    # badge: ink on paper
    icon(ic, x + 2, y + 2, 1)
    # ON cell: ink card, paper icon
    rect(x + TILE + GAP, y, TILE, TILE, 1)
    icon(ic, x + TILE + GAP + 2, y + 2, 0)
    # OFF cell: halo on sparse dither
    sx = x + 2 * (TILE + GAP)
    sparse(sx, y, TILE, TILE)
    halo(ic, sx + 2, y + 2)
    icon(ic, sx + 2, y + 2, 1)


y = M
text('approved', M, y)
x = M + NAME_W
for name, ic in refs:
    text(name, x, y)
    three(ic, x, y + 7)
    x += OPT_W + 8
y += row_h
for f in families:
    text(f, M, y + 7 + 8)
    x = M + NAME_W
    for o, ic in opts[f]:
        text(o + (' pick' if picks.get(f) == o else ''), x, y)
        three(ic, x, y + 7)
        if picks.get(f) == o:
            rect(x, y + 7 + TILE + 2, OPT_W, 1, 1)
        x += OPT_W + 8
    y += row_h

flat = bytearray()
for row in px:
    line = bytearray()
    for v in row:
        line += bytes([0 if v else 255]) * S
    for _ in range(S):
        flat += line
write_png(out, W * S, H * S, flat)
print('wrote', out, W * S, H * S)
