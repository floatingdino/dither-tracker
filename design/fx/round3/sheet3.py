#!/usr/bin/env python3
"""Review sheets for the round-three candidates (T28f). Stdlib only.

Usage: python3 design/fx/round3/sheet3.py [--number 37] [--out DIR] [--scale 4]

Writes, at true Nomad pixels (S = 4) unless --scale says otherwise:
  design/captures/t28f-<group>.png   one per group, every candidate as a strip over a sticker total
                                     on the quad (4-player) tile: clean, f1..fN, flips per beat
  design/captures/t28f-narrow.png    every candidate on the narrow 155x175 tile (5/6 players)
Each row's label gives the role, the trigger, the holds and the tiers verdict computed here
(furniture or diamond hits, and clipping of a 3-digit total on the narrow tile).
"""
import argparse
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
sys.path.insert(0, HERE)
import author3  # noqa: E402
import render3  # noqa: E402

GROUPS = [('damage', 'damage variety'), ('heal', 'heal variety'), ('counter', 'counters'),
          ('state', 'state changes'), ('table', 'table moments')]


def holds(path):
    for line in open(os.path.join(path, 'meta.txt')):
        if line.startswith('hold_ms'):
            return line.split()[1:-1]
    return []


def tiers(path):
    q = render3.furniture_hits(path, render3.QUAD)
    n = render3.furniture_hits(path, render3.NARROW)
    c = max(render3.sequence(path, render3.NARROW, '108')[1])
    ok = lambda b: 'ok' if not b else 'hits'
    return f'quad {ok(q)}  narrow {ok(n or c)}'


def short(s, n=43):
    return s if len(s) <= n else s[:n - 1] + '.'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--number', default='37')
    ap.add_argument('--out', default=os.path.join(ROOT, 'design', 'captures'))
    ap.add_argument('--scale', type=int, default=4)
    ap.add_argument('--only', nargs='*')
    a = ap.parse_args()
    render3.S = a.scale
    allrows = []
    for g, title in GROUPS:
        if a.only and g not in a.only:
            continue
        rows = []
        for name, info in author3.EFFECTS.items():
            if info['group'] != g:
                continue
            p = os.path.join(HERE, name)
            lab = [f'{info["size"]} / {info["role"]}', short(info['trigger']),
                   'hold ' + ' '.join(holds(p)), tiers(p)]
            rows.append((p, name, lab))
        allrows += rows
        out = os.path.join(a.out, f't28f-{g}.png')
        render3.sheet(rows, render3.QUAD, out, f't28f {title}: quad tile 233x175, s=4', a.number)
        print('wrote', out)
    if not a.only or 'narrow' in a.only:
        out = os.path.join(a.out, 't28f-narrow.png')
        rows = allrows or [(os.path.join(HERE, n), n, []) for n in author3.EFFECTS]
        render3.sheet(rows, render3.NARROW, out, 't28f all candidates: narrow tile 155x175, s=4',
                      a.number, cols=3)
        print('wrote', out)


if __name__ == '__main__':
    main()
