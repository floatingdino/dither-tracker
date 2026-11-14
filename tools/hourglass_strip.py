#!/usr/bin/env python3
"""T66: the hourglass pulse as a 4x strip (stdlib only).

Rows 1-3 — option A (shipped, design/icons/hourglass-1..4) on the real diamond, cropped from the JVM
renders `HourglassPulseTest` writes (app/build/purity/t66/pulse-<state>-<speed>-<i>.png, 1 px per
dither px, Nomad 4p S = 4): plain (MENU dither), day (paper), night (ink); glyph → FAST frames →
glyph, then the SLOW frames. Row 4-5 — option B (design/icons/candidates/t66/hourglass-b-1..4) as
art, on paper and inverted. Every source pixel becomes 4x4, so the strip is on the S = 4 grid.

    ./gradlew :app:testDebugUnitTest --tests '*HourglassPulseTest'
    python3 tools/hourglass_strip.py design/captures/t66-hourglass-strip.png
"""
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RENDERS = os.path.join(ROOT, "app/build/purity/t66")
CAND = os.path.join(ROOT, "design/icons/candidates/t66")
S = 4
# The diamond (213..254 x 155..196 at S = 4, 4p) with a 2 px margin.
X0, Y0, X1, Y1 = 211, 153, 257, 199
GAP = 4


def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8
    idat = b""
    w = h = depth = ctype = 0
    while pos < len(data):
        n, = struct.unpack(">I", data[pos:pos + 4])
        t = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
        elif t == b"IDAT":
            idat += body
        pos += 12 + n
    assert depth == 1 and ctype == 0, (depth, ctype)
    raw = zlib.decompress(idat)
    stride = (w + 7) // 8
    rows = []
    prev = bytearray(stride)
    i = 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        if f == 2:
            for k in range(stride):
                line[k] = (line[k] + prev[k]) & 0xFF
        elif f != 0:
            raise ValueError("filter %d" % f)
        prev = line
        rows.append([1 if (line[x >> 3] >> (7 - (x & 7))) & 1 else 0 for x in range(w)])
    return rows  # 1 = white


def crop(rows):
    return [r[X0:X1] for r in rows[Y0:Y1]]


def art(path, invert):
    g = [[(0 if c == "#" else 1) for c in line.rstrip("\n")] for line in open(path)]
    pad = (Y1 - Y0 - 16) // 2
    w = X1 - X0
    bg = 0 if invert else 1
    out = [[bg] * w for _ in range(Y1 - Y0)]
    for y in range(16):
        for x in range(16):
            v = g[y][x]
            out[pad + y][pad + x] = (1 - v) if invert else v
    return out


def write_png(path, cells):
    h = Y1 - Y0
    cw = X1 - X0
    rows_out = []
    for row in cells:
        n = len(row)
        width = n * cw + (n - 1) * GAP
        block = [[1] * width for _ in range(h)]
        for i, c in enumerate(row):
            for y in range(h):
                for x in range(cw):
                    block[y][i * (cw + GAP) + x] = c[y][x]
        rows_out.append(block)
    W = max(len(b[0]) for b in rows_out)
    full = []
    for j, b in enumerate(rows_out):
        for line in b:
            full.append(line + [1] * (W - len(line)))
        if j < len(rows_out) - 1:
            full.extend([[1] * W for _ in range(GAP)])
    H = len(full)
    stride = W * S
    raw = bytearray()
    for line in full:
        big = bytearray()
        for v in line:
            big.extend([255 if v else 0] * S)
        for _ in range(S):
            raw.append(0)
            raw.extend(big)

    def chunk(t, b):
        return struct.pack(">I", len(b)) + t + b + struct.pack(">I", zlib.crc32(t + b) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", stride, H * S, 8, 0, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    open(path, "wb").write(png)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "t66-hourglass-strip.png"
    rows = []
    for state in ("UNSET", "DAY", "NIGHT"):
        base = crop(read_png(os.path.join(RENDERS, "pulse-%s-base.png" % state)))
        fast = [crop(read_png(os.path.join(RENDERS, "pulse-%s-fast-%d.png" % (state, i)))) for i in range(4)]
        slow = [crop(read_png(os.path.join(RENDERS, "pulse-%s-slow-%d.png" % (state, i)))) for i in range(3)]
        rows.append([base] + fast + [base, base] + slow + [base])
    b = [os.path.join(CAND, "hourglass-b-%d.txt" % i) for i in range(1, 5)]
    rows.append([art(p, False) for p in b])
    rows.append([art(p, True) for p in b])
    write_png(out, rows)
    print("wrote", out)


if __name__ == "__main__":
    main()
