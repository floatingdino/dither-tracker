#!/usr/bin/env python3
"""Author the round-three effect candidates in design/fx/round3/<name>/ (T28f; stdlib only).

Usage: python3 design/fx/round3/author3.py [name ...]      (default: all)

Design only: nothing here is read by the app build (it reads design/fx/<name>/ one level deep).
Re-running overwrites that effect's frame-N.txt and meta.txt. Symbols as round two (render3.py):
'#' ink, 'o' paper, 'x' cut (invert), '1'-'9' tile layers declared per effect (global phase).

Geometry. Most candidates are one frame size, FW x FH = 149 x 165, anchored on the total's centre,
so the same frames fit the quad tile (233 wide) and the narrow tile (155 wide, 3 px margins) and
nothing depends on sub-tile centring. Coordinates are dither px relative to the total's centre
(= the tile centre on both tiers). A 2-digit sticker total spans x -34..33, y -28..27; 3 digits
x -52..51. Keep-outs (cleared by keepout(), and reported if anything had to be cleared):
  tab       |x| <= 47 and y >= 64      (both tiers)
  diamond   |x| + (y + 87) <= 27       (top-centre of the narrow middle seats; the quad seats'
                                         diamond corner and +/- glyphs lie outside the frame)
"""
import math
import os
import random
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
FX = os.path.dirname(HERE)
ROOT = os.path.abspath(os.path.join(FX, '..', '..'))
sys.path.insert(0, FX)
sys.path.insert(0, os.path.join(FX, 'options'))
sys.path.insert(0, HERE)
from author import G, line, polyline  # noqa: E402  (round-one primitives, unchanged)
from author2 import sparkle, dot, dotted_arc, halo, wedge, box_fill, CHUNKS  # noqa: E402
import render3  # noqa: E402

FW, FH = 149, 165
EFFECTS = {}

# T55: the owner's approvals. These are authored into the shipped set, design/fx/<name>/, with the
# role the runtime plays them for (app/.../fx/FxRole); the rest stay candidates here in round3/.
PROMOTED = {
    'chomp': 'damage', 'slash': 'damage', 'stomp': 'big-hit', 'crit': 'big-hit',
    'chime': 'heal', 'heart-burst': 'heal',
    'ooze': 'poison', 'brand': 'poison', 'imp-raspberry': 'commander', 'imp-poke': 'commander',
    'charge': 'energy', 'level-up': 'experience', 'rad-glow': 'rad', 'storm-copy': 'storm',
    'crown-drop': 'monarch', 'crown-drop-narrow': 'monarch', 'torch': 'initiative',
    'torch-narrow': 'initiative', 'dusk': 'night', 'dawn': 'day', 'ring-tempt': 'ring',
    'your-turn': 'turn', 'spotlight': 'turn', 'start-flag': 'first-player', 'revive': 'revive',
}


def effect_dir(name):
    """Where an effect's frames live: the shipped set for a promoted one, round3/ otherwise."""
    return os.path.join(FX, name) if name in PROMOTED else os.path.join(HERE, name)


def effect(group, role, size, trigger, intent):
    def deco(fn):
        name = fn.__name__.replace('_', '-')
        EFFECTS[name] = dict(fn=fn, group=group, role=role, size=size, trigger=trigger,
                             intent=intent)
        return fn
    return deco


# ---------------------------------------------------------------- primitives

def F():
    return G(FW, FH)


class OG(G):
    """A frame grid whose centre is offset from the total's centre (anchor `centre -ox 0`): drawing
    coordinates stay relative to the total, like F(), so the art reads the same."""

    def __init__(self, w, h, ox):
        G.__init__(self, w, h)
        self.ox = ox

    def put(self, x, y, ch):
        G.put(self, x + self.ox, y, ch)

    def at(self, x, y):
        return G.at(self, x + self.ox, y)

    def cells(self):
        for x, y in G.cells(self):
            yield x - self.ox, y


def icon(name):
    return [r for r in open(os.path.join(ROOT, 'design', 'icons', name + '.txt')).read().split('\n') if r]


def trim(art):
    """Crop blank rows/cols off ASCII art."""
    rows = [r for r in art if set(r) - {'.'}]
    cols = [i for i in range(len(art[0])) if any(r[i] != '.' for r in art)]
    return [r[cols[0]:cols[-1] + 1] for r in rows]


def spr(g, art, cx, cy, k=2, mapping=None):
    """Paste ASCII art scaled k x, its centre at (cx, cy). mapping remaps symbols ('#' -> 'x')."""
    h, w = len(art) * k, len(art[0]) * k
    x0, y0 = cx - w // 2, cy - h // 2
    for j, row in enumerate(art):
        for i, v in enumerate(row):
            v = (mapping or {}).get(v, v)
            if v == '.':
                continue
            for yy in range(k):
                for xx in range(k):
                    g.put(x0 + i * k + xx, y0 + j * k + yy, v)


def ellipse(g, cx, cy, rx, ry, ch, inner=None):
    """Filled ellipse (or a ring when inner=(rx2, ry2))."""
    for x, y in g.cells():
        px, py = x + .5 - cx, y + .5 - cy
        if (px / rx) ** 2 + (py / ry) ** 2 <= 1:
            if inner and (px / inner[0]) ** 2 + (py / inner[1]) ** 2 <= 1:
                continue
            g.put(x, y, ch)


def ring(g, cx, cy, r, w, ch='#'):
    ellipse(g, cx, cy, r, r, ch, inner=(r - w, r - w))


def star5(g, cx, cy, r, ch='#', rot=-90, inner=.45):
    pts = []
    for i in range(10):
        a = math.radians(rot + i * 36)
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    g.poly(pts, ch)


def heart(g, cx, cy, s, ch='#'):
    """Heart of half-width ~s, filled."""
    pts = []
    for i in range(48):
        t = 2 * math.pi * i / 48
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((cx + x * s / 16, cy + y * s / 16))
    g.poly(pts, ch)
    return pts


def outline(g, src, ch='#', w=1, out=None):
    """Ring of width w just inside region cells of g where src(x, y) is True -> ch."""
    cells = [(x, y) for x, y in g.cells() if src(x, y)]
    s = set(cells)
    for x, y in cells:
        if any((x + a, y + b) not in s for a in range(-w, w + 1) for b in range(-w, w + 1)
               if abs(a) + abs(b) <= w):
            (out or g).put(x, y, ch)


def region(fn):
    """Draw with fn into a scratch grid and return the set of its cells."""
    t = F()
    fn(t)
    return set((x, y) for x, y in t.cells() if t.at(x, y) not in (None, '.'))


# Full-tier-only variants (their `-narrow` twin plays on the narrow tier, T55): the diamond biting
# the narrow middle seats' top edge is not on their tiles, so only the tab keep-out applies.
FULL_ONLY = {'crown-drop', 'torch'}


def keepout(g, diamond=True):
    n = 0
    for x, y in g.cells():
        if g.at(x, y) == '.':
            continue
        if (abs(x) <= 47 and y >= 64) or (diamond and abs(x) + (y + 87) <= 27):
            g.put(x, y, '.')
            n += 1
    return n


def zig(g, x0, x1, y, amp, period, w, ch):
    pts, x, i = [], x0, 0
    while x <= x1:
        pts.append((x, y + (amp if i % 2 else -amp)))
        x += period / 2
        i += 1
    g.stroke(pts, w, ch)


def speed(g, x, y, dx, dy, n=3, gap=6, L=8, w=2):
    """n parallel speed lines trailing from (x, y) in direction (dx, dy)."""
    d = math.hypot(dx, dy)
    ux, uy = dx / d, dy / d
    px, py = -uy, ux
    for i in range(n):
        o = (i - (n - 1) / 2) * gap
        sx, sy = x + px * o, y + py * o
        g.stroke([(sx, sy), (sx + ux * L, sy + uy * L)], w)


def burst_ticks(g, cx, cy, r0, r1, angles, w=2):
    for a in angles:
        a = math.radians(a)
        g.stroke([(cx + math.cos(a) * r0, cy + math.sin(a) * r0),
                  (cx + math.cos(a) * r1, cy + math.sin(a) * r1)], w)


def puff(g, cx, cy, r):
    """Dust puff: three overlapping circles, ink outline, paper inside."""
    t = F()
    for dx, dy, rr in ((-r * .7, r * .2, r * .7), (0, -r * .2, r), (r * .75, r * .25, r * .65)):
        t.disc(cx + dx, cy + dy, rr, '#')
    cells = set((x, y) for x, y in t.cells() if t.at(x, y) == '#')
    for x, y in cells:
        edge = any((x + a, y + b) not in cells for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        g.put(x, y, '#' if edge else 'o')


# ================================================================ 1. damage variety

MALLET_HANDLE = [(14, -56), (50, -82)]


def mallet(g, x0, y0, x1, y1):
    """Toy mallet head: 2 px ink rim, D4 shaded body (tile 1, fill), paper highlight stripe."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            rim = x < x0 + 2 or x > x1 - 2 or y < y0 + 2 or y > y1 - 2
            corner = (x in (x0, x1)) and (y in (y0, y1))
            if corner:
                continue
            g.put(x, y, '#' if rim else '1')
    for x in range(x0 + 4, x1 - 3):
        g.put(x, y0 + 4, 'o')
        g.put(x, y0 + 5, 'o')
    for y in (y0 + 2, y1 - 2):          # two bands round the barrel
        pass
    for x in (x0 + 7, x0 + 8, x1 - 8, x1 - 7):
        for y in range(y0 + 2, y1 - 1):
            g.put(x, y, '#')


@effect('damage', 'damage', 'small', 'damage 1-3 (pool with pow, crack)',
        'A toy mallet bonks the top of the total; dizzy stars circle over it.')
def bonk():
    f1 = F()
    mallet(f1, -14, -56, 42, -31)
    f1.stroke(MALLET_HANDLE, 5)
    burst_ticks(f1, 14, -30, 34, 44, (188, 204, 336, 352), 2)
    halo(f1)
    f2 = F()
    dotted_arc(f2, 34, 8, 180, 360, 7, cy=-40)
    for x, y, r in ((-30, -42, 7), (6, -48, 8), (32, -40, 6)):
        star5(f2, x, y, r)
    halo(f2)
    f3 = F()
    for x, y, r in ((-16, -47, 6), (24, -46, 7)):
        star5(f3, x, y, r)
    dot(f3, -36, -38)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 350, 300], {'1': ('D4', 'fill')}


def scallop(g, cx, cy, r, n, a0, a1, ch='o'):
    """A bite: a disc with n tooth-scallops along its rim between angles a0..a1 (deg)."""
    g.disc(cx, cy, r, ch)
    for i in range(n):
        a = math.radians(a0 + (a1 - a0) * (i + .5) / n)
        g.disc(cx + math.cos(a) * r, cy + math.sin(a) * r, r * .38, ch)


@effect('damage', 'damage', 'small', 'damage 1-3, 1-digit totals too (anchored on the last digit)',
        'Jaws snap at the top-right corner of the total and leave a scalloped bite in it.')
def nibble():
    W = H = 72
    f1 = G(W, H)
    # an open monster mouth over the corner: ink lips, paper inside (so it already eats the
    # corner of the digit), three fangs down and two up
    ellipse(f1, 2, 0, 24, 18, '#')
    ellipse(f1, 2, 0, 20, 14, 'o')
    for x in (-10, 2, 14):
        f1.poly([(x - 4, -14), (x + 4, -14), (x, -5)], '#')
    for x in (-4, 8):
        f1.poly([(x - 4, 14), (x + 4, 14), (x, 6)], '#')
    halo(f1, src=('#',))
    f2 = G(W, H)
    scallop(f2, 0, 0, 13, 5, 100, 350)
    for x, y, art in ((22, 18, CHUNKS[4]), (30, 6, CHUNKS[5]), (14, 28, ['##', '##'])):
        f2.paste(art, x, y)
    halo(f2, src=('#',))
    f3 = G(W, H)
    scallop(f3, 0, 0, 13, 5, 100, 350)
    dot(f3, 26, 30)
    dot(f3, 16, 34)
    return [f1, f2, f3], ('tr', -6, 6), [300, 350, 300], {}


TEETH_X = list(range(-52, 53, 13))


def jaw(g, y, sgn, tooth):
    """A jaw band: gum (D12, tile 1) with a row of paper teeth (ink outline) pointing at the total."""
    for x in range(-60, 61):
        for d in range(6):
            g.put(x, y - sgn * d, '#' if d in (0, 5) else '1')
    for x in TEETH_X:
        tri = [(x - 5, y), (x + 5, y), (x, y + sgn * tooth)]
        t = F()
        t.poly(tri, 'o')
        cells = set((a, b) for a, b in t.cells() if t.at(a, b) == 'o')
        for a, b in cells:
            edge = any((a + p, b + q) not in cells for p, q in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            g.put(a, b, '#' if edge else 'o')


@effect('damage', 'damage', 'big', 'damage 4-9',
        'Monster jaws open over and under the total, chomp shut through it, then leave tooth marks.')
def chomp():
    f1 = F()
    jaw(f1, -50, 1, 12)
    jaw(f1, 50, -1, 12)
    halo(f1)
    f2 = F()
    jaw(f2, -36, 1, 10)
    jaw(f2, 36, -1, 10)
    zig(f2, -44, 44, 0, 5, 12, 2, 'x')       # the teeth meet: a white zig-zag bitten through
    for sx in (-1, 1):
        burst_ticks(f2, sx * 56, 0, 6, 14, (150, 180, 210) if sx < 0 else (-30, 0, 30), 2)
    halo(f2)
    f3 = F()
    for x in range(-30, 31, 12):             # tooth notches left in the top and bottom edges
        f3.poly([(x - 4, -29), (x + 4, -29), (x, -21)], 'o')
        f3.poly([(x - 4, 28), (x + 4, 28), (x, 20)], 'o')
    for x, y, art in ((-46, 42, CHUNKS[0]), (40, 46, CHUNKS[2]), (-8, 50, CHUNKS[4])):
        f3.paste(art, x, y)
    halo(f3, src=('#',))
    return [f1, f2, f3], ('centre', 0, 0), [300, 350, 300], {'1': ('D8', 'fill')}


@effect('damage', 'damage', 'small', 'damage 1-9 (any size: the cheapest cut-out)',
        'One clean sword stroke cuts across the total; a hairline split stays, then a glint.')
def slash():
    pts = [(-72, 14), (72, -22)]
    f1 = F()
    f1.stroke(pts, lambda t: max(1.0, 5 * math.sin(math.pi * t) ** .7), 'x')
    sparkle(f1, 64, -20, 9, waist=.26)
    halo(f1, src=('#',))
    f2 = F()
    # the split: a 1 px paper line through the digits only (paper on paper shows nothing)
    f2.stroke([(-40, 6), (40, -14)], 1, 'o')
    sparkle(f2, -64, 12, 6, waist=.3)
    halo(f2, src=('#',))
    return [f1, f2], ('centre', 0, 0), [350, 350], {}


def foot(g):
    """A giant bare footprint, toes up: a sole (with an instep curve) and five toes."""
    ellipse(g, 0, -6, 34, 30, '#')
    ellipse(g, 6, 30, 24, 26, '#')
    for x, y in g.cells():
        if -6 <= y <= 30 and -30 + (y + 6) * .1 <= x <= 32 - (y + 6) * .05:
            g.put(x, y, '#')
    ellipse(g, -30, 18, 9, 18, '.')                # the instep bites in on the left
    for x, y, r in ((-24, -46, 9), (-6, -52, 7), (10, -50, 6), (23, -45, 5), (33, -36, 4.5)):
        g.disc(x, y, r, '#')


@effect('damage', 'big-hit', 'huge', 'damage 10 or more (with focus)',
        'A shadow falls over the total, a giant foot stomps it flat (digits ghosted), dust.')
def stomp():
    f1 = F()
    ellipse(f1, 0, 0, 62, 50, '2')               # the shadow: light dither on paper only
    f2 = F()
    inside = region(foot)
    t = F()
    foot(t)
    outline(t, lambda x, y: (x, y) in inside, ch='R', w=2)
    for x, y in inside:
        if t.at(x, y) == 'R' or y < -38:
            f2.put(x, y, '#')                  # rim and toes: ink
        else:
            f2.put(x, y, '1')                  # under the sole the digits are squashed to a ghost
    for sx in (-1, 1):
        puff(f2, sx * 56, 50, 8)
        puff(f2, sx * 62, 30, 6)
    halo(f2, src=('#',))
    f3 = F()
    for sx in (-1, 1):
        puff(f3, sx * 62, 52, 11)
        puff(f3, sx * 52, 30, 7)
        f3.stroke([(sx * 30, 28), (sx * 40, 36), (sx * 38, 46)], 2, 'x')
    halo(f3, src=('#',))
    f4 = F()
    for sx in (-1, 1):
        puff(f4, sx * 64, 48, 6)
        dot(f4, sx * 50, 58)
    return [f1, f2, f3, f4], ('centre', 0, 0), [250, 400, 300, 250], {'1': ('D12', 'paper'),
                                                                   '2': ('D2', 'ink')}


# ================================================================ 2. heal variety

NOTE = ['...#..',
        '...##.',
        '...#.#',
        '...#..',
        '.###..',
        '####..',
        '.##...']
NOTE2 = ['..######',
         '..######',
         '..#....#',
         '..#....#',
         '.##...##',
         '###..###',
         '##...##.']


@effect('heal', 'heal', 'small', 'heal 1-3 (pool with twinkle, sprout, heart)',
        'A two-note item jingle: notes hop off the shoulders of the total, an 8-bit chime.')
def chime():
    f1 = F()
    spr(f1, NOTE, -56, -8, 3)
    sparkle(f1, -40, -38, 5, waist=.3)
    halo(f1)
    f2 = F()
    spr(f2, NOTE, -54, -44, 3)
    spr(f2, NOTE2, 52, -30, 3)
    sparkle(f2, 26, -52, 6, waist=.3)
    halo(f2)
    f3 = F()
    for x, y in ((-56, -56), (56, -50), (30, -60)):
        dot(f3, x, y)
    return [f1, f2, f3], ('centre', 0, 0), [300, 350, 250], {}


FLASK = ['....####....',
         '....#oo#....',
         '...######...',
         '....#..#....',
         '....#..#....',
         '...#....#...',
         '..#......#..',
         '.#.o......#.',
         '#..o.......#',
         '#1111111111#',
         '#1111111111#',
         '#1111111111#',
         '.#11111111#.',
         '..########..']


POUR = ['..########..',
        '.#........#.',
        '#..........#',
        '#.o........#',
        '#.o........#',
        '#1111111111#',
        '.#11111111#.',
        '..#111111#..',
        '...#1111#...',
        '....#11#....',
        '....#11#....',
        '....#..#....']


@effect('heal', 'heal', 'big', 'heal 4-9',
        'A potion glugs into the total: it drains to a ghost, refills from the bottom, bubbles.')
def refill():
    def level(g, ylev, waves=True):
        for x, y in g.cells():
            if -40 <= x <= 40 and -30 <= y < ylev:
                g.put(x, y, '2')               # the empty part: the digits ghosted (D12 paper)
        if waves:
            pts = [(x, ylev + 1.5 * math.sin(x / 4)) for x in range(-44, 45, 2)]
            g.stroke(pts, 2, 'x')
    f1 = F()
    level(f1, 10)
    spr(f1, POUR, 40, -60, 3)
    for y in (-36, -24, -12):
        f1.disc(40, y, 2.2, '#')
    halo(f1, src=('#',))
    f2 = F()
    level(f2, -12)
    spr(f2, FLASK, 54, -52, 3, {'1': '.'})
    for x, y, r in ((-20, -22, 3), (6, -36, 2), (22, -26, 3)):
        ring(f2, x, y, r + 1.5, 1.5)
    halo(f2, src=('#',))
    f3 = F()
    for x, y, r in ((-18, -42, 3), (12, -50, 4), (32, -40, 2)):
        ring(f3, x, y, r + 1.5, 1.5)
    sparkle(f3, -44, -34, 7, waist=.3)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 300, 300], {'1': ('D8', 'fill'), '2': ('D12', 'paper')}


@effect('heal', 'heal', 'huge', 'heal 10 or more (with sunrise, vines)',
        'A big soft heart wraps the total, then bursts into little hearts flying to the corners.')
def heart_burst():
    f1 = F()
    shape = region(lambda t: heart(t, 0, 0, 70, '#'))
    t = F()
    for x, y in shape:
        t.put(x, y, 'h')
    outline(t, lambda x, y: (x, y) in shape, ch='#', w=1, out=f1)
    for x, y in shape:
        if f1.at(x, y) == '.':
            f1.put(x, y, '1')                # light D2 blush on paper only
    halo(f1, src=('#',))
    f2 = F()
    for x, y, s in ((-58, -50, 9), (56, -54, 11), (-62, 10, 7), (62, 6, 8), (-30, -64, 6),
                    (34, 50, 7), (-40, 52, 6)):
        heart(f2, x, y, s)
    halo(f2)
    f3 = F()
    for x, y, s in ((-62, -62, 6), (64, -66, 7), (-66, -10, 5)):
        heart(f3, x, y, s)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [350, 350, 250], {'1': ('D2', 'ink')}


# ================================================================ 3. counter-specific

def drips(g, lens, ch='x'):
    for x, L in lens:
        g.stroke([(x, -30), (x, -30 + L)], 3, ch)
        g.disc(x, -30 + L, 2.6, ch)


@effect('counter', 'poison', 'counter', 'poison counter gained',
        'Ooze slops over the top of the total and drips down through it; the poison mark shows.')
def ooze():
    goo = [(-30, 10), (-18, 18), (-6, 8), (8, 22), (20, 12), (30, 16)]
    f1 = F()
    for x, y in f1.cells():
        if -38 <= x <= 38 and -33 <= y <= -27 + 2 * math.sin(x / 5):
            f1.put(x, y, 'x')
    drips(f1, goo)
    spr(f1, trim(icon('poison')), -58, -40, 2)
    halo(f1, src=('#',))
    f2 = F()
    for x, y in f2.cells():
        if -38 <= x <= 38 and -33 <= y <= -28 + 2 * math.sin(x / 5):
            f2.put(x, y, 'x')
    drips(f2, [(x, L * 2 + 8) for x, L in goo])
    for x, y in ((-18, 44), (8, 52)):
        f2.disc(x, y, 3, '#')
    ellipse(f2, -4, 40, 30, 4, '#')            # the puddle
    spr(f2, trim(icon('poison')), -58, -46, 2)
    halo(f2, src=('#',))
    f3 = F()
    ellipse(f3, -4, 42, 22, 3, '#')
    f3.disc(10, 52, 2, '#')
    return [f1, f2, f3], ('centre', 0, 0), [300, 350, 250], {}


@effect('counter', 'poison', 'counter', 'poison counter gained (alt)',
        'The poison mark is slapped over the total like a sticker, then shrinks off and bubbles.')
def brand():
    mark = trim(icon('poison'))
    f1 = F()
    spr(f1, mark, 0, -2, 4)                  # slapped on like a sticker: ink, 2 px paper keyline
    halo(f1)
    halo(f1, src=('o',), ch='o')
    f2 = F()
    spr(f2, mark, -54, -40, 2)
    for x, y, r in ((-30, -44, 3), (-20, -56, 4), (-40, -64, 2)):
        ring(f2, x, y, r + 1.5, 1.5)
    halo(f2, src=('#',))
    f3 = F()
    for x, y, r in ((-22, -62, 2), (-36, -70, 3)):
        ring(f3, x, y, r + 1.5, 1.5)
    return [f1, f2, f3], ('centre', 0, 0), [350, 300, 250], {}


# The commander imp (design/icons/commander.txt), redrawn larger with expressions.
IMP_PEEK = ['#..................#',
            '##................##',
            '###...########...###',
            '.##################.',
            '####################',
            '###oooo######oooo###',
            '###o##o######o##o###',
            '###oooo######oooo###']
IMP_RASP = ['#..................#',
            '##................##',
            '###...########...###',
            '.##################.',
            '####################',
            '####################',
            '###o#o#o####o#o#o###',
            '##o#####o##o#####o##',
            '####################',
            '#####oooooooooo#####',
            '.####o########o####.',
            '..####oo####oo####..',
            '....####o##o####....',
            '.......o####o.......',
            '.......o#oo#o.......',
            '........o##o........',
            '.........oo.........']
IMP_GRIN = ['#..................#',
            '##................##',
            '###...########...###',
            '.##################.',
            '####################',
            '###oooo######oooo###',
            '####o#o######o#o####',
            '####################',
            '###o############o###',
            '####oooooooooooo####',
            '.####o#o#o#o#o#####.',
            '..################..',
            '....############....',
            '.......######.......']
HAND = ['.#.#.', '#####', '#####']


@effect('counter', 'commander', 'counter', 'commander damage taken (on the target)',
        'The commander imp pops up behind the total, blows a raspberry, ducks back down.')
def imp_raspberry():
    f1 = F()
    spr(f1, IMP_PEEK, 34, -44, 2)
    spr(f1, HAND, 18, -30, 2)
    spr(f1, HAND, 50, -30, 2)
    halo(f1)
    f2 = F()
    spr(f2, IMP_RASP, 36, -50, 2)
    for x, y in ((8, -36), (2, -44), (4, -28), (-4, -36)):
        dot(f2, x, y)
    spr(f2, HAND, 18, -30, 2)
    spr(f2, HAND, 54, -30, 2)
    halo(f2)
    f3 = F()
    puff(f3, 36, -38, 7)
    dot(f3, 58, -46)
    return [f1, f2, f3], ('centre', 0, 0), [300, 400, 250], {}


@effect('counter', 'commander', 'counter', 'commander damage taken (alt)',
        'The imp jabs the total with a pitchfork: three holes punched in the digit, a cheeky grin.')
def imp_poke():
    def fork(g, x_head, tine_ch, tine_len):
        g.stroke([(-74, -2), (x_head, -2)], 3)
        g.stroke([(x_head, -12), (x_head, 8)], 3)
        for y in (-12, -2, 8):
            g.stroke([(x_head, y), (x_head + tine_len, y)], 2, tine_ch)
    f1 = F()
    spr(f1, IMP_GRIN, -56, -34, 2)
    fork(f1, -48, '#', 9)
    halo(f1)
    f2 = F()
    spr(f2, IMP_GRIN, -50, -36, 2)
    fork(f2, -40, '#', 8)
    for y in (-12, -2, 8):
        f2.stroke([(-31, y), (-22, y)], 3, 'x')     # the tines go in: white through the digit
    burst_ticks(f2, -26, -2, 16, 22, (-70, -100, 70, 100), 2)
    halo(f2, src=('#',))
    f3 = F()
    for y in (-12, -2, 8):
        f3.disc(-24, y, 3, 'o')
    puff(f3, -56, -40, 7)
    return [f1, f2, f3], ('centre', 0, 0), [300, 350, 300], {}


@effect('counter', 'energy', 'counter', 'energy counter gained',
        'A bolt strikes the top of the total and cracks into it, then sparks crackle round it.')
def charge():
    E = trim(icon('energy'))
    f1 = F()
    spr(f1, E, -28, -46, 3)
    halo(f1)
    f1.stroke([(-34, -27), (-28, -16), (-36, -6)], 3, 'x')   # where it lands: a cut into the 3
    burst_ticks(f1, -34, -28, 8, 14, (200, 240, 300, 340), 2)
    f2 = F()
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        pts = [(sx * (44 + (i % 2) * 7), sy * (10 + i * 7)) for i in range(5)]
        f2.stroke(pts, 2)
    spr(f2, E, 56, -52, 2)
    halo(f2)
    f3 = F()
    for x, y in ((-50, -36), (52, 30), (-46, 34)):
        sparkle(f3, x, y, 4, waist=.3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 300, 250], {}


def chevron(g, cx, cy, w, h, t=3, ch='#'):
    g.stroke([(cx - w, cy + h), (cx, cy), (cx + w, cy + h)], t, ch)


@effect('counter', 'experience', 'counter', 'experience counter gained',
        'Level up: a pillar of light behind the total, chevrons climbing both sides, a star.')
def level_up():
    f1 = F()
    for x, y in f1.cells():                    # a pillar of light with a round top, fading up
        if abs(x) <= 42 and y <= 46 and (y >= -38 or (x / 42) ** 2 + ((y + 38) / 22) ** 2 <= 1):
            f1.put(x, y, '1' if y > -34 else '2')
    for sx in (-1, 1):
        for i in range(2):
            chevron(f1, sx * 60, 30 - i * 14, 9, 7)
    f2 = F()
    for sx in (-1, 1):
        for i in range(3):
            chevron(f2, sx * 60, -2 - i * 14, 9, 7)
    spr(f2, trim(icon('experience')), 0, -46, 2)
    halo(f2)
    f3 = F()
    for x, y, r in ((-60, -50, 6), (60, -54, 8)):
        sparkle(f3, x, y, r, waist=.3)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 350, 250], {'1': ('D2', 'ink'), '2': ('D1', 'ink')}


@effect('counter', 'rad', 'counter', 'rad counter gained',
        'A glow ring pulses round the total, it glitches in scanlines, specks drift off.')
def rad_glow():
    f1 = F()
    ellipse(f1, 0, 0, 62, 46, '1', inner=(50, 36))
    spr(f1, trim(icon('rads')), -56, -44, 2)
    halo(f1, src=('#',))
    f2 = F()
    ellipse(f2, 0, 0, 72, 56, '2', inner=(62, 46))
    for x, y in f2.cells():
        if abs(x) <= 40 and -30 <= y <= 30:
            f2.put(x, y, '3')                  # the total glitches: HLINES paper
    spr(f2, trim(icon('rads')), -58, -50, 2)
    halo(f2, src=('#',))
    f3 = F()
    for x, y in ((-60, -12), (58, -36), (64, 20), (-30, -50)):
        dot(f3, x, y)
    return [f1, f2, f3], ('centre', 0, 0), [300, 300, 250], {'1': ('D4', 'ink'), '2': ('D2', 'ink'),
                                                           '3': ('HLINES', 'paper')}


CLOUD = ['.....####.......',
         '...##1111##.....',
         '..#11111111#....',
         '.#111111111##...',
         '.#1111111111###.',
         '#11111111111111#',
         '#11111111111111#',
         '.##############.']


@effect('counter', 'storm', 'counter', 'storm count up',
        'A storm cloud strikes the total once, then copies the strike three times over.')
def storm_copy():
    def bolt(g, x, y0, y1, w=3):
        pts = [(x, y0), (x - 6, (y0 + y1) / 2), (x + 3, (y0 + y1) / 2 + 2), (x - 4, y1)]
        g.stroke(pts, w, 'x')
    f1 = F()
    spr(f1, CLOUD, -36, -54, 3)
    bolt(f1, -34, -40, 24)
    halo(f1, src=('#',))
    f2 = F()
    spr(f2, CLOUD, -36, -54, 3)
    spr(f2, CLOUD, 40, -58, 2)
    for x in (-36, 4, 40):
        bolt(f2, x, -40 if x != 4 else -34, 26, 2)
    halo(f2, src=('#',))
    f3 = F()
    for x, y in ((-40, 36), (2, 40), (38, 36)):
        sparkle(f3, x, y, 4, waist=.3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 350, 250], {'1': ('D8', 'fill')}


# ================================================================ 4. state changes

CROWN = trim(icon('monarch'))


def crown_frames(drop_y, lines):
    """f1: the crown falls on the line it lands on (T55, the owner: frame 1 must line up with where
    it drops in frame 2); f2: landed on the total with a bounce and a glint; f3: twinkles."""
    f1 = F()
    spr(f1, CROWN, 0, drop_y, 2)
    for x, y0, y1 in lines:
        f1.stroke([(x, y0), (x, y1)], 2)
    halo(f1)
    f2 = F()
    spr(f2, CROWN, 0, -42, 2)
    burst_ticks(f2, 0, -32, 24, 32, (180, 200, 340, 360), 2)
    sparkle(f2, 34, -58, 6, waist=.3)
    halo(f2)
    f3 = F()
    sparkle(f3, -30, -56, 6, waist=.3)
    sparkle(f3, 30, -50, 4, waist=.3)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [250, 400, 250], {}


@effect('state', 'monarch', 'state', 'monarch claimed (full-tier tiles)',
        'A crown drops in on the line it lands on, lands on top of the total with a bounce and a glint.')
def crown_drop():
    # Full tiles: 20 px of fall; speed lines between the spikes up to the frame's top edge.
    return crown_frames(-62, [(-8, -82, -77), (8, -82, -77)])


@effect('state', 'monarch', 'state', 'monarch claimed (narrow tiles: the diamond bites the top)',
        'crown-drop for the narrow tier: a shorter fall that keeps clear of the centre diamond.')
def crown_drop_narrow():
    # Narrow middle seats: the diamond reaches y = -60 over the centre line, so the crown starts just
    # under it (5 px of fall) with its speed lines beside the diamond.
    return crown_frames(-47, [(-16, -72, -64), (16, -72, -64)])


@effect('state', 'monarch', 'state', 'monarch claimed (alt)',
        'A giant crown sticker is slapped over the total, then shrinks to sit on top of it.')
def crown_cut():
    f1 = F()
    spr(f1, CROWN, 0, -16, 4)                # a giant crown sticker slapped over the total
    halo(f1)
    halo(f1, src=('o',), ch='o')
    f2 = F()
    spr(f2, CROWN, 0, -44, 2)
    for x, y, r in ((-40, -48, 6), (40, -48, 6)):
        sparkle(f2, x, y, r, waist=.3)
    halo(f2)
    return [f1, f2], ('centre', 0, 0), [350, 350], {}


def torch_frames(g, cx, cy, r_glow, r_light, r_core, k, embers1, embers2):
    """The initiative flame at (cx, cy): f1 a small D4 glow; f2 a bigger flame in a D4 core inside a
    complete D1 firelight disc (T55: the owner asked for the circle to be completed); f3 embers.
    Tile layers are `ink`, so whatever lies under the light (the − glyph on a full tile) still reads."""
    f1 = g()
    ellipse(f1, cx - 4, cy - 4, r_glow, r_glow, '1')
    spr(f1, trim(icon('initiative')), cx - 4, cy - 4, 2)
    halo(f1, src=('#',))
    f2 = g()
    ellipse(f2, cx, cy, r_light, r_light, '2', inner=(r_core, r_core))
    ellipse(f2, cx, cy, r_core, r_core, '1')
    spr(f2, trim(icon('initiative')), cx, cy - 2, k)
    for x, y in embers1:
        dot(f2, x, y)
    halo(f2, src=('#',))
    f3 = g()
    for x, y in embers2:
        dot(f3, x, y)
    return [f1, f2, f3]


TORCH_W = 173      # full tier: x -98..74 about the total, so the disc (to x = -96) is whole
TORCH_OX = 12      # the frame centre sits 12 px left of the total's centre: `anchor centre -12 0`


@effect('state', 'initiative', 'state', 'initiative taken (full-tier tiles)',
        'A torch flares at the shoulder of the total; a whole disc of firelight, behind the - glyph; embers.')
def torch():
    g = lambda: OG(TORCH_W, FH, TORCH_OX)
    frames = torch_frames(g, -50, -24, 20, 46, 26, 3,
                          ((-30, -62), (-62, -66), (-44, -74)), ((-28, -68), (-58, -74), (-40, -60)))
    return frames, ('centre', -TORCH_OX, 0), [300, 350, 250], {'1': ('D4', 'ink'), '2': ('D1', 'ink')}


@effect('state', 'initiative', 'state', 'initiative taken (narrow tiles)',
        'torch for the narrow tier: a smaller whole disc of firelight that fits the 155-wide tile.')
def torch_narrow():
    frames = torch_frames(F, -45, -30, 14, 27, 17, 2,
                          ((-34, -62), (-58, -64), (-46, -70)), ((-32, -68), (-56, -72), (-44, -62)))
    return frames, ('centre', 0, 0), [300, 350, 250], {'1': ('D4', 'ink'), '2': ('D1', 'ink')}


def sky(g, sym):
    """A dome of night sky behind the total, its top kept clear of the diamond."""
    ellipse(g, 0, -4, 74, 56, sym)
    for x, y in g.cells():
        if y > 22:
            g.put(x, y, '.')


@effect('state', 'day-night', 'state', 'it becomes night (every seat at once)',
        'The sun sinks behind the total; a night sky with a moon and stars rises over it.')
def dusk():
    f1 = F()
    spr(f1, trim(icon('day')), 48, 14, 3)
    halo(f1)
    f2 = F()
    sky(f2, '1')
    spr(f2, trim(icon('night')), -44, -36, 2)
    for x, y, r in ((12, -48, 4), (46, -38, 5), (-14, -42, 3), (56, -14, 3), (-58, -2, 3)):
        sparkle(f2, x, y, r, waist=.3)
    halo(f2, src=('#',))
    f3 = F()
    spr(f3, trim(icon('night')), -44, -44, 2)
    sparkle(f3, 46, -46, 4, waist=.3)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 400, 250], {'1': ('D1', 'ink')}


@effect('state', 'day-night', 'state', 'it becomes day (every seat at once)',
        'The moon sets behind the total; the sun comes up with soft rays.')
def dawn():
    f1 = F()
    spr(f1, trim(icon('night')), -48, 16, 3)
    halo(f1)
    f2 = F()
    for i in range(8):                       # a soft corona: eight D4 rays round the sun
        a = math.radians(22.5 + i * 45)
        h = math.radians(9)
        f2.poly([(44 + math.cos(a) * 14, -44 + math.sin(a) * 14),
                 (44 + math.cos(a - h) * 34, -44 + math.sin(a - h) * 34),
                 (44 + math.cos(a + h) * 34, -44 + math.sin(a + h) * 34)], '1')
    ellipse(f2, 44, -44, 17, 17, '.')
    spr(f2, trim(icon('day')), 44, -44, 2)
    halo(f2, src=('#',))
    f3 = F()
    spr(f3, trim(icon('day')), 48, -54, 2)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 400, 250], {'1': ('D4', 'ink')}


@effect('state', 'citys-blessing', 'state', "city's blessing gained",
        'The castle pops up; bunting strings across the top of the total; confetti.')
def bunting():
    def string(g, sag, n_flags):
        pts = [(x, -58 + sag * (1 - (x / 70) ** 2)) for x in range(-70, 71, 2)]
        g.stroke(pts, 1)
        for i in range(n_flags):
            x = -63 + i * 126 / (n_flags - 1)
            y = -58 + sag * (1 - (x / 70) ** 2)
            tri = [(x - 6, y), (x + 6, y), (x, y + 12)]
            if i % 2:
                g.poly(tri, '#')
            else:
                t = F()
                t.poly(tri, '1')
                cells = set((a, b) for a, b in t.cells() if t.at(a, b) == '1')
                for a, b in cells:
                    edge = any((a + p, b + q) not in cells for p, q in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                    g.put(a, b, '#' if edge else '1')
    f1 = F()
    spr(f1, trim(icon('citys-blessing')), -56, -42, 2)
    halo(f1, src=('#',))
    f2 = F()
    string(f2, 16, 9)
    halo(f2, src=('#',))
    f3 = F()
    for x, y in ((-50, -64), (-18, -58), (28, -66), (56, -56)):
        box_fill(f3, x, y, x + 2, y + 2, '#')
    return [f1, f2, f3], ('centre', 0, 0), [300, 400, 250], {'1': ('CHECKER', 'fill')}


@effect('state', 'ring', 'state', 'the ring tempts you',
        'The ring drops onto the total; the total fades nearly invisible; a glint as it returns.')
def ring_tempt():
    R = trim(icon('ring'))
    f1 = F()
    spr(f1, R, 30, -54, 2)
    sparkle(f1, 52, -66, 5, waist=.3)
    halo(f1)
    f2 = F()
    for x, y in f2.cells():
        if abs(x) <= 40 and -30 <= y <= 30:
            f2.put(x, y, '1')                  # the total all but vanishes (D12 paper)
    dotted_arc(f2, 58, 42, 0, 359, 8)
    spr(f2, R, 0, 0, 3)
    halo(f2, src=('#',))
    f3 = F()
    sparkle(f3, 0, -2, 7, waist=.28)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 450, 250], {'1': ('D12', 'paper')}


# ================================================================ 5. table moments

@effect('table', 'turn', 'table', 'the turn passes to this seat',
        'Chevrons rush in from both sides at the total, then corner brackets lock on.')
def your_turn():
    f1 = F()
    for sx in (-1, 1):
        for i in range(3):
            x = sx * (70 - i * 10)
            f1.stroke([(x, -12), (x - sx * 8, 0), (x, 12)][::1] if False else
                      [(x + sx * 0, -12), (x - sx * 9, 0), (x, 12)], 3)
    f2 = F()
    for sx in (-1, 1):
        for sy in (-1, 1):
            x, y = sx * 50, sy * 40
            f2.stroke([(x, y - sy * 12), (x, y), (x - sx * 14, y)], 3)
    return [f1, f2], ('centre', 0, 0), [300, 350], {}


@effect('table', 'turn', 'table', 'the turn passes to this seat (alt)',
        'A spotlight swings onto the total from the corner, then leaves a lit pool under it.')
def spotlight():
    f1 = F()
    f1.poly([(-74, -82), (-60, -82), (46, 30), (-30, 44)], '1')
    ellipse(f1, 6, 38, 46, 9, '1')
    f2 = F()
    ellipse(f2, 0, 38, 50, 9, '1')
    for x, y, r in ((-58, -36, 6), (58, -30, 5)):
        sparkle(f2, x, y, r, waist=.3)
    halo(f2)
    return [f1, f2], ('centre', 0, 0), [350, 350], {'1': ('D4', 'ink')}


def flag(g, px, py, flip, wave):
    """Chequered flag: pole at (px, py) going down 50, cloth 30x20 to the side (flip -1 = left),
    5 px squares (a drawn motif, like confetti, not a shading), rippling by wave."""
    g.stroke([(px, py - 2), (px, py + 50)], 3)
    g.disc(px, py - 4, 2.5, '#')
    for i in range(30):
        off = wave * math.sin(i / 5)
        for j in range(21):
            x, y = px + flip * (i + 2), py + j + off
            edge = j in (0, 20) or i == 29
            g.put(x, y, '#' if edge or ((i // 5) + (j // 5)) % 2 == 0 else 'o')


@effect('table', 'first-player', 'table', 'first-player reveal: this seat starts',
        'A checkered start flag pops up and waves; speed lines: go!')
def start_flag():
    f1 = F()
    flag(f1, 34, -72, 1, 1.5)
    halo(f1, src=('#',))
    f2 = F()
    flag(f2, 66, -72, -1, -2.5)
    for y in (-66, -58, -50):
        f2.stroke([(16, y + 2), (30, y + 2)], 2)
    halo(f2, src=('#',))
    f3 = F()
    sparkle(f3, 46, -62, 6, waist=.3)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 350, 250], {}


@effect('damage', 'big-hit', 'huge', 'damage 20 or more in one readout (T55: was the natural 20)',
        'Critical hit: a big burst behind the total, then four fat twinkles at the corners.')
def crit():
    f1 = F()
    pts = []
    for i in range(24):
        a = math.radians(-82.5 + i * 15)
        r = 72 if i % 2 == 0 else 46
        pts.append((math.cos(a) * r * .98, math.sin(a) * r * .9))
    shape = region(lambda t: t.poly(pts, '#'))
    t = F()
    outline(t, lambda x, y: (x, y) in shape, ch='#', w=1, out=f1)
    for x, y in shape:
        if f1.at(x, y) == '.':
            f1.put(x, y, '1')
    halo(f1, src=('#',))
    f2 = F()
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        sparkle(f2, sx * 56, sy * 44, 11, waist=.28)
    halo(f2)
    f3 = F()
    for x, y in ((-60, -60), (62, -58), (-64, 50), (64, 52)):
        dot(f3, x, y)
    return [f1, f2, f3], ('centre', 0, 0), [350, 350, 250], {'1': ('D4', 'ink')}


@effect('table', 'dice', 'table', 'a natural 1 is rolled',
        'Fumble: a little grey cloud parks over the total and rains on it; a sweat drop.')
def fumble():
    def rain(g, phase):
        for x in range(-36, 37, 10):
            for y in range(-34 + (x // 10 % 2) * 6 + phase, 40, 14):
                g.stroke([(x, y), (x - 1, y + 5)], 2, 'x')
    f1 = F()
    spr(f1, CLOUD, -4, -50, 3)
    halo(f1, src=('#',))
    f2 = F()
    spr(f2, CLOUD, -4, -50, 3)
    rain(f2, 0)
    f2.poly([(58, -24), (54, -16), (58, -12), (62, -16)], '#')
    halo(f2, src=('#',))
    f3 = F()
    for x in (-30, -2, 26):
        ellipse(f3, x, 40, 6, 1.5, '#')
    return [f1, f2, f3], ('centre', 0, 0), [300, 400, 250], {'1': ('D8', 'fill')}


@effect('table', 'coin', 'table', 'coin flip reveal (before the result shows)',
        'The coin flips up beside the total: face, edge-on with spin arcs, face, glint.')
def coin_spin():
    C = trim(icon('coin'))
    f1 = F()
    spr(f1, C, 50, 10, 3)
    halo(f1)
    f2 = F()
    ellipse(f2, 50, -36, 4, 18, '#')
    ellipse(f2, 50, -36, 2, 16, '1')
    dotted_arc(f2, 18, 24, 120, 240, 6, cx=50, cy=-36)
    dotted_arc(f2, 18, 24, -60, 60, 6, cx=50, cy=-36)
    halo(f2, src=('#',))
    f3 = F()
    spr(f3, C, 50, -10, 3)
    sparkle(f3, 66, -36, 5, waist=.3)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 300, 350], {'1': ('D8', 'fill')}


@effect('table', 'revive', 'table', 'a knocked-out seat is revived',
        'A flatline runs through the total, kicks into a heartbeat, a heart pops at the end.')
def revive():
    f1 = F()
    f1.stroke([(-72, 0), (72, 0)], 1, 'x')
    f2 = F()
    ekg = [(-72, 0), (-30, 0), (-24, -6), (-18, 0), (-10, 0), (-4, 10), (4, -46), (12, 34),
           (18, 0), (30, 0), (38, -10), (46, 0), (72, 0)]
    f2.stroke(ekg, 3, 'x')
    f3 = F()
    heart(f3, 56, -30, 10)
    sparkle(f3, 38, -50, 5, waist=.3)
    halo(f3)
    return [f1, f2, f3], ('centre', 0, 0), [300, 350, 300], {}


# ---------------------------------------------------------------- write

def write(name, info):
    frames, anchor, holds, tiles = info['fn']()
    cleared = [keepout(g, diamond=name not in FULL_ONLY) if anchor[0] == 'centre' and g.h == FH else 0
               for g in frames]
    d = effect_dir(name)
    role = PROMOTED.get(name, info['role'])
    os.makedirs(d, exist_ok=True)
    for f in os.listdir(d):
        if f.startswith('frame-'):
            os.remove(os.path.join(d, f))
    frames = frames + [G(frames[0].w, frames[0].h)]   # the empty end frame (no offset needed)
    for i, g in enumerate(frames, 1):
        with open(os.path.join(d, f'frame-{i}.txt'), 'w') as f:
            f.write('\n'.join(g.rows()) + '\n')
    holds = list(holds) + [0]
    ref, dx, dy = anchor
    with open(os.path.join(d, 'meta.txt'), 'w') as f:
        f.write(f'# {name}: {info["intent"]}\n')
        f.write(f'# trigger: {info["trigger"]}\n')
        f.write(f'size {frames[0].w} {frames[0].h}\n')
        f.write(f'frames {len(frames)}\n')
        f.write(f'role {role}\n')
        f.write(f'anchor {ref} {dx} {dy}\n')
        for k, (t, m) in sorted(tiles.items()):
            f.write(f'tile {k} {t} {m}\n')
        f.write('hold_ms ' + ' '.join(map(str, holds)) + '\n')
    rows = [g.rows() for g in frames]
    cnt = lambda chs: [sum(sum(r.count(c) for c in chs) for r in fr) for fr in rows]
    out = {}
    for t in ('quad', 'narrow'):
        tier = render3.TIERS[t]
        out[t] = (render3.flips(d, tier), render3.furniture_hits(d, tier),
                  max(render3.sequence(d, tier, '108')[1]))
    with open(os.path.join(d, 'meta.txt'), 'a') as f:
        f.write('ink_px ' + ' '.join(map(str, cnt('#'))) + '\n')
        f.write('knockout_px ' + ' '.join(map(str, cnt('o'))) + '\n')
        if any(cnt('x')):
            f.write('cut_px ' + ' '.join(map(str, cnt('x'))) + '\n')
        if tiles:
            f.write('tile_px ' + ' '.join(map(str, cnt('123456789'))) + '\n')
        fq = out['quad'][0]
        f.write('flips ' + ' '.join(map(str, fq)) + f'   # total {sum(fq)}, over sticker "37" on the'
                ' quad tile\n')
        fn = out['narrow'][0]
        f.write(f'# flips on the narrow tile: {" ".join(map(str, fn))} (total {sum(fn)})\n')
        f.write(('# role: shipped (T55; app/.../fx/FxPolicy.kt plays it).\n' if name in PROMOTED else
                 '# role: proposed (a candidate: not shipped).\n') +
                '# anchor: frame centre = ref point + (dx, dy) in dither px, seat-upright coords.\n'
                '# symbols: # ink, o paper, x cut (invert), digits = tile layers (global phase).\n'
                '# hold_ms: last frame (empty) = 0: the clean redraw is the end state.\n')
    return out, cleared


def main():
    names = sys.argv[1:] or list(EFFECTS)
    for n in names:
        out, cleared = write(n, EFFECTS[n])
        q, nn = out['quad'], out['narrow']
        print(f'{n:14s} quad {q[0]} sum {sum(q[0])} furn {q[1]} | narrow {nn[0]} furn {nn[1]}'
              f' clip108 {nn[2]} | keepout-cleared {cleared}')


if __name__ == '__main__':
    main()
