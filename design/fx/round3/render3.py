#!/usr/bin/env python3
"""Round-three effect renderer (T28f, design only): the round-two compositor extended to the two
seat tiers, so every candidate is judged on a full 4-player tile and on a narrow 5/6-player tile.
Stdlib only. Imports ../options/render2.py (sticker glyphs, tile patterns, PNG io) unchanged.

Tiers (both upright, seat-local coords, y down; the frame is composited at GLOBAL pattern phase,
i.e. the tile's real screen origin is added before a tile layer is sampled):
  quad    233x175, from design/captures/t07-delta.png (bottom-left seat, screen origin (0, 176)),
          totals erased: tab, +/- glyphs and the corner of the centre diamond remain.
  narrow  155x175, from design/captures/t14-6p-narrow.png (bottom-MIDDLE seat, screen origin
          (156, 176)), total and badges erased: tab and the diamond biting the top edge remain.
The life total is centred on the tile centre in both: (116, 87) and (77, 87).

Symbols as round two: . transparent, # ink, o paper, x cut (invert), 1-9 tile layers
(meta `tile <d> <TILE> ink|paper|fill`). Anchors: `tile` = the tile centre, otherwise a point of
the total's sticker box (centre|tl|tr|bl|br|top|bottom|left|right). Anything outside the tile is
clipped (the runtime clips to the panel too) and counted as `clip`.

Used by author3.py (flips per beat into meta.txt) and sheet3.py (the review sheets).
"""
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
FX = os.path.dirname(HERE)
ROOT = os.path.abspath(os.path.join(FX, '..', '..'))
sys.path.insert(0, os.path.join(FX, 'options'))
sys.path.insert(0, FX)
import render as r1  # noqa: E402
import render2  # noqa: E402

S = 4          # output scale of the sheets (device px per dither px)
CAP_S = 4      # the scale the captures were taken at


class Tier:
    def __init__(self, name, w, h, cx, cy, origin):
        self.name, self.w, self.h, self.cx, self.cy, self.origin = name, w, h, cx, cy, origin
        self._base = None
        self._furn = None

    def base(self):
        """Clean tile (no total): list of bytearrays, 1 = ink."""
        if self._base is None:
            self._base = _load_base(self)
        return self._base

    def furniture(self):
        """Cells that hold furniture ink, plus the centre-diamond zone (the narrow middle seats
        have the diamond biting the top edge; the quad seats in one top corner)."""
        if self._furn is None:
            b = self.base()
            m = set((x, y) for y in range(self.h) for x in range(self.w) if b[y][x])
            for y in range(self.h):
                for x in range(self.w):
                    if self.name == 'quad' and ((self.w - 1 - x) + y <= 24 or x + y <= 24):
                        m.add((x, y))
                    if self.name == 'narrow' and abs(x - 77) + y <= 24:
                        m.add((x, y))
            self._furn = m
        return self._furn


def _load_base(t):
    if t.name == 'quad':
        sc = render2.base()
        return [bytearray(sc.p[176 + y][x] for x in range(t.w)) for y in range(t.h)]
    w, h, rows = r1.read_png_grey(os.path.join(ROOT, 'design', 'captures', 't14-6p-narrow.png'))
    ox, oy = t.origin
    tile = [bytearray(1 if rows[(oy + y) * CAP_S + 1][(ox + x) * CAP_S + 1] < 128 else 0 for x in range(t.w))
            for y in range(t.h)]
    for y in range(26, 150):          # erase the total and the toggle badges under it
        for x in range(6, t.w - 6):
            tile[y][x] = 0
    return tile


QUAD = Tier('quad', 233, 175, 116, 87, (0, 176))
NARROW = Tier('narrow', 155, 175, 77, 87, (156, 176))
TIERS = {'quad': QUAD, 'narrow': NARROW}


def box_size(number):
    return render2.box_size(number)


def draw_number(tile, t, number):
    tw, th = box_size(number)
    x0, y0 = t.cx - tw // 2, t.cy - th // 2
    for i, ch in enumerate(number):
        rows = render2.GLYPHS.get(ch) or render2.GLYPHS['notdef']
        gx = x0 + i * (render2.GW + render2.GTRACK)
        for y, line in enumerate(rows):
            for x, v in enumerate(line):
                if v == '#' and 0 <= gx + x < t.w and 0 <= y0 + y < t.h:
                    tile[y0 + y][gx + x] = 1


def frame_origin(frame, anchor, number, t):
    fh, fw = len(frame), len(frame[0])
    ref, dx, dy = anchor
    if ref == 'tile':
        return t.cx + dx - fw // 2, t.cy + dy - fh // 2
    tw, th = box_size(number)
    rx, ry = render2.REFS[ref]
    return t.cx + rx * (tw // 2) + dx - fw // 2, t.cy + ry * (th // 2) + dy - fh // 2


def stamp(tile, t, frame, anchor, number, tiles):
    """Composite one frame onto an upright tile; returns the count of non-transparent cells
    that fell outside the tile (clipped)."""
    x0, y0 = frame_origin(frame, anchor, number, t)
    ox, oy = t.origin
    clipped = 0
    for y, row in enumerate(frame):
        for x, v in enumerate(row):
            if v == '.':
                continue
            lx, ly = x0 + x, y0 + y
            if not (0 <= lx < t.w and 0 <= ly < t.h):
                clipped += 1
                continue
            if v == '#':
                tile[ly][lx] = 1
            elif v == 'o':
                tile[ly][lx] = 0
            elif v == 'x':
                tile[ly][lx] ^= 1
            elif v.isdigit():
                name, mode = tiles[v]
                p = render2.tile_pat(name)
                sx, sy = ox + lx, oy + ly          # global phase
                ink = p[sy % len(p)][sx % len(p[0])] == '#'
                if mode == 'ink':
                    if ink:
                        tile[ly][lx] = 1
                elif mode == 'paper':
                    if ink:
                        tile[ly][lx] = 0
                else:
                    tile[ly][lx] = 1 if ink else 0
            else:
                raise ValueError(f'bad symbol {v!r}')
    return clipped


def load(path):
    frames, anchor, meta, tiles = render2.load_effect(path)
    return frames, anchor, meta, tiles


def sequence(path, t, number='37'):
    """(tiles, clipped): clean tile, then each drawn frame over it."""
    frames, anchor, meta, tiles = load(path)
    clean = [bytearray(r) for r in t.base()]
    draw_number(clean, t, number)
    seq, clip = [clean], []
    for f in frames:
        s = [bytearray(r) for r in clean]
        clip.append(stamp(s, t, f, anchor, number, tiles))
        seq.append(s)
    return seq, clip


def diff(a, b):
    return sum(x != y for ra, rb in zip(a, b) for x, y in zip(ra, rb))


def flips(path, t, number='37'):
    seq, _ = sequence(path, t, number)
    seq = seq + [seq[0]]
    return [diff(a, b) for a, b in zip(seq, seq[1:])]


def furniture_hits(path, t, number='37'):
    seq, _ = sequence(path, t, number)
    fm = t.furniture()
    return sum(1 for s in seq[1:] for (x, y) in fm if s[y][x] != seq[0][y][x])


def number_change(t, a='37', b='36'):
    sa = [bytearray(r) for r in t.base()]
    sb = [bytearray(r) for r in t.base()]
    draw_number(sa, t, a)
    draw_number(sb, t, b)
    return diff(sa, sb)


# ---------------------------------------------------------------- drawing the sheets

def blit(c, tile, ox, oy):
    for y, row in enumerate(tile):
        dst = c.p[oy + y]
        for x, v in enumerate(row):
            if v:
                dst[ox + x] = True


def strip(path, t, number='37', gap=6):
    """One effect as a row of tiles: clean, f1..fN; each labelled with its flips."""
    seq, clip = sequence(path, t, number)
    fl = flips(path, t, number)
    n = len(seq)
    c = r1.Bits(n * (t.w + gap), t.h + 12)
    for i, s in enumerate(seq):
        ox = i * (t.w + gap)
        blit(c, s, ox, 0)
        if i == 0:
            txt = 'clean'
        else:
            txt = f'f{i} {fl[i - 1]}'
            if i == n - 1:
                txt += f' >clean {fl[-1]}'
            if clip[i - 1]:
                txt += f' clip{clip[i - 1]}'
        r1.label(c, txt, ox + 2, t.h + 4)
        for x in range(0, t.w, 2):          # dotted rule under the tile, outside it
            c.p[t.h + 1][ox + x] = True
        for y in range(0, t.h, 2):          # dotted side rules: the tile edge, outside it
            if ox + t.w < c.w:
                c.p[y][ox + t.w] = True
    return c


def sheet(rows, t, out, title, number='37', lw=176, cols=1):
    """rows: [(path, name, [label lines])], laid out in `cols` columns (top to bottom first)."""
    strips = [(name, lines, strip(path, t, number)) for path, name, lines in rows]
    per = -(-len(strips) // cols)
    columns = [strips[i * per:(i + 1) * per] for i in range(cols)]
    cw = lw + max(s.w for _, _, s in strips) + 12
    W = cw * cols
    H = 24 + max(sum(s.h + 12 for _, _, s in col) for col in columns) + 4
    c = r1.Bits(W, H)
    r1.label(c, title, 4, 4, k=2)
    for x in range(4, W - 4, 2):
        c.p[19][x] = True
    for ci, col in enumerate(columns):
        x0, y = ci * cw, 24
        for name, lines, s in col:
            r1.label(c, name, x0 + 4, y + 6, k=2)
            for j, part in enumerate(lines):
                r1.label(c, part, x0 + 4, y + 22 + j * 8)
            blit(c, [[1 if v else 0 for v in row] for row in s.p], x0 + lw, y)
            y += s.h + 12
    c.to_png(out, S)
    return c
