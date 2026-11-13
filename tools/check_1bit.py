#!/usr/bin/env python3
"""Pixel-purity + dither-grid alignment checker for Dither Tracker captures (stdlib only).

usage: check_1bit.py IMAGE.png [--scale S] [--max-report N]

1. Purity: fails on any pixel that is not exactly #000000 or #FFFFFF (and fully opaque).
2. With --scale S: computes the grid like DitherGrid (size = floor(dim / S), leftover split
   centred, odd pixel right/bottom) and fails unless every S×S dither cell is a single colour and
   each margin strip is uniform — i.e. every black/white run boundary lies on the S-grid.

Exit status 0 = pass, 1 = fail, 2 = usage / decode error.
"""
import struct
import sys
import zlib


def load_png(path):
    """Returns (width, height, channels, rows) with rows as unfiltered bytes per scanline."""
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('not a PNG')
    i, idat, w = 8, [], None
    while i < len(data):
        n, t = struct.unpack('>I4s', data[i:i + 8])
        body = data[i + 8:i + 8 + n]
        i += 12 + n
        if t == b'IHDR':
            w, h, depth, ctype, _, _, interlace = struct.unpack('>IIBBBBB', body)
            if depth != 8 or interlace != 0 or ctype not in (0, 2, 4, 6):
                raise ValueError(f'unsupported PNG (depth={depth} type={ctype} interlace={interlace})')
        elif t == b'IDAT':
            idat.append(body)
        elif t == b'IEND':
            break
    ch = {0: 1, 2: 3, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(b''.join(idat))
    stride = w * ch
    rows, prev, k = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[k]
        line = bytearray(raw[k + 1:k + 1 + stride])
        k += 1 + stride
        if f == 1:
            for x in range(ch, stride):
                line[x] = (line[x] + line[x - ch]) & 255
        elif f == 2:
            line = bytearray((a + b) & 255 for a, b in zip(line, prev))
        elif f == 3:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                b = prev[x]
                c = prev[x - ch] if x >= ch else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else (b if pb <= pc else c))) & 255
        rows.append(bytes(line))
        prev = line
    return w, h, ch, rows


def grid(dim, s):
    n = dim // s
    left = (dim - n * s) // 2
    return n, left, dim - n * s - left


def main(argv):
    args = argv[1:]
    if not args or args[0].startswith('-'):
        print(__doc__)
        return 2
    path, scale, max_report = args[0], None, 10
    i = 1
    while i < len(args):
        if args[i] == '--scale':
            scale = int(args[i + 1]); i += 2
        elif args[i] == '--max-report':
            max_report = int(args[i + 1]); i += 2
        else:
            print(f'unknown arg {args[i]}'); return 2
    try:
        w, h, ch, rows = load_png(path)
    except Exception as e:  # noqa: BLE001
        print(f'FAIL decode {path}: {e}')
        return 2

    problems = []
    lum = []  # per row: bytes of 0/255
    black = white = 0
    for y, r in enumerate(rows):
        if ch >= 3:
            c0, c1, c2 = r[0:w * ch:ch], r[1:w * ch:ch], r[2:w * ch:ch]
            if not (c0 == c1 == c2):
                for x in range(w):
                    if not (c0[x] == c1[x] == c2[x]):
                        problems.append(f'impure pixel ({x},{y}) rgb=({c0[x]},{c1[x]},{c2[x]})'); break
        else:
            c0 = r[0:w * ch:ch]
        if ch in (2, 4):
            alpha = r[ch - 1:w * ch:ch]
            if alpha.translate(None, b'\xff'):
                x = next(i for i, a in enumerate(alpha) if a != 255)
                problems.append(f'non-opaque pixel ({x},{y}) alpha={alpha[x]}')
        bad = c0.translate(None, b'\x00\xff')
        if bad:
            x = next(i for i, v in enumerate(c0) if v not in (0, 255))
            problems.append(f'impure pixel ({x},{y}) value={c0[x]}')
        b = c0.count(0)
        black += b
        white += w - b
        lum.append(c0)
        if len(problems) >= max_report:
            break

    if not problems and scale:
        s = scale
        gw, ml, mr = grid(w, s)
        gh, mt, mb = grid(h, s)
        print(f'grid S={s}: {gw}x{gh} dither px, margins L{ml} R{mr} T{mt} B{mb}')
        x_end = ml + gw * s
        margin_vals = set()
        for y in range(h):
            r = lum[y]
            margin_vals.update(r[:ml]); margin_vals.update(r[x_end:])
            if y < mt or y >= mt + gh * s:
                margin_vals.update(r)
                continue
            cy = (y - mt) // s
            y0 = mt + cy * s
            if y != y0:
                if r[ml:x_end] != lum[y0][ml:x_end]:
                    x = next(i for i in range(ml, x_end) if r[i] != lum[y0][i])
                    problems.append(f'off-grid edge: row {y} differs from cell top row {y0} at x={x} (cell {(x - ml) // s},{cy})')
            else:
                sample = r[ml:x_end:s]
                for k in range(1, s):
                    if r[ml + k:x_end:s] != sample:
                        sub = r[ml + k:x_end:s]
                        c = next(i for i in range(gw) if sub[i] != sample[i])
                        problems.append(f'off-grid edge: row {y} cell ({c},{cy}) not uniform at x={ml + c * s + k}')
                        break
            if len(problems) >= max_report:
                break
        if len(margin_vals) > 1:
            problems.append('margins are not a single colour')

    total = w * h
    if problems:
        print(f'FAIL {path} ({w}x{h}):')
        for p in problems[:max_report]:
            print('  ' + p)
        return 1
    print(f'PASS {path} ({w}x{h}): 1-bit pure ({100 * black / total:.1f}% black)'
          + (f', aligned to S={scale}' if scale else ''))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
