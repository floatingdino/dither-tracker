#!/usr/bin/env python3
"""T53 contact sheet: every arrangement's play screen, one dither px per pixel, labelled.

    python3 tools/contact_sheet_t53.py <dir of <id>.png captures> <out.png>

Each capture is a 1872x1404 screencap drawn at S = 4 (with Scale AUTO 4-dinner picks S = 3 and
6-dinner S = 2 on their own: their tiles are not comfortable at 4). It is sampled back to its dither
grid (1 px = 1 dither px, an exact integer reduction; S = 2 at every other dither px), framed, and
labelled in the app's own `ui` bitmap face (design/font/ui/glyphs.txt) at 2x. Output: an 8-bit greyscale PNG with only 0 and 255.
"""
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from check_1bit import load_png  # noqa: E402

ORDER = [
    # id, tile at its S, tier, offered in the picker at S = 4
    ("2-across", "468x175 full", True), ("2-ends", "351x233 full", True),
    ("2-side", "233x351 full", True), ("2-corner", "233x234 full", True),
    ("3-tee", "292x175 full", True), ("3-pyramid", "233x175 full", True),
    ("3-side", "155x351 narrow", True), ("3-end", "148x170 narrow", True),
    ("4-grid", "233x175 full", True), ("4-dinner", "S3: 252x185 full", False),
    ("5-three-two", "155x175 narrow", True), ("5-end", "148x170 narrow", True),
    ("6-three-three", "155x175 narrow", True), ("6-dinner", "S2: 207x259 narrow, 1/2", False),
]
NEW = {"2-side", "2-corner", "3-side", "3-end"}
# With Scale AUTO the table steps S down until the layout is comfortable: these two never are at 4.
SCALE = {"4-dinner": 3, "6-dinner": 2}
SLOT_W, SLOT_H = 624, 468   # the S = 3 grid; S = 4 grids (468x351) are centred in it
PAD, LABEL_K, COLS = 16, 2, 4


def font():
    glyphs, cur, rows = {}, None, []
    for line in open(os.path.join(HERE, "..", "design", "font", "ui", "glyphs.txt")):
        line = line.rstrip("\n")
        if line.startswith("--- "):
            if cur is not None:
                glyphs[cur] = rows
            name = line[4:]
            cur = " " if name == "U+0020" else (chr(int(name[2:], 16)) if name.startswith("U+") else name)
            rows = []
        elif cur is not None and line and line[0] in ".#:":
            rows.append(line)
    if cur is not None:
        glyphs[cur] = rows
    return glyphs


def main(src, out):
    g = font()
    gw, gh = 5, 9
    label_h = gh * LABEL_K + 8
    rows_n = (len(ORDER) + COLS - 1) // COLS
    W = COLS * (SLOT_W + PAD) + PAD
    H = rows_n * (SLOT_H + label_h + PAD) + PAD
    px = bytearray(b"\xff" * (W * H))

    def put(x, y, ink):
        if 0 <= x < W and 0 <= y < H:
            px[y * W + x] = 0 if ink else 255

    def text(s, x, y):
        for ch in s:
            rows = g.get(ch) or g.get("?")
            for ry, r in enumerate(rows[:gh]):
                for rx, c in enumerate(r[:gw]):
                    if c == "#":
                        for dy in range(LABEL_K):
                            for dx in range(LABEL_K):
                                put(x + rx * LABEL_K + dx, y + ry * LABEL_K + dy, True)
            x += (gw + 1) * LABEL_K

    for i, (ident, tile, offered) in enumerate(ORDER):
        cx = PAD + (i % COLS) * (SLOT_W + PAD)
        cy = PAD + (i // COLS) * (SLOT_H + label_h + PAD)
        w, h, ch, rows = load_png(os.path.join(src, ident + ".png"))
        s = SCALE.get(ident, 4)
        step = max(s, 3)  # S = 2 is shown at a quarter of the device size (every other dither px), labelled
        dw, dh = w // step, h // step
        ox, oy = cx + (SLOT_W - dw) // 2, cy + (SLOT_H - dh) // 2
        for y in range(dh):
            row = rows[y * step + s // 2]
            for x in range(dw):
                put(ox + x, oy + y, row[(x * step + s // 2) * ch] < 128)
        for x in range(dw + 2):  # a 1 px frame round the screen
            put(ox - 1 + x, oy - 1, True); put(ox - 1 + x, oy + dh, True)
        for y in range(dh + 2):
            put(ox - 1, oy - 1 + y, True); put(ox + dw, oy - 1 + y, True)
        label = f"{ident}  {tile}" + ("  NEW" if ident in NEW else "") + ("" if offered else "  (not at S4)")
        text(label, cx, cy + SLOT_H + 4)

    raw = b"".join(b"\x00" + bytes(px[y * W:(y + 1) * W]) for y in range(H))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 0, 0, 0, 0)) + \
        chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    open(out, "wb").write(png)
    print(f"wrote {out} ({W}x{H})")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
