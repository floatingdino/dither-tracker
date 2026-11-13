#!/usr/bin/env python3
"""Render the hero-numeral candidates (T26) at true device pixels. Stdlib only.

Usage: python3 design/font/candidates/render.py [name ...]

Reads every design/font/candidates/<name>/glyphs.txt (same format as design/font/<face>/glyphs.txt)
plus the current design/font/num-l for reference, and writes next to this script:

  specimen-<name>.png    all glyphs + sample totals, pure 1-bit, at the face's play-screen size
  in-context-<name>.png  1872x1404 mock of the 4-player Play screen on the Nomad (S=4)
  comparison.png         every face showing `40` and `-128` side by side, labelled

Every pixel written is pure black or pure white (labels use the app's own `ui` face).
Layout maths mirror app/.../play/PlayLayout.kt: 468x351 dither grid, 233x175 panels, 1-px dotted
dividers, top row rotated 180 degrees, number slot sized for 4 glyphs at kMax, +/- at pmK = k/2.
One deviation, noted in the README: when kMax is 1 (faces drawn natively at full size) the +/-
are drawn at half size (2x2 majority downsample) instead of full size.
"""
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
FONT_DIR = os.path.dirname(HERE)
S = 4                     # device px per dither px (Nomad)
DEV_W, DEV_H = 1872, 1404
INK, PAPER = 0, 255

# PlayLayout constants
MAX_CHARS, EDGE_MIN, GAP_MIN, V_MIN = 4, 8, 8, 4
TAB_W, TAB_H, DOT, DOT_GAP = 55, 12, 2, 2
DIAMOND_R, DIAMOND_CUT, BAR_H, BAR_PITCH = 13, 2, 9, 3
D6 = ["#.#.", ".#..", "#.#.", "...#"]   # Role.TAB / Role.MENU


# ---------------------------------------------------------------- faces

class Face:
    def __init__(self, path):
        self.path = path
        self.name = os.path.basename(os.path.dirname(path))
        self.glyphs = {}
        self.cap = None
        self.comments = []
        cur, rows = None, []
        for line in open(path).read().split('\n'):
            if line.startswith('--- '):
                if cur is not None:
                    self.glyphs[cur] = rows
                key = line[4:].split()[0]
                cur = chr(int(key[2:], 16)) if key.startswith('U+') else key
                rows = []
            elif cur is None:
                s = line.split('#', 1)[0].split() if not line.startswith('#') else []
                if line.startswith('#'):
                    self.comments.append(line[1:].strip())
                elif s and s[0] == 'size':
                    self.w, self.h = int(s[1]), int(s[2])
                elif s and s[0] == 'tracking':
                    self.tracking = int(s[1])
                elif s and s[0] == 'cap':
                    self.cap = (int(s[1]), int(s[2]))
            elif line.strip():
                rows.append(line.strip())
        if cur is not None:
            self.glyphs[cur] = rows
        if self.cap is None:
            self.cap = (0, self.h)
        for ch, g in self.glyphs.items():
            assert len(g) == self.h and all(len(r) == self.w for r in g), f"{path}: glyph {ch!r} wrong size"
            assert all(c in '#.' for r in g for c in r), f"{path}: glyph {ch!r} bad char"

    def glyph(self, ch):
        if ch == '−':
            ch = '-'
        return self.glyphs.get(ch) or self.glyphs['notdef']

    def width_of(self, n, k):
        return 0 if n <= 0 else k * (n * self.w + (n - 1) * self.tracking)


def half(rows):
    """2x2 majority downsample (>= 2 of 4 ink) for the +/- of k=1 faces."""
    h, w = len(rows), len(rows[0])

    def solid(x, y):  # ignore isolated dither pixels (e.g. a baked checker shadow)
        if rows[y][x] != '#':
            return False
        return any(0 <= x + dx < w and 0 <= y + dy < h and rows[y + dy][x + dx] == '#'
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    out = []
    for y in range(0, h - 1, 2):
        out.append(''.join('#' if sum(solid(x + dx, y + dy) for dy in (0, 1) for dx in (0, 1)) >= 2 else '.'
                           for x in range(0, w - 1, 2)))
    return out


# ---------------------------------------------------------------- canvas (device px, 1-bit)

class Canvas:
    def __init__(self, w, h, fill=PAPER):
        self.w, self.h = w, h
        self.px = bytearray([fill]) * (w * h)

    def rect(self, x, y, w, h, v=INK):
        x0, y0, x1, y1 = max(0, x), max(0, y), min(self.w, x + w), min(self.h, y + h)
        if x1 <= x0:
            return
        for yy in range(y0, y1):
            self.px[yy * self.w + x0: yy * self.w + x1] = bytes([v]) * (x1 - x0)

    def dpx(self, gx, gy, v=INK):
        """One dither px at dither coordinate (gx, gy)."""
        self.rect(gx * S, gy * S, S, S, v)

    def invert(self, x, y, w, h):
        for yy in range(max(0, y), min(self.h, y + h)):
            for xx in range(max(0, x), min(self.w, x + w)):
                i = yy * self.w + xx
                self.px[i] = 255 - self.px[i]

    def save(self, path):
        w, h = self.w, self.h
        raw = b''.join(b'\x00' + bytes(self.px[y * w:(y + 1) * w]) for y in range(h))

        def chunk(t, d):
            c = struct.pack('>I', len(d)) + t + d
            return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

        png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
        with open(path, 'wb') as f:
            f.write(png)


def draw_rows(c, rows, gx, gy, k, rot=0, v=INK):
    """Draw a glyph bitmap at dither (gx, gy), scale k, rotation 0/180 (about its own box)."""
    h, w = len(rows), len(rows[0])
    for y in range(h):
        for x in range(w):
            if rows[y][x] == '#':
                xx, yy = (x, y) if rot == 0 else (w - 1 - x, h - 1 - y)
                c.rect((gx + xx * k) * S, (gy + yy * k) * S, k * S, k * S, v)


def draw_text(c, face, text, gx, gy, k, rot=0, v=INK, tracking=None):
    t = face.tracking if tracking is None else tracking
    n = len(text)
    adv = (face.w + t) * k
    for i, ch in enumerate(text):
        j = i if rot == 0 else n - 1 - i
        draw_rows(c, face.glyph(ch), gx + j * adv, gy, k, rot, v)


def label(c, text, gx, gy, k=1):
    draw_text(c, UI, text, gx, gy, k)


# ---------------------------------------------------------------- play layout (mirror of PlayLayout)

def fits(face, k, pw, ph, pm_half):
    pmw = face.w // 2 if pm_half else face.w * pmk(k)
    w = 2 * EDGE_MIN + 2 * pmw + 2 * GAP_MIN + face.width_of(MAX_CHARS, k)
    h = face.h * k + 2 * (TAB_H + V_MIN)
    return w <= pw and h <= ph


def pmk(k):
    return max(1, k // 2)


def max_k(face, pw, ph):
    best = 1
    for k in range(1, 9):
        if fits(face, k, pw, ph, pm_half=(k == 1)):
            best = k
        else:
            break
    return best


def play_screen(face, lives=(40, 37, 108, -7)):
    gw, gh = DEV_W // S, DEV_H // S
    pw, ph = (gw - 1) // 2, (gh - 1) // 2
    k = max_k(face, pw, ph)
    pm_half = k == 1
    minus = half(face.glyph('-')) if pm_half else face.glyph('-')
    plus = half(face.glyph('+')) if pm_half else face.glyph('+')
    pk = 1 if pm_half else pmk(k)
    pmw, pmh = len(minus[0]) * pk, len(minus) * pk
    slot_w, slot_h = face.width_of(MAX_CHARS, k), face.h * k
    side = (pw - slot_w) // 2
    pmx, pmy = (side - pmw) // 2, (ph - pmh) // 2
    slot_y = (ph - slot_h) // 2
    tab_x, tab_y = (pw - TAB_W) // 2, ph - TAB_H

    c = Canvas(DEV_W, DEV_H)
    for seat in range(4):
        # Draw the seat upright into its own panel canvas, then rotate it into place.
        p = Canvas(pw * S, ph * S)
        draw_rows(p, minus, pmx, pmy, pk)
        draw_rows(p, plus, pw - pmx - pmw, pmy, pk)
        s = str(lives[seat])
        n = len(s)
        kk = k
        while kk > 1 and face.width_of(n, kk) > slot_w:
            kk -= 1
        tw = face.width_of(n, kk)
        lx = side + (slot_w - tw) // 2
        cap_top, cap_h = face.cap
        ly = slot_y + (slot_h - cap_h * kk) // 2 - cap_top * kk
        draw_text(p, face, s, lx, ly, kk)
        px0 = 0 if seat in (0, 3) else pw + 1
        py0 = 0 if seat in (0, 1) else ph + 1
        rot = seat in (0, 1)
        for y in range(p.h):
            sy = p.h - 1 - y if rot else y
            row = p.px[sy * p.w:(sy + 1) * p.w]
            if rot:
                row = row[::-1]
            o = (py0 * S + y) * DEV_W + px0 * S
            c.px[o:o + p.w] = row
        # Drawer tab (global-phase D6) with three haloed dots, in the player's frame.
        for ty in range(TAB_H):
            for tx in range(TAB_W):
                fx, fy = tab_x + tx, tab_y + ty
                gx, gy = (px0 + pw - 1 - fx, py0 + ph - 1 - fy) if rot else (px0 + fx, py0 + fy)
                if D6[gy % 4][gx % 4] == '#':
                    c.dpx(gx, gy)
        dots_w = 3 * DOT + 2 * DOT_GAP
        dx0, dy0 = tab_x + (TAB_W - dots_w) // 2, tab_y + (TAB_H - DOT) // 2
        for i in range(3):
            fx = dx0 + i * (DOT + DOT_GAP)
            for (ox, oy, w, h, v) in ((fx - 1, dy0 - 1, DOT + 2, DOT + 2, PAPER), (fx, dy0, DOT, DOT, INK)):
                gx = px0 + pw - ox - w if rot else px0 + ox
                gy = py0 + ph - oy - h if rot else py0 + oy
                c.rect(gx * S, gy * S, w * S, h * S, v)
    # Dotted dividers (checker slice, global phase)
    for y in range(gh):
        if (pw + y) % 2 == 0:
            c.dpx(pw, y)
    for x in range(gw):
        if (x + ph) % 2 == 0:
            c.dpx(x, ph)
    # Centre diamond, D6, with three haloed bars
    cx, cy = pw, ph
    dh = DIAMOND_R - DIAMOND_CUT
    for dy in range(-dh, dh + 1):
        hw = min(dh + DIAMOND_CUT - abs(dy), dh)
        for dx in range(-hw, hw + 1):
            gx, gy = cx + dx, cy + dy
            c.dpx(gx, gy, INK if D6[gy % 4][gx % 4] == '#' else PAPER)
    bar_top = cy - BAR_H // 2
    for i in range(3):
        bx = cx + (i - 1) * BAR_PITCH
        c.rect((bx - 1) * S, (bar_top - 1) * S, 3 * S, (BAR_H + 2) * S, PAPER)
    for i in range(3):
        bx = cx + (i - 1) * BAR_PITCH
        c.rect(bx * S, bar_top * S, S, BAR_H * S, INK)
    return c, k


# ---------------------------------------------------------------- sheets

SAMPLES = ['40', '37', '-7', '128', '0', '21', '69', '1000']


def specimen(face, k):
    adv = (face.w + face.tracking) * k
    gh = face.h * k
    pad = 12
    row1, row2 = "012345", "6789+-"
    width = max(pad * 2 + face.width_of(6, k), pad * 2 + 2 * face.width_of(4, k) + 24)
    title = (f"{face.name}  {face.w}x{face.h}  tracking {face.tracking}  k={k}  "
             f"({face.h * k} dither px / {face.h * k * S} device px tall)")
    width = max(width, pad * 2 + 6 * len(title))
    lines = 2 + (len(SAMPLES) + 1) // 2 + 1
    height = pad + 12 + lines * (gh + 16) + pad
    c = Canvas(width * S, height * S)
    label(c, title, pad, pad)
    y = pad + 16
    for r in (row1, row2):
        draw_text(c, face, r, pad, y, k)
        y += gh + 16
    col_w = (width - 2 * pad) // 2
    for i, s in enumerate(SAMPLES):
        cx = pad + (i % 2) * col_w
        draw_text(c, face, s, cx, y, k)
        if i % 2 == 1:
            y += gh + 16
    # pressed-state inversion sample
    inv_w = face.width_of(2, k) + 16
    c.rect(pad * S, y * S, inv_w * S, (gh + 8) * S, INK)
    draw_text(c, face, '40', pad + 8, y + 4, k, v=PAPER)
    label(c, "pressed (inverted)", pad + inv_w + 8, y + gh // 2 - 4)
    return c


def comparison(faces):
    pad, gap = 12, 14
    rows = []
    for f in faces:
        k = max_k(f, 233, 175)
        rows.append((f, k))
    width = DEV_W // S
    height = pad + sum(f.h * k + 14 + gap for f, k in rows) + pad
    c = Canvas(width * S, height * S)
    y = pad
    for f, k in rows:
        note = "current" if f.name == 'num-l' else "candidate"
        label(c, f"{f.name} ({note})  {f.w}x{f.h} at k={k}", pad, y)
        y += 13
        draw_text(c, f, '40', pad, y, k)
        draw_text(c, f, '-128', pad + f.width_of(2, k) + 40, y, k)
        y += f.h * k + gap
        for x in range(pad, width - pad):
            if x % 2 == 0:
                c.dpx(x, y - gap // 2)
    return c


UI = Face(os.path.join(FONT_DIR, 'ui', 'glyphs.txt'))


def main():
    names = sys.argv[1:] or sorted(d for d in os.listdir(HERE)
                                   if os.path.isfile(os.path.join(HERE, d, 'glyphs.txt')))
    faces = [Face(os.path.join(HERE, n, 'glyphs.txt')) for n in names]
    for f in faces:
        ctx, k = play_screen(f)
        ctx.save(os.path.join(HERE, f'in-context-{f.name}.png'))
        specimen(f, k).save(os.path.join(HERE, f'specimen-{f.name}.png'))
        print(f"{f.name}: {f.w}x{f.h} tracking {f.tracking} -> kMax {k}, '-999' = "
              f"{f.width_of(4, k)}x{f.h * k} dither px")
    if not sys.argv[1:]:
        ref = Face(os.path.join(FONT_DIR, 'num-l', 'glyphs.txt'))
        ctx, _ = play_screen(ref)
        ctx.save(os.path.join(HERE, 'in-context-num-l.png'))
        comparison([ref] + faces).save(os.path.join(HERE, 'comparison.png'))


if __name__ == '__main__':
    main()
