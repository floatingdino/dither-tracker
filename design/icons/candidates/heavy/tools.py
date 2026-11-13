#!/usr/bin/env python3
"""T40 heavy-icon candidate tools (Python 3 stdlib only). Reads design/icons/ but never writes there.

Usage (from the repo root):
  python3 design/icons/candidates/heavy/tools.py validate [--report]
      validate.py's checks run on this folder (+ alts/), plus a contour-halo check: for a 1 px and a
      2 px paper halo on a dither fill, report dither left *enclosed* by the glyph (islands) and
      1-px dither slivers (a dither pixel pinched between halo/ink on both sides). Exit 1 on format errors.
  python3 design/icons/candidates/heavy/tools.py sheet
      before-after.png / before-after-inverted.png: each current icon next to its heavy candidate at
      8x, then both at 1x beside a mini2-bold numeral (the peek/drawer pairing) and at 4x (device size).
  python3 design/icons/candidates/heavy/tools.py mocks
      halo-mocks.png: close / menu / more on the 41x41 centre diamond (D6 fill and the gem's solid black
      centre) and on a D6 drawer tab, each with (a) today's square paper backing, (b) a 1 px contour
      halo, (c) a 2 px contour halo.  halo-sheet.png: every heavy icon on D6 with 1 px and 2 px halos.
  python3 design/icons/render.py --src design/icons/candidates/heavy --out design/icons/candidates/heavy --name contact-sheet
      plain contact sheet of the candidate set (render.py is reused read-only).
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.abspath(os.path.join(HERE, '..', '..'))
DESIGN = os.path.dirname(ICONS)
ALTS = os.path.join(HERE, 'alts')

_spec = importlib.util.spec_from_file_location('icon_render', os.path.join(ICONS, 'render.py'))
_render = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_render)
write_png = _render.write_png
FONT = dict(_render.FONT)
FONT.update({  # digits and a few marks for labels (3x5)
    '0': '####.##.##.####', '1': '.#.##..#..#.###', '2': '###..#####..###',
    '3': '###..####..####', '4': '#.##.####..#..#', '5': '####..###..####',
    '6': '####..####.####', '7': '###..#..#..#..#', '8': '####.#####.####',
    '9': '####.####..####', '(': '.#.#..#..#...#.', ')': '.#...#..#..#.#.',
    '+': '....#.###.#....', '.': '.............#.', '/': '..#..#.#.#..#..',
    ' ': '...............',
})
WHITE, BLACK, GREY, PAPER = 255, 0, 170, 230


# ---------------------------------------------------------------- loading

def read_icon(path):
    with open(path, newline='') as f:
        return f.read()


def rows_of(text):
    return text.split('\n')[:16]


def ink_set(rows):
    return {(x, y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == '#'}


def list_icons(folder):
    if not os.path.isdir(folder):
        return []
    return sorted(f[:-4] for f in os.listdir(folder) if f.endswith('.txt'))


def load(folder, name):
    return rows_of(read_icon(os.path.join(folder, name + '.txt')))


def tile(name):
    with open(os.path.join(DESIGN, 'patterns', name + '.txt')) as f:
        t = [l for l in f.read().split('\n') if l and not l.startswith('#') or (l and set(l) <= {'#', '.'})]
    t = [l for l in t if set(l) <= {'#', '.'}]
    return t


def mini_digit(ch):
    """Rows of a mini2/bold glyph (10x14)."""
    path = os.path.join(DESIGN, 'font', 'candidates', 'mini2', 'bold', 'glyphs.txt')
    out, cur = {}, None
    for line in open(path).read().split('\n'):
        if line.startswith('--- '):
            cur = line[4:]; out[cur] = []
        elif cur is not None and line and set(line) <= {'#', '.'}:
            out[cur].append(line)
    return out[ch]


# ---------------------------------------------------------------- halo maths

def disc(r):
    """Offsets of the halo brush: r=1 -> 3x3 square (8-neighbour), r=2 -> 5x5 minus corners."""
    if r == 1:
        return [(dx, dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)]
    return [(dx, dy) for dx in range(-r, r + 1) for dy in range(-r, r + 1)
            if dx * dx + dy * dy <= r * r + 1]


def dilate(ink, r):
    out = set()
    for x, y in ink:
        for dx, dy in disc(r):
            out.add((x + dx, y + dy))
    return out


def fill_holes(ink):
    """Ink plus every enclosed counter (paper not 4-connected to the cell border)."""
    outside, stack = set(), [(x, y) for x in range(16) for y in (0, 15)] + [(x, y) for y in range(16) for x in (0, 15)]
    while stack:
        p = stack.pop()
        if p in outside or p in ink or not (0 <= p[0] < 16 and 0 <= p[1] < 16):
            continue
        outside.add(p)
        x, y = p
        stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return {(x, y) for x in range(16) for y in range(16)} - outside


def halo_mask(ink, mode):
    """mode 1 / 2: literal halo (dilate the ink; counters wider than 2r show dither).
    'f1' / 'f2': silhouette halo (dilate ink + filled counters; counters stay paper)."""
    if isinstance(mode, str) and mode.startswith('f'):
        return dilate(fill_holes(ink), int(mode[1:]))
    return dilate(ink, mode)


def halo_problems(ink, r, pad=4):
    """Return (enclosed_islands, slivers) for glyph ink on dither with an r-px paper halo
    (r = 1, 2 literal or 'f1', 'f2' silhouette)."""
    lo, hi = -pad, 16 + pad
    covered = halo_mask(ink, r)
    dither = {(x, y) for x in range(lo, hi) for y in range(lo, hi) if (x, y) not in covered}
    # flood from the border
    seen, stack = set(), [(x, y) for (x, y) in dither if x in (lo, hi - 1) or y in (lo, hi - 1)]
    while stack:
        p = stack.pop()
        if p in seen or p not in dither:
            continue
        seen.add(p)
        x, y = p
        stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    islands = []
    rest = dither - seen
    while rest:
        comp, stack = set(), [next(iter(rest))]
        while stack:
            p = stack.pop()
            if p in comp or p not in rest:
                continue
            comp.add(p)
            x, y = p
            stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
        rest -= comp
        islands.append(len(comp))
    slivers = 0
    for (x, y) in dither:
        lr = (x - 1, y) not in dither and (x + 1, y) not in dither
        ud = (x, y - 1) not in dither and (x, y + 1) not in dither
        if lr or ud:
            slivers += 1
    return sorted(islands), slivers


# ---------------------------------------------------------------- validate

def validate(report):
    errors, seen = [], {}
    real = {n: '\n'.join(load(ICONS, n)) for n in list_icons(ICONS)}
    groups = [('', HERE), ('alts/', ALTS)]
    total = 0
    for prefix, folder in groups:
        for name in list_icons(folder):
            total += 1
            fn = prefix + name + '.txt'
            text = read_icon(os.path.join(folder, name + '.txt'))
            if not text.endswith('\n') or text.endswith('\n\n'):
                errors.append(f'{fn}: must end with exactly one trailing newline')
            rows = text.split('\n')[:-1] if text.endswith('\n') else text.split('\n')
            if len(rows) != 16:
                errors.append(f'{fn}: {len(rows)} lines, expected 16'); continue
            bad = False
            for y, row in enumerate(rows):
                if len(row) != 16:
                    errors.append(f'{fn}:{y + 1}: {len(row)} chars, expected 16'); bad = True
                if set(row) - {'#', '.'}:
                    errors.append(f'{fn}:{y + 1}: characters other than # and .'); bad = True
            if bad:
                continue
            ink = ink_set(rows)
            if not ink:
                errors.append(f'{fn}: blank icon'); continue
            edge = [(x, y) for x, y in ink if x in (0, 15) or y in (0, 15)]
            if edge:
                errors.append(f'{fn}: touches the 1px margin at {edge[:4]}')
            key = '\n'.join(rows)
            if key in seen:
                errors.append(f'{fn}: identical to {seen[key]}')
            for rn, rk in real.items():
                if rk == key and rn != name.split('-')[0]:
                    errors.append(f'{fn}: identical to the real {rn}.txt')
            seen[key] = fn
            if report:
                xs = [p[0] for p in ink]; ys = [p[1] for p in ink]
                dx = (min(xs) + max(xs)) / 2 - 7.5
                dy = (min(ys) + max(ys)) / 2 - 7.5
                w = max(xs) - min(xs) + 1; h = max(ys) - min(ys) + 1
                if abs(dx) > 0.5 or abs(dy) > 0.5:
                    errors.append(f'{fn}: off centre ({dx:+.1f}, {dy:+.1f})')
                h1 = halo_problems(ink, 1); h2 = halo_problems(ink, 2)
                f1 = halo_problems(ink, 'f1'); f2 = halo_problems(ink, 'f2')
                old = ''
                rn = name.split('-')[0] if prefix else name
                if rn in real:
                    oi = ink_set(real[rn].split('\n'))
                    old = f'(was {len(oi):3})'
                print(f'{prefix + name:20} bbox x{min(xs)}-{max(xs)} y{min(ys)}-{max(ys)} ({w}x{h})  '
                      f'ctr ({dx:+.1f},{dy:+.1f})  ink {len(ink):3} {old}  dens {len(ink) / (w * h):.2f}  '
                      f'counters on dither: r1 {h1[0]} r2 {h2[0]}  slivers (silhouette halo) r1 {f1[1]} r2 {f2[1]}')
    for e in errors:
        print('FAIL', e)
    print(f'{total} candidate icons checked, {len(errors)} problem(s)')
    return 1 if errors else 0


# ---------------------------------------------------------------- canvas

class Canvas:
    def __init__(self, w, h, bg=PAPER):
        self.w, self.h = w, h
        self.px = bytearray([bg] * (w * h))

    def put(self, x, y, v):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = v

    def block(self, x, y, s, v):
        for yy in range(s):
            for xx in range(s):
                self.put(x + xx, y + yy, v)

    def frame(self, x, y, w, h, v=GREY):
        for i in range(-1, w + 1):
            self.put(x + i, y - 1, v); self.put(x + i, y + h, v)
        for i in range(-1, h + 1):
            self.put(x - 1, y + i, v); self.put(x + w, y + i, v)

    def text(self, s, ox, oy, k=2, v=BLACK):
        for ci, ch in enumerate(s.lower()):
            g = FONT.get(ch)
            if not g:
                continue
            g = g.ljust(15, '.')
            for i, c in enumerate(g):
                if c == '#':
                    self.block(ox + ci * 4 * k + (i % 3) * k, oy + (i // 3) * k, k, v)

    def bitmap(self, rows, ox, oy, s, invert=False, frame=True):
        for y, line in enumerate(rows):
            for x, c in enumerate(line):
                on = (c == '#') != invert
                self.block(ox + x * s, oy + y * s, s, BLACK if on else WHITE)
        if frame:
            self.frame(ox, oy, len(rows[0]) * s, len(rows) * s)

    def save(self, path):
        write_png(path, self.w, self.h, self.px)
        print('wrote', os.path.relpath(path), f'{self.w}x{self.h}')


# ---------------------------------------------------------------- before/after sheet

def pairs():
    out = []
    for name in list_icons(HERE):
        cur = load(ICONS, name) if os.path.exists(os.path.join(ICONS, name + '.txt')) else None
        out.append((name, cur, load(HERE, name)))
    for name in list_icons(ALTS):
        base = name.rsplit('-', 1)[0]
        cur = load(ICONS, base) if os.path.exists(os.path.join(ICONS, base + '.txt')) else None
        out.append(('alt ' + name, cur, load(ALTS, name)))
    return out


def sheet():
    digit = mini_digit('2')
    ps = pairs()
    cols = 4
    cw, ch = 2 * 128 + 24 + 160 + 24, 128 + 8 + 14 + 16
    for inv, fname in ((False, 'before-after.png'), (True, 'before-after-inverted.png')):
        rows_n = (len(ps) + cols - 1) // cols
        c = Canvas(cols * cw + 16, rows_n * ch + 16)
        for i, (name, cur, new) in enumerate(ps):
            ox, oy = 16 + (i % cols) * cw, 16 + (i // cols) * ch
            if cur:
                c.bitmap(cur, ox, oy, 8, inv)
            c.bitmap(new, ox + 128 + 12, oy, 8, inv)
            # right column: 1x pairs with a mini numeral, then 4x (device px) heavy
            rx = ox + 2 * 128 + 24
            for j, ic in enumerate((cur, new)):
                if not ic:
                    continue
                y = oy + j * 22
                bg = BLACK if inv else WHITE
                for yy in range(-2, 18):
                    for xx in range(-2, 30):
                        c.put(rx + xx, y + yy, bg)
                c.bitmap(ic, rx, y, 1, inv, frame=False)
                for yy, line in enumerate(digit):
                    for xx, ch_ in enumerate(line):
                        c.put(rx + 17 + xx, y + 1 + yy, BLACK if (ch_ == '#') != inv else WHITE)
            # 1x labels
            c.text('was', rx + 34, oy + 4, 1)
            c.text('new', rx + 34, oy + 26, 1)
            c.bitmap(new, rx, oy + 52, 4, inv)
            c.text(name, ox, oy + 128 + 8, 2)
        c.save(os.path.join(HERE, fname))


# ---------------------------------------------------------------- mocks

def d6(x, y):
    t = tile('D6')
    return t[y % len(t)][x % len(t[0])] == '#'


def compose(field_w, field_h, inside, fill, glyph, gx, gy, mode, invert_glyph=False):
    """Return a field_w x field_h grid of 0/1 (1 = ink).
    inside(x,y) -> bool shape mask; fill(x,y) -> ink of the fill; mode: 'square', 1, 2 or 'none'."""
    ink = {(x + gx, y + gy) for (x, y) in ink_set(glyph)}
    if mode == 'square':
        paper = {(x, y) for x in range(gx, gx + 16) for y in range(gy, gy + 16)}
    elif mode == 'none':
        paper = set()
    else:
        paper = {(x + gx, y + gy) for (x, y) in halo_mask(ink_set(glyph), mode)}
    grid = []
    for y in range(field_h):
        row = []
        for x in range(field_w):
            if (x, y) in ink:
                v = 1
            elif (x, y) in paper:
                v = 0
            elif inside(x, y):
                v = 1 if fill(x, y) else 0
            else:
                v = 0
            row.append(v)
        grid.append(row)
    return grid


def blit_grid(c, grid, ox, oy, s):
    for y, row in enumerate(grid):
        for x, v in enumerate(row):
            c.block(ox + x * s, oy + y * s, s, BLACK if v else WHITE)
    c.frame(ox, oy, len(grid[0]) * s, len(grid) * s)


def mocks():
    D = 41
    diamond = lambda x, y: abs(x - 20) + abs(y - 20) <= 20
    gem_black = lambda x, y: abs(x - 20) + abs(y - 20) <= 16
    fill_d6 = lambda x, y: d6(x, y)
    fill_gem = lambda x, y: True if gem_black(x, y) else d6(x, y)
    def cand(name):
        p = os.path.join(HERE, name + '.txt')
        if os.path.exists(p):
            return load(HERE, name)
        return load(ALTS, name)
    glyphs = [('close now', load(ICONS, 'close'))]
    for n in ['close'] + [a for a in list_icons(ALTS) if a.startswith('close')]:
        glyphs.append(((n + ' new') if n == 'close' else n, cand(n)))
    glyphs += [('menu now', load(ICONS, 'menu')), ('menu new', cand('menu')),
               ('more now', load(ICONS, 'more')), ('more new', cand('more'))]
    modes = ['square', 'f1', 'f2']
    s = 5
    cell = D * s + 14
    head = 40
    c = Canvas(160 + 6 * cell + 30, head + len(glyphs) * cell + 30 + 2 * (20 * s + 14) + 60)
    for j, lab in enumerate(['d6 square', 'd6 halo 1', 'd6 halo 2', 'gem square', 'gem halo 1', 'gem halo 2']):
        c.text(lab, 160 + j * cell, 12, 2)
    for i, (lab, g) in enumerate(glyphs):
        y = head + i * cell
        c.text(lab, 8, y + D * s // 2 - 5, 2)
        for j in range(6):
            fill = fill_d6 if j < 3 else fill_gem
            grid = compose(D, D, diamond, fill, g, 12, 12, modes[j % 3])
            blit_grid(c, grid, 160 + j * cell, y, s)
    # drawer tab: 48x20 D6 strip with the dots
    y0 = head + len(glyphs) * cell + 30
    c.text('tab (d6) square / halo 1 / halo 2', 8, y0 - 22, 2)
    for i, (lab, g) in enumerate([('more now', load(ICONS, 'more')), ('more new', cand('more'))]):
        y = y0 + i * (20 * s + 14)
        c.text(lab, 8, y + 40, 2)
        for j, m in enumerate(modes):
            grid = compose(48, 20, lambda x, y: True, fill_d6, g, 16, 2, m)
            blit_grid(c, grid, 160 + j * (48 * s + 14), y, s)
    c.save(os.path.join(HERE, 'halo-mocks.png'))

    # halo sheet: every heavy icon on D6 at 1 / 2 px halo, plus the same at 1x
    names = list_icons(HERE) + ['alts/' + a for a in list_icons(ALTS)]
    s2 = 5
    F = 22
    cw = 4 * (F * s2 + 10) + 10
    chh = F * s2 + 34
    cols = 3
    rows_n = (len(names) + cols - 1) // cols
    c = Canvas(cols * cw + 10, rows_n * chh + 10)
    for i, n in enumerate(names):
        g = load(ALTS, n[5:]) if n.startswith('alts/') else load(HERE, n)
        ox, oy = 10 + (i % cols) * cw, 10 + (i // cols) * chh
        for j, m in enumerate(['none', 1, 'f1', 'f2']):
            grid = compose(F, F, lambda x, y: True, fill_d6, g, 3, 3, m)
            blit_grid(c, grid, ox + j * (F * s2 + 10), oy, s2)
        c.text(n.replace('alts/', 'alt ') + '  plain/lit1/sil1/sil2', ox, oy + F * s2 + 8, 2)
    c.save(os.path.join(HERE, 'halo-sheet.png'))


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'validate'
    if cmd == 'validate':
        sys.exit(validate('--report' in sys.argv))
    elif cmd == 'sheet':
        sheet()
    elif cmd == 'mocks':
        mocks()
    else:
        print(__doc__); sys.exit(2)
