#!/usr/bin/env python3
"""Validate design/icons/*.txt (stdlib only). Exit code 1 on any failure.

Checks:
  * exactly 16 lines of exactly 16 chars, only '#' and '.', trailing newline
  * nothing drawn in the outer 1px margin (art lives in the inner 14x14)
  * no blank icons and no two identical icons

Usage: python3 design/icons/validate.py [--report]
  --report  also print each icon's ink bounding box and its offset from the
            optical centre (7.5, 7.5) as a centring aid.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    report = '--report' in sys.argv
    errors = []
    seen = {}
    names = sorted(f for f in os.listdir(HERE) if f.endswith('.txt'))
    for fn in names:
        name = fn[:-4]
        with open(os.path.join(HERE, fn), newline='') as f:
            text = f.read()
        if not text.endswith('\n') or text.endswith('\n\n'):
            errors.append(f'{fn}: must end with exactly one trailing newline')
        rows = text.split('\n')[:-1] if text.endswith('\n') else text.split('\n')
        if len(rows) != 16:
            errors.append(f'{fn}: {len(rows)} lines, expected 16')
            continue
        bad = False
        for y, row in enumerate(rows):
            if len(row) != 16:
                errors.append(f'{fn}:{y + 1}: {len(row)} chars, expected 16'); bad = True
            if set(row) - {'#', '.'}:
                errors.append(f'{fn}:{y + 1}: characters other than # and .'); bad = True
        if bad:
            continue
        ink = [(x, y) for y, row in enumerate(rows) for x, c in enumerate(row) if c == '#']
        if not ink:
            errors.append(f'{fn}: blank icon')
            continue
        edge = [(x, y) for x, y in ink if x in (0, 15) or y in (0, 15)]
        if edge:
            errors.append(f'{fn}: touches the 1px margin at {edge[:4]}')
        key = '\n'.join(rows)
        if key in seen:
            errors.append(f'{fn}: identical to {seen[key]}.txt')
        seen[key] = name
        if report:
            xs = [p[0] for p in ink]; ys = [p[1] for p in ink]
            dx = (min(xs) + max(xs)) / 2 - 7.5
            dy = (min(ys) + max(ys)) / 2 - 7.5
            print(f'{name:16} bbox x{min(xs)}-{max(xs)} y{min(ys)}-{max(ys)}  '
                  f'centre offset ({dx:+.1f}, {dy:+.1f})')
    for e in errors:
        print('FAIL', e)
    print(f'{len(names)} icons checked, {len(errors)} problem(s)')
    sys.exit(1 if errors else 0)


if __name__ == '__main__':
    main()
