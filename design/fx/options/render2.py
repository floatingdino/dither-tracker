#!/usr/bin/env python3
"""Round-two effect renderer (T28d): effects over the *sticker* hero numerals, inside a full seat
tile with its real furniture (tab, +/- glyphs, centre-diamond corner), at true Nomad pixels.
Stdlib only. Reuses the PNG/label helpers of ../render.py without modifying it.

Usage:
  python3 design/fx/options/render2.py                  # sheet.png + strips (all options)
  python3 design/fx/options/render2.py --only claw-cut  # just that strip
  python3 design/fx/options/render2.py --context quake:1 claw-cut:1:36
  python3 design/fx/options/render2.py --review DIR     # also write S=2 strips to DIR for review

Frame symbols (superset of round one):
  .  transparent
  #  ink (black)
  o  paper (white knock-out)
  x  cut: paper over whatever ink is under it (the numeral), ink over paper. = invert under the
     mask. A runtime can do it as an invert, or as two masks split by the numeral's own ink mask.
  1-9  tile layer: meta 'tile <d> <TILE> <mode>'; the tile is sampled at GLOBAL screen phase.
     mode ink   = black where the tile has ink, transparent elsewhere
     mode paper = white where the tile has ink, transparent elsewhere (ghosts ink through a dither)
     mode fill  = black where the tile has ink, white elsewhere (opaque)
meta 'anchor tile dx dy' puts the frame centre at the seat tile's centre + (dx, dy). The other refs
(centre, tl, tr, ...) are points of the life total's layout box, as in round one.
"""
import argparse
import os
import sys

sys.dont_write_bytecode = True  # never drop __pycache__ into design/fx/

HERE = os.path.dirname(os.path.abspath(__file__))
FX = os.path.dirname(HERE)
ROOT = os.path.abspath(os.path.join(FX, '..', '..'))
sys.path.insert(0, FX)
import render as r1  # noqa: E402  (round-one helpers: write_png, read_png_grey, label, FONT)

STICKER = os.path.join(ROOT, 'design', 'font', 'candidates', 'sticker', 'glyphs.txt')
CAPTURE = os.path.join(ROOT, 'design', 'captures', 't07-delta.png')
PAT = os.path.join(ROOT, 'design', 'patterns')
S = 4
SW, SH = 468, 351                 # screen in dither px
TW, TH = 233, 175                 # one seat tile
TCX, TCY = 116, 87                # tile centre = life-total centre
# seats: screen origin of the tile and whether it is rotated 180
SEAT_BL = (0, 176, False)
SEAT_TR = (234, 0, True)
GW, GTRACK = 32, 4                # sticker advance


def tile_pat(name, _c={}):
    if name not in _c:
        _c[name] = [r for r in open(os.path.join(PAT, name + '.txt')).read().split('\n') if r]
    return _c[name]


def load_sticker():
    g, cur = {}, None
    for line in open(STICKER):
        line = line.rstrip('\n')
        if line.startswith('--- '):
            cur = line[4:].strip()
            g[cur] = []
        elif cur is not None and line and line[0] in '.#':
            g[cur].append(line)
    return g


GLYPHS = load_sticker()


def box_size(number):
    n = len(number)
    return n * GW + (n - 1) * GTRACK, 56


# ---------------------------------------------------------------- screen canvas

class Screen:
    """Whole play screen in dither px, 1 = ink."""

    def __init__(self, w=SW, h=SH):
        self.w, self.h = w, h
        self.p = [bytearray(w) for _ in range(h)]

    def copy(self):
        s = Screen.__new__(Screen)
        s.w, s.h = self.w, self.h
        s.p = [bytearray(r) for r in self.p]
        return s

    def to_png(self, path, s=S):
        W = self.w * s
        px = bytearray()
        for row in self.p:
            line = bytearray()
            for v in row:
                line += (b'\x00' if v else b'\xff') * s
            px += bytes(line) * s
        r1.write_png(path, W, self.h * s, px)


def base_screen():
    """t07 capture with every life total and the readout removed (effects play after commit)."""
    w, h, rows = r1.read_png_grey(CAPTURE)
    sc = Screen()
    for y in range(SH):
        r = rows[y * S + 1]
        sc.p[y] = bytearray(1 if r[x * S + 1] < 128 else 0 for x in range(SW))
    for seat in (SEAT_BL, SEAT_TR, (0, 0, True), (234, 176, False)):
        for ly in range(6, 150):
            for lx in range(30, 203):
                sx, sy = to_screen(seat, lx, ly)
                sc.p[sy][sx] = 0
    return sc


def to_screen(seat, lx, ly):
    x0, y0, rot = seat
    if rot:
        return x0 + TW - 1 - lx, y0 + TH - 1 - ly
    return x0 + lx, y0 + ly


def furniture_mask():
    """Local (upright) tile cells that hold furniture ink in the BL seat, plus the diamond zones of
    both top corners (the diamond sits in the top-left corner for the other diagonal's seats)."""
    sc = base_screen()
    m = set()
    for ly in range(TH):
        for lx in range(TW):
            sx, sy = to_screen(SEAT_BL, lx, ly)
            if sc.p[sy][sx]:
                m.add((lx, ly))
            if (TW - 1 - lx) + ly <= 24 or lx + ly <= 24:
                m.add((lx, ly))
    return m


def draw_number(sc, seat, number, cx=TCX, cy=TCY):
    tw, th = box_size(number)
    x0, y0 = cx - tw // 2, cy - th // 2
    for i, ch in enumerate(number):
        rows = GLYPHS.get(ch) or GLYPHS['notdef']
        gx = x0 + i * (GW + GTRACK)
        for y, line in enumerate(rows):
            for x, v in enumerate(line):
                if v == '#':
                    sx, sy = to_screen(seat, gx + x, y0 + y)
                    sc.p[sy][sx] = 1


def number_ink(seat, number):
    s = Screen()
    draw_number(s, seat, number)
    return s


REFS = {'centre': (0, 0), 'tl': (-1, -1), 'tr': (1, -1), 'bl': (-1, 1), 'br': (1, 1),
        'top': (0, -1), 'bottom': (0, 1), 'left': (-1, 0), 'right': (1, 0)}


def frame_origin(frame, anchor, number):
    fh, fw = len(frame), len(frame[0])
    ref, dx, dy = anchor
    if ref == 'tile':
        return TCX + dx - fw // 2, TCY + dy - fh // 2
    tw, th = box_size(number)
    rx, ry = REFS[ref]
    return TCX + rx * (tw // 2) + dx - fw // 2, TCY + ry * (th // 2) + dy - fh // 2


def stamp(sc, seat, frame, anchor, number, tiles, clip_report=None):
    """Composite one frame onto the screen for a seat. Clipped to the seat tile."""
    x0, y0 = frame_origin(frame, anchor, number)
    for y, row in enumerate(frame):
        for x, v in enumerate(row):
            if v == '.':
                continue
            lx, ly = x0 + x, y0 + y
            if not (0 <= lx < TW and 0 <= ly < TH):
                if clip_report is not None:
                    clip_report.append((lx, ly))
                continue
            sx, sy = to_screen(seat, lx, ly)
            if v == '#':
                sc.p[sy][sx] = 1
            elif v == 'o':
                sc.p[sy][sx] = 0
            elif v == 'x':
                sc.p[sy][sx] ^= 1
            elif v.isdigit():
                name, mode = tiles[v]
                t = tile_pat(name)
                ink = t[sy % len(t)][sx % len(t[0])] == '#'
                if mode == 'ink':
                    if ink:
                        sc.p[sy][sx] = 1
                elif mode == 'paper':
                    if ink:
                        sc.p[sy][sx] = 0
                else:
                    sc.p[sy][sx] = 1 if ink else 0
            else:
                raise ValueError(f'bad symbol {v!r}')


# ---------------------------------------------------------------- effects on disk

def load_effect(path):
    frames = []
    i = 1
    while os.path.exists(os.path.join(path, f'frame-{i}.txt')):
        rows = [r for r in open(os.path.join(path, f'frame-{i}.txt')).read().split('\n') if r]
        frames.append(rows)
        i += 1
    meta, tiles = {}, {}
    for line in open(os.path.join(path, 'meta.txt')):
        parts = line.split()
        if not parts or line.startswith('#'):
            continue
        if parts[0] == 'tile':
            tiles[parts[1]] = (parts[2], parts[3])
        else:
            meta[parts[0]] = parts[1:]
    a = meta['anchor']
    anchor = (a[0], int(a[1]), int(a[2])) if len(a) == 3 else ('centre', int(a[0]), int(a[1]))
    # drop a trailing empty frame: the runtime's clean redraw is the end state
    while frames and all(set(r) <= {'.'} for r in frames[-1]):
        frames.pop()
    return frames, anchor, meta, tiles


def list_options():
    return sorted(n for n in os.listdir(HERE) if os.path.isfile(os.path.join(HERE, n, 'meta.txt')))


def effect_path(name):
    if name.startswith('r1-'):
        return os.path.join(FX, name[3:])
    return os.path.join(HERE, name)


# ---------------------------------------------------------------- tiles, flips, sheet

_BASE = None


def base():
    global _BASE
    if _BASE is None:
        _BASE = base_screen()
    return _BASE


def seat_tile(sc, seat=SEAT_BL):
    """Cut the upright tile for a seat out of a screen (local coords)."""
    t = Screen(TW, TH)
    for ly in range(TH):
        for lx in range(TW):
            sx, sy = to_screen(seat, lx, ly)
            t.p[ly][lx] = sc.p[sy][sx]
    return t


def sequence(name, number, seat=SEAT_BL, clip=None):
    """Screens: clean, f1, ..., fN (drawn frames only)."""
    frames, anchor, meta, tiles = load_effect(effect_path(name))
    clean = base().copy()
    draw_number(clean, seat, number)
    seq = [clean]
    for f in frames:
        s = clean.copy()
        stamp(s, seat, f, anchor, number, tiles, clip)
        seq.append(s)
    return seq


def diff(a, b, seat=SEAT_BL):
    n = 0
    for ly in range(TH):
        for lx in range(TW):
            sx, sy = to_screen(seat, lx, ly)
            n += a.p[sy][sx] != b.p[sy][sx]
    return n


def flips(name, number='37'):
    seq = sequence(name, number)
    seq = seq + [seq[0]]
    return [diff(a, b) for a, b in zip(seq, seq[1:])]


def furniture_hits(name, number='37'):
    """Furniture/diamond-zone cells that any frame changes (should be 0)."""
    fm = furniture_mask()
    seq = sequence(name, number)
    bad = 0
    for s in seq[1:]:
        for (lx, ly) in fm:
            sx, sy = to_screen(SEAT_BL, lx, ly)
            if s.p[sy][sx] != seq[0].p[sy][sx]:
                bad += 1
    return bad


def number_change_flips(a='37', b='36'):
    sa, sb = base().copy(), base().copy()
    draw_number(sa, SEAT_BL, a)
    draw_number(sb, SEAT_BL, b)
    return diff(sa, sb)


def strip(name, number, roles):
    seq = sequence(name, number)
    fl = flips(name, number)
    n = len(seq)
    gap = 6
    c = r1.Bits(n * (TW + gap), TH + 12)
    for i, s in enumerate(seq):
        t = seat_tile(s)
        ox = i * (TW + gap)
        for y in range(TH):
            row = t.p[y]
            for x in range(TW):
                if row[x]:
                    c.p[y][ox + x] = True
        # tile border: dotted, outside the tile so it never hides an effect pixel
        txt = 'base = clean' if i == 0 else f'f{i}  {fl[i - 1]} flips'
        if i == n - 1:
            txt += f'  > clean {fl[-1]}'
        r1.label(c, txt, ox + 2, TH + 4)
        for x in range(0, TW, 2):
            c.p[TH + 1][ox + x] = True
    return c


def sheet(rows, number, out, scale):
    strips = [(n, lab, strip(n, number, None)) for n, lab in rows]
    lw = 120
    W = lw + max(s.w for _, _, s in strips) + 4
    H = sum(s.h + 10 for _, _, s in strips) + 8
    c = r1.Bits(W, H)
    y = 4
    for n, lab, s in strips:
        title = n
        r1.label(c, title, 4, y + 60, k=2)
        for j, part in enumerate(lab.split('|')):
            r1.label(c, part, 4, y + 76 + j * 8)
        for yy in range(s.h):
            src = s.p[yy]
            dst = c.p[y + yy]
            for xx in range(s.w):
                if src[xx]:
                    dst[lw + xx] = True
        y += s.h + 10
    c.to_png(out, scale)


def context(name, k, number, out):
    frames, anchor, meta, tiles = load_effect(effect_path(name))
    sc = base().copy()
    numbers = {(0, 0, True): '40', (234, 176, False): '108'}
    for seat, num in numbers.items():
        draw_number(sc, seat, num)
    for seat in (SEAT_BL, SEAT_TR):
        draw_number(sc, seat, number)
        stamp(sc, seat, frames[k - 1], anchor, number, tiles)
    sc.to_png(out, S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--number', default='37')
    ap.add_argument('--only', nargs='*', default=None)
    ap.add_argument('--context', nargs='*', default=None, help='name:k[:number]')
    ap.add_argument('--review', default=None, help='dir for S=2 review strips')
    ap.add_argument('--nosheet', action='store_true')
    a = ap.parse_args()
    import order  # local: sheet order + labels
    names = a.only or [n for n, _ in order.ROWS]
    print('reference: sticker 37->36 flips', number_change_flips('37', '36'),
          ' 40->33', number_change_flips('40', '33'))
    for n in names:
        fl = flips(n, a.number)
        hits = furniture_hits(n, a.number)
        print(f'{n:14s} flips {fl} total {sum(fl)}  furniture/diamond hits {hits}')
        if a.review:
            strip(n, a.number, None).to_png(os.path.join(a.review, f'strip-{n}.png'), 2)
    if not a.only and not a.nosheet:
        sheet(order.ROWS, a.number, os.path.join(HERE, 'sheet.png'), S)
        print('wrote sheet.png')
    for spec in a.context or []:
        p = spec.split(':')
        n, k = p[0], int(p[1])
        num = p[2] if len(p) > 2 else a.number
        out = os.path.join(HERE, f'in-context-{n}-f{k}.png')
        context(n, k, num, out)
        print('wrote', out)


if __name__ == '__main__':
    main()
