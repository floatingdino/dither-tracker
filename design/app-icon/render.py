#!/usr/bin/env python3
"""Render the launcher-icon concepts and write the launcher resources (Python 3 stdlib only).

Usage:
  python3 design/app-icon/render.py                 # previews for every concept + resources for PICK
  python3 design/app-icon/render.py --previews-only # previews only; app/src/main/res is untouched
  python3 design/app-icon/render.py --check         # verify committed resources match the ASCII

Source of truth: design/app-icon/<concept>/icon.txt (format in README.md): a 54x54 grid of
"icon pixels" (1 icon px = 2 dp), '#' ink, '.' paper, and any other character mapped to a dither
tile from design/patterns/ by a `fill <char> <TILE>` line. Tiles are anchored to the canvas origin
(global phase), so a fill reads the same wherever it sits.

Every pixel written is pure black or pure white; every scale is an integer, nearest-neighbour.
Previews use grey only as a backdrop/guide (never inside the icon).
"""
import argparse
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PATTERNS = os.path.join(ROOT, 'design', 'patterns')
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')

PICK = 'heart'
CONCEPTS = ['sticker', 'diamond', 'heart']

GRID = 54          # adaptive canvas: 108 dp / 2 dp per icon px
VIEW = 36          # 72 dp viewport (what a mask can show), centred: icon px 9..44
VIEW0 = (GRID - VIEW) // 2
SAFE = 33          # 66 dp safe circle every mask keeps

# density bucket -> device px per icon px (108 dp canvas = 54 icon px)
ADAPTIVE = {'mdpi': 2, 'hdpi': 3, 'xhdpi': 4, 'xxhdpi': 6, 'xxxhdpi': 8}
# density bucket -> legacy 48 dp icon size in device px
LEGACY = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}

BLACK, WHITE, GREY, LIGHT, GUIDE = 0, 255, 128, 210, 170


# ---------------------------------------------------------------- source

def load_tile(name):
    with open(os.path.join(PATTERNS, name + '.txt')) as f:
        rows = [r for r in f.read().split('\n') if r]
    return rows


def load_concept(name):
    """Returns a GRID x GRID list of booleans (True = ink)."""
    path = os.path.join(HERE, name, 'icon.txt')
    fills, rows, body = {}, [], False
    w = h = None
    with open(path) as f:
        for n, line in enumerate(f.read().split('\n'), 1):
            if body:
                if line:
                    rows.append((n, line))
                continue
            if not line or line.startswith('#'):
                continue
            k = line.split()
            if k[0] == 'size':
                w, h = int(k[1]), int(k[2])
            elif k[0] == 'fill':
                fills[k[1]] = load_tile(k[2])
            elif k[0] == '---':
                body = True
            else:
                sys.exit(f'{path}:{n}: unknown header line {line!r}')
    if (w, h) != (GRID, GRID) or len(rows) != GRID:
        sys.exit(f'{path}: expected size {GRID} {GRID} and {GRID} rows, got {w}x{h}, {len(rows)} rows')
    ink = []
    for y, (n, line) in enumerate(rows):
        if len(line) != GRID:
            sys.exit(f'{path}:{n}: expected {GRID} chars, found {len(line)}')
        out = []
        for x, c in enumerate(line):
            if c == '#':
                out.append(True)
            elif c == '.':
                out.append(False)
            elif c in fills:
                t = fills[c]
                out.append(t[y % len(t)][x % len(t[0])] == '#')
            else:
                sys.exit(f'{path}:{n}:{x + 1}: char {c!r} has no fill line')
        ink.append(out)
    return ink


# ---------------------------------------------------------------- PNG

def _chunk(t, d):
    c = struct.pack('>I', len(d)) + t + d
    return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)


def _png(w, h, ihdr_tail, raw, extra=b''):
    return (b'\x89PNG\r\n\x1a\n' + _chunk(b'IHDR', struct.pack('>II', w, h) + ihdr_tail) + extra
            + _chunk(b'IDAT', zlib.compress(raw, 9)) + _chunk(b'IEND', b''))


def write_grey(path, w, h, px):
    """8-bit greyscale (previews)."""
    raw = b''.join(b'\x00' + bytes(px[y * w:(y + 1) * w]) for y in range(h))
    with open(path, 'wb') as f:
        f.write(_png(w, h, bytes([8, 0, 0, 0, 0]), raw))


def write_1bit(path, img):
    """1-bit greyscale PNG: img is rows of booleans (True = ink/black)."""
    h, w = len(img), len(img[0])
    raw = bytearray()
    for row in img:
        raw.append(0)
        for i in range(0, w, 8):
            b = 0
            for j in range(8):
                x = i + j
                white = x < w and not row[x]
                b = (b << 1) | (1 if white else 0)
            raw.append(b)
    with open(path, 'wb') as f:
        f.write(_png(w, h, bytes([1, 0, 0, 0, 0]), bytes(raw)))


def write_2bit_palette(path, img):
    """2-bit palette PNG with transparency: img cells are 0 black, 1 white, 2 transparent."""
    h, w = len(img), len(img[0])
    raw = bytearray()
    for row in img:
        raw.append(0)
        for i in range(0, w, 4):
            b = 0
            for j in range(4):
                b = (b << 2) | (row[i + j] if i + j < w else 2)
            raw.append(b)
    plte = _chunk(b'PLTE', bytes([0, 0, 0, 255, 255, 255, 255, 255, 255]))
    trns = _chunk(b'tRNS', bytes([255, 255, 0]))
    with open(path, 'wb') as f:
        f.write(_png(w, h, bytes([2, 3, 0, 0, 0]), bytes(raw), plte + trns))


# ---------------------------------------------------------------- geometry

def scale(img, k):
    return [[c for c in row for _ in range(k)] for row in img for _ in range(k)]


def crop(img, x0, y0, w, h):
    return [row[x0:x0 + w] for row in img[y0:y0 + h]]


def in_circle(x, y, n):
    """Pixel (x, y) of an n x n square lies inside the inscribed circle (centre test)."""
    r = n / 2
    return (x + 0.5 - r) ** 2 + (y + 0.5 - r) ** 2 <= r * r


def in_squircle(x, y, n):
    """Superellipse |x|^4 + |y|^4 <= 1 (close to the common launcher squircle)."""
    r = n / 2
    u, v = (x + 0.5 - r) / r, (y + 0.5 - r) / r
    return u ** 4 + v ** 4 <= 1


def legacy(ink, size, round_):
    """The 36-px viewport at the largest integer scale that fits `size`, centred on paper."""
    k = size // VIEW
    art = scale(crop(ink, VIEW0, VIEW0, VIEW, VIEW), k)
    m = (size - VIEW * k) // 2
    out = []
    for y in range(size):
        row = []
        for x in range(size):
            ax, ay = x - m, y - m
            v = 0 if (0 <= ax < VIEW * k and 0 <= ay < VIEW * k and art[ay][ax]) else 1
            if round_ and not in_circle(x, y, size):
                v = 2
            row.append(v)
        out.append(row)
    return out, k, m


# ---------------------------------------------------------------- previews

def preview(name, ink):
    """Full canvas at 4x with zone guides; circle and squircle masks at 4x and 1x on white and grey."""
    pad = 16
    big = GRID * 4
    vbig = VIEW * 4
    w = pad + big + pad + 2 * (vbig + pad)
    top = max(big, 2 * vbig + pad)
    h = pad + top + pad + VIEW + 8 + pad
    px = bytearray([LIGHT] * (w * h))

    def put(x, y, v):
        if 0 <= x < w and 0 <= y < h:
            px[y * w + x] = v

    # full canvas, 4x
    for y in range(GRID * 4):
        for x in range(GRID * 4):
            put(pad + x, pad + y, BLACK if ink[y // 4][x // 4] else WHITE)
    # guides outside the canvas: ticks at the viewport and safe-circle edges
    for e in (VIEW0 * 4, (VIEW0 + VIEW) * 4):
        for t in range(6):
            put(pad + e, pad - 1 - t, GUIDE); put(pad + e, pad + big + t, GUIDE)
            put(pad - 1 - t, pad + e, GUIDE); put(pad + big + t, pad + e, GUIDE)

    view = crop(ink, VIEW0, VIEW0, VIEW, VIEW)
    # 4x masked: row 1 on white, row 2 on grey; circle | squircle
    for row, back in ((0, WHITE), (1, GREY)):
        oy = pad + row * (vbig + pad)
        for col, test in enumerate((in_circle, in_squircle)):
            ox = pad + big + pad + col * (vbig + pad)
            for y in range(vbig + 2 * 4):
                for x in range(vbig + 2 * 4):
                    put(ox - 4 + x, oy - 4 + y, back)
            for y in range(vbig):
                for x in range(vbig):
                    if test(x, y, vbig):
                        put(ox + x, oy + y, BLACK if view[y // 4][x // 4] else WHITE)
    # 1x row: circle/squircle on white, then on grey
    oy = pad + top + pad + 4
    ox = pad + 4
    for back in (WHITE, GREY):
        for test in (in_circle, in_squircle):
            for y in range(VIEW + 8):
                for x in range(VIEW + 8):
                    put(ox - 4 + x, oy - 4 + y, back)
            for y in range(VIEW):
                for x in range(VIEW):
                    if test(x, y, VIEW):
                        put(ox + x, oy + y, BLACK if view[y][x] else WHITE)
            ox += VIEW + 8 + pad
    out = os.path.join(HERE, f'preview-{name}.png')
    write_grey(out, w, h, px)
    return out


# ---------------------------------------------------------------- resources

# The Supernote Nomad (300 dpi) draws app icons at exactly 90 x 90 px in its "My Apps" list. A
# bitmap in a `mipmap-300dpi` bucket is used there unscaled, so the Nomad gets its own design: a
# 30 x 30 grid at 3 device px per icon pixel (design/app-icon/heart-nomad/icon.txt).
NOMAD = 'heart-nomad'
NOMAD_DPI = 300
NOMAD_GRID = 30
NOMAD_K = 3


def load_nomad():
    rows = [l.rstrip('\n') for l in open(os.path.join(HERE, NOMAD, 'icon.txt'))]
    rows = [r for r in rows if r]
    assert len(rows) == NOMAD_GRID and all(len(r) == NOMAD_GRID and set(r) <= set('#.') for r in rows), \
        f'{NOMAD}/icon.txt must be {NOMAD_GRID} x {NOMAD_GRID} of # and .'
    return [[c == '#' for c in r] for r in rows]


def resources(ink):
    """Yields (path, writer, image) for every launcher resource of the picked concept."""
    # No adaptive icon (see NOMAD below): launchers that honour it rescale the layers by non-integer
    # factors, which smears the dither. Plain per-density bitmaps only.
    nomad = load_nomad()
    for f in ('ic_launcher.png', 'ic_launcher_round.png'):
        yield os.path.join(RES, f'mipmap-{NOMAD_DPI}dpi', f), write_1bit, scale(nomad, NOMAD_K)
    for d, size in LEGACY.items():
        sq, _, _ = legacy(ink, size, False)
        yield os.path.join(RES, f'mipmap-{d}', 'ic_launcher.png'), write_1bit, [[v == 0 for v in r] for r in sq]
        rd, _, _ = legacy(ink, size, True)
        yield os.path.join(RES, f'mipmap-{d}', 'ic_launcher_round.png'), write_2bit_palette, rd


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- GENERATED by design/app-icon/render.py: 1-bit bitmap foreground on a flat white background. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/white" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--previews-only', action='store_true')
    ap.add_argument('--check', action='store_true')
    a = ap.parse_args()
    concepts = {n: load_concept(n) for n in CONCEPTS}
    if a.check:
        import tempfile
        bad = 0
        with tempfile.TemporaryDirectory() as tmp:
            for path, writer, img in resources(concepts[PICK]):
                t = os.path.join(tmp, 'x.png')
                writer(t, img)
                if not os.path.exists(path) or open(path, 'rb').read() != open(t, 'rb').read():
                    print('stale:', os.path.relpath(path, ROOT)); bad += 1
        sys.exit(1 if bad else 0)
    for n, ink in concepts.items():
        print('wrote', os.path.relpath(preview(n, ink), ROOT))
    if a.previews_only:
        return
    for path, writer, img in resources(concepts[PICK]):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        writer(path, img)
        print('wrote', os.path.relpath(path, ROOT), f'{len(img[0])}x{len(img)}')


if __name__ == '__main__':
    main()
