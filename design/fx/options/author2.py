#!/usr/bin/env python3
"""Author the round-two effect frames in design/fx/options/<name>/ (stdlib only).

Usage: python3 design/fx/options/author2.py [name ...]     (default: all)

Re-running overwrites that effect's frame-N.txt and meta.txt. Symbols: see render2.py
('#' ink, 'o' paper, 'x' cut/invert, '1'-'9' tile layers declared in meta.txt).
Coordinates are dither px relative to the frame centre. Tile-anchored frames are the whole seat
tile (233x175) with the centre on the life total's centre, so (0, 0) = numeral centre and the
tile spans x -116..116, y -87..87. Keep-outs in those coords: '-' glyph x -101..-92 / '+' glyph
x 90..103 (both y -7..6), tab x -44..44 y >= 67, diamond zone in both top corners.
A 2-digit sticker total spans x -34..33, y -28..27; 3 digits x -52..51.
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
from author import G, line, polyline  # noqa: E402  (round-one primitives, unmodified)
import render2  # noqa: E402

EFFECTS = {}
TILE_W, TILE_H = 233, 175


def effect(fn):
    EFFECTS[fn.__name__.replace('_', '-')] = fn
    return fn


def tileframe():
    return G(TILE_W, TILE_H)


# ---------------------------------------------------------------- primitives

def sparkle(g, cx, cy, r, ch='#', waist=0.24):
    """4-point twinkle with concave sides and a fat core."""
    c = r * waist
    g.poly([(cx, cy - r), (cx + c, cy - c), (cx + r, cy), (cx + c, cy + c),
            (cx, cy + r), (cx - c, cy + c), (cx - r, cy), (cx - c, cy - c)], ch)


def dot(g, x, y, ch='#', n=2):
    for dx in range(n):
        for dy in range(n):
            g.put(x + dx, y + dy, ch)


def dotted_arc(g, rx, ry, a0, a1, step, ch='#', n=2, cx=0, cy=0):
    """Dots of n x n every ~step px along an ellipse arc (angles in degrees, 0 = right, y down)."""
    a = math.radians(a0)
    end = math.radians(a1)
    while a <= end:
        x, y = cx + rx * math.cos(a), cy + ry * math.sin(a)
        dot(g, round(x - n / 2), round(y - n / 2), ch, n)
        # advance by arc length
        dx, dy = -rx * math.sin(a), ry * math.cos(a)
        a += step / max(1e-6, math.hypot(dx, dy))


def halo(g, src=('#',), ch='o', diag=True):
    nb = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if diag else [])
    add = [(x, y) for x, y in g.cells()
           if g.at(x, y) == '.' and any(g.at(x + a, y + b) in src for a, b in nb)]
    for x, y in add:
        g.put(x, y, ch)


def chip(g, x, y, art):
    g.paste(art, x, y)


CHIPS = [['##.', '###', '.#.'], ['.##', '###', '##.'], ['##', '##'], ['#.', '##'],
         ['###', '.##'], ['.#', '##', '#.']]


def wedge(g, ox, oy, ix, iy, base, ch='#'):
    """Triangle from a base centred on (ox, oy) to a tip at (ix, iy)."""
    L = math.hypot(ix - ox, iy - oy) or 1
    px, py = -(iy - oy) / L * base / 2, (ix - ox) / L * base / 2
    g.poly([(ox + px, oy + py), (ox - px, oy - py), (ix, iy)], ch)


def box_fill(g, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            g.put(x, y, ch)


def burst_outline(g, cx, cy, r_out, r_in, n=8, ch_rim='#', ch_in='o', rim=2, seed=3, rot=-90):
    """Comic burst: paper interior, ink rim (rim px), irregular points."""
    rnd = random.Random(seed)
    pts = []
    for i in range(2 * n):
        ang = math.radians(rot) + i * math.pi / n + rnd.uniform(-.06, .06)
        rr = (r_out * rnd.uniform(.82, 1.05)) if i % 2 == 0 else r_in * rnd.uniform(.9, 1.1)
        pts.append((cx + math.cos(ang) * rr, cy + math.sin(ang) * rr))
    inner = G(g.w, g.h)
    inner.poly(pts, 'x')
    # rim = cells within rim px of the outside
    for x, y in inner.cells():
        if inner.at(x, y) != 'x':
            continue
        edge = any(inner.at(x + a, y + b) in (None, '.')
                   for a in range(-rim, rim + 1) for b in range(-rim, rim + 1)
                   if abs(a) + abs(b) <= rim)
        g.put(x, y, ch_rim if edge else ch_in)


# ================================================================= 1. refits of the approved three

CHUNKS = [['.##.', '####', '###.'], ['###', '###', '.#.'], ['.##', '###', '##.'],
          ['##.', '###', '.##'], ['###', '##.'], ['.#', '##', '##']]


@effect
def pow2():
    """Refit of pow: a bigger comic burst lands on the top-right shoulder and punches a paper star
    out of the digit; next beat a ragged dent stays bitten out of the digit (paper only, so it
    shows only where there is ink) while chunks fly off with speed ticks; then clean."""
    W = H = 84
    f1 = G(W, H)
    burst_outline(f1, 0, 0, 22, 11, n=8, seed=5)
    halo(f1)
    f2 = G(W, H)
    burst_outline(f2, -3, 3, 11, 8, n=7, seed=9, rim=0, ch_rim='o')
    for ang, d, art in ((-62, 27, CHUNKS[0]), (-22, 30, CHUNKS[1]), (-100, 29, CHUNKS[4]),
                        (16, 28, CHUNKS[5])):
        a = math.radians(ang)
        chip(f2, round(math.cos(a) * d), round(math.sin(a) * d), art)
        f2.stroke([(math.cos(a) * (d - 15), math.sin(a) * (d - 15)),
                   (math.cos(a) * (d - 8), math.sin(a) * (d - 8))], 2)
    halo(f2)
    return [f1, f2], ('tr', -14, 10), [350, 300], {}


@effect
def crack2():
    """Refit of crack: a cut (white through the digits, black over paper) zig-zags down through the
    numeral and out past it; next beat it branches and two shards drop; then crumbs; then clean."""
    W, H = 64, 108
    pts = [(-4, -48), (2, -38), (-6, -26), (5, -13), (-3, -2), (8, 9), (0, 19), (6, 31), (1, 46)]
    f1 = G(W, H)
    f1.stroke(pts, lambda t: 3 if .15 < t < .85 else 2, 'x')
    f2 = G(W, H)
    f2.stroke(pts, lambda t: 4 if .2 < t < .8 else 2, 'x')
    f2.stroke([(-3, -2), (-12, -7), (-19, -4)], 3, 'x')
    f2.stroke([(8, 9), (16, 5), (22, 10)], 3, 'x')
    f2.stroke([(6, 31), (14, 37)], 2, 'x')
    chip(f2, -20, 42, CHUNKS[0])
    chip(f2, 20, 46, CHUNKS[2])
    halo(f2)
    f3 = G(W, H)
    dot(f3, -21, 50)
    dot(f3, 21, 51)
    dot(f3, 4, 50)
    return [f1, f2, f3], ('centre', 0, 0), [350, 300, 250], {}


@effect
def twinkle2():
    """Refit of twinkle: a fat twinkle pops off one shoulder while a glint is knocked out of the other
    digit (paper only, clipped by the digit), like shine on a sticker; they swap and drift up; dots; clean."""
    W, H = 150, 100
    f1 = G(W, H)
    sparkle(f1, -48, 2, 13, waist=.28)
    halo(f1)
    sparkle(f1, 10, -23, 7, 'o', waist=.38)     # glint cut into the right digit's top bar
    f2 = G(W, H)
    sparkle(f2, 48, -32, 13, waist=.28)
    sparkle(f2, -50, -26, 6, waist=.3)
    halo(f2)
    sparkle(f2, -16, -4, 7, 'o', waist=.38)     # glint in the left digit's middle
    f3 = G(W, H)
    for x, y in ((-50, -38), (50, -48), (6, -44)):
        dot(f3, x, y)
    return [f1, f2, f3], ('centre', 0, 0), [300, 300, 250], {}


# ================================================================= 2. claw, closer

def lerp(p, q, t):
    return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)


CLAW_LINES = [((-68, -46), (14, 52)), ((-46, -56), (36, 42)), ((-24, -66), (58, 32))]
BIGCHUNKS = [['..##..', '.#####', '######', '#####.', '.###..'],
             ['####..', '#####.', '.#####', '..###.'],
             ['.###', '####', '####', '###.', '.#..'],
             ['#####', '.####', '..###', '...#.'],
             ['.##.', '####', '####', '.##.']]


def slash(g, pts, wmax, ch='x', power=0.9, zone=None):
    """Tapered claw slash along a polyline. ch 'x' cuts (white over the digits, black over paper).
    With zone=(zx, zy) it is drawn without the cut symbol instead: paper inside the zone
    (the numeral box), ink outside it."""
    t = G(g.w, g.h)
    t.stroke(pts, lambda u: max(1.0, wmax * math.sin(math.pi * u) ** power), 'x')
    for x, y in t.cells():
        if t.at(x, y) == 'x':
            if zone is None:
                g.put(x, y, ch)
            else:
                g.put(x, y, 'o' if abs(x) <= zone[0] and abs(y) <= zone[1] else '#')


def claw_debris(g, pts):
    for (x, y), art in zip(pts, BIGCHUNKS):
        chip(g, x, y, art)


def nicks(g, lines, a=.4, b=.6, ch='o'):
    for p, q in lines:
        g.stroke([lerp(p, q, a), lerp(p, q, b)], lambda t: 3 if .25 < t < .75 else 2, ch)


@effect
def claw_cut():
    """Three long tapered slashes swipe across the numeral and well past it on both sides: black
    over paper, white where they cut through the digits (the 'x' cut symbol). Next beat: short
    white nicks stay in the digits and chunks spill off the exit end. Clean."""
    W, H = 160, 132
    f1 = G(W, H)
    for p, q in CLAW_LINES:
        slash(f1, [p, q], 5)
    f2 = G(W, H)
    nicks(f2, CLAW_LINES)
    claw_debris(f2, [(26, 58), (46, 50), (66, 42), (36, 66)])
    halo(f2, src=('#',))
    return [f1, f2], ('centre', 0, 0), [350, 300], {}


@effect
def claw_paper():
    """claw-cut without the new cut symbol: the slash is paper inside the 2-digit numeral box and
    ink outside it. Works with a round-one runtime; the price is a short invisible stretch where
    a slash crosses paper inside the box (and on 3-digit totals the outer digits are not cut)."""
    W, H = 160, 132
    f1 = G(W, H)
    for p, q in CLAW_LINES:
        slash(f1, [p, q], 5, zone=(35, 28))
    f2 = G(W, H)
    nicks(f2, CLAW_LINES)
    claw_debris(f2, [(26, 58), (46, 50), (66, 42), (36, 66)])
    halo(f2, src=('#',))
    return [f1, f2], ('centre', 0, 0), [350, 300], {}


@effect
def claw_swipe():
    """Tile-scale claw: three slashes rake the tile from near its top-left to its bottom-right,
    thin over paper, fattest where they cut the digits. Next beat: white nicks stay in the
    numeral and chunks land in the bottom-right. Clean."""
    f1 = tileframe()
    lines = [((-108, -50), (52, 62)), ((-86, -64), (78, 52)), ((-62, -76), (102, 38))]
    for p, q in lines:
        slash(f1, [p, q], 6, power=1.6)
    f2 = tileframe()
    nicks(f2, lines, .40, .60)
    claw_debris(f2, [(62, 64), (84, 56), (102, 46), (76, 74), (100, 68)])
    halo(f2, src=('#',))
    return [f1, f2], ('tile', 0, 0), [350, 300], {}


# ================================================================= 3. big options (tile-wide)

@effect
def quake():
    """Shockwave. Beat 1: the numeral is jolted, sliced by three paper cuts, with impact ticks
    at its four corners. Beat 2: a shockwave has run out to the tile edges as two broken dotted
    arcs. Beat 3: a few dots at the rim. Clean."""
    f1 = tileframe()
    for y, x0, x1 in ((-14, -60, 52), (2, -54, 60), (16, -58, 54)):
        f1.stroke([(x0, y), (x1, y)], 1, 'o')
    for sx in (-1, 1):
        for sy in (-1, 1):
            for da in (-22, 0, 22):
                a = math.atan2(sy * 34, sx * 46) + math.radians(da)
                r0, r1 = 50, 60 if da == 0 else 56
                f1.stroke([(math.cos(a) * r0 * 1.15, math.sin(a) * r0 * .85),
                           (math.cos(a) * r1 * 1.15, math.sin(a) * r1 * .85)], 2)
    f2 = tileframe()
    dotted_arc(f2, 102, 74, 200, 340, 9)
    dotted_arc(f2, 102, 74, 22, 58, 9)
    dotted_arc(f2, 102, 74, 122, 158, 9)
    dotted_arc(f2, 82, 60, 208, 332, 11)
    dotted_arc(f2, 82, 60, 26, 62, 11)
    dotted_arc(f2, 82, 60, 118, 154, 11)
    f3 = tileframe()
    for a in (215, 270, 325, 40, 140):
        r = math.radians(a)
        dot(f3, round(108 * math.cos(r)), round(78 * math.sin(r)))
    return [f1, f2, f3], ('tile', 0, 0), [300, 300, 250], {}


@effect
def focus():
    """BIG HIT. Beat 1: manga focus lines rush in from every edge of the tile while the numeral
    is knocked out to a ghost (paper through a line tile, so it can't be mistaken for the
    shadow). Beat 2: the numeral is back, solid, framed by four impact corners. Clean."""
    f1 = tileframe()
    rnd = random.Random(7)
    n = 34
    for i in range(n):
        a = 2 * math.pi * (i + .5) / n + rnd.uniform(-.03, .03)
        c, s = math.cos(a), math.sin(a)
        t = min(118 / abs(c) if c else 1e9, 89 / abs(s) if s else 1e9)
        ox, oy = c * t, s * t
        if abs(oy) < 18 and abs(ox) > 80:      # +/- glyphs
            continue
        if oy > 60 and abs(ox) < 54:           # tab
            continue
        if oy < -56 and abs(ox) > 84:          # diamond corners
            continue
        L = rnd.choice((22, 28, 34))
        wedge(f1, ox, oy, ox - c * L, oy - s * L, 3)
    box_fill(f1, -56, -30, 55, 29, '1')
    f2 = tileframe()
    for sx in (-1, 1):
        for sy in (-1, 1):
            x, y = sx * 74, sy * 52
            f2.stroke([(x, y - sy * 12), (x, y), (x - sx * 14, y)], 3)
    return [f1, f2], ('tile', 0, 0), [400, 300], {'1': ('HLINES', 'paper')}


FAULT = [(-116, -36), (-96, -30), (-88, -40), (-70, -28), (-48, -32), (-36, -16), (-20, -20),
         (-8, 0), (6, -6), (14, 10), (30, 6), (44, 22), (58, 18), (70, 30), (92, 30), (100, 40),
         (116, 42)]
def taper(t):
    return 4 if .35 < t < .65 else (3 if .2 < t < .8 else 2)


@effect
def fault():
    """Tile-wide crack. Beat 1: a jagged cut runs edge to edge across the tile, white through the
    digits and black over paper, with two short branches. Beat 2: it splits further and chunks
    drop toward the bottom. Beat 3: crumbs. Clean."""
    f1 = tileframe()
    f1.stroke(FAULT, taper, 'x')
    for b in FAULT_BRANCHES[:2]:
        f1.stroke(b, 2, 'x')
    f2 = tileframe()
    f2.stroke(FAULT, taper, 'x')
    for b in FAULT_BRANCHES:
        f2.stroke(b, 2, 'x')
    for x, y, art in ((-30, 44, BIGCHUNKS[0]), (22, 48, BIGCHUNKS[1]), (58, 50, BIGCHUNKS[2]),
                      (-60, 40, BIGCHUNKS[4])):
        chip(f2, x, y, art)
    halo(f2)
    f3 = tileframe()
    for x, y in ((-32, 60), (24, 62), (60, 60), (-62, 54)):
        dot(f3, x, y)
    return [f1, f2, f3], ('tile', 0, 0), [350, 300, 250], {}


def vine(g, side, y_top, leaves, flower_at=False, runner=0):
    """A vine up a side edge from the bottom corner (side -1 left, 1 right), below the +/- glyphs,
    with an optional runner along the bottom edge toward the tab."""
    x_edge = side * 111
    pts = []
    y = 86
    while y >= y_top:
        pts.append((x_edge + side * -2.5 * math.sin((86 - y) / 10), y))
        y -= 2
    g.stroke(pts, 2)
    for ly in leaves:
        xs = x_edge + side * -2.5 * math.sin((86 - ly) / 10)
        leaf(g, xs - side, ly, -side)
    if flower_at:
        flower(g, x_edge - side * 2, y_top - 5)
    if runner:
        rp = [(x_edge - side * i, 84 - 2 * math.sin(i / 7)) for i in range(0, runner + 1, 2)]
        g.stroke(rp, 2)
        for i in range(12, runner, 14):
            leaf_up(g, x_edge - side * i, 82 - 2 * math.sin(i / 7), -side)


def leaf(g, x, y, d):
    pts = [(x, y), (x + d * 4, y - 5), (x + d * 10, y - 6), (x + d * 8, y - 1), (x + d * 3, y + 1)]
    g.poly(pts, '#')


def flower(g, x, y):
    for dx, dy in ((0, -3), (3, 0), (0, 3), (-3, 0)):
        g.disc(x + dx, y + dy, 2.4, '#')
    for dx in (-1, 0):
        for dy in (-1, 0):
            g.put(x + dx, y + dy, 'o')


@effect
def vines():
    """HEAL. Beat 1: two vines sprout in the bottom corners and climb the side edges. Beat 2:
    they have reached up to the +/- glyphs, opened a flower each, and sent runners along the
    bottom edge toward the tab. Beat 3: only the two flowers. Clean."""
    f1 = tileframe()
    for s in (-1, 1):
        vine(f1, s, 52, [74, 62])
    f2 = tileframe()
    for s in (-1, 1):
        vine(f2, s, 26, [74, 62, 50, 38], flower_at=True, runner=52)
    f3 = tileframe()
    for s in (-1, 1):
        flower(f3, s * 109, 21)
    return [f1, f2, f3], ('tile', 0, 0), [300, 350, 250], {}


GLINT = ['...#...', '..#.#..', '.#...#.', '#.....#', '.#...#.', '..#.#..', '...#...']


@effect
def rise():
    """HEAL. Beat 1: a few fat twinkles pop low around the numeral. Beat 2: they have risen and
    spread across the upper tile, with hollow glints between. Beat 3: dots at the top. Clean."""
    f1 = tileframe()
    for x, y, r in ((-64, 16, 11), (66, 4, 9), (-50, 42, 6), (52, 40, 7)):
        sparkle(f1, x, y, r, waist=.3)
    halo(f1)
    f2 = tileframe()
    for x, y, r in ((-66, -40, 12), (62, -52, 10), (-20, -64, 7), (90, -26, 6)):
        sparkle(f2, x, y, r, waist=.3)
    for x, y in ((28, -42), (-92, -50), (-44, -70)):
        f2.paste([''.join(c for c in row) for row in GLINT], x, y)
    halo(f2)
    f3 = tileframe()
    for x, y in ((-70, -72), (64, -76), (-30, -80), (20, -78), (88, -52)):
        dot(f3, x, y)
    return [f1, f2, f3], ('tile', 0, 0), [300, 300, 250], {}


@effect
def sunrise():
    """HEAL. Beat 1: a fan of soft dither rays rises from behind the numeral. Beat 2: the rays
    reach the top of the tile, a fat twinkle at the tips of two. Clean."""
    def rays(g, r0, r1, sym, spread=11):
        for i in range(7):
            a = math.radians(-90 + (i - 3) * 24)
            h = math.radians(spread / 2)
            p0 = (math.cos(a) * r0, math.sin(a) * r0 - 4)
            g.poly([p0, (math.cos(a - h) * r1, math.sin(a - h) * r1 - 4),
                    (math.cos(a + h) * r1, math.sin(a + h) * r1 - 4)], sym)
    f1 = tileframe()
    rays(f1, 40, 66, '1')
    f2 = tileframe()
    rays(f2, 46, 118, '1', spread=11)
    for x, y in f2.cells():   # keep clear of the tile edge and the diamond corners
        if f2.at(x, y) == '1' and (y < -84 or abs(x) + (-y) > 165):
            f2.put(x, y, '.')
    sparkle(f2, -40, -70, 9, waist=.3)
    sparkle(f2, 40, -70, 7, waist=.3)
    halo(f2)
    return [f1, f2], ('tile', 0, 0), [300, 350], {'1': ('D4', 'ink')}


# ================================================================= 4. optional flourishes

@effect
def ko_shatter():
    """KO. Beat 1: the numeral is knocked to a grey ghost and a crack runs across the tile through
    it. Beat 2: the ghost fades further and chunks fall. Then the runtime draws the dead panel
    (flips here are measured to the clean number, not to the dead panel)."""
    f1 = tileframe()
    box_fill(f1, -56, -30, 55, 29, '1')
    f1.stroke(FAULT, taper, '#')
    for b in FAULT_BRANCHES[:2]:
        f1.stroke(b, 2, '#')
    halo(f1)
    f2 = tileframe()
    box_fill(f2, -56, -30, 55, 29, '2')
    for x, y, art in ((-40, 42, CHUNKS[0]), (-6, 52, CHUNKS[1]), (30, 46, CHUNKS[2]),
                      (58, 56, CHUNKS[3]), (-66, 58, CHUNKS[4])):
        chip(f2, x, y, art)
    halo(f2)
    return [f1, f2], ('tile', 0, 0), [350, 350], {'1': ('D8', 'paper'), '2': ('D12', 'paper')}


def confetti(g, x, y, kind, rnd):
    if kind == 0:                      # tumbling bar
        a = rnd.uniform(0, math.pi)
        g.stroke([(x - 5 * math.cos(a), y - 5 * math.sin(a)),
                  (x + 5 * math.cos(a), y + 5 * math.sin(a))], 3)
    elif kind == 1:                    # solid square
        box_fill(g, x - 3, y - 3, x + 1, y + 1, '#')
    elif kind == 2:                    # checker square (tile layer)
        box_fill(g, x - 4, y - 4, x + 3, y + 3, '3')
    else:                              # streamer squiggle
        g.stroke([(x - 10 + i, y + 3 * math.sin(i / 2.5)) for i in range(21)], 2)


@effect
def winner():
    """LAST PLAYER STANDING. Beat 1: a laurel wreath closes round the numeral with two big
    twinkles over it. Beat 2: confetti bursts across the whole tile around the wreath. Beat 3: a
    few pieces settle at the bottom. Clean."""
    rnd = random.Random(11)

    def scatter(g, n, ylo, yhi):
        placed = tries = 0
        pts = []
        while placed < n and tries < 4000:
            tries += 1
            x, y = rnd.randint(-104, 104), rnd.randint(ylo, yhi)
            if (x / 80) ** 2 + ((y - 4) / 58) ** 2 < 1:   # inside / on the wreath
                continue
            if abs(y) < 24 and abs(x) > 80:
                continue
            if y > 54 and abs(x) < 54:
                continue
            if y < -54 and abs(x) > 80:
                continue
            if any(math.hypot(x - a, y - b) < 22 for a, b in pts):
                continue
            pts.append((x, y))
            confetti(g, x, y, placed % 4, rnd)
            placed += 1
    f1 = tileframe()
    wreath(f1)
    sparkle(f1, -24, -58, 9, waist=.3)
    sparkle(f1, 26, -56, 7, waist=.3)
    halo(f1)
    f2 = tileframe()
    wreath(f2)
    scatter(f2, 16, -84, 80)
    halo(f2, src=('#', '3'))
    f3 = tileframe()
    for x, y in ((-92, 70), (-66, 76), (68, 74), (94, 68)):
        confetti(f3, x, y, 1, rnd)
    return [f1, f2, f3], ('tile', 0, 0), [350, 400, 250], {'3': ('CHECKER', 'fill')}



FAULT_BRANCHES = [[(-36, -16), (-44, -4), (-42, 4)], [(30, 6), (38, -6), (46, -8)],
                  [(-70, -28), (-66, -16)], [(70, 30), (64, 40)]]




def leaf_up(g, x, y, d):
    g.poly([(x, y), (x + d * 2, y - 5), (x + d * 3, y - 10), (x + d * 5, y - 6), (x + d * 4, y - 1)])




def wreath(g, rx=64, ry=44, top_gap=40):
    """Laurel wreath round the numeral: two branches from the bottom, open at the top."""
    for side in (-1, 1):
        a0, a1 = 90, 270 - top_gap if side < 0 else -90 + top_gap
        pts = []
        n = 28
        for i in range(n + 1):
            a = math.radians(90 + side * -(i / n) * (180 - top_gap))
            pts.append((math.cos(a) * rx, math.sin(a) * ry + 4))
        g.stroke(pts, 2)
        for i in range(3, n + 1, 4):
            a = math.radians(90 + side * -(i / n) * (180 - top_gap))
            x, y = math.cos(a) * rx, math.sin(a) * ry + 4
            tx, ty = -math.sin(a) * rx * side * -1, math.cos(a) * ry * side * -1   # along the stem
            L = math.hypot(tx, ty)
            tx, ty = tx / L, ty / L
            nx, ny = math.cos(a), math.sin(a)                                      # outward
            for k in (1, -1):
                bx, by = x + nx * k * 1.5, y + ny * k * 1.5
                tip = (bx + tx * 6 + nx * k * 5, by + ty * 6 + ny * k * 5)
                g.poly([(bx, by), (bx + tx * 2 + nx * k * 3 + ty * k, by + ty * 2 + ny * k * 3 - tx * k),
                        tip, (bx + tx * 4 + nx * k * 1, by + ty * 4 + ny * k * 1)])




# ---------------------------------------------------------------- write

MARGIN = 3


def write(name, frames, anchor, holds, tiles, doc):
    ref, dx, dy = anchor
    d = os.path.join(HERE, name)
    os.makedirs(d, exist_ok=True)
    for f in os.listdir(d):
        if f.startswith('frame-'):
            os.remove(os.path.join(d, f))
    if ref == 'tile':
        # keep a 3 px paper margin inside the tile so nothing touches the dotted dividers
        for g in frames:
            for y in range(g.h):
                for x in range(g.w):
                    if x < MARGIN or y < MARGIN or x >= g.w - MARGIN or y >= g.h - MARGIN:
                        g.c[y][x] = '.'
    frames = frames + [G(frames[0].w, frames[0].h)]
    for i, g in enumerate(frames, 1):
        with open(os.path.join(d, f'frame-{i}.txt'), 'w') as f:
            f.write('\n'.join(g.rows()) + '\n')
    holds = list(holds) + [0]
    with open(os.path.join(d, 'meta.txt'), 'w') as f:
        f.write(f'# {name}: {" ".join(doc.split())}\n')
        f.write(f'size {frames[0].w} {frames[0].h}\n')
        f.write(f'frames {len(frames)}\n')
        f.write(f'anchor {ref} {dx} {dy}\n')
        for k, (t, m) in sorted(tiles.items()):
            f.write(f'tile {k} {t} {m}\n')
        f.write('hold_ms ' + ' '.join(map(str, holds)) + '\n')
    rows = [g.rows() for g in frames]
    cnt = lambda chs: [sum(sum(r.count(c) for c in chs) for r in fr) for fr in rows]
    fl = render2.flips(name)
    with open(os.path.join(d, 'meta.txt'), 'a') as f:
        f.write('ink_px ' + ' '.join(map(str, cnt('#'))) + '\n')
        f.write('knockout_px ' + ' '.join(map(str, cnt('o'))) + '\n')
        if any(cnt('x')):
            f.write('cut_px ' + ' '.join(map(str, cnt('x'))) + '\n')
        if tiles:
            f.write('tile_px ' + ' '.join(map(str, cnt('123456789'))) + '\n')
        f.write('flips ' + ' '.join(map(str, fl)) + f'   # total {sum(fl)} dither px'
                f' = {sum(fl) * 16} device px, over sticker "37" in a full tile\n')
        f.write('# anchor: frame centre = ref point + (dx, dy) in dither px, seat-upright coords\n'
                '#   (y down). ref "tile" = the seat tile centre; otherwise a point of the life\n'
                '#   total\'s layout box (centre|tl|tr|bl|br|top|bottom|left|right).\n'
                '# symbols: # ink, o paper, x cut (invert), digits = tile layers above (global phase)\n'
                '# hold_ms: last frame (empty) = 0: the clean redraw is the end state.\n'
                '# flips: changed dither px base->f1, ..., last drawn frame->clean.\n')
    return fl


def main():
    names = sys.argv[1:] or list(EFFECTS)
    for n in names:
        frames, anchor, holds, tiles = EFFECTS[n]()
        fl = write(n, frames, anchor, holds, tiles, EFFECTS[n].__doc__)
        print(f'{n:12s} {frames[0].w}x{frames[0].h} drawn={len(frames)} flips={fl} total={sum(fl)}')


if __name__ == '__main__':
    main()
