#!/usr/bin/env python3
"""Render every design/icons/*.txt into a contact-sheet PNG (stdlib only).

Usage: python3 design/icons/render.py [--scale N] [--cols N] [--src DIR] [--out DIR] [--name BASE]

  --src DIR   render the *.txt files in DIR instead (e.g. a folder of candidate
              sketches named poison-a.txt, poison-b.txt ...); default: this folder
  --name BASE output base name (default contact-sheet -> contact-sheet.png and
              contact-sheet-inverted.png)

Writes contact-sheet.png and contact-sheet-inverted.png next to this script.
Each cell shows the icon at N x (nearest-neighbour, default 8) inside a thin
grey frame marking the 16x16 bounds, plus a 1x preview and its name.
The grey frame/labels are review aids only; icon pixels are pure black/white.
"""
import argparse
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))

# 3x5 pixel font for labels (a-z and '-').
FONT = {
    'a': '.#.#.#####.##.#',
    'b': '##.#.###.#.###.',
    'c': '.###..#..#...##',
    'd': '##.#.##.##.###.',
    'e': '####..##.#..###',
    'f': '####..##.#..#..',
    'g': '.###..#.##.#.##',
    'h': '#.##.#####.##.#',
    'i': '###.#..#..#.###',
    'j': '..#..#..##.#.#.',
    'k': '#.##.###.#.##.#',
    'l': '#..#..#..#..###',
    'm': '#.########.##.#',
    'n': '##.#.##.##.##.#',
    'o': '.#.#.##.##.#.#.',
    'p': '##.#.###.#..#..',
    'q': '.#.#.##.##.#.##',
    'r': '##.#.###.#.##.#',
    's': '.###...#...###.',
    't': '###.#..#..#..#.',
    'u': '#.##.##.##.####',
    'v': '#.##.##.##.#.#.',
    'w': '#.##.########.#',
    'x': '#.##.#.#.#.##.#',
    'y': '#.##.#.#..#..#.',
    'z': '###..#.#.#..###',
    '-': '......###......',
}

WHITE, BLACK, GREY, PAPER = 255, 0, 170, 230


def load_icons(src=HERE):
    icons = []
    for fn in sorted(os.listdir(src)):
        if fn.endswith('.txt'):
            with open(os.path.join(src, fn)) as f:
                rows = f.read().split('\n')[:16]
            icons.append((fn[:-4], rows))
    return icons


def write_png(path, w, h, px):
    raw = b''.join(b'\x00' + bytes(px[y * w:(y + 1) * w]) for y in range(h))

    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)


def render(icons, scale, cols, invert):
    pad = 12
    big = 16 * scale
    label_h = 16 + 10
    cw, ch = big + pad * 2, big + pad + label_h + pad
    rows = (len(icons) + cols - 1) // cols
    w, h = cw * cols, ch * rows
    px = bytearray([PAPER] * (w * h))

    def put(x, y, v):
        if 0 <= x < w and 0 <= y < h:
            px[y * w + x] = v

    def blit(rows_, ox, oy, s):
        for y, line in enumerate(rows_):
            for x, c in enumerate(line[:16]):
                on = (c == '#') != invert
                v = BLACK if on else WHITE
                for yy in range(s):
                    for xx in range(s):
                        put(ox + x * s + xx, oy + y * s + yy, v)
        # grey frame just outside the 16x16 bounds
        for i in range(-1, 16 * s + 1):
            put(ox + i, oy - 1, GREY); put(ox + i, oy + 16 * s, GREY)
            put(ox - 1, oy + i, GREY); put(ox + 16 * s, oy + i, GREY)

    def text(s, ox, oy, k=2):
        for ci, chr_ in enumerate(s):
            g = FONT.get(chr_)
            if not g:
                continue
            g = g.ljust(15, '.')
            for i, c in enumerate(g):
                if c == '#':
                    for yy in range(k):
                        for xx in range(k):
                            put(ox + ci * 4 * k + (i % 3) * k + xx, oy + (i // 3) * k + yy, BLACK)

    for i, (name, rows_) in enumerate(icons):
        cx, cy = (i % cols) * cw + pad, (i // cols) * ch + pad
        blit(rows_, cx, cy, scale)
        ly = cy + big + pad // 2
        blit(rows_, cx, ly, 1)
        text(name, cx + 22, ly + 3)
    return w, h, px


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--scale', type=int, default=8)
    ap.add_argument('--cols', type=int, default=10)
    ap.add_argument('--out', default=HERE)
    ap.add_argument('--src', default=HERE)
    ap.add_argument('--name', default='contact-sheet')
    a = ap.parse_args()
    icons = load_icons(a.src)
    for inv, name in ((False, a.name + '.png'), (True, a.name + '-inverted.png')):
        w, h, px = render(icons, a.scale, a.cols, inv)
        write_png(os.path.join(a.out, name), w, h, px)
        print('wrote', os.path.join(a.out, name), f'{w}x{h}', f'{len(icons)} icons')


if __name__ == '__main__':
    main()
