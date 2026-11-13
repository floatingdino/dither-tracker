#!/usr/bin/env python3
"""Render the hit/heal effect frames in design/fx/<name>/ (stdlib only, no PIL).

Usage: python3 design/fx/render.py [--scale S] [--number 37] [--context]

Writes, next to this script, in pure black/white at device pixels (S device px per dither px,
default 4 = the Nomad):
  strip-<name>.png   base number, then every frame composited over it, side by side
  sheet.png          all strips stacked, each labelled
  in-context-<name>-f<k>.png   (with --context) the 1872x1404 play screen, built from
                     design/captures/t06-play.png, with frame k drawn on one upright seat
                     (bottom-left) and one rotated seat (top-right)

Frame format: '#' draw black, 'o' draw white (knock-out), '.' transparent.
meta.txt 'anchor dx dy' = offset of the frame centre from the life total's centre, in dither px,
in the seat's own upright coordinates (y down). Rotated seats rotate frame and offset by 180.
"""
import argparse
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
GLYPHS = os.path.join(ROOT, 'design', 'font', 'num-l', 'glyphs.txt')
CAPTURE = os.path.join(ROOT, 'design', 'captures', 't06-play.png')
K = 2  # num-l glyph scale on the play screen (56 dither px tall)

# 3x5 label font (a-z, 0-9, a few marks)
FONT = {
    'a': '.#.#.#####.##.#', 'b': '##.#.###.#.###.', 'c': '.###..#..#...##', 'd': '##.#.##.##.###.',
    'e': '####..##.#..###', 'f': '####..##.#..#..', 'g': '.###..#.##.#.##', 'h': '#.##.#####.##.#',
    'i': '###.#..#..#.###', 'j': '..#..#..##.#.#.', 'k': '#.##.###.#.##.#', 'l': '#..#..#..#..###',
    'm': '#.########.##.#', 'n': '##.#.##.##.##.#', 'o': '.#.#.##.##.#.#.', 'p': '##.#.###.#..#..',
    'q': '.#.#.##.##.#.##', 'r': '##.#.###.#.##.#', 's': '.###...#...###.', 't': '###.#..#..#..#.',
    'u': '#.##.##.##.####', 'v': '#.##.##.##.#.#.', 'w': '#.##.########.#', 'x': '#.##.#.#.#.##.#',
    'y': '#.##.#.#..#..#.', 'z': '###..#.#.#..###', '-': '......###......', ' ': '...............',
    '0': '####.##.##.####', '1': '.#.##..#..#.###', '2': '###..#####..###', '3': '###..####..####',
    '4': '#.##.####..#..#', '5': '####..###..####', '6': '####..####.####', '7': '###..#..#..#..#',
    '8': '####.#####.####', '9': '####.####..####', '(': '.#.#..#..#...#.', ')': '.#...#..#..#.#.',
    '/': '..#..#.#.#..#..', ':': '....#.....#....', '.': '............#..', '>': '#...#...#.#.#..',
    '=': '...###...###...', '+': '....#.###.#....', '~': '......####.....', '%': '#.#..#.#.#..#.#',
}


# ---------- io ----------

def write_png(path, w, h, px):
    """px: bytearray of 0/255 grey values, row-major."""
    raw = b''.join(b'\x00' + bytes(px[y * w:(y + 1) * w]) for y in range(h))

    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)


def read_png_grey(path):
    """Minimal PNG decoder (8-bit grey/RGB/RGBA, non-interlaced) -> (w, h, rows of luma)."""
    d = open(path, 'rb').read()
    i, idat = 8, b''
    while i < len(d):
        n, = struct.unpack('>I', d[i:i + 4])
        t = d[i + 4:i + 8]
        if t == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', d[i + 8:i + 18])
        elif t == b'IDAT':
            idat += d[i + 8:i + 8 + n]
        i += 12 + n
    bpp = {0: 1, 2: 3, 4: 2, 6: 4}[ct]
    raw = zlib.decompress(idat)
    stride = w * bpp
    prev = bytearray(stride)
    rows = []
    p = 0
    for _ in range(h):
        f = raw[p]
        cur = bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        if f == 1:
            for x in range(bpp, stride):
                cur[x] = (cur[x] + cur[x - bpp]) & 255
        elif f == 2:
            for x in range(stride):
                cur[x] = (cur[x] + prev[x]) & 255
        elif f == 3:
            for x in range(stride):
                a = cur[x - bpp] if x >= bpp else 0
                cur[x] = (cur[x] + ((a + prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = cur[x - bpp] if x >= bpp else 0
                c = prev[x - bpp] if x >= bpp else 0
                b = prev[x]
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                cur[x] = (cur[x] + pr) & 255
        rows.append(bytes(cur[0::bpp]))
        prev = cur
    return w, h, rows


# ---------- 1-bit canvas on the dither grid (True = ink) ----------

class Bits:
    def __init__(self, w, h, ink=False):
        self.w, self.h = w, h
        self.p = [[ink] * w for _ in range(h)]

    def set(self, x, y, v):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = v

    def get(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h and self.p[y][x]

    def copy(self):
        b = Bits(self.w, self.h)
        b.p = [r[:] for r in self.p]
        return b

    def fill(self, x0, y0, w, h, v):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.set(x, y, v)

    def to_png(self, path, s):
        W, H = self.w * s, self.h * s
        px = bytearray(W * H)
        for y in range(self.h):
            row = bytearray()
            for v in self.p[y]:
                row += (b'\x00' if v else b'\xff') * s
            for yy in range(s):
                o = (y * s + yy) * W
                px[o:o + W] = row
        write_png(path, W, H, px)


def load_glyphs():
    g, cur = {}, None
    for line in open(GLYPHS):
        line = line.rstrip('\n')
        if line.startswith('--- '):
            cur = line[4:].strip()
            g[cur] = []
        elif cur is not None and line and line[0] in '.#':
            g[cur].append(line)
    return g


def text_size(s, glyphs, k=K):
    return len(s) * 16 * k + (len(s) - 1) * 4 * k, 28 * k


def draw_number(c, s, cx, cy, glyphs, rot=False, k=K):
    """Draw s centred at (cx, cy); rot=True draws it rotated 180 about that centre."""
    tw, th = text_size(s, glyphs, k)
    x0, y0 = cx - tw // 2, cy - th // 2
    for i, ch in enumerate(s):
        rows = glyphs[ch]
        gx = x0 + i * 20 * k
        for y, line in enumerate(rows):
            for x, v in enumerate(line):
                if v != '#':
                    continue
                for yy in range(k):
                    for xx in range(k):
                        px, py = gx + x * k + xx, y0 + y * k + yy
                        if rot:
                            px, py = 2 * cx - 1 - px, 2 * cy - 1 - py
                        c.set(px, py, True)


def label(c, s, x, y, k=1):
    for ci, ch in enumerate(s.lower()):
        g = FONT.get(ch, FONT[' '])
        for i, v in enumerate(g):
            if v == '#':
                c.fill(x + ci * 4 * k + (i % 3) * k, y + (i // 3) * k, k, k, True)


def dotted_rect(c, x, y, w, h):
    for i in range(w):
        if i % 2 == 0:
            c.set(x + i, y, True)
            c.set(x + i, y + h - 1, True)
    for j in range(h):
        if j % 2 == 0:
            c.set(x, y + j, True)
            c.set(x + w - 1, y + j, True)


# ---------- effects ----------

def load_effect(name):
    d = os.path.join(HERE, name)
    frames = []
    i = 1
    while os.path.exists(os.path.join(d, f'frame-{i}.txt')):
        rows = [r for r in open(os.path.join(d, f'frame-{i}.txt')).read().split('\n') if r]
        frames.append(rows)
        i += 1
    meta = {'tiles': {}}
    for line in open(os.path.join(d, 'meta.txt')):
        parts = line.split('#')[0].split()
        if parts and parts[0] == 'tile':
            meta['tiles'][parts[1]] = (load_tile(parts[2]), parts[3])
        elif parts:
            meta[parts[0]] = parts[1:]
    a = meta['anchor']
    ref, dx, dy = (a[0], int(a[1]), int(a[2])) if len(a) == 3 else ('centre', int(a[0]), int(a[1]))
    for f in frames:
        assert len(f) == len(frames[0]) and all(len(r) == len(f[0]) for r in f), name
    return frames, (ref, dx, dy), meta


def load_tile(name):
    path = os.path.join(HERE, '..', 'patterns', name + '.txt')
    return [r for r in open(path).read().split('\n') if r]


def list_effects():
    return sorted(n for n in os.listdir(HERE)
                  if os.path.isfile(os.path.join(HERE, n, 'meta.txt')))


REFS = {'centre': (0, 0), 'tl': (-1, -1), 'tr': (1, -1), 'bl': (-1, 1), 'br': (1, 1),
        'top': (0, -1), 'bottom': (0, 1), 'left': (-1, 0), 'right': (1, 0)}


def stamp(c, frame, cx, cy, anchor, rot=False, number='37', tiles=None):
    """Composite frame with its centre at ref point of the number box + (dx, dy).

    ref is a point of the life total's ink box (centre, tl, tr, bl, br, top, bottom, left, right),
    so corner-anchored effects follow the box for 1-, 2- and 3-digit totals. Digits are tile layers
    ([tiles]: digit -> (tile rows, ink|paper|fill)), sampled at the canvas's (global) phase."""
    fh, fw = len(frame), len(frame[0])
    ref, dx, dy = anchor
    tw, th = text_size(number, None)
    rx, ry = REFS[ref]
    x0 = cx + rx * (tw // 2) + dx - fw // 2
    y0 = cy + ry * (th // 2) + dy - fh // 2
    for y, row in enumerate(frame):
        for x, v in enumerate(row):
            if v == '.':
                continue
            px, py = x0 + x, y0 + y
            if rot:
                px, py = 2 * cx - 1 - px, 2 * cy - 1 - py
            if v.isdigit():
                t, mode = tiles[v]
                on = t[py % len(t)][px % len(t[0])] == '#'
                if mode == 'fill':
                    c.set(px, py, on)
                elif on:
                    c.set(px, py, mode == 'ink')
                continue
            c.set(px, py, v == '#')


def flips(name, glyphs, number='37'):
    """Changed dither px per transition: base->f1, f1->f2, ..., last->base."""
    frames, anchor, meta = load_effect(name)
    W = H = 160
    base = Bits(W, H)
    draw_number(base, number, W // 2, H // 2, glyphs)
    seq = [base]
    for f in frames:
        b = base.copy()
        stamp(b, f, W // 2, H // 2, anchor, number=number, tiles=meta['tiles'])
        seq.append(b)
    if len(frames) and any(ch != '.' for r in frames[-1] for ch in r):
        seq.append(base)  # the runtime always ends by redrawing the clean number
    out = []
    for a, b in zip(seq, seq[1:]):
        out.append(sum(a.p[y][x] != b.p[y][x] for y in range(H) for x in range(W)))
    return out


ROLES = {'claw': 'damage', 'pow': 'damage', 'zap': 'damage', 'crack': 'damage',
         'heart': 'heal', 'twinkle': 'heal', 'sprout': 'heal',
         'wham': 'big hit (opt)', 'fizzle': 'ko (opt)'}
ORDER = ['pow', 'zap', 'claw', 'crack', 'heart', 'sprout', 'twinkle', 'wham', 'fizzle']
CELL_W, CELL_H = 233, 175  # one true seat panel


def strip(name, glyphs, number):
    frames, anchor, meta = load_effect(name)
    n = len(frames) + 1
    c = Bits(CELL_W * n, CELL_H + 10)
    for i in range(n):
        ox = i * CELL_W
        cx, cy = ox + CELL_W // 2, CELL_H // 2
        draw_number(c, number, cx, cy, glyphs)
        if i:
            stamp(c, frames[i - 1], cx, cy, anchor, number=number, tiles=meta['tiles'])
        dotted_rect(c, ox, 0, CELL_W, CELL_H)
        label(c, 'base' if i == 0 else f'f{i}', ox + 3, CELL_H + 3)
    return c


def sheet(names, glyphs, number):
    strips = [(n, strip(n, glyphs, number)) for n in names]
    lw = 92
    W = lw + max(s.w for _, s in strips) + 8
    H = sum(s.h + 6 for _, s in strips) + 8
    c = Bits(W, H)
    y = 4
    for n, s in strips:
        label(c, n, 4, y + CELL_H // 2 - 6, k=2)
        role = ROLES.get(n)
        if role:
            label(c, role, 4, y + CELL_H // 2 + 8)
        for yy in range(s.h):
            for xx in range(s.w):
                if s.p[yy][xx]:
                    c.set(lw + xx, y + yy, True)
        y += s.h + 6
    return c


def context(name, k, glyphs, number):
    w, h, rows = read_png_grey(CAPTURE)
    S = 4
    c = Bits(w // S, h // S)
    for y in range(c.h):
        r = rows[y * S + 1]
        for x in range(c.w):
            c.p[y][x] = r[x * S + 1] < 128
    frames, anchor, meta = load_effect(name)
    pw, ph = c.w // 2, c.h // 2
    # seats: (panel x0, y0, rotated)
    for px0, py0, rot in ((0, ph, False), (pw, 0, True)):
        # find the life total's ink bbox in the middle band of the panel and replace the number
        xs, ys = [], []
        for y in range(py0 + 30, py0 + ph - 30):
            for x in range(px0 + 40, px0 + pw - 40):
                if c.p[y][x]:
                    xs.append(x)
                    ys.append(y)
        x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
        cx, cy = (x0 + x1 + 1) // 2, (y0 + y1 + 1) // 2
        c.fill(x0, y0, x1 - x0 + 1, y1 - y0 + 1, False)
        draw_number(c, number, cx, cy, glyphs, rot=rot)
        stamp(c, frames[k - 1], cx, cy, anchor, rot=rot, number=number, tiles=meta['tiles'])
    return c


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--scale', type=int, default=4)
    ap.add_argument('--number', default='37')
    ap.add_argument('--context', nargs='*', default=None,
                    help='name:k[:number] ... e.g. claw:1:33 (default: none)')
    ap.add_argument('--only', nargs='*', default=None)
    a = ap.parse_args()
    glyphs = load_glyphs()
    names = a.only or sorted(list_effects(), key=lambda n: ORDER.index(n) if n in ORDER else 99)
    for n in names:
        s = strip(n, glyphs, a.number)
        s.to_png(os.path.join(HERE, f'strip-{n}.png'), a.scale)
        print(n, 'flips per transition (dither px):', flips(n, glyphs, a.number))
    if not a.only:
        sheet(names, glyphs, a.number).to_png(os.path.join(HERE, 'sheet.png'), a.scale)
        print('wrote sheet.png')
    for spec in a.context or []:
        parts = spec.split(':')
        n, k = parts[0], int(parts[1])
        num = parts[2] if len(parts) > 2 else a.number
        out = os.path.join(HERE, f'in-context-{n}-f{k}.png')
        context(n, k, glyphs, num).to_png(out, a.scale)
        print('wrote', out)


if __name__ == '__main__':
    main()
