#!/usr/bin/env python3
"""Render the T37 round-two mini numerals and corner peeks at true Nomad pixels (S=4). Stdlib only.

Usage: python3 design/font/candidates/mini2/render.py

Reads design/font/candidates/mini2/<name>/glyphs.txt (standard glyphs.txt format, no ':' cells),
optional <name>/alt.txt (alternative glyphs keyed `0-nodot` etc., shown on the specimen only), the
round-one `half` (../mini/half) and the real faces (num-l, num-l-pm, num-m, ui, design/icons).
Writes, next to this script:

  specimen-<name>.png    the charset at k=1, 2 and 4 (k=4 for inspection), alternates, k=2 beside
                         round-one half, and play-size samples beside the hero sticker
  comparison.png         strict / roomy / bold / num-m / half: digit strips, (a) the commander
                         target tile at k=1 and k=2, (b) the pending-delta readout at k=1 and k=2,
                         (c) drawer cells
  peeks-<layout>.png     each corner-peek layout on an upright (seat 3) and a rotated (seat 0) tile
                         with 1, 3, 6 and 8 active counters, in the recommended face
  in-context.png         one real game state on the full 1872x1404 Play screen
  in-context-commander.png  one real commander-mode state on the full screen

Everything is composed on the dither grid (one value per dither px), patterns are resolved at
global phase (SPEC rule 3) and the grid is upscaled by S, so every PNG is pure black/white on the
S-grid. Geometry mirrors PlayLayout / CommanderLayout / PeekLayout / DrawerLayout at S=4 (the
commander button is no longer on the tile; tabs are hidden in commander mode). Peek placements are
checked against the keep-out rects and a warning is printed on any collision.
"""
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
CAND = os.path.dirname(HERE)
FONT_DIR = os.path.dirname(CAND)
ICON_DIR = os.path.join(os.path.dirname(FONT_DIR), 'icons')
S = 4
GW, GH = 468, 351          # dither grid of the Nomad at S=4
PW, PH = 233, 175          # panel
PAPER, INK, CHECK, D6, DRAWER = 0, 1, 2, 3, 4
D6_ROWS = ["#.#.", ".#..", "#.#.", "...#"]
DRAWER_ROWS = ["#...", "....", "..#.", "...."]

# PlayLayout at S=4 (player frame)
SLOT_X, SLOT_Y, SLOT_W, SLOT_H = 46, 59, 140, 56   # num-l k=1, 4 chars ("-999")
PM_X, PM_Y, PM_W = 13, 78, 20                      # num-l-pm, cap-aligned
TAB_X, TAB_Y, TAB_W, TAB_H, TAB_HIT_PAD = 72, 154, 89, 21, 8
DOT, DOT_GAP, DOT_HALO = 3, 3, 2
EDGE_MIN, V_MIN = 8, 4
RO_SLOT_H = SLOT_Y                                 # readout is centred in 0..SLOT_Y
DIAMOND_R, DIAMOND_CUT, DIAMOND_HIT_PAD = 24, 4, 4
# PeekLayout / DrawerLayout / CommanderLayout at S=4
ICON, PEEK_PAD, PEEK_ICON_GAP = 16, 2, 2
CELL_W, CELL_H, CELL_INSET, CELL_PAD, CELL_ICON_GAP = 58, 27, 2, 2, 2
QUIP_GAP, QUIP_K_MAX, QUIP_K_MIN, LIFE_ICON_GAP, LETHAL_PAD = 10, 4, 2, 3, 3


# ---------------------------------------------------------------- faces and icons

class Face:
    def __init__(self, path, name=None):
        self.name = name or os.path.basename(os.path.dirname(path))
        self.glyphs, self.cap, self.tracking = {}, None, 0
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


def crop(b, x, y, w, h):
    o = Buf(w, h)
    for yy in range(h):
        for xx in range(w):
            o.put(xx, yy, b.get(x + xx, y + yy))
    return o


# ---------------------------------------------------------------- tile pieces (player frame)

def diamond_centre(seat):
    """The centre diamond's centre in the player's frame: always a FAR corner (y = -1).
    Seats 0 (TL, rotated) and 2 (BR) have it far-left; seats 1 (TR, rotated) and 3 (BL) far-right."""
    return (-1, -1) if seat in (0, 2) else (PW, -1)


def in_diamond(seat, x, y, pad=0):
    cx, cy = diamond_centre(seat)
    dx, dy = abs(x - cx), abs(y - cy)
    r = DIAMOND_R + pad
    return dx + dy <= r + DIAMOND_CUT and dx <= r and dy <= r


def chrome(p, pm=True, tab=True):
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


def diamond_quarter(p, seat):
    """The part of the centre diamond that falls inside this tile (for standalone tile mocks)."""
    for y in range(0, DIAMOND_R + 2):
        for x in range(PW):
            if in_diamond(seat, x, y):
                p.put(x, y, D6)


def hero(p, s, y=SLOT_Y + 1, v=INK):
    tw = HERO.width(s)
    p.text(HERO, s, SLOT_X + centre(SLOT_W, tw), y, v=v)


def readout(p, face, s, k=1):
    """Pending delta centred above the number, box centred in 0..SLOT_Y (PlayLayout.readoutFootprint)."""
    p.text(face, s, centre(PW, face.width(s, k)), centre(RO_SLOT_H, face.h * k), k)


def readout_rect(face, k, chars=6):
    w, h = face.width('0' * chars, k), face.h * k
    return (centre(PW, w), centre(RO_SLOT_H, h), w, h)


def target_tile(face, quip, life, k=1, ro=None, ro_face=None, ro_k=None):
    """Commander-target tile as CommanderLayout composes it (tabs hidden in the mode): the readout
    slot on top, then the block quip (ui, ALL CAPS, one size below the largest that fits, >= 2),
    QUIP_GAP, heart + life in `face` at k, centred between the readout and the tab band."""
    p = Buf(PW, PH)
    ro_face, ro_k = ro_face or face, ro_k or k
    if ro:
        readout(p, ro_face, ro, ro_k)
    roh = ro_face.h * ro_k
    top = centre(SLOT_Y, roh) + roh + V_MIN
    bottom = PH - TAB_H - TAB_HIT_PAD - V_MIN
    row_h = max(ICON, face.h * k)

    def block_h(q):
        return UI.cap[1] * q + QUIP_GAP + row_h
    q = QUIP_K_MAX
    while q > 1 and not (UI.width(quip, q) <= PW - 2 * EDGE_MIN and block_h(q) <= bottom - top):
        q -= 1
    q = max(q - 1, min(q, QUIP_K_MIN))
    bt = top + (bottom - top - block_h(q)) // 2
    p.text(UI, quip, centre(PW, UI.width(quip, q)), bt - UI.cap[0] * q, q)
    y = bt + UI.cap[1] * q + QUIP_GAP
    nw = face.width(life, k)
    x = centre(PW, ICON + LIFE_ICON_GAP + nw)
    p.bitmap(ICONS['life'], x, y + centre(row_h, ICON))
    p.text(face, life, x + ICON + LIFE_ICON_GAP, y + centre(row_h, face.h * k), k)
    return p


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
        ux = cx + centre(cw, ICON + CELL_ICON_GAP + vw)
        p.bitmap(ICONS[ic], ux, cy + centre(ch, ICON))
        p.text(face, v, ux + ICON + CELL_ICON_GAP, cy + centre(ch, vh), k)


# ---------------------------------------------------------------- corner peeks

LETHAL_POISON = 8
BOTTOM_Y = PH - 6 - ICON          # bottom chip row: 6 px off the near edge (tab rows 154..174)
PITCH = 17                        # 16-px icon rows, 1 px apart (each icon has an empty 1-px ring)
TAB_L = TAB_X - TAB_HIT_PAD - V_MIN - PEEK_PAD  # chips left of the tab end at x <= 58 (ink-chip pad incl.)
TAB_R = TAB_X + TAB_W + TAB_HIT_PAD + V_MIN  # chips right of the tab start at x >= 173


def chip_w(face, v, gap=PEEK_ICON_GAP):
    return ICON + gap + face.width(v)


def keepouts(seat, ro_face=None, ro_k=1):
    """Player-frame rects a peek must stay V_MIN clear of (diamond handled separately)."""
    r = [('number slot', SLOT_X, SLOT_Y, SLOT_W, SLOT_H),
         ('- glyph', PM_X, PM_Y, PM_W, 17), ('+ glyph', PW - PM_X - PM_W, PM_Y, PM_W, 17),
         ('tab hit', TAB_X - TAB_HIT_PAD, TAB_Y - TAB_HIT_PAD, TAB_W + 2 * TAB_HIT_PAD, TAB_H + TAB_HIT_PAD)]
    if ro_face:
        r.append(('readout',) + readout_rect(ro_face, ro_k))
    return r


def check(seat, rects, tag, ro_face=None, ro_k=1):
    bad = []
    for (x, y, w, h) in rects:
        if x < EDGE_MIN - 2 or x + w > PW - EDGE_MIN + 2 or y < 3 or y + h > PH - 3:
            bad.append('edge')
        for name, kx, ky, kw, kh in keepouts(seat, ro_face, ro_k):
            if x < kx + kw + V_MIN - 1 and kx < x + w + V_MIN - 1 and y < ky + kh + V_MIN - 1 and ky < y + h + V_MIN - 1:
                bad.append(name)
        if any(in_diamond(seat, xx, yy, DIAMOND_HIT_PAD) for xx in (x, x + w - 1) for yy in (y, y + h - 1)):
            bad.append('diamond')
    if bad:
        print(f"  ! {tag}: peeks collide with {sorted(set(bad))}")
    return not bad


def draw_chip(p, face, ic, v, x, y, gap=PEEK_ICON_GAP):
    """icon then value, value centred on the 16-px icon row. Poison >= 8 is an ink chip."""
    w = chip_w(face, v, gap) if ic else face.width(v)
    vy = y + centre(ICON, face.h)
    if ic == 'poison' and int(v) >= LETHAL_POISON:
        p.rect(x - PEEK_PAD, y - 1, w + 2 * PEEK_PAD, ICON + 2, INK)
        p.bitmap(ICONS[ic], x, y, v=PAPER)
        p.text(face, v, x + ICON + gap, vy, v=PAPER)
    elif ic:
        p.rect(x - 1, y - 1, w + 2, ICON + 2, PAPER)       # opaque on paper
        p.bitmap(ICONS[ic], x, y)
        p.text(face, v, x + ICON + gap, vy)
    else:                                                   # the "+N" chip
        p.rect(x - 1, y - 1, w + 2, ICON + 2, PAPER)
        p.text(face, v, x, vy)
    return (x - PEEK_PAD, y - 1, w + 2 * PEEK_PAD, ICON + 2)


def fit_overflow(items, cap):
    """At most `cap` slots: when there are more items, the last slot becomes '+N'."""
    if len(items) <= cap:
        return list(items)
    return list(items[:cap - 1]) + [(None, f'+{len(items) - cap + 1}')]


def lay_stack(face, items, seat):
    """A: bottom-left column, first counter in the corner, growing up the side (3 rows)."""
    out = []
    for i, (ic, v) in enumerate(fit_overflow(items, 3)):
        out.append((ic, v, EDGE_MIN, BOTTOM_Y - i * PITCH))
    return out


def lay_split(face, items, seat):
    """B: both bottom corners, either side of the tab, alternating L, R, L, R... bottom-up;
    the left column is left-aligned on the edge, the right column right-aligned on the edge."""
    out = []
    for i, (ic, v) in enumerate(fit_overflow(items, 6)):
        row, right = i // 2, i % 2 == 1
        w = chip_w(face, v) if ic else face.width(v)
        x = PW - EDGE_MIN - w if right else EDGE_MIN
        out.append((ic, v, x, BOTTOM_Y - row * PITCH))
    return out


RUN_GAP, RUN_ICON_GAP = 2, 1


def lay_run(face, items, seat):
    """C: bottom-left horizontal run along the near edge growing toward the tab (x <= 60), compact
    chips (icon + 1 + digits); a chip that does not fit starts a new row above (3 rows max)."""
    out, x, row = [], EDGE_MIN, 0
    rest = list(items)
    while rest:
        ic, v = rest[0]
        w = chip_w(face, v, RUN_ICON_GAP)
        if x > EDGE_MIN and x + w > TAB_L:
            row, x = row + 1, EDGE_MIN
        if row == 3:
            break
        # the last free slot of the last row takes '+N' if more remain after this one
        if row == 2 and len(rest) > 1:
            more = (None, f'+{len(rest)}')
            mw = face.width(more[1])
            if x + w + RUN_GAP + mw > TAB_L:
                out.append((None, more[1], x, BOTTOM_Y - row * PITCH, RUN_ICON_GAP))
                rest = []
                break
        out.append((ic, v, x, BOTTOM_Y - row * PITCH, RUN_ICON_GAP))
        x += w + RUN_GAP
        rest.pop(0)
    return out


def lay_far(face, items, seat):
    """D: the far corner away from the centre diamond (far-left on seats 1 and 3, far-right on
    0 and 2), a column growing down from the far edge (3 rows: the number slot starts at y 59). Side flips by seat."""
    left = seat in (1, 3)
    out = []
    for i, (ic, v) in enumerate(fit_overflow(items, 3)):
        w = chip_w(face, v) if ic else face.width(v)
        x = EDGE_MIN if left else PW - EDGE_MIN - w
        out.append((ic, v, x, 4 + i * PITCH))
    return out


LAYOUTS = [('stack', lay_stack, 'A  bottom-left column, growing up the side (3 slots)'),
           ('split', lay_split, 'B  both bottom corners either side of the tab, alternating (6 slots)'),
           ('run', lay_run, 'C  bottom-left run toward the tab, wrapping upward (compact chips)'),
           ('far', lay_far, 'D  far corner away from the diamond (side flips by seat; 3 slots)')]


def place_peeks(p, face, items, layout, seat, tag='', ro_face=None, ro_k=1):
    fn = dict((n, f) for n, f, _ in LAYOUTS)[layout]
    rects = []
    for it in fn(face, items, seat):
        ic, v, x, y = it[:4]
        gap = it[4] if len(it) > 4 else PEEK_ICON_GAP
        rects.append(draw_chip(p, face, ic, v, x, y, gap))
    check(seat, rects, tag or f'{layout} seat {seat} n={len(items)}', ro_face, ro_k)


# ---------------------------------------------------------------- full screen

def screen(tiles, close=False):
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
    cx, cy, R, cut = PW, PH, DIAMOND_R, DIAMOND_CUT
    dh = R - cut
    for dy in range(-dh, dh + 1):
        hw = min(dh + cut - abs(dy), dh)
        for dx in range(-hw, hw + 1):
            g.put(cx + dx, cy + dy, D6)
    if close:
        g.rect(cx - 10, cy - 10, 21, 21, PAPER)
        g.bitmap(ICONS['close'], cx - 8, cy - 8)
    else:
        bw, bh, pitch, halo = 3, 15, 6, 2
        span = 2 * pitch + bw
        g.rect(cx - span // 2 - halo, cy - bh // 2 - halo, span + 2 * halo, bh + 2 * halo, PAPER)
        for i in range(3):
            g.rect(cx + (i - 1) * pitch - 1, cy - bh // 2, bw, bh, INK)
    return g


# ---------------------------------------------------------------- sheets

SAMPLES = ['40', '37', '35', '108', '-7', '+12', '0', '21']
CELLS = [('commander-tax', '2'), ('poison', '7'), ('energy', '12'), ('experience', '0')]
CHARSET = '0123456789+-/: '


def specimen(face):
    pad = 12
    alt = ALTS.get(face.name)
    width = 520
    c = Buf(width, 520)
    c.text(UI, f"{face.name}  {face.w}x{face.h}  tracking {face.tracking}  ('-999' = {face.width('-999')} px;"
               f" k=1 is {face.h * S} device px tall)", pad, pad)
    y = pad + 16
    x = pad + c.text(face, CHARSET, pad, y) + face.tracking
    c.bitmap(face.glyph('notdef'), x, y)
    if alt:
        ax = x + face.w + 24
        c.text(UI, 'alt 0:', ax, y + 3)
        ax += 40
        for key in sorted(alt.glyphs):
            c.bitmap(alt.glyphs[key], ax, y)
            ax += face.w + 8
    y += face.h + 10
    c.text(UI, 'k=2, then round-one half (15x27) at k=1', pad, y)
    y += 12
    c.text(face, '0123456789+-', pad, y, 2)
    y += face.h * 2 + 4
    c.text(HALF, '0123456789+-', pad, y)
    y += HALF.h + 10
    c.text(UI, 'k=4 (inspection: every font px is 4x4 dither px)', pad, y)
    y += 12
    c.text(face, '01234', pad, y, 4)
    y += face.h * 4 + 4
    c.text(face, '56789', pad, y, 4)
    if alt:
        ax = pad + face.width('56789', 4) + 16
        for key in sorted(alt.glyphs):
            c.bitmap(alt.glyphs[key], ax, y, 4)
            ax += (face.w + 4) * 4
    y += face.h * 4 + 10
    c.text(UI, 'hero sticker beside the face at k=1 and k=2, bottoms aligned', pad, y)
    y += 12
    col = 232
    for i, s in enumerate(SAMPLES[:6]):
        x = pad + (i % 2) * col
        yy = y + (i // 2) * (HERO.h + 6)
        c.text(HERO, s, x, yy)
        bx = x + HERO.width(s) + 10
        c.text(face, s, bx, yy + HERO.cap[1] - face.h)
        c.text(face, s, bx + face.width(s) + 10, yy + HERO.cap[1] - 2 * face.h, 2)
    y += 3 * (HERO.h + 6)
    return crop(c, 0, 0, width, y + 4)


def comparison(faces):
    pad, gap, lab = 10, 10, 13
    cols = faces + [NUM_M, HALF]
    width = pad * 2 + len(cols) * PW + (len(cols) - 1) * gap
    ro_h = 120
    strip_h = 52
    sections = [('0  THE FACES: 0123456789 at k=1, then k=2 (half: k=1 only, it is already 27 px)', strip_h),
                ('a1 COMMANDER TARGET, life at k=1 (readout -7 at the same k; tabs hidden in the mode)', PH),
                ('a2 COMMANDER TARGET, life at k=2 (num-m k=2 = today; half drawn at k=1)', PH),
                ('b1 PENDING-DELTA READOUT above the hero at k=1', ro_h),
                ('b2 PENDING-DELTA READOUT at k=2 (num-m k=2 = today; half drawn at k=1)', ro_h),
                ('c  DRAWER CELLS at k=1 (cell content box is 19 px tall)', CELL_H)]
    height = pad + sum(lab + h + 2 * gap for _, h in sections) + pad
    c = Buf(width, height)
    y = pad
    for si, (title, h) in enumerate(sections):
        c.text(UI, title, pad, y)
        y += lab
        for i, f in enumerate(cols):
            x = pad + i * (PW + gap)
            kk = 1 if f is HALF else (2 if si in (2, 4) else 1)
            if si == 0:
                c.text(UI, f"{f.name} {f.w}x{f.h}", x, y)
                c.text(f, '0123456789', x, y + 11)
                if f is not HALF:
                    c.text(f, '01234', x, y + 11 + f.h + 3, 2)
                continue
            if si in (1, 2):
                t = target_tile(f, 'NOT YOUR DAY', '14', kk, ro='-7')
                c.blit(t, x, y)
            elif si in (3, 4):
                t = Buf(PW, PH); chrome(t, tab=False); hero(t, '33'); readout(t, f, '-7', kk)
                c.blit(crop(t, 0, 0, PW, ro_h), x, y)
            else:
                t = Buf(PW, CELL_H)
                drawer_cells(t, f, CELLS, 0)
                c.blit(t, x, y)
            c.frame(x - 1, y - 1, PW + 2, h + 2)
            if si == 1:
                c.text(UI, f.name, x + 3, y + 3)
        y += h + 2 * gap
    return c


PEEK_SETS = [
    ('1 active', '40', [('poison', '7')]),
    ('3 active', '23', [('commander-tax', '4'), ('poison', '7'), ('energy', '12')]),
    ('6 active', '108', [('commander-tax', '4'), ('poison', '7'), ('energy', '12'), ('experience', '3'),
                         ('rads', '2'), ('storm', '9')]),
    ('8 active, poison 10', '-12', [('commander-tax', '4'), ('poison', '10'), ('energy', '12'), ('experience', '3'),
                                    ('rads', '2'), ('storm', '9'), ('tickets', '1'), ('generic-counter', '5')]),
]


def peeks_sheet(face, layout, desc):
    pad, gap, lab = 10, 10, 13
    width = pad * 2 + 4 * PW + 3 * gap
    height = pad + 2 * lab + 2 * (lab + PH + gap) + pad
    c = Buf(width, height)
    c.text(UI, f'{desc}  -  values in {face.name} k=1', pad, pad)
    for i, (name, life, items) in enumerate(PEEK_SETS):
        c.text(UI, f'{name} (life {life})', pad + i * (PW + gap), pad + lab + 2)
    for r, (seat, label) in enumerate([(3, 'upright: seat 3 (bottom-left), diamond at its far-right corner'),
                                       (0, 'rotated: seat 0 (top-left) as seen on screen, diamond at its far-left corner')]):
        y = pad + 2 * lab + r * (lab + PH + gap) + lab
        c.text(UI, label, pad, y - lab + 2)
        for i, (name, life, items) in enumerate(PEEK_SETS):
            x = pad + i * (PW + gap)
            t = Buf(PW, PH)
            chrome(t); hero(t, life); diamond_quarter(t, seat)
            place_peeks(t, face, items, layout, seat, f'{layout} seat {seat} {name}')
            c.blit(t, x, y, rot=seat in (0, 1))
            c.frame(x - 1, y - 1, PW + 2, PH + 2)
    return c


def in_context(face, layout):
    """One real state, drawers closed: seat 0 has three counters, seat 1 an open readout (-6 on 34),
    seat 3 has six counters with poison 8 (inverted), seat 2 nothing."""
    t0 = Buf(PW, PH); chrome(t0); hero(t0, '31')
    place_peeks(t0, face, [('commander-tax', '2'), ('poison', '3'), ('energy', '12')], layout, 0, 'in-context seat 0')
    t1 = Buf(PW, PH); chrome(t1); hero(t1, '34'); readout(t1, face, '-6', 2)
    t2 = Buf(PW, PH); chrome(t2); hero(t2, '40')
    t3 = Buf(PW, PH); chrome(t3); hero(t3, '17')
    place_peeks(t3, face, [('commander-tax', '4'), ('poison', '8'), ('energy', '12'), ('experience', '3'),
                           ('rads', '2'), ('storm', '9')], layout, 3, 'in-context seat 3')
    return screen({0: t0, 1: t1, 2: t2, 3: t3})


def in_context_commander(face, k):
    """Commander mode, seat 3 is the target. Seat 0's commander has dealt 12, seat 1's 21 (lethal,
    ink card) and just dealt 3 more (source readout +3, linked life loss -3 on the target), seat 2's
    none. Tabs are hidden in the mode and the diamond shows the close X."""
    tiles = {}
    for seat, dmg in ((0, '12'), (1, '21'), (2, '0')):
        t = Buf(PW, PH); chrome(t, tab=False)
        if int(dmg) >= 21:
            tw = HERO.width(dmg)
            x = SLOT_X + centre(SLOT_W, tw)
            t.rect(x - LETHAL_PAD, SLOT_Y + 1 - LETHAL_PAD, tw + 2 * LETHAL_PAD, HERO.cap[1] + 2 * LETHAL_PAD)
            hero(t, dmg, v=PAPER)
            readout(t, face, '+3', k)
        else:
            hero(t, dmg)
        tiles[seat] = t
    tiles[3] = target_tile(face, 'NOT YOUR DAY', '14', k, ro='-3')
    return screen(tiles, close=True)


UI = Face(os.path.join(FONT_DIR, 'ui', 'glyphs.txt'))
HERO = Face(os.path.join(FONT_DIR, 'num-l', 'glyphs.txt'))
PMF = Face(os.path.join(FONT_DIR, 'num-l-pm', 'glyphs.txt'))
NUM_M = Face(os.path.join(FONT_DIR, 'num-m', 'glyphs.txt'))
HALF = Face(os.path.join(CAND, 'mini', 'half', 'glyphs.txt'))
ICONS = {n: icon(n) for n in ('life', 'commander-tax', 'poison', 'energy', 'experience', 'rads', 'storm',
                              'tickets', 'generic-counter', 'close')}
ALTS = {}

RECOMMENDED = 'strict'
RECOMMENDED_LAYOUT = 'split'
RECOMMENDED_TARGET_K = 2


def main():
    names = ['strict', 'roomy', 'bold']
    faces = [Face(os.path.join(HERE, n, 'glyphs.txt')) for n in names]
    for n in names:
        ap = os.path.join(HERE, n, 'alt.txt')
        if os.path.isfile(ap):
            ALTS[n] = Face(ap, n)
    for f in faces:
        assert not any(':' in r for g in f.glyphs.values() for r in g), f"{f.name}: mini faces have no shadow"
        assert all(ch in f.glyphs for ch in CHARSET + '') and 'notdef' in f.glyphs, f"{f.name}: charset"
        specimen(f).png(os.path.join(HERE, f'specimen-{f.name}.png'))
        print(f"{f.name}: {f.w}x{f.h} tracking {f.tracking}, '-999' = {f.width('-999')}x{f.h}")
    comparison(faces).png(os.path.join(HERE, 'comparison.png'))
    rec = faces[names.index(RECOMMENDED)]
    for name, _, desc in LAYOUTS:
        peeks_sheet(rec, name, desc).png(os.path.join(HERE, f'peeks-{name}.png'))
    in_context(rec, RECOMMENDED_LAYOUT).png(os.path.join(HERE, 'in-context.png'))
    in_context_commander(rec, RECOMMENDED_TARGET_K).png(os.path.join(HERE, 'in-context-commander.png'))


if __name__ == '__main__':
    main()
