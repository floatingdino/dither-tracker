#!/usr/bin/env python3
"""Dice / coin presentation sprites (T52): authors the 1-bit sprites the dice panel's roll
presentation is built from, and writes them as Kotlin string tables. Stdlib only.

    python3 design/tools/author.py            # writes the two Kotlin files below
    python3 design/tools/author.py --preview DIR   # also a 4x PNG of every sprite (on a checker
                                                   # of the real tiles, for eyeballing)

Outputs (generated, committed; do not edit by hand):
  app/src/main/java/au/com/josephmark/dithertracker/tools/DiceSprites.kt   the shipped set (variant A)
  app/src/test/java/au/com/josephmark/dithertracker/tools/DiceSpritesB.kt the variant not built (B),
                                                                            test-only: the sheet draws it
  app/src/test/java/au/com/josephmark/dithertracker/tools/CoinFacesT61.kt  T61's coin-face options
                                                                            (test-only: the contact sheet)

Coin faces (T61): the landed coin's heads / tails faces are 28 x 28 bitmaps (`#` ink, `.` not drawn)
in `design/tools/coin/heads.txt` / `tails.txt` (an ink disc, a paper ring, the emblem knocked out of
the ink field), shipped as `COIN_HEADS` / `COIN_TAILS`. Their 16 x 16 twins are the icons
`design/icons/coin-heads.txt` / `coin-tails.txt`. Every option drawn for T61 (a, b, c; both sizes)
is in `design/icons/candidates/t61/`.

Symbols (as the effects' frame files, design/fx/README.md):
  .  transparent      #  ink      o  paper (opaque knock-out)
  1  D4 (light dither)   2  D8 (checker)   3  D12 (dark dither)   v  VLINES (a coin's milled rim)
Tiles are sampled at the screen's global phase by the runtime (`DitherRenderer.fillMask`).

Solids are real 3-D models (an icosahedron, a cube, a 24-sided coin) turned to each pose and
projected orthographically; each visible facet is shaded by its normal against a light from the
upper left (paper, D4, D8, D12) and outlined in ink. No digits or pips appear on the tumbling
solids: the result must not be readable before it lands (SPEC rule 4 note, T52).
"""
import math
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
MAIN = os.path.join(ROOT, 'app/src/main/java/au/com/josephmark/dithertracker/tools/DiceSprites.kt')
TEST = os.path.join(ROOT, 'app/src/test/java/au/com/josephmark/dithertracker/tools/DiceSpritesB.kt')
T61 = os.path.join(ROOT, 'app/src/test/java/au/com/josephmark/dithertracker/tools/CoinFacesT61.kt')
COIN_DIR = os.path.join(HERE, 'coin')
T61_DIR = os.path.join(ROOT, 'design', 'icons', 'candidates', 't61')

PHI = (1 + 5 ** 0.5) / 2
LIGHT = (-0.40, 0.50, 0.77)  # x right, y up, z toward the viewer: upper left, mostly frontal


def norm(v):
    m = math.sqrt(sum(c * c for c in v))
    return tuple(c / m for c in v)


LIGHT = norm(LIGHT)


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def rot(v, ax, ay, az):
    x, y, z = v
    c, s = math.cos(ax), math.sin(ax)
    y, z = y * c - z * s, y * s + z * c
    c, s = math.cos(ay), math.sin(ay)
    x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(az), math.sin(az)
    x, y = x * c - y * s, x * s + y * c
    return (x, y, z)


# ---------- models: (vertices, faces as vertex-index lists, outward; face groups for edges) ----------

def icosahedron():
    vs = []
    for a in (-1, 1):
        for b in (-PHI, PHI):
            vs += [(0, a, b), (a, b, 0), (b, 0, a)]
    fs = []
    n = len(vs)
    for i in range(n):
        for j in range(i + 1, n):
            for k in range(j + 1, n):
                if all(abs(math.dist(vs[p], vs[q]) - 2) < 1e-6 for p, q in ((i, j), (j, k), (i, k))):
                    f = [i, j, k]
                    c = tuple(sum(vs[t][d] for t in f) / 3 for d in range(3))
                    nn = cross(sub(vs[j], vs[i]), sub(vs[k], vs[i]))
                    if dot(nn, c) < 0:
                        f = [i, k, j]
                    fs.append(f)
    assert len(fs) == 20
    return vs, fs, list(range(len(fs)))


def cube():
    vs = [(x, y, z) for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)]
    idx = {v: i for i, v in enumerate(vs)}
    fs = []
    for axis in range(3):
        for sgn in (-1, 1):
            quad = [v for v in vs if v[axis] == sgn]
            c = [0, 0, 0]
            c[axis] = sgn
            # order around the face centre
            u = (axis + 1) % 3
            w = (axis + 2) % 3
            quad.sort(key=lambda p: math.atan2(p[w], p[u]))
            f = [idx[p] for p in quad]
            nn = cross(sub(vs[f[1]], vs[f[0]]), sub(vs[f[2]], vs[f[0]]))
            if dot(nn, c) < 0:
                f.reverse()
            fs.append(f)
    return vs, fs, list(range(len(fs)))


def coin(sides=24, thick=0.16):
    vs = []
    for z in (thick, -thick):
        for i in range(sides):
            a = 2 * math.pi * i / sides
            vs.append((math.cos(a), math.sin(a), z))
    top = list(range(sides))
    bottom = list(range(2 * sides - 1, sides - 1, -1))
    fs = [top, bottom]
    groups = [0, 1]
    for i in range(sides):
        j = (i + 1) % sides
        fs.append([i, sides + i, sides + j, j])
        groups.append(2)  # the rim: one smooth surface, no edges between its strips
    # orient outward
    out = []
    for f in fs:
        c = tuple(sum(vs[t][d] for t in f) / len(f) for d in range(3))
        nn = cross(sub(vs[f[1]], vs[f[0]]), sub(vs[f[2]], vs[f[0]]))
        out.append(f if dot(nn, c) >= 0 else f[::-1])
    return vs, out, groups


# ---------- raster ----------

def shade_of(n, rim=False):
    l = dot(n, LIGHT)
    if rim:
        return 'v' if l > 0.0 else '2'
    if l > 0.78:
        return 'o'
    if l > 0.45:
        return '1'
    return '2'


def line(grid, x0, y0, x1, y1):
    """Bresenham, 8-connected, 1 px."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if 0 <= y0 < len(grid) and 0 <= x0 < len(grid[0]):
            grid[y0][x0] = '#'
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def render_solid(model, angles, height, rim_group=None):
    """Turns [model] by [angles], scales it to [height] px tall and rasterises it to symbol rows:
    every visible facet filled with its shade, then every visible edge (not between two strips of
    one smooth surface, like a coin's rim) as a 1-px Bresenham line in ink."""
    vs, fs, groups = model
    rv = [rot(v, *angles) for v in vs]
    vis = []
    for f, g in zip(fs, groups):
        a, b, c = rv[f[0]], rv[f[1]], rv[f[2]]
        nn = norm(cross(sub(b, a), sub(c, a)))
        if nn[2] > 1e-6:
            vis.append((f, g, nn))
    ys = [-v[1] for v in rv]
    xs = [v[0] for v in rv]
    span = max(ys) - min(ys)
    sc = (height - 1) / span
    w = int(math.floor((max(xs) - min(xs)) * sc + 0.5)) + 1
    h = height
    x0, y0 = min(xs), min(ys)

    def proj(v):
        return ((v[0] - x0) * sc, (-v[1] - y0) * sc)

    def inside(pts, x, y):
        sgn = 0
        for i in range(len(pts)):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % len(pts)]
            cr = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if abs(cr) < 1e-9:
                continue
            s = 1 if cr > 0 else -1
            if sgn == 0:
                sgn = s
            elif s != sgn:
                return False
        return True

    polys = [([proj(rv[t]) for t in f], g, nn) for f, g, nn in vis]
    grid = [['.'] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            for pts, g, nn in polys:
                if inside(pts, x, y):
                    grid[y][x] = shade_of(nn, rim=(g == rim_group))
                    break
    # edges: each visible facet's edges, unless both sides are the same smooth group
    owner = {}
    for f, g, nn in vis:
        for i in range(len(f)):
            e = tuple(sorted((f[i], f[(i + 1) % len(f)])))
            owner.setdefault(e, []).append(g)
    for e, gs in owner.items():
        if len(gs) == 2 and gs[0] == gs[1] and gs[0] == rim_group:
            continue
        ax, ay = proj(rv[e[0]])
        bx, by = proj(rv[e[1]])
        line(grid, int(round(ax)), int(round(ay)), int(round(bx)), int(round(by)))
    return trim([''.join(r) for r in grid])


def pad_rows(rows, p):
    if p <= 0:
        return rows
    w = len(rows[0])
    return ['.' * (w + 2 * p)] * p + ['.' * p + r + '.' * p for r in rows] + ['.' * (w + 2 * p)] * p


def trim(rows):
    while rows and set(rows[0]) == {'.'}:
        rows = rows[1:]
    while rows and set(rows[-1]) == {'.'}:
        rows = rows[:-1]
    left = min(len(r) - len(r.lstrip('.')) for r in rows)
    right = min(len(r) - len(r.rstrip('.')) for r in rows)
    return [r[left:len(r) - right] for r in rows]


def from_art(text):
    rows = [r for r in text.strip('\n').split('\n')]
    w = max(len(r) for r in rows)
    return [r.ljust(w, '.') for r in rows]


# ---------- hand-drawn bits ----------

# Speed ticks trailing a die (it travels right -> left, so they sit on its right).
TRAIL = from_art("""
..######
........
#######.
........
...#####
""")

# The bounce: two dust puffs either side of where the die touched down.
DUST = from_art("""
.11..........................11.
1111........................1111
.11.##....................##.11.
"""
)

# Impact ticks round a landed result: drawn to the right of the result box (it sits at the face's
# left edge). Anchored on the box's right edge, vertically centred.
IMPACT = from_art("""
.......#
......#.
.....#..
........
...#####
........
.....#..
......#.
.......#
""")

# A four-point twinkle (natural 20, heads' glint).
TWINKLE = from_art("""
....#....
....#....
...#1#...
...#1#...
####1####
...#1#...
...#1#...
....#....
....#....
""")

TWINKLE_S = from_art("""
...#...
...#...
..#1#..
###1###
..#1#..
...#...
...#...
""")

# Natural 1's flop: a little sweat drop and a sad puff.
SWEAT = from_art("""
...#...
..#o#..
..#o#..
.#ooo#.
#oo1oo#
#o111o#
.#111#.
..###..
""")

PUFF = from_art("""
.....1111.....
...11111111...
.111111111111.
11111111111111
.111111111111.
...1111..111..
""")

# Coin spin arcs, above and below the coin while it flips.
ARC_UP = from_art("""
...######...
.##......##.
#..........#
""")

ARC_DOWN = from_art("""
#..........#
.##......##.
...######...
""")

# Tails' thunk: two flat lines under the coin.
THUD = from_art("""
.########.
..........
###....###
""")


# ---------- variant B (not built: test-only, drawn on the sheet) ----------

# The dust-up: a cartoon fight cloud the die rattles inside (two poses), then it pops.
CLOUD_A = from_art("""
........1111.....111.......
.....11111111...11111......
...1111111111111111111.....
..11111111111111111111111..
.1111111111111111111111111.
11111111111111111111111111.
.1111111111111111111111111.
..111111111111111111111111.
...111111111111111111111...
.....1111..1111111..111....
""")

CLOUD_B = from_art("""
.......111.....1111........
....111111111.111111111....
..11111111111111111111111..
.1111111111111111111111111.
.11111111111111111111111111
11111111111111111111111111.
.1111111111111111111111111.
.111111111111111111111111..
...11111111111111111111....
....111...1111111....111...
""")

POP = from_art("""
#.........#.........#
.#........#........#.
..#...............#..
.....................
####.............####
.....................
..#...............#..
.#........#........#.
#.........#.........#
""")

QMARK = from_art("""
.###.
#ooo#
..oo#
.o##.
.#o..
.....
.#o..
""")


def cloud(w, h, circles):
    """A cartoon dust cloud: the union of [circles] (cx, cy, r) filled with D4, outlined in ink."""
    inside = [[any((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r for cx, cy, r in circles) for x in range(w)] for y in range(h)]
    rows = []
    for y in range(h):
        row = ''
        for x in range(w):
            if not inside[y][x]:
                row += '.'
            elif any(not (0 <= x + dx < w and 0 <= y + dy < h) or not inside[y + dy][x + dx]
                     for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                row += '#'
            else:
                row += '1'
        rows.append(row)
    return trim(rows)


def read_bits(path):
    with open(path) as f:
        rows = f.read().rstrip('\n').split('\n')
    assert rows and all(len(r) == len(rows[0]) and set(r) <= {'#', '.'} for r in rows), path
    return rows


def build():
    ico = icosahedron()
    cub = cube()
    cn = coin()
    shipped = {}
    # d20: three poses of a real icosahedron, 24 px tall at k = 1 (the result is 28).
    shipped['D20_A'] = render_solid(ico, (0.8, 1.1, 0.25), 24)
    shipped['D20_B'] = render_solid(ico, (1.25, 1.55, 0.9), 23)
    shipped['D20_C'] = render_solid(ico, (0.8, 1.1, -0.45), 24)
    # T59: the fast display's in-between poses (A, AB, B, BC, C), halfway between the turns either side.
    shipped['D20_AB'] = render_solid(ico, (1.02, 1.33, 0.58), 24)
    shipped['D20_BC'] = render_solid(ico, (1.02, 1.33, 0.22), 23)
    # d6: a cube tumbling corner over edge.
    shipped['D6_A'] = render_solid(cub, (0.55, 0.62, 0.20), 22)
    shipped['D6_B'] = render_solid(cub, (0.20, 0.95, 0.70), 23)
    shipped['D6_C'] = render_solid(cub, (-0.62, 0.40, -0.25), 22)
    shipped['D6_AB'] = render_solid(cub, (0.38, 0.78, 0.45), 23)
    shipped['D6_BC'] = render_solid(cub, (-0.21, 0.68, 0.22), 22)
    # coin: flipped about the horizontal axis — tilted (face), edge-on, tilted (the other face).
    shipped['COIN_A'] = render_solid(cn, (-1.05, 0.0, 0.05), 16, rim_group=2)
    shipped['COIN_B'] = render_solid(cn, (-1.52, 0.0, 0.0), 6, rim_group=2)
    shipped['COIN_C'] = render_solid(cn, (-(math.pi - 1.15), 0.0, -0.05), 14, rim_group=2)
    shipped['COIN_AB'] = render_solid(cn, (-1.30, 0.0, 0.03), 11, rim_group=2)
    shipped['COIN_BC'] = render_solid(cn, (-1.75, 0.0, -0.02), 10, rim_group=2)
    shipped['TRAIL'] = TRAIL
    shipped['DUST'] = DUST
    shipped['IMPACT'] = IMPACT
    shipped['TWINKLE'] = TWINKLE
    shipped['TWINKLE_S'] = TWINKLE_S
    shipped['SWEAT'] = SWEAT
    shipped['PUFF'] = PUFF
    shipped['ARC_UP'] = ARC_UP
    shipped['ARC_DOWN'] = ARC_DOWN
    shipped['THUD'] = THUD
    # T61: the landed coin's faces (28 x 28; not in the presentation's sprite table: `DiceArt` draws them).
    shipped['COIN_HEADS'] = read_bits(os.path.join(COIN_DIR, 'heads.txt'))
    shipped['COIN_TAILS'] = read_bits(os.path.join(COIN_DIR, 'tails.txt'))
    b = {
        'CLOUD_A': cloud(52, 31, [(12, 17, 9), (24, 11, 10), (37, 13, 9), (43, 21, 8), (28, 21, 10), (14, 23, 7)]),
        'CLOUD_B': cloud(52, 31, [(10, 16, 8), (21, 10, 9), (33, 9, 9), (44, 16, 8), (36, 22, 9), (20, 22, 9)]),
        'POP': POP, 'QMARK': QMARK,
        # the corner of the die poking out of the cloud, two ways
        'POKE_A': trim(render_solid(ico, (0.2, 0.6, 0.9), 14)),
        'POKE_B': trim(render_solid(ico, (0.9, -0.3, -0.4), 13)),
        'POKE6_A': trim(render_solid(cub, (0.5, 0.7, 0.3), 12)),
        'POKE6_B': trim(render_solid(cub, (-0.4, 0.5, -0.6), 12)),
        # coin B: spinning on the spot (face, edge, the other face), all in the result box
        'SPIN_A': render_solid(cn, (0.0, 1.0, 0.0), 26, rim_group=2),
        'SPIN_B': render_solid(cn, (0.0, 1.48, 0.0), 26, rim_group=2),
        'SPIN_C': render_solid(cn, (0.0, math.pi - 0.95, 0.0), 26, rim_group=2),
    }
    return shipped, b


def kotlin(name, sprites, pkg_doc, internal=True):
    out = []
    out.append('package au.com.josephmark.dithertracker.tools')
    out.append('')
    out.append('// GENERATED by design/tools/author.py (T52) — do not edit; re-run the script.')
    out.append('')
    out.append('/**')
    for line in pkg_doc:
        out.append(' * ' + line if line else ' *')
    out.append(' */')
    out.append(('internal ' if internal else '') + 'object ' + name + ' {')
    for k, rows in sprites.items():
        out.append('    /** %d×%d. */' % (len(rows[0]), len(rows)))
        out.append('    @JvmField val %s: Array<String> = arrayOf(' % k)
        for r in rows:
            out.append('        "%s",' % r)
        out.append('    )')
        out.append('')
    out[-1] = '}'
    return '\n'.join(out) + '\n'


def preview(path, sprites, S=4):
    sys.path.insert(0, os.path.join(ROOT, 'design', 'fx'))
    import render as r1
    bay = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5]

    def tile(c, x, y):
        if c == '1':
            return bay[(y % 4) * 4 + x % 4] < 4
        if c == '2':
            return (x + y) % 2 == 0
        if c == '3':
            return bay[(y % 4) * 4 + x % 4] < 12
        if c == 'v':
            return x % 2 == 0
        return c == '#'

    gap = 4
    W = sum(len(r[0]) + gap for r in sprites.values()) + gap
    H = max(len(r) for r in sprites.values()) + 2 * gap
    px = bytearray([255]) * (W * S * H * S)
    ox = gap
    for rows in sprites.values():
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                gx, gy = ox + x, gap + y
                ink = tile(c, gx, gy) if c != 'o' and c != '.' else False
                if ink:
                    for dy in range(S):
                        for dx in range(S):
                            px[(gy * S + dy) * W * S + gx * S + dx] = 0
        ox += len(rows[0]) + gap
    r1.write_png(path, W * S, H * S, px)


def main():
    shipped, b = build()
    doc_a = [
        'The dice panel\'s roll presentation sprites (T52), variant A "tumble across the face": the',
        'd20 / d6 / coin in three tumbling poses each, and the bits the frames are composed of (speed',
        'ticks, the bounce\'s dust, the landing\'s impact ticks, twinkles, natural 1\'s sweat and puff,',
        'coin arcs and thud). One row per string: `.` transparent, `#` ink, `o` paper, `1` D4, `2` D8,',
        '`3` D12, `v` VLINES (tiles at global phase). Drawn by [DiceArt] / [DicePainter]; the frame',
        'scripts are [Presentation]. Source and previews: `design/tools/author.py`, `design/tools/README.md`.',
    ]
    doc_b = [
        'Variant B of the roll presentation (T52, not built): "dust-up" — the die rattles inside a',
        'cartoon fight cloud on the result, then the cloud pops; the coin spins on the spot. Test-only:',
        '`DiceSheetTest` draws it beside variant A for `design/captures/t52-*-presentation.png`.',
    ]
    with open(MAIN, 'w') as f:
        f.write(kotlin('DiceSprites', shipped, doc_a))
    with open(TEST, 'w') as f:
        f.write(kotlin('DiceSpritesB', b, doc_b))
    t61 = {}
    for opt in 'abc':
        for side in ('heads', 'tails'):
            t61['%s_%s_COIN' % (side.upper(), opt.upper())] = read_bits(os.path.join(T61_DIR, '%s-%s-coin.txt' % (side, opt)))
            t61['%s_%s_ICON' % (side.upper(), opt.upper())] = read_bits(os.path.join(T61_DIR, '%s-%s.txt' % (side, opt)))
    doc_t61 = [
        'T61 coin-face options (test-only): a imp face / imp tail (shipped), b skull / shooting star,',
        'c fish head / fish tail. `*_COIN` is the 28 x 28 landed coin, `*_ICON` the 16 x 16 icon.',
        '`CoinFacesSheetTest` draws them on the real panel for `design/captures/t61-coin-faces.png`.',
    ]
    with open(T61, 'w') as f:
        f.write(kotlin('CoinFacesT61', t61, doc_t61))
    if len(sys.argv) > 2 and sys.argv[1] == '--preview':
        os.makedirs(sys.argv[2], exist_ok=True)
        preview(os.path.join(sys.argv[2], 'sprites-a.png'), shipped)
        preview(os.path.join(sys.argv[2], 'sprites-b.png'), b)
    for k, rows in shipped.items():
        print(k, len(rows[0]), 'x', len(rows))
        print('\n'.join(rows))


if __name__ == '__main__':
    main()
