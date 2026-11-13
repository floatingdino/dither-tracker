#!/usr/bin/env python3
"""Author the effect frames in design/fx/<name>/ from a few geometric primitives (stdlib only).

Usage: python3 design/fx/author.py [name ...]      (default: all effects)

The frame-N.txt files are the deliverable; this script is how they were drawn. Hand edits to the
.txt files are fine, but re-running this script for that effect overwrites them.
Symbols: '#' draw black, 'o' draw white (knock-out), '.' transparent.
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import render  # noqa: E402

PAT = os.path.join(HERE, '..', 'patterns')


def tile(name):
    return [r for r in open(os.path.join(PAT, name + '.txt')).read().split('\n') if r]


class G:
    """Frame grid. Coordinates are relative to the frame centre (cx, cy) = (w//2, h//2)."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.c = [['.'] * w for _ in range(h)]

    def put(self, x, y, ch):
        x, y = int(math.floor(x)) + self.w // 2, int(math.floor(y)) + self.h // 2
        if 0 <= x < self.w and 0 <= y < self.h:
            self.c[y][x] = ch

    def at(self, x, y):
        x, y = x + self.w // 2, y + self.h // 2
        return self.c[y][x] if 0 <= x < self.w and 0 <= y < self.h else None

    def cells(self):
        for y in range(-(self.h // 2), self.h - self.h // 2):
            for x in range(-(self.w // 2), self.w - self.w // 2):
                yield x, y

    def disc(self, cx, cy, r, ch='#'):
        for x, y in self.cells():
            if (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 <= r * r:
                self.put(x, y, ch)

    def stroke(self, pts, width, ch='#', steps=4):
        """Polyline; width is a number or f(t in 0..1). Square-ish pixel brush (no stray dots)."""
        segs = list(zip(pts, pts[1:]))
        total = sum(math.dist(a, b) for a, b in segs) or 1
        done = 0
        for a, b in segs:
            L = math.dist(a, b)
            n = max(1, int(L * steps))
            for i in range(n + 1):
                t = i / n
                x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
                w = width((done + L * t) / total) if callable(width) else width
                if w <= 0:
                    continue
                if w < 1.5:
                    self.put(x, y, ch)
                else:
                    self.disc(x, y, w / 2, ch) if w >= 2.5 else self.square(x, y, ch)
            done += L

    def square(self, x, y, ch):
        for dx in (0, 1):
            for dy in (0, 1):
                self.put(x - .5 + dx, y - .5 + dy, ch)

    def poly(self, pts, ch='#'):
        for x, y in self.cells():
            px, py = x + .5, y + .5
            inside = False
            for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]):
                if (y1 > py) != (y2 > py) and px < x1 + (py - y1) * (x2 - x1) / (y2 - y1):
                    inside = not inside
            if inside:
                self.put(x, y, ch)

    def halo(self, src='#', ch='o', diag=True):
        """Paper-coloured outline around src cells so marks stay legible over the numeral."""
        nb = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if diag else [])
        add = []
        for x, y in self.cells():
            if self.at(x, y) == '.' and any(self.at(x + a, y + b) == src for a, b in nb):
                add.append((x, y))
        for x, y in add:
            self.put(x, y, ch)

    def pattern(self, name, mask, ch, phase=(0, 0)):
        """Where mask(x, y) and the dither tile has ink, put ch. Tile phase is frame-local (see README)."""
        t = tile(name)
        th, tw = len(t), len(t[0])
        for x, y in self.cells():
            if mask(x, y):
                fx, fy = x + self.w // 2 + phase[0], y + self.h // 2 + phase[1]
                if t[fy % th][fx % tw] == '#':
                    self.put(x, y, ch)

    def paste(self, art, cx, cy, mapping=None):
        """Paste ASCII art (list of strings) centred at (cx, cy)."""
        h, w = len(art), len(art[0])
        for j, row in enumerate(art):
            for i, v in enumerate(row):
                v = (mapping or {}).get(v, v)
                if v != '.':
                    self.put(cx - w // 2 + i, cy - h // 2 + j, v)

    def rows(self):
        return [''.join(r) for r in self.c]


def line(g, x0, y0, x1, y1, ch='#'):
    """Integer Bresenham line, 1 cell wide, no doubled corners."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        g.put(x0, y0, ch)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def polyline(g, pts, ch='#'):
    for a, b in zip(pts, pts[1:]):
        line(g, a[0], a[1], b[0], b[1], ch)


def up2(a):
    """Art grid -> frame grid at 2x. One art px = 2 dither px = the num-l pen width at k=2,
    so effect strokes share the numeral's (and the icons') chunky pixel language."""
    g = G(a.w * 2, a.h * 2)
    for y in range(a.h):
        for x in range(a.w):
            v = a.c[y][x]
            if v != '.':
                for yy in (0, 1):
                    for xx in (0, 1):
                        g.c[2 * y + yy][2 * x + xx] = v
    return g


def outline_of(region, ch='#', diag=False):
    """Cells of a filled region that touch its outside: a 1-cell outline (diag: 8-neighbour, so
    diagonal edges come out as solid staircases instead of broken dashes)."""
    nb = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if diag else [])
    g = G(region.w, region.h)
    for x, y in region.cells():
        if region.at(x, y) not in (None, '.'):
            if any(region.at(x + a, y + b) in (None, '.') for a, b in nb):
                g.put(x, y, ch)
            else:
                g.put(x, y, 'o')
    return g


# ----------------------------------------------------------------------------------------------
# Number geometry for a 2-digit total at k=2, relative to its centre, in dither px: x -36..35,
# y -28..27 (art px, i.e. /2: x -18..17, y -14..13). Strokes are 4 dither px (2 art px).
# The '7' diagonal runs '/' at slope 2; its top bar is y -28..-25.
# ----------------------------------------------------------------------------------------------

EFFECTS = {}


def effect(fn):
    EFFECTS[fn.__name__.replace('_', '-')] = fn
    return fn


# ---------------- damage ----------------

def claw_slash(a, x0, y0, rows, thick_from, thick_to):
    """A slope-2 '\\' slash in art px: 1 px wide at the ends, 2 px wide in the middle."""
    for i in range(rows):
        x, y = x0 + i // 2, y0 + i
        a.put(x, y, '#')
        if thick_from <= i < thick_to:
            a.put(x + 1, y, '#')


@effect
def claw():
    """Three claw slashes rake across the numeral, then shrink to thin scratches."""
    A = 32
    slashes = ((-13, -1, 14), (-9, -9, 18), (-1, -13, 14))  # x0, y0, rows (art px), 7 art apart
    frames = []
    for keep, bold in ((1.0, True), (0.45, False)):
        a = G(A, A)
        for x0, y0, rows in slashes:
            cut = int(rows * (1 - keep) / 2)
            for i in range(cut, rows - cut):
                x, y = x0 + i, y0 + i            # 45 deg: no digit has this stroke, so no misread
                a.put(x, y, '#')
                if bold and rows // 5 <= i < rows - rows // 5:
                    a.put(x + 1, y, '#')       # 2 art px (~ numeral weight) in the middle
        g = up2(a)
        if not bold:                            # scratches: 1 dither px wide
            for x, y in g.cells():
                if g.at(x, y) == '#' and g.at(x - 1, y) == '#':
                    g.put(x, y, '.')
        g.halo(diag=False)
        frames.append(g)
    return frames, ('centre', 0, -2), [350, 300, 0]


def star(n, r_out, r_in, seed, jitter=0.07, rot=-math.pi / 2):
    rnd = random.Random(seed)
    pts = []
    for i in range(2 * n):
        ang = rot + i * math.pi / n + rnd.uniform(-jitter, jitter)
        r = r_out * rnd.uniform(0.85, 1.05) if i % 2 == 0 else r_in * rnd.uniform(0.9, 1.1)
        pts.append((math.cos(ang) * r, math.sin(ang) * r))
    return pts


@effect
def pow():
    """Comic impact burst dents the numeral's top corner, then four chips fly off."""
    A = 30
    frames = []
    a = G(A, A)
    pts = []
    for i in range(16):
        ang = -math.pi / 2 + i * math.pi / 8
        r = (11.5 if i % 4 == 0 else 9.5) if i % 2 == 0 else 5.5
        pts.append((math.cos(ang) * r, math.sin(ang) * r))
    a.poly(pts, 'x')
    a = outline_of(a, diag=True)  # 1 art px black outline, paper inside (dents the digit)
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A, A)
    for i, art in enumerate((['##', '#.'], ['##', '.#'], ['.#', '##'], ['#.', '##'])):
        ang = -math.pi / 4 - i * math.pi / 2
        x, y = math.cos(ang), math.sin(ang)
        a.paste(art, round(x * 12), round(y * 12))
        line(a, round(x * 6), round(y * 6), round(x * 8), round(y * 8))
    g = up2(a)
    g.halo()
    frames.append(g)
    return frames, ('tr', -6, 2), [350, 300, 0]


@effect
def zap():
    """A lightning bolt strikes the top of the numeral, sparks fly, a wisp of smoke rises."""
    A_W, A_H = 24, 32
    frames = []
    # frame centre sits 22 dither px (11 art) above the numeral's top edge: top is art y = +11
    top = 11
    hit = (-1, top + 2)
    a = G(A_W, A_H)
    a.poly([(3, -16), (-5, -2), (-1, -2), (-2.5, top + 3), (6, -5), (2, -5), (8, -16)], '#')
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A_W, A_H)
    for ang in (200, 250, 290, 340):          # sparks kick up off the impact point
        r = math.radians(ang)
        line(a, round(hit[0] + math.cos(r) * 3), round(hit[1] + math.sin(r) * 3),
             round(hit[0] + math.cos(r) * 7), round(hit[1] + math.sin(r) * 7))
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A_W, A_H)
    for x, y in ((-1, 5), (0, 3), (0, 2), (-1, 0)):
        a.put(x, y, '#')
    frames.append(up2(a))
    return frames, ('top', 2, -22), [300, 300, 250, 0]


@effect
def wham():
    """BIG HIT (optional, change >= 10): eight impact wedges slam in from all round, then recoil."""
    A_W, A_H = 60, 48
    frames = []
    for rx, ry, ln, base in ((26, 21, 6, 1.6), (29, 23, 3, 1.0)):
        a = G(A_W, A_H)
        for i in range(8):
            ang = i * math.pi / 4 + math.pi / 8
            c, s_ = math.cos(ang), math.sin(ang)
            ox, oy = c * rx, s_ * ry                      # outer end on an ellipse round the numeral
            ix, iy = ox - c * ln, oy - s_ * ln            # tip, pointing in
            px, py = -s_ * base, c * base
            a.poly([(ox + px, oy + py), (ox - px, oy - py), (ix, iy)], '#')
        g = up2(a)
        g.halo()
        frames.append(g)
    return frames, ('centre', 0, 0), [400, 300, 0]


@effect
def crack():
    """A crack zig-zags down through the numeral, splits and drops two shards, then crumbs."""
    A_W, A_H = 38, 34
    pts = [(-6, -16), (-3, -9), (-7, -5), (0, 2), (-3, 5), (4, 12), (2, 16)]
    frames = []
    a = G(A_W, A_H)
    polyline(a, pts)
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A_W, A_H)
    polyline(a, pts)
    polyline(a, [(-7, -5), (-11, -6)])
    polyline(a, [(0, 2), (5, 0)])
    a.paste(['##', '.#'], -11, 2)
    a.paste(['#.', '##'], 8, 7)
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A_W, A_H)
    a.put(-11, 10, '#')
    a.put(9, 15, '#')
    frames.append(up2(a))
    return frames, ('centre', 4, 0), [350, 300, 250, 0]


# ---------------- heal ----------------

SPARKLE = ['.....#.....',
           '.....#.....',
           '....###....',
           '....###....',
           '...#####...',
           '###########',
           '...#####...',
           '....###....',
           '....###....',
           '.....#.....',
           '.....#.....']
SPARKLE_S = ['..#..',
             '..#..',
             '#####',
             '..#..',
             '..#..']
GLINT = ['..#..', '.#.#.', '#...#', '.#.#.', '..#..']


@effect
def twinkle():
    """Fat-cored twinkles pop at the numeral's shoulders, then hollow glints drift up and out."""
    A_W, A_H = 50, 38
    frames = []
    a = G(A_W, A_H)
    a.paste(SPARKLE, -20, 5)
    a.paste(GLINT, 21, -5)
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A_W, A_H)
    a.paste(GLINT, -21, -9)
    a.paste(SPARKLE, 19, -12)
    a.put(-2, -17, '#')
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A_W, A_H)
    a.put(-19, -16, '#')
    a.put(22, -18, '#')
    a.put(4, -18, '#')
    frames.append(up2(a))
    return frames, ('centre', 0, -4), [300, 300, 250, 0]


HEART_S = ['.##.##.', '#######', '#######', '.#####.', '..###..', '...#...']
HEART_B = ['.###...###.',
           '#####.#####',
           '#oo########',
           '#o#########',
           '###########',
           '.#########.',
           '..#######..',
           '...#####...',
           '....###....',
           '.....#.....']


@effect
def heart():
    """A little heart pops off the numeral's shoulder: small, big with a shine and pop ticks, then gone."""
    A = 22
    frames = []
    a = G(A, A)
    a.paste(HEART_S, 0, 2)
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A, A)
    a.paste(HEART_B, 0, -1)
    for (x0, y0, x1, y1) in ((-8, -7, -9, -8), (8, -7, 9, -8), (-9, 1, -10, 1), (9, 1, 10, 1)):
        line(a, x0, y0, x1, y1)
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A, A)
    a.put(-10, -9, '#')
    a.put(10, -9, '#')
    a.put(0, -10, '#')
    frames.append(up2(a))
    return frames, ('tr', 6, -10), [300, 350, 250, 0]


SPROUT_S = ['##..##',
            '###.##',
            '.#.#..',
            '..#...',
            '..#...']
SPROUT_B = ['####.......',
            '#####...###',
            '.####..####',
            '..###.####.',
            '....#.##...',
            '.....#.....',
            '.....#.....',
            '.....#.....',
            '.....#.....']


@effect
def sprout():
    """A seedling pokes up out of the gap between the digits, unfurls two leaves, then a twinkle."""
    A_W, A_H = 16, 14
    frames = []
    a = G(A_W, A_H)
    a.paste(SPROUT_S, 0, 4)
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A_W, A_H)
    a.paste(SPROUT_B, 0, 2)
    g = up2(a)
    g.halo()
    frames.append(g)
    a = G(A_W, A_H)
    a.put(-5, -5, '#')
    a.put(5, -6, '#')
    frames.append(up2(a))
    return frames, ('top', 0, -14), [300, 350, 250, 0]


# ---------------- KO (optional) ----------------

@effect
def fizzle():
    """KO (optional): the numeral dissolves into dither and puffs away; then the dead panel draws."""
    W, H = 80, 76
    box = lambda x, y: -38 <= x < 38 and -30 <= y < 30
    frames = []
    for t, wisps in (('D4', [(-20, -33)]),
                     ('D8', [(-14, -35), (14, -34)]),
                     ('D12', [(-8, -37), (20, -37)])):
        g = G(W, H)
        g.pattern(t, box, 'o')
        for x, y in wisps:
            g.paste(['##', '##'], x, y)
        frames.append(g)
    return frames, ('centre', 0, -2), [300, 300, 300, 0]


def write(name, frames, anchor, holds, doc):
    d = os.path.join(HERE, name)
    os.makedirs(d, exist_ok=True)
    for f in os.listdir(d):
        if f.startswith('frame-'):
            os.remove(os.path.join(d, f))
    frames = frames + [G(frames[0].w, frames[0].h)]  # last frame: empty (clean redraw)
    for i, g in enumerate(frames, 1):
        with open(os.path.join(d, f'frame-{i}.txt'), 'w') as f:
            f.write('\n'.join(g.rows()) + '\n')
    ref, dx, dy = anchor
    with open(os.path.join(d, 'meta.txt'), 'w') as f:
        f.write(f'# {name}: {doc}\n')
        f.write(f'size {frames[0].w} {frames[0].h}\n')
        f.write(f'frames {len(frames)}\n')
        f.write(f'role {ROLES[name]}\n')
        f.write(f'anchor {ref} {dx} {dy}\n')
        f.write('hold_ms ' + ' '.join(str(h) for h in holds) + '\n')
    glyphs = render.load_glyphs()
    fl = render.flips(name, glyphs)
    ink = [sum(r.count('#') for r in g.rows()) for g in frames]
    ko = [sum(r.count('o') for r in g.rows()) for g in frames]
    with open(os.path.join(d, 'meta.txt'), 'a') as f:
        f.write('ink_px ' + ' '.join(map(str, ink)) + '\n')
        f.write('knockout_px ' + ' '.join(map(str, ko)) + '\n')
        f.write('flips ' + ' '.join(map(str, fl)) + f'   # total {sum(fl)} dither px'
                f' = {sum(fl) * 16} device px, over sample "37"\n')
        f.write('# anchor: frame centre = <ref point of the life-total ink box> + (dx, dy), dither px,\n'
                '#   seat-upright coords (y down); ref in centre|tl|tr|bl|br|top|bottom|left|right.\n'
                '# hold_ms: per frame, last frame (empty) = 0: it is the end state, the clean number.\n'
                '# flips: changed dither px per transition base->f1, f1->f2, ..., f(N-1)->fN(clean).\n')
    return fl


# T28c: only these round-one effects still ship from this script. pow, crack and twinkle in
# design/fx/ are now the round-two refits (from options/author2.py); claw, wham and zap are retired.
# Re-authoring those names here would overwrite the shipped frames, so it is refused.
ROLES = {'heart': 'heal', 'sprout': 'heal', 'fizzle': 'ko'}


def main():
    names = sys.argv[1:] or list(ROLES)
    for n in names:
        if n not in ROLES:
            sys.exit(f'{n}: not authored here any more (T28c: see design/fx/README.md)')
        frames, anchor, holds = EFFECTS[n]()
        fl = write(n, frames, anchor, holds, EFFECTS[n].__doc__.strip())
        print(f'{n:8s} {frames[0].w}x{frames[0].h} frames={len(frames) + 1} flips={fl} total={sum(fl)}')


if __name__ == '__main__':
    main()
