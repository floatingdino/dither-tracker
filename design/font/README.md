# Bitmap faces

Hand-authored 1-bit faces for the dither grid. `:app:generateFonts` turns every
`design/font/<face>/glyphs.txt` into the generated Kotlin object `type.Fonts` (rows packed into
`intArrayOf` literals — no parsing at runtime) and fails the build, with `file:line`, on anything
malformed. Draw them with `type.TextRenderer`; see `type.Font` for metrics.

| Face    | Box (w×h) | Tracking | Cap band  | Use | Charset |
|---------|-----------|----------|-----------|-----|---------|
| `ui`    | 5×9       | 1        | rows 0–6  | labels, buttons, menus, settings, log | A–Z, a–z (x-height rows 2–6, descenders 7–8), 0–9, `+ - − × / : ; . , ! ? ( ) [ ] % # & ' " < > = _ \| *`, space |
| `num-l` | 16×28     | 4        | rows 0–27 | hero life totals | 0–9, `+`, `-`, space |
| `num-m` | 8×14      | 2        | rows 0–13 | counters, commander damage, pending deltas, dice | 0–9, `+ - / :`, space |

All three share one skeleton language: dotted zero, round bowls built from midpoint-circle
corners, a slope-2 diagonal on the `4`, a 3-rows-per-step `7`. `num-l` is that skeleton drawn with
a 2×2 pen (2 px strokes, 45° runs 3 px wide); `num-m` and `ui` are 1 px.

Any face can be drawn at an integer glyph scale `k` (each font px → k dither px → k·S device px).
At S=4 on the Nomad, `num-l` k=1 is 112 device px tall (the Figma life total); `ui` k=1 has a
2.4 mm cap height.

## File format (`<face>/glyphs.txt`)

```
# comments and blank lines are allowed in the header only
face num-m          # must equal the directory name
size 8 14           # glyph box width (1..32) and height; every glyph is exactly this size
tracking 2          # default paper gap between glyph boxes
cap 0 14            # cap band: top row and height (vertical centring aligns this band)

--- 0               # glyph header: one character ...
..####..            # exactly <height> rows of exactly <width> chars: '#' ink, '.' paper
...
--- U+2212          # ... or U+XXXX (uppercase hex; required for space: U+0020) ...
--- notdef          # ... or notdef: the box drawn for unknown characters (required)
```

Blank lines may separate glyphs, but not interrupt one. No comments after the first `---` (rows
start with `#`). Lookup falls back lowercase → uppercase and `−` ↔ `-` before using `notdef`.

Rules of thumb when editing: keep every stroke the face's pen width (1 px; 2 px for `num-l`), keep
curves to regular step patterns (e.g. 3-2-1-2-3) rather than Bresenham noise, and keep shared
features (bowls, corners, the zero's dot) identical across glyphs. Check on the device at S=4.
