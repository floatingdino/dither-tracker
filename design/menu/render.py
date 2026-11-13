#!/usr/bin/env python3
"""T36 centre-menu concepts: true-pixel mocks of the Play screen with the table menu open.

Stdlib only (PNG via zlib/struct). Everything is drawn on the 468x351 dither grid of the Nomad and
written at S = 4 (1872x1404 device px), pure black and white. Icons come from design/icons/*.txt,
text from design/font/{ui,num-m,num-l,num-l-pm}/glyphs.txt, fills from design/patterns/*.txt at
global phase (SPEC rule 3). The play screen mirrors app/.../play/PlayLayout.kt (T27 sticker
numerals, ± glyphs, 89x21 tabs, commander button, R24/cut-4 diamond with three bars).

Usage:
  python3 design/menu/render.py              # every concept + the second-level mocks
  python3 design/menu/render.py gem tent     # only these concepts
  python3 design/menu/render.py --review DIR # also write half-size previews to DIR

Outputs (next to this script):
  closed.png                         the play screen, menu closed (reference)
  concept-<n>-<name>-open.png        full screen, menu open (1872x1404)
  concept-<n>-<name>-detail.png      the changed region (plus margin) at 2x device px (8 per dither px)
  concept-<n>-<name>-key<k>.png      intermediate keyframes, when a concept uses them
  concept-<n>-<name>-{confirm,dice,settings}.png   second level (recommended concept)
Stats (flips = dither px that change from the closed screen, footprint = bbox of those) are printed.
"""
import os
import struct
import sys
import zlib

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
S = 4
SW, SH = 468, 351
PW, PH = 233, 175                 # panel; dividers at x = 233, y = 175
CX, CY = PW, PH                   # the crossing = diamond centre
TARGET = 27                       # 9 mm fingertip minimum in dither px at S = 4


# ------------------------------------------------------------------ assets

def load_face(name):
    path = os.path.join(ROOT, 'design', 'font', name, 'glyphs.txt')
    f = {'glyphs': {}, 'tracking': 1, 'cap': None}
    cur, rows = None, []
    for line in open(path).read().split('\n'):
        if line.startswith('--- '):
            if cur is not None:
                f['glyphs'][cur] = rows
            key = line[4:].split()[0]
            cur = chr(int(key[2:], 16)) if key.startswith('U+') else key
            rows = []
        elif cur is None:
            p = line.split()
            if not p or line.startswith('#'):
                continue
            if p[0] == 'size':
                f['w'], f['h'] = int(p[1]), int(p[2])
            elif p[0] == 'tracking':
                f['tracking'] = int(p[1])
            elif p[0] == 'cap':
                f['cap'] = (int(p[1]), int(p[2]))
        elif line.strip():
            rows.append(line.strip())
    if cur is not None:
        f['glyphs'][cur] = rows
    if f['cap'] is None:
        f['cap'] = (0, f['h'])
    return f


UI = load_face('ui')
NUM_M = load_face('num-m')
NUM_L = load_face('num-l')
NUM_PM = load_face('num-l-pm')


def glyph(face, ch):
    g = face['glyphs']
    if ch == '−':
        ch = '-'
    return g.get(ch) or g.get(ch.upper()) or g['notdef']


def icon(name, _c={}):
    if name not in _c:
        _c[name] = [r for r in open(os.path.join(ROOT, 'design', 'icons', name + '.txt')).read().split('\n') if r]
    return _c[name]


def pattern(name, _c={}):
    if name not in _c:
        _c[name] = [r for r in open(os.path.join(ROOT, 'design', 'patterns', name + '.txt')).read().split('\n') if r]
    return _c[name]


# Sprite / screen symbols:
#   '.' transparent   '#' ink   'o' paper
#   ':' shadow mask (Role.SHADOW = CHECKER)   'm' Role.MENU (D6)   's' Role.DRAWER (SPARSE)
#   'c' CHECKER   'd' D4 (light)   'k' D10 (dark)   -- all sampled at GLOBAL screen phase when blitted
FILLS = {':': 'CHECKER', 'c': 'CHECKER', 'm': 'D6', 's': 'SPARSE', 'd': 'D4', 'k': 'D10', 'x': 'DIAG'}


def fill_ink(ch, sx, sy):
    t = pattern(FILLS[ch])
    return 1 if t[sy % len(t)][sx % len(t[0])] == '#' else 0


def text_rows(face, s, tracking=None, k=1):
    t = face['tracking'] if tracking is None else tracking
    n = len(s)
    w = n * face['w'] + (n - 1) * t if n else 0
    rows = [['.'] * w for _ in range(face['h'])]
    for i, ch in enumerate(s):
        g = glyph(face, ch)
        for y, line in enumerate(g):
            for x, v in enumerate(line):
                if v != '.':
                    rows[y][i * (face['w'] + t) + x] = v
    rows = [''.join(r) for r in rows]
    if k > 1:
        rows = [''.join(c * k for c in r) for r in rows for _ in range(k)]
    return rows


def text_w(face, s, tracking=None):
    t = face['tracking'] if tracking is None else tracking
    return len(s) * face['w'] + (len(s) - 1) * t if s else 0


# ------------------------------------------------------------------ sprites (upright, reader's frame)

class Sprite:
    def __init__(self, w, h, ch='.'):
        self.w, self.h = w, h
        self.p = [[ch] * w for _ in range(h)]

    def rect(self, x, y, w, h, ch):
        for yy in range(max(0, y), min(self.h, y + h)):
            for xx in range(max(0, x), min(self.w, x + w)):
                self.p[yy][xx] = ch

    def frame(self, x, y, w, h, ch='#', t=1):
        self.rect(x, y, w, t, ch)
        self.rect(x, y + h - t, w, t, ch)
        self.rect(x, y, t, h, ch)
        self.rect(x + w - t, y, t, h, ch)

    def put(self, rows, x, y, opaque=False, ink='#', paper='o'):
        for yy, line in enumerate(rows):
            for xx, v in enumerate(line):
                X, Y = x + xx, y + yy
                if not (0 <= X < self.w and 0 <= Y < self.h):
                    continue
                if v == '#':
                    self.p[Y][X] = ink
                elif v == '.':
                    if opaque:
                        self.p[Y][X] = paper
                elif v in 'o':
                    self.p[Y][X] = paper
                else:
                    self.p[Y][X] = v

    def text(self, face, s, x, y, ink='#', k=1, opaque=False, tracking=None):
        self.put(text_rows(face, s, tracking, k), x, y, opaque=opaque, ink=ink)

    def ctext(self, face, s, cx, y, ink='#', k=1, tracking=None):
        """Text centred on column cx (cap band top at y)."""
        w = text_w(face, s, tracking) * k
        self.text(face, s, cx - w // 2, y, ink, k, tracking=tracking)

    def icon(self, name, x, y, ink='#', opaque=False):
        self.put(icon(name), x, y, opaque=opaque, ink=ink)

    def invert(self, x, y, w, h):
        sw = {'#': 'o', 'o': '#', '.': '#'}
        for yy in range(max(0, y), min(self.h, y + h)):
            for xx in range(max(0, x), min(self.w, x + w)):
                v = self.p[yy][xx]
                self.p[yy][xx] = sw.get(v, v)

    def diamond(self, cx, cy, r, ch, cut=0, inset=0):
        """Stepped diamond |dx|+|dy| <= r - inset (tips cut flat to r - cut)."""
        rr = r - inset
        half = rr - cut if cut else rr
        for dy in range(-half, half + 1):
            hw = min(rr - abs(dy), half)
            for dx in range(-hw, hw + 1):
                X, Y = cx + dx, cy + dy
                if 0 <= X < self.w and 0 <= Y < self.h:
                    self.p[Y][X] = ch


def rot_xy(facing, x, y, w, h):
    """Upright sprite px (x, y) of a w x h sprite -> offset in its rotated box, for a reader at
    edge `facing` (S upright, N 180, E reader at the right edge, W reader at the left edge)."""
    if facing == 'S':
        return x, y
    if facing == 'N':
        return w - 1 - x, h - 1 - y
    if facing == 'E':
        return y, w - 1 - x
    return h - 1 - y, x


def rot_size(facing, w, h):
    return (w, h) if facing in 'NS' else (h, w)


# ------------------------------------------------------------------ screen

class Screen:
    def __init__(self):
        self.p = [bytearray(SW) for _ in range(SH)]

    def copy(self):
        s = Screen.__new__(Screen)
        s.p = [bytearray(r) for r in self.p]
        return s

    def set(self, x, y, ch):
        if not (0 <= x < SW and 0 <= y < SH) or ch == '.':
            return
        if ch == '#':
            self.p[y][x] = 1
        elif ch == 'o':
            self.p[y][x] = 0
        elif ch == '~':                       # invert
            self.p[y][x] ^= 1
        else:
            self.p[y][x] = fill_ink(ch, x, y)

    def blit(self, sp, x, y, facing='S'):
        """Blit a Sprite (or rows) with its rotated box's top-left at screen (x, y)."""
        rows = sp.p if isinstance(sp, Sprite) else sp
        h, w = len(rows), len(rows[0])
        for yy in range(h):
            r = rows[yy]
            for xx in range(w):
                v = r[xx]
                if v != '.':
                    ox, oy = rot_xy(facing, xx, yy, w, h)
                    self.set(x + ox, y + oy, v)

    def blit_c(self, sp, cx, cy, facing='S'):
        """Blit centred on (cx, cy): the sprite's own centre pixel (w//2, h//2) lands there."""
        rows = sp.p if isinstance(sp, Sprite) else sp
        h, w = len(rows), len(rows[0])
        ox, oy = rot_xy(facing, w // 2, h // 2, w, h)
        self.blit(sp, cx - ox, cy - oy, facing)

    def rect(self, x, y, w, h, ch):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, ch)

    def frame(self, x, y, w, h, ch='#'):
        self.rect(x, y, w, 1, ch)
        self.rect(x, y + h - 1, w, 1, ch)
        self.rect(x, y, 1, h, ch)
        self.rect(x + w - 1, y, 1, h, ch)

    def diamond(self, cx, cy, r, ch, cut=0):
        half = r - cut if cut else r
        for dy in range(-half, half + 1):
            hw = min(r - abs(dy), half)
            for dx in range(-hw, hw + 1):
                self.set(cx + dx, cy + dy, ch)

    def diamond_ring(self, cx, cy, r, ch='#'):
        """1-px 4-connected staircase outline of |dx|+|dy| <= r (pixel-perfect 45-degree edge)."""
        for dy in range(-r, r + 1):
            for hw in {r - abs(dy), max(0, r - abs(dy) - 1)}:
                for dx in (-hw, hw):
                    self.set(cx + dx, cy + dy, ch)

    def save(self, path, scale=S, crop=None):
        x0, y0, x1, y1 = crop or (0, 0, SW, SH)
        W, H = (x1 - x0) * scale, (y1 - y0) * scale
        raw = bytearray()
        for y in range(y0, y1):
            row = self.p[y]
            line = b'\x00' + b''.join((b'\x00' if row[x] else b'\xff') * scale for x in range(x0, x1))
            raw += line * scale
        write_png(path, W, H, bytes(raw))


def write_png(path, w, h, raw):
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)


# ------------------------------------------------------------------ play screen (mirror of PlayLayout)

SEATS = [  # (panel x, panel y, rotated 180)  seat order as PlayLayout: TL, TR, BR, BL
    (0, 0, True), (PW + 1, 0, True), (PW + 1, PH + 1, False), (0, PH + 1, False)]
LIVES = ['34', '40', '27', '19']
NAMES = ['Ash', 'Bo', 'Cy', 'Dee']
TAB_W, TAB_H, DOT, DOT_GAP, DOT_HALO = 89, 21, 3, 3, 2
DIAMOND_R, DIAMOND_CUT = 24, 4
BAR_W, BAR_H, BAR_PITCH, BAR_HALO = 3, 15, 6, 2
SLOT_W = 4 * 32 + 3 * 4                         # number slot: 4 glyphs of num-l
SIDE = (PW - SLOT_W) // 2                       # 46
SLOT_Y = (PH - 56) // 2                         # 59
PM_X = (SIDE - 20) // 2                         # 13


def seat_sprite(seat, life):
    """One upright panel's furniture and life total (paper = transparent)."""
    sp = Sprite(PW, PH)
    # life total, centred in the slot
    w = text_w(NUM_L, life)
    cap_top, cap_h = NUM_L['cap']
    sp.put(text_rows(NUM_L, life), SIDE + (SLOT_W - w) // 2, SLOT_Y + (56 - cap_h) // 2 - cap_top)
    # +/- : cap band centred on the number's cap band
    pm_y = SLOT_Y + NUM_L['cap'][0] + (NUM_L['cap'][1] - NUM_PM['cap'][1]) // 2 - NUM_PM['cap'][0]
    sp.put(glyph(NUM_PM, '-'), PM_X, pm_y)
    sp.put(glyph(NUM_PM, '+'), PW - PM_X - 20, pm_y)
    # drawer tab (MENU/TAB = D6) with three dots on one paper backing
    tx, ty = (PW - TAB_W) // 2, PH - TAB_H
    sp.rect(tx, ty, TAB_W, TAB_H, 'm')
    dw = 3 * DOT + 2 * DOT_GAP
    dx, dy = tx + (TAB_W - dw) // 2, ty + (TAB_H - DOT) // 2
    sp.rect(dx - DOT_HALO, dy - DOT_HALO, dw + 2 * DOT_HALO, DOT + 2 * DOT_HALO, 'o')
    for i in range(3):
        sp.rect(dx + i * (DOT + DOT_GAP), dy, DOT, DOT, '#')
    # commander button (near-right corner): 1-px frame, paper, icon
    bx, by = PW - 4 - 22, PH - 4 - 22
    sp.rect(bx, by, 22, 22, 'o')
    sp.frame(bx, by, 22, 22)
    sp.icon('commander', bx + 3, by + 3)
    return sp


def draw_diamond_button(sc, glyph_name=None, fill='m', cut=DIAMOND_CUT):
    sc.diamond(CX, CY, DIAMOND_R, fill, cut)
    if glyph_name is None:   # three bars on one paper backing
        top = CY - BAR_H // 2
        x0 = CX - BAR_PITCH - BAR_W // 2
        sc.rect(x0 - BAR_HALO, top - BAR_HALO, 2 * BAR_PITCH + BAR_W + 2 * BAR_HALO, BAR_H + 2 * BAR_HALO, 'o')
        for i in range(3):
            sc.rect(x0 + i * BAR_PITCH, top, BAR_W, BAR_H, '#')


def play_screen(lives=LIVES):
    sc = Screen()
    for (x0, y0, rot), life in zip(SEATS, lives):
        sc.blit(seat_sprite(None, life), x0, y0, 'N' if rot else 'S')
    for y in range(SH):
        if (PW + y) % 2 == 0:
            sc.set(PW, y, '#')
    for x in range(SW - 1):
        if (x + PH) % 2 == 0:
            sc.set(x, PH, '#')
    draw_diamond_button(sc)
    return sc


def restore_dividers(sc, x0, y0, x1, y1):
    """Redraw divider dots inside a box (after clearing it)."""
    for y in range(y0, y1):
        if (PW + y) % 2 == 0 and x0 <= PW < x1:
            sc.set(PW, y, '#')
    for x in range(x0, x1):
        if (x + PH) % 2 == 0 and y0 <= PH < y1:
            sc.set(x, PH, '#')


# ------------------------------------------------------------------ shared widgets

def close_diamond(sc, r=DIAMOND_R, cut=DIAMOND_CUT):
    """Open state of the menu button: the same diamond, inverted to solid ink, with the close glyph
    knocked out in paper (pressed/selected = inversion) -- unmistakably 'open'."""
    sc.diamond(CX, CY, r, '#', cut)
    sc.blit_c([row.replace('#', 'o') for row in icon('close')], CX, CY)


def icon_tile(w, h, name, label, inv=False, frame=True, fill='o', gap=3, k=1):
    """Upright tile: optional 1-px frame, fill, icon over a ui caps label, both centred."""
    sp = Sprite(w, h, fill)
    if frame:
        sp.frame(0, 0, w, h)
    ink = '#'
    lw = text_w(UI, label) * k if label else 0
    ch = 16 + (gap + 7 * k if label else 0) - (2 if label else 0)   # icon art has a 1-px margin
    y = (h - ch) // 2 - (1 if label else 0)
    sp.icon(name, (w - 16) // 2, y)
    if label:
        sp.text(UI, label, (w - lw) // 2 + (w - lw) % 2 * 0, y + 15 + gap - 1, k=k)
    if inv:
        sp.invert(1, 1, w - 2, h - 2)
    return sp


# ------------------------------------------------------------------ stats & output

def diff_stats(a, b):
    n, x0, y0, x1, y1 = 0, SW, SH, -1, -1
    for y in range(SH):
        ra, rb = a.p[y], b.p[y]
        if ra == rb:
            continue
        for x in range(SW):
            if ra[x] != rb[x]:
                n += 1
                x0, y0, x1, y1 = min(x0, x), min(y0, y), max(x1, x), max(y1, y)
    return n, (x0, y0, x1 + 1, y1 + 1)


def life_hidden(open_sc):
    """Dither px of life-total ink (any seat) that the open state changes."""
    lives = Screen()
    for (x0, y0, rot), life in zip(SEATS, LIVES):
        sp = Sprite(PW, PH)
        w = text_w(NUM_L, life)
        sp.put(text_rows(NUM_L, life), SIDE + (SLOT_W - w) // 2, SLOT_Y + (56 - NUM_L['cap'][1]) // 2)
        lives.blit(sp, x0, y0, 'N' if rot else 'S')
    base = play_screen()
    n = 0
    for y in range(SH):
        for x in range(SW):
            if lives.p[y][x] and open_sc.p[y][x] != base.p[y][x]:
                n += 1
    return n


def emit(name, sc, base, review=None, note=''):
    path = os.path.join(HERE, name + '.png')
    sc.save(path)
    n, bb = diff_stats(base, sc)
    print(f'{name:34s} flips {n:5d} dpx ({n * 16:6d} device px)  bbox {bb[2] - bb[0]}x{bb[3] - bb[1]} '
          f'at {bb[:2]}  life-ink hidden {life_hidden(sc)} {note}')
    if review:
        sc.save(os.path.join(review, name + '.png'), scale=2)
    return n, bb


def detail(name, sc, bb, margin=10):
    x0, y0, x1, y1 = bb
    crop = (max(0, x0 - margin), max(0, y0 - margin), min(SW, x1 + margin), min(SH, y1 + margin))
    sc.save(os.path.join(HERE, name + '-detail.png'), scale=2 * S, crop=crop)


CONCEPTS = {}


def concept(n, name):
    def deco(fn):
        CONCEPTS[name] = (n, fn)
        return fn
    return deco


# CONCEPTS-BEGIN
ITEMS = {  # first level: icon, short label (<= 5 chars), long label (<= 8 chars), action
    'new': ('new-game', 'NEW', 'NEW GAME', 'restart -> confirm'),
    'undo': ('undo', 'UNDO', 'UNDO', 'undo last change, menu stays open'),
    'redo': ('redo', 'REDO', 'REDO', 'redo, menu stays open'),
    'dice': ('dice', 'DICE', 'DICE', 'd6 / d20 / coin -> result'),
    'first': ('turn', 'FIRST', 'FIRST', 'random starting player -> result'),
    'log': ('history', 'LOG', 'HISTORY', 'history + turn/timer -> panel'),
    'setup': ('settings', 'SETUP', 'SETTINGS', 'settings -> panel'),
    'clean': ('refresh', 'CLEAN', 'REFRESH', 'full e-ink refresh, closes menu'),
}
T = 35          # square tile, odd so it centres on a pixel column


def knock(rows):
    """Icon rows with ink turned to paper (for knock-outs on a black ground)."""
    return [r.replace('#', 'o') for r in rows]


# ---------------------------------------------------------------- 1 gem
GEM_R = 28
GEM_RIM = 3
# facet centre (in units of r), item. 180-degree symmetric in *kind*: undo|redo, dice|first,
# log|setup, clean|new sit opposite each other, so each long side reads one of every pair upright.
GEM_FACETS = [((0, -2), 'dice'), ((1, -1), 'clean'), ((2, 0), 'redo'), ((1, 1), 'setup'),
              ((0, 2), 'first'), ((-1, 1), 'new'), ((-2, 0), 'undo'), ((-1, -1), 'log')]


def gem_facing(ox, oy, seats='NS'):
    """Occupied-edge rule: a facet faces the nearest edge that has a seat. With seats only on the
    long edges (the 2x2 table), the upper half faces N, the lower half S, and the two tips on the
    horizontal divider split one each (W -> N, E -> S) so the menu is 180-degree symmetric."""
    if seats == 'NSEW':
        if abs(ox) > abs(oy):
            return 'E' if ox > 0 else 'W'
        if abs(ox) == abs(oy):          # diagonals: pinwheel clockwise
            return {(1, -1): 'E', (1, 1): 'S', (-1, 1): 'W', (-1, -1): 'N'}[(ox, oy)]
        return 'S' if oy > 0 else 'N'
    if oy == 0:
        return 'N' if ox < 0 else 'S'
    return 'S' if oy > 0 else 'N'


def petal_sprite(name, label, r=GEM_R, inset=2, inv=False):
    sz = 2 * r + 1
    sp = Sprite(sz, sz, '.')
    sp.diamond(r, r, r, '#' if inv else 'o', inset=inset)
    ink = 'o' if inv else '#'
    sp.put(icon(name), r - 8, r - 16, ink=ink)
    if label:
        w = text_w(UI, label)
        sp.text(UI, label, r - w // 2, r + 2, ink=ink)
    return sp


def gem_frame(sc, R, r=GEM_R):
    """Big stepped diamond: 1-px staircase outline, MENU-dither rim, MENU gutters between facets."""
    sc.diamond(CX, CY, R + GEM_RIM, 'm')
    sc.diamond_ring(CX, CY, R + GEM_RIM)


def crop_icon(name):
    """Icon rows trimmed to the ink bbox."""
    rows = icon(name)
    ys = [y for y, r in enumerate(rows) if '#' in r]
    xs = [x for r in rows for x, v in enumerate(r) if v == '#']
    return [r[min(xs):max(xs) + 1] for r in rows[min(ys):max(ys) + 1]]


def tent_facet(name, r=GEM_R, inset=2, inv=False):
    """A tip facet on the horizontal divider: icon-only, drawn twice -- upright in the lower half
    for the near side, 180 degrees in the upper half for the far side; the divider is its fold."""
    sz = 2 * r + 1
    sp = Sprite(sz, sz, '.')
    sp.diamond(r, r, r, '#' if inv else 'o', inset=inset)
    ink = 'o' if inv else '#'
    ic = crop_icon(name)
    w, h = len(ic[0]), len(ic)
    sp.put(ic, r - w // 2, r + 5, ink=ink)
    sp.put([row[::-1] for row in ic[::-1]], sz - 1 - (r - w // 2) - (w - 1), sz - 1 - (r + 5) - (h - 1), ink=ink)
    return sp


def gem_open(base, seats='NS', pressed=None):
    sc = base.copy()
    r = GEM_R
    gem_frame(sc, 3 * r)
    for (ox, oy), key in GEM_FACETS:
        ic, lab = ITEMS[key][:2]
        x, y = CX + ox * r, CY + oy * r
        if oy == 0 and seats == 'NS':
            sc.blit_c(tent_facet(ic, inv=(key == pressed)), x, y)
            restore_dividers(sc, x - r + 3, y, x + r - 2, y + 1)
        else:
            sc.blit_c(petal_sprite(ic, lab, inv=(key == pressed)), x, y, gem_facing(ox, oy, seats))
    sc.diamond(CX, CY, r - 2, '#')
    sc.blit_c(knock(icon('close')), CX, CY)
    return sc


@concept(1, 'gem')
def gem(base):
    """The diamond blooms into a big stepped diamond cut into a 3x3 lattice of diamond facets: the
    centre facet is close, the eight around it are the tools. Every edge is a 45-degree staircase
    (pixel-perfect on the grid); text inside stays axis-aligned."""
    out = {'open': gem_open(base)}
    # keyframe 1 (optional 'bloom' beat): the diamond doubles, still empty, already inverted
    k1 = base.copy()
    gem_frame(k1, 2 * GEM_R - 6)
    k1.diamond(CX, CY, 2 * GEM_R - 6, 'o')
    k1.diamond(CX, CY, GEM_R - 2, '#')
    k1.blit_c(knock(icon('close')), CX, CY)
    out['key1'] = k1
    out['allsides'] = gem_open(base, seats='NSEW')
    return out


# ---------------------------------------------------------------- 2 pinwheel block
@concept(2, 'pinwheel')
def pinwheel(base):
    """A 3x3 block of square tiles on one shared 1-px grid, centred on the crossing; the centre
    cell is close. Labels turn in a pinwheel: each edge of the table reads two tiles upright."""
    sc = base.copy()
    rim = 3
    n = 3 * T + 4                     # 3 cells + 4 grid lines
    tot = n + 2 * rim + 2
    x0, y0 = CX - tot // 2, CY - tot // 2
    sc.rect(x0, y0, tot, tot, 'm')
    sc.frame(x0, y0, tot, tot)
    gx, gy = x0 + 1 + rim, y0 + 1 + rim
    sc.rect(gx, gy, n, n, '#')
    cells = [((0, 0), 'N', 'new'), ((1, 0), 'N', 'undo'), ((2, 0), 'E', 'redo'), ((2, 1), 'E', 'dice'),
             ((2, 2), 'S', 'first'), ((1, 2), 'S', 'log'), ((0, 2), 'W', 'setup'), ((0, 1), 'W', 'clean')]
    for (c, rr), f, key in cells:
        ic, lab = ITEMS[key][:2]
        sp = icon_tile(T, T, ic, lab, frame=False)
        sc.blit(sp, gx + 1 + c * (T + 1), gy + 1 + rr * (T + 1), f)
    cx0 = gx + 1 + (T + 1)
    sc.rect(cx0, cx0 - gx + gy, T, T, 'o')
    sc.diamond(CX, CY, 16, '#', 2)
    sc.blit_c(knock(icon('close')), CX, CY)
    return {'open': sc}


# ---------------------------------------------------------------- 3 tent ribbon
TENT_W, TENT_H = 45, 61


def tent_button(key, inv=False, long=False):
    ic, short, lng = ITEMS[key][:3]
    lab = lng if long else short
    bw, bh = TENT_W, TENT_H
    sp = Sprite(bw, bh, 'o')
    sp.frame(0, 0, bw, bh)
    half = Sprite(bw - 2, bh // 2 - 1, '.')
    lw = text_w(UI, lab)
    half.icon(ic, (bw - 2 - 16) // 2, 1)
    half.text(UI, lab, (bw - 2 - lw) // 2, 18)  # 2 px clear of the frame
    sp.put(half.p, 1, bh // 2 + 1)
    sp.put([row[::-1] for row in half.p[::-1]], 1, bh - (bh // 2 + 1) - half.h)
    for x in range(2, bw - 2, 2):
        sp.p[bh // 2][x] = '#'
    if inv:
        sp.invert(1, 1, bw - 2, bh - 2)
    return sp


@concept(3, 'tent')
def tent(base):
    """The horizontal divider threads a row of tent cards: each button carries the same icon and
    label on both faces -- upright for the near side, 180 degrees for the far side -- with the
    dotted divider as the fold. Undo/redo sit beside the centre, where both sides reach them."""
    sc = base.copy()
    gap = 5
    left = ['setup', 'new', 'dice', 'undo']      # outer -> inner
    right = ['redo', 'first', 'log', 'clean']    # inner -> outer
    y = CY - TENT_H // 2
    for k, key in enumerate(reversed(left)):
        sc.blit(tent_button(key), CX - 25 - (k + 1) * (TENT_W + gap) + gap, y)
    for k, key in enumerate(right):
        sc.blit(tent_button(key), CX + 26 + k * (TENT_W + gap), y)
    close_diamond(sc)
    return {'open': sc}


# ---------------------------------------------------------------- 4 turntable
CARD_TW, CARD_TH = 51, 33


def turntable_card(face_label='TURN 7  0:42'):
    keys = ['new', 'undo', 'redo', 'dice', 'first', 'log', 'setup', 'clean', None]
    w = 3 * CARD_TW + 4
    head = 12
    h = head + 3 * CARD_TH + 3
    sp = Sprite(w, h, '#')
    sp.rect(1, 1, w - 2, head - 2, 'm')
    for txt, x in (('TABLE', 4), (face_label, w - 4 - text_w(UI, face_label))):
        sp.rect(x - 2, 2, text_w(UI, txt) + 4, 9, 'o')
        sp.text(UI, txt, x, 3)
    for i, key in enumerate(keys):
        c, rr = i % 3, i // 3
        x, y = 1 + c * (CARD_TW + 1), head + rr * (CARD_TH + 1)
        if key is None:
            t = icon_tile(CARD_TW, CARD_TH, 'rotate', 'TURN', frame=False)
        else:
            ic, _, lng = ITEMS[key][:3]
            t = icon_tile(CARD_TW, CARD_TH, ic, lng, frame=False)
        sp.put(t.p, x, y)
    return sp


def card_box(facing, w, h, hang=23):
    """Screen top-left of a card that hangs from the crossing towards `facing`."""
    if facing == 'S':
        return CX - w // 2, CY + hang
    if facing == 'N':
        return CX - w // 2 - (w % 2 == 0), CY - hang - h + 1
    if facing == 'E':
        return CX + hang, CY - w // 2
    return CX - hang - h + 1, CY - w // 2


def turntable_open(base, facing):
    sc = base.copy()
    sp = turntable_card()
    x, y = card_box(facing, sp.w, sp.h)
    sc.blit(sp, x, y, facing)
    close_diamond(sc)
    return sc


@concept(4, 'turntable')
def turntable(base):
    """One card with full-word labels hangs off the diamond towards the side that opened it (the
    diamond's half you tapped); TURN swings it to the next seat in one redraw. Readable, familiar --
    but it points at one player at a time."""
    return {'open': turntable_open(base, 'S'), 'north': turntable_open(base, 'N'),
            'east': turntable_open(base, 'E')}
# CONCEPTS-END


def main():
    args = sys.argv[1:]
    review = None
    if '--review' in args:
        i = args.index('--review')
        review = args[i + 1]
        del args[i:i + 2]
        os.makedirs(review, exist_ok=True)
    base = play_screen()
    base.save(os.path.join(HERE, 'closed.png'))
    if review:
        base.save(os.path.join(review, 'closed.png'), scale=2)
    names = args or sorted(CONCEPTS, key=lambda k: CONCEPTS[k][0])
    for name in names:
        n, fn = CONCEPTS[name]
        out = fn(base)          # {'open': Screen, 'key1': Screen, 'confirm': Screen, ...}
        stem = f'concept-{n}-{name}'
        _, bb = emit(f'{stem}-open', out['open'], base, review)
        detail(stem, out['open'], bb)
        for k, sc in out.items():
            if k != 'open':
                emit(f'{stem}-{k}', sc, base, review)


if __name__ == '__main__':
    main()
