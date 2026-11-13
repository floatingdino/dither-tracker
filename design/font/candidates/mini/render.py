#!/usr/bin/env python3
"""Render the mini sticker numeral candidates (T37) at true Nomad pixels (S=4). Stdlib only.

Usage: python3 design/font/candidates/mini/render.py [name ...]

Reads design/font/candidates/mini/<name>/glyphs.txt (standard glyphs.txt format, no ':' cells) and
the real faces it is compared with (num-l, num-l-pm, num-m, ui, design/icons/*.txt). Writes, next to
this script:

  specimen-<name>.png    every glyph at k=1 and k=2, then 40 37 35 108 -7 +12 0 21 in the hero
                         sticker (with its checker shadow) beside the mini face, both at full size
  in-context-<name>.png  1872x1404 Play-screen board: two commander-target tiles (one rotated, one
                         upright) with quip + mini life on plain paper, a pending-delta readout tile
                         and a counter-peek tile, all in the candidate face
  comparison.png         the three candidates and num-m side by side in the three contexts

Everything is composed on the dither grid (one value per dither px), patterns are resolved at
global phase (SPEC rule 3), and the grid is upscaled by S, so every PNG is pure black/white and
on the S-grid. Geometry mirrors PlayLayout / CommanderLayout / PeekLayout / DrawerLayout at S=4
(numbers quoted in the README); the target-tile composition is the T37 proposal, not the app's.
"""
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
FONT_DIR = os.path.dirname(os.path.dirname(HERE))
ICON_DIR = os.path.join(os.path.dirname(FONT_DIR), 'icons')
S = 4
GW, GH = 468, 351          # dither grid of the Nomad at S=4
PW, PH = 233, 175          # panel
PAPER, INK, CHECK, D6, DRAWER = 0, 1, 2, 3, 4
D6_ROWS = ["#.#.", ".#..", "#.#.", "...#"]
DRAWER_ROWS = ["#...", "....", "..#.", "...."]

# PlayLayout at S=4 (player frame)
SLOT_Y, SLOT_W = 59, 140            # num-l k=1, 4 chars
NUM_Y = 60                          # hero glyph top (cap 53 centred in the 56 slot)
PM_X, PM_Y = 13, 78                 # num-l-pm, cap-aligned
TAB_X, TAB_Y, TAB_W, TAB_H = 72, 154, 89, 21
DOT, DOT_GAP, DOT_HALO = 3, 3, 2
BTN_X, BTN_Y, BTN_VIS, BTN_PAD = 207, 149, 22, 2
BAND_TOP, BAND_BOTTOM = 119, 141    # under-row band (number + V_MIN .. tab/button hit - V_MIN)
UNDER_X, UNDER_W = 8, 217
RO_SLOT_H = 59                      # readout is centred in 0..SLOT_Y
# PeekLayout / DrawerLayout at S=4
ICON, PEEK_PAD, PEEK_GAP, PEEK_ICON_GAP = 16, 2, 6, 2
CELL_W, CELL_H, CELL_INSET, CELL_PAD, CELL_ICON_GAP = 58, 27, 2, 2, 2


# ---------------------------------------------------------------- faces and icons

class Face:
    def __init__(self, path):
        self.name = os.path.basename(os.path.dirname(path))
        self.glyphs, self.cap = {}, None
        cur, rows = None, []
        for line in open(path).read().split('\n'):
            if line.startswith('--- '):
                if cur is not None:
                    self.glyphs[cur] = rows
                key = line[4:].split()[0]
                cur = chr(int(key[2:], 16)) if key.startswith('U+') else key
                rows = []
            elif cur is None:
                s = line.split() if not line.startswith('#') else []
                if s and s[0] == 'size':
                    self.w, self.h = int(s[1]), int(s[2])
                elif s and s[0] == 'tracking':
                    self.tracking = int(s[1])
                elif s and s[0] == 'cap':
                    self.cap = (int(s[1]), int(s[2]))
            elif line.strip():
                rows.append(line.strip())
        if cur is not None:
            self.glyphs[cur] = rows
        self.cap = self.cap or (0, self.h)
        for ch, g in self.glyphs.items():
            assert len(g) == self.h and all(len(r) == self.w for r in g), f"{path}: {ch!r} wrong size"
            assert all(c in '#.:' for r in g for c in r), f"{path}: {ch!r} bad char"

    def glyph(self, ch):
        ch = '-' if ch == '−' else ch
        if ch not in self.glyphs and ch.upper() in self.glyphs:
            ch = ch.upper()
        return self.glyphs.get(ch) or self.glyphs['notdef']

    def width(self, s, k=1):
        n = len(s)
        return 0 if n == 0 else k * (n * self.w + (n - 1) * self.tracking)

    def capmid2(self, k=1):
        """Twice the cap band's centre row (integer maths, like TextLayout)."""
        return k * (2 * self.cap[0] + self.cap[1])


def icon(name):
    return [r.strip() for r in open(os.path.join(ICON_DIR, name + '.txt')).read().split('\n') if r.strip()]


# ---------------------------------------------------------------- dither buffers

class Buf:
    """A rectangle of dither px holding PAPER / INK / pattern ids."""

    def __init__(self, w, h, fill=PAPER):
        self.w, self.h = w, h
        self.v = bytearray([fill]) * (w * h)

    def put(self, x, y, v):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.v[y * self.w + x] = v

    def get(self, x, y):
        return self.v[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else PAPER

    def rect(self, x, y, w, h, v=INK):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.put(xx, yy, v)

    def frame(self, x, y, w, h, v=INK):
        self.rect(x, y, w, 1, v); self.rect(x, y + h - 1, w, 1, v)
        self.rect(x, y, 1, h, v); self.rect(x + w - 1, y, 1, h, v)

    def bitmap(self, rows, x, y, k=1, v=INK, shadow=True):
        for yy, r in enumerate(rows):
            for xx, c in enumerate(r):
                if c == '#' or (c == ':' and shadow):
                    val = v if c == '#' else CHECK
                    for dy in range(k):
                        for dx in range(k):
                            if val == CHECK and self.get(x + xx * k + dx, y + yy * k + dy) != PAPER:
                                continue
                            self.put(x + xx * k + dx, y + yy * k + dy, val)

    def text(self, face, s, x, y, k=1, v=INK, shadow=True):
        adv = (face.w + face.tracking) * k
        for i, ch in enumerate(s):
            self.bitmap(face.glyph(ch), x + i * adv, y, k, v, shadow)
        return face.width(s, k)

    def blit(self, src, x, y, rot=False):
        for yy in range(src.h):
            for xx in range(src.w):
                sx, sy = (src.w - 1 - xx, src.h - 1 - yy) if rot else (xx, yy)
                self.put(x + xx, y + yy, src.v[sy * src.w + sx])

    def png(self, path):
        """Resolve patterns at global phase (origin = this buffer's origin) and write at S."""
        w, h = self.w * S, self.h * S
        lines = []
        for gy in range(self.h):
            row = bytearray()
            for gx in range(self.w):
                v = self.v[gy * self.w + gx]
                ink = (v == INK or (v == CHECK and (gx + gy) % 2 == 0)
                       or (v == D6 and D6_ROWS[gy % 4][gx % 4] == '#')
                       or (v == DRAWER and DRAWER_ROWS[gy % 4][gx % 4] == '#'))
                row += (b'\x00' if ink else b'\xff') * S
            lines.extend([b'\x00' + bytes(row)] * S)

        def chunk(t, d):
            return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
        data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0))
        data += chunk(b'IDAT', zlib.compress(b''.join(lines), 9)) + chunk(b'IEND', b'')
        with open(path, 'wb') as f:
            f.write(data)


def centre(space, size):
    return (space - size) // 2


# ---------------------------------------------------------------- tile pieces (player frame)

def chrome(p, pm=True, tab=True, button=True):
    if pm:
        p.bitmap(PMF.glyph('-'), PM_X, PM_Y)
        p.bitmap(PMF.glyph('+'), PW - PM_X - PMF.w, PM_Y)
    if tab:
        p.rect(TAB_X, TAB_Y, TAB_W, TAB_H, D6)
        dw = 3 * DOT + 2 * DOT_GAP
        dx, dy = TAB_X + centre(TAB_W, dw), TAB_Y + centre(TAB_H, DOT)
        p.rect(dx - DOT_HALO, dy - DOT_HALO, dw + 2 * DOT_HALO, DOT + 2 * DOT_HALO, PAPER)
        for i in range(3):
            p.rect(dx + i * (DOT + DOT_GAP), dy, DOT, DOT, INK)
    if button:
        p.frame(BTN_X, BTN_Y, BTN_VIS, BTN_VIS)
        p.bitmap(ICONS['commander'], BTN_X + 1 + BTN_PAD, BTN_Y + 1 + BTN_PAD)


def hero(p, s, y=NUM_Y):
    tw = HERO.width(s)
    p.text(HERO, s, (PW - SLOT_W) // 2 + centre(SLOT_W, tw), y)


def readout(p, face, s, k=1):
    """Pending delta centred above the number, box centred in 0..SLOT_Y (PlayLayout.readoutFootprint)."""
    h = face.h * k
    p.text(face, s, centre(PW, face.width(s, k)), centre(RO_SLOT_H, h), k)


def target_tile(face, quip, life, k=1, qmax=3, gap=None, heart=True, heart_gap=4):
    """T37 target tile: the quip (ui, ALL CAPS) over the life in the mini face, on plain paper.

    Quip and life are one unit, centred on the hero number's cap centre, so the eye lands where the
    big total normally is. Quip scale: the largest <= qmax that fits the tile (EDGE_MIN 8 each
    side). The gap scales with the quip so short and long quips keep the same rhythm. The heart
    (life icon) leads the number, centred on its cap band, and heart + number are centred as a unit.
    """
    p = Buf(PW, PH)
    chrome(p, pm=False)
    q = qmax
    while q > 1 and UI.width(quip, q) > PW - 16:
        q -= 1
    qcap, lcap = UI.cap[1] * q, face.cap[1] * k
    g = gap if gap is not None else 3 * q + 2
    top = (2 * NUM_Y + HERO.cap[1] - (qcap + g + lcap)) // 2
    p.text(UI, quip, centre(PW, UI.width(quip, q)), top - UI.cap[0] * q, q)
    ly = top + qcap + g - face.cap[0] * k
    lw = face.width(life, k)
    ux = centre(PW, (ICON + heart_gap if heart else 0) + lw)
    if heart:
        p.bitmap(ICONS['life'], ux, ly + face.cap[0] * k + centre(lcap, ICON))
        ux += ICON + heart_gap
    p.text(face, life, ux, ly, k)
    return p


def peeks(p, face, items, k=1, row_h=None):
    """Counter peeks (PeekLayout): chips icon+value centred in the under row, which grows to the
    face if it is taller than the icon row (the band 119..141 has 22 px)."""
    vh = face.h * k
    rh = max(ICON + 2, vh) if row_h is None else row_h
    uy = BAND_TOP + centre(BAND_BOTTOM - BAND_TOP, rh)
    widths = [PEEK_PAD + ICON + PEEK_ICON_GAP + face.width(v, k) + PEEK_PAD for _, v in items]
    total = sum(widths) + PEEK_GAP * (len(items) - 1)
    x = UNDER_X + centre(UNDER_W, total)
    for (ic, v), w in zip(items, widths):
        p.bitmap(ICONS[ic], x + PEEK_PAD, uy + centre(rh, ICON))
        p.text(face, v, x + PEEK_PAD + ICON + PEEK_ICON_GAP, uy + centre(rh, vh), k)
        x += w + PEEK_GAP
    return uy, rh


def drawer_cells(p, face, items, y, k=1):
    """A row of drawer tray cells (DrawerLayout): DRAWER texture, non-zero cells are paper cards
    with a 1-px frame and icon+value centred; zero cells are a haloed icon on the texture."""
    p.rect(0, y, PW, CELL_H, DRAWER)
    x0 = centre(PW, 4 * CELL_W)
    for i, (ic, v) in enumerate(items):
        cx, cy = x0 + i * CELL_W + CELL_INSET, y + CELL_INSET
        cw, ch = CELL_W - 2 * CELL_INSET, CELL_H - 2 * CELL_INSET
        if v == '0':
            ix, iy = cx + centre(cw, ICON), cy + centre(ch, ICON)
            p.rect(ix - 1, iy - 1, ICON + 2, ICON + 2, PAPER)
            p.bitmap(ICONS[ic], ix, iy)
            continue
        p.rect(cx, cy, cw, ch, PAPER)
        p.frame(cx, cy, cw, ch)
        vw, vh = face.width(v, k), face.h * k
        uw = ICON + CELL_ICON_GAP + vw
        ux = cx + centre(cw, uw)
        p.bitmap(ICONS[ic], ux, cy + centre(ch, ICON))
        p.text(face, v, ux + ICON + CELL_ICON_GAP, cy + centre(ch, vh), k)


def screen(tiles):
    """tiles: seat -> Buf (player frame). Seats 0,1 are the top row (rotated 180)."""
    g = Buf(GW, GH)
    org = {0: (0, 0), 1: (PW + 1, 0), 2: (PW + 1, PH + 1), 3: (0, PH + 1)}
    for s, t in tiles.items():
        g.blit(t, *org[s], rot=s in (0, 1))
    for y in range(GH):
        if (PW + y) % 2 == 0:
            g.put(PW, y, INK)
    for x in range(GW):
        if (x + PH) % 2 == 0:
            g.put(x, PH, INK)
    cx, cy, R, cut = PW, PH, 24, 4
    dh = R - cut
    for dy in range(-dh, dh + 1):
        hw = min(dh + cut - abs(dy), dh)
        for dx in range(-hw, hw + 1):
            g.put(cx + dx, cy + dy, D6)
    bw, bh, pitch, halo = 3, 15, 6, 2
    span = 2 * pitch + bw
    g.rect(cx - span // 2 - halo, cy - bh // 2 - halo, span + 2 * halo, bh + 2 * halo, PAPER)
    for i in range(3):
        g.rect(cx + (i - 1) * pitch - 1, cy - bh // 2, bw, bh, INK)
    return g


# ---------------------------------------------------------------- sheets

SAMPLES = ['40', '37', '35', '108', '-7', '+12', '0', '21']
PEEKS = [('commander-tax', '4'), ('poison', '7'), ('energy', '12')]
CELLS = [('commander-tax', '2'), ('poison', '7'), ('energy', '12'), ('experience', '0')]


def in_context(face):
    t0 = target_tile(face, 'HERE IT COMES', '35')
    t3 = target_tile(face, 'UH OH', '21')
    t1 = Buf(PW, PH); chrome(t1); hero(t1, '40'); readout(t1, face, '+12')
    t2 = Buf(PW, PH); chrome(t2); hero(t2, '37'); peeks(t2, face, PEEKS)
    return screen({0: t0, 1: t1, 2: t2, 3: t3})


def specimen(face):
    pad = 12
    k2 = 2
    hero_w = HERO.width('108')
    col = hero_w + 14 + face.width('108') + 30
    width = max(pad * 2 + 2 * col, pad * 2 + face.width('0123456789+-', k2), pad * 2 + face.width('0123456789+- ', 1) + 40)
    width = max(width, pad * 2 + UI.width('num-l sticker (hero, with shadow) beside the mini face, both at play size'))
    rows_h = face.h + 16 + face.h * k2 + 16 + 4 * (HERO.h + 12)
    height = pad + 14 + rows_h + 20 + pad
    c = Buf(width, height)
    title = f"{face.name}  {face.w}x{face.h}  tracking {face.tracking}  cap {face.cap[1]}  (k=1: {face.h * S} device px tall)"
    c.text(UI, title, pad, pad)
    y = pad + 16
    c.text(face, '0123456789+- ', pad, y)
    c.bitmap(face.glyph('notdef'), pad + face.width('0123456789+- ') + face.tracking, y)
    y += face.h + 14
    c.text(UI, 'k=2', pad, y - 11)
    c.text(face, '0123456789+-', pad, y, k2)
    y += face.h * k2 + 16
    c.text(UI, 'num-l sticker (hero, with shadow) beside the mini face, both at play size', pad, y - 12)
    for i, s in enumerate(SAMPLES):
        x = pad + (i % 2) * col
        yy = y + (i // 2) * (HERO.h + 12)
        c.text(HERO, s, x, yy)
        # bottoms aligned on the cap baseline
        c.text(face, s, x + HERO.width(s) + 14, yy + HERO.cap[1] - face.cap[1] - face.cap[0])
    return c


def comparison(faces):
    pad, gap, lab = 10, 10, 13
    cols = faces + [NUM_M]
    width = pad * 2 + len(cols) * PW + (len(cols) - 1) * gap
    ro_h, pk_top = 120, 114
    pk_h = PH - pk_top + 6 + CELL_H + 4
    height = pad + lab + PH + 2 * gap + lab + ro_h + 2 * gap + lab + pk_h + pad + 12
    c = Buf(width, height)
    y1 = pad + lab
    y2 = y1 + PH + 2 * gap + lab
    y3 = y2 + ro_h + 2 * gap + lab
    c.text(UI, '1  COMMANDER TARGET: quip + life on plain paper (num-m column: num-m k=2, no strip)', pad, pad)
    c.text(UI, '2  PENDING-DELTA READOUT above the hero (num-m column: today, num-m k=2)', pad, y2 - lab)
    c.text(UI, '3  COUNTER PEEKS in the under row, and drawer cells (num-m column: today, k=1)', pad, y3 - lab)
    for i, f in enumerate(cols):
        x = pad + i * (PW + gap)
        is_m = f is NUM_M
        k_big = 2 if is_m else 1
        name = f.name + (' (current)' if is_m else f' {f.w}x{f.h}')
        # context 1
        t = target_tile(f, 'UH OH', '35', k=k_big)
        c.blit(t, x, y1)
        c.frame(x - 1, y1 - 1, PW + 2, PH + 2)
        c.text(UI, name, x + 3, y1 + 3)
        # context 2: top of a tile: readout + hero
        t = Buf(PW, PH); chrome(t, tab=False, button=False); hero(t, '40'); readout(t, f, '-7', k_big)
        c.blit(crop(t, 0, 0, PW, ro_h), x, y2)
        c.frame(x - 1, y2 - 1, PW + 2, ro_h + 2)
        # context 3: lower part of a tile with peeks, then a drawer row
        t = Buf(PW, PH); chrome(t); hero(t, '37'); peeks(t, f, PEEKS)
        c.blit(crop(t, 0, pk_top, PW, PH - pk_top), x, y3)
        drawer_row = Buf(PW, CELL_H)
        drawer_cells(drawer_row, f, CELLS, 0)
        c.blit(drawer_row, x, y3 + PH - pk_top + 6)
        c.frame(x - 1, y3 - 1, PW + 2, PH - pk_top + 6 + CELL_H + 2)
        if not is_m and f.h > CELL_H - 2 * (CELL_INSET + CELL_PAD):
            c.text(UI, f"{f.h} px > {CELL_H - 2 * (CELL_INSET + CELL_PAD)} px cell content box", x, y3 + pk_h - 2)
    return c


def crop(b, x, y, w, h):
    o = Buf(w, h)
    for yy in range(h):
        for xx in range(w):
            o.put(xx, yy, b.get(x + xx, y + yy))
    return o


UI = Face(os.path.join(FONT_DIR, 'ui', 'glyphs.txt'))
HERO = Face(os.path.join(FONT_DIR, 'num-l', 'glyphs.txt'))
PMF = Face(os.path.join(FONT_DIR, 'num-l-pm', 'glyphs.txt'))
NUM_M = Face(os.path.join(FONT_DIR, 'num-m', 'glyphs.txt'))
ICONS = {n: icon(n) for n in ('life', 'commander', 'commander-tax', 'poison', 'energy', 'experience')}


def main():
    names = sys.argv[1:] or sorted(d for d in os.listdir(HERE) if os.path.isfile(os.path.join(HERE, d, 'glyphs.txt')))
    faces = [Face(os.path.join(HERE, n, 'glyphs.txt')) for n in names]
    for f in faces:
        assert not any(':' in r for g in f.glyphs.values() for r in g), f"{f.name}: mini faces have no shadow"
        specimen(f).png(os.path.join(HERE, f'specimen-{f.name}.png'))
        in_context(f).png(os.path.join(HERE, f'in-context-{f.name}.png'))
        print(f"{f.name}: {f.w}x{f.h} tracking {f.tracking}, '-999' = {f.width('-999')}x{f.h}")
    if not sys.argv[1:]:
        comparison(faces).png(os.path.join(HERE, 'comparison.png'))


if __name__ == '__main__':
    main()
