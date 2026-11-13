#!/usr/bin/env python3
"""Sheet of the shipped effects (T28c): every effect in design/fx/ drawn by the round-two renderer
(design/fx/options/render2.py: sticker numerals, a full seat tile with its furniture, true Nomad
pixels at S=4), each strip starting with the clean tile. Stdlib only; render2.py is imported, not
changed, and its own options sheet is left alone.

Usage: python3 design/fx/sheet.py [--number 37]   -> design/fx/sheet.png, flips printed per effect
"""
import argparse
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'options'))
import render2  # noqa: E402

# Policy order (app/.../fx/FxPolicy.kt), then the alternates.
ROWS = [
    ('pow', 'damage'), ('crack', 'damage'), ('claw-cut', 'damage'), ('quake', 'damage|tile'),
    ('focus', 'big hit|tile'),
    ('twinkle', 'heal'), ('rise', 'heal|tile'), ('sprout', 'heal'), ('heart', 'heal'),
    ('sunrise', 'big heal|tile'), ('vines', 'big heal|tile'),
    ('ko-shatter', 'ko|tile'), ('fizzle', 'ko|compact'), ('winner', 'last standing|tile'),
    ('fault', 'damage|tile, unused'),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--number', default='37')
    a = ap.parse_args()
    render2.effect_path = lambda name: os.path.join(HERE, name)
    shipped = sorted(n for n in os.listdir(HERE) if os.path.isfile(os.path.join(HERE, n, 'meta.txt')))
    assert sorted(n for n, _ in ROWS) == shipped, (shipped, ROWS)
    for n, _ in ROWS:
        fl = render2.flips(n, a.number)
        print(f'{n:12s} flips {fl} total {sum(fl)}  furniture/diamond hits {render2.furniture_hits(n, a.number)}')
    render2.sheet(ROWS, a.number, os.path.join(HERE, 'sheet.png'), render2.S)
    print('wrote design/fx/sheet.png')


if __name__ == '__main__':
    main()
