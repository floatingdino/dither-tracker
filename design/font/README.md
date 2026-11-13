# Bitmap faces

Hand-authored 1-bit faces for the dither grid. `:app:generateFonts` turns every
`design/font/<face>/glyphs.txt` into the generated Kotlin object `type.Fonts` (rows packed into
`intArrayOf` literals — no parsing at runtime) and fails the build, with `file:line`, on anything
malformed. Draw them with `type.TextRenderer`; see `type.Font` for metrics.

| Face    | Box (w×h) | Tracking | Cap band  | Use | Charset |
|---------|-----------|----------|-----------|-----|---------|
| `ui`    | 5×9       | 1        | rows 0–6  | labels, buttons, menus, settings, log | A–Z, a–z (x-height rows 2–6, descenders 7–8), 0–9, `+ - − × / : ; . , ! ? ( ) [ ] % # & ' " < > = _ \| *`, space |
| `num-l` | 32×56     | 4        | rows 0–52 | hero life totals and commander damage ("sticker", T27) | 0–9, `+`, `-`, space |
| `num-l-pm` | 20×20  | 4        | rows 0–16 | the −/+ zone glyphs beside the hero number (T27) | `+ - −`, space |
| `num-m` | 10×14     | 2        | rows 0–13 | the small numerals at k=1: pending deltas, the commander target's life, drawer values, peeks, dice | 0–9, `+ - / :`, space |

`num-l` is the **sticker** face (T26 candidate, adopted T27): puffy bubble numerals on a ~10 px
round pen with ball terminals and a drop shadow. `num-l-pm` is its half-size `+`/`−` (5 px pen,
rounded ends, same shadow). `num-m` is the hero's little sibling since T37: the `bold` mini
sticker from `candidates/mini2/` (3-px verticals, 2-px horizontals, pointed-oval zero with a pill,
the 1's ball flag, the 4's solid wedge, knob terminals; no shadow), drawn at k=1 wherever it
appears, so a pending `−7` or a counter's `12` reads as a small copy of the life total. `ui` is
1 px. The previous thin `num-l` and `num-m` are kept in `candidates/num-l-thin/` and
`candidates/num-m-thin/` (not built).

`num-m` is 10 px wide: `999` at its tracking is 34 px, 2 px more than a Nomad drawer cell's value
box, so the drawer closes a value's tracking to 1 px when (and only when) it would not fit
(`DrawerLayout.valueTracking`).

**Drop shadows.** In a glyph, `:` marks the shadow *mask*: paper in the glyph, filled at draw
time by `TextRenderer` with `Role.SHADOW` (50% checker) at global pattern phase (SPEC rule 3),
before any ink. The mask turns with the text, so the shadow falls down-right in the reader's
frame. Convention: 3 px down-right of the body, a 1 px paper keyline, never inside a counter.

Any face can be drawn at an integer glyph scale `k` (each font px → k dither px → k·S device px).
At S=4 on the Nomad, `num-l` k=1 is 224 device px tall (body 212 px, 18 mm); `ui` k=1 has a
2.4 mm cap height.

## File format (`<face>/glyphs.txt`)

```
# comments and blank lines are allowed in the header only
face num-m          # must equal the directory name
size 8 14           # glyph box width (1..32) and height; every glyph is exactly this size
tracking 2          # default paper gap between glyph boxes
cap 0 14            # cap band: top row and height (vertical centring aligns this band)

--- 0               # glyph header: one character ...
..####..            # exactly <height> rows of exactly <width> chars: '#' ink, '.' paper, ':' shadow mask
...
--- U+2212          # ... or U+XXXX (uppercase hex; required for space: U+0020) ...
--- notdef          # ... or notdef: the box drawn for unknown characters (required)
```

Blank lines may separate glyphs, but not interrupt one. No comments after the first `---` (rows
start with `#`). Lookup falls back lowercase → uppercase and `−` ↔ `-` before using `notdef`.

Rules of thumb when editing: keep every stroke the face's pen width (1 px; 2 px for `num-l`), keep
curves to regular step patterns (e.g. 3-2-1-2-3) rather than Bresenham noise, and keep shared
features (bowls, corners, the zero's dot) identical across glyphs. Check on the device at S=4.
