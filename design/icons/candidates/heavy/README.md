# T40 heavy icons (candidates)

A weight pass over the icon set. The icons were too thin next to the sticker numerals (num-l ~8 px strokes,
mini2/bold 3 px verticals, the ± at a 5 px pen). These are **candidates only**. `design/icons/*.txt` is
untouched, and the coordinator promotes these files in T41.

- `*.txt` are the recommended replacements, one per changed icon, with the same names as the real icons.
- `alts/close-b.txt` and `alts/close-c.txt` are the two alternative X's.
- [`before-after.png`](before-after.png) / [`before-after-inverted.png`](before-after-inverted.png): the
  current icon and the heavy one at 8×, both at 1× beside a mini2/bold "2" (the peek/drawer pairing), and
  the heavy one at 4× (device size).
- [`halo-mocks.png`](halo-mocks.png): close / menu / more on the 41×41 centre diamond, on a D6 fill and on
  the gem's solid black centre, plus `more` on a D6 drawer tab. Each is shown with today's square paper
  backing, a 1 px contour halo and a 2 px contour halo.
- [`halo-sheet.png`](halo-sheet.png): every candidate on D6, shown plain, with a literal 1 px halo, and
  with a 1 px and a 2 px silhouette halo.
- [`contact-sheet.png`](contact-sheet.png) / [`-inverted`](contact-sheet-inverted.png): the plain
  `render.py` sheet of this folder.

```sh
python3 design/icons/candidates/heavy/tools.py validate --report   # format, margin, centring, halo checks
python3 design/icons/candidates/heavy/tools.py sheet               # before-after(.png, -inverted.png)
python3 design/icons/candidates/heavy/tools.py mocks               # halo-mocks.png, halo-sheet.png
python3 design/icons/render.py --src design/icons/candidates/heavy --out design/icons/candidates/heavy --name contact-sheet
```

`tools.py` uses the stdlib only. It imports `render.py` read-only for its PNG writer and font. Its
`validate` runs `validate.py`'s checks on this folder and `alts/`. It also checks the centring (to within
½ px) and fails on a candidate identical to a different real icon.

## Weight rule

- **Orthogonal strokes are 2 px minimum.** Outlines (rings, pages, stones, clouds, card edges) are 2 px.
  The UI glyphs `plus`, `minus` and `menu` go to 3–4 px with rounded ends (corner pixels cut), echoing
  the ± zone glyphs.
- **Diagonal strokes use runs of 3 or more per row** (≈2.1 px true width) instead of today's 2-runs
  (≈1.4 px). The UI diagonals (`close`, `check`, `chevron-*`) use runs of 4 (≈2.8 px), which matches a
  3 px orthogonal stroke optically.
- **1 px lines survive only as details inside a solid or a 2 px frame:** the text lines on `history`,
  the jewel slots on `monarch`, the tufts on `dead`.
- **Where an outline couldn't take 2 px walls and still fit its details, the icon became a solid
  silhouette with paper knockouts:** `dice` (pips), `new-game` (front card with a plus), `monarch`
  (band and jewels), `night` (solid crescent, separate dot stars).
- Result: ink counts rose by about 1.3–2.2× (see `validate --report`, "was"). Outline icons now sit at a
  density of about 0.35–0.6 and solids at about 0.55–0.85. That is the same family as `life`, `energy` and
  `commander`.

## Per icon

| Icon | Change |
|---|---|
| `close` | **Pick (A):** a 12×12 X, 4-px diagonal runs (≈2.8 px), blunt chamfered ends |
| `alts/close-b` | 5-px runs (≈3.5 px): the full "sticker" weight. The crossing turns into a blob at 1×. |
| `alts/close-c` | A compact 10×10 X with 4-px runs. Dense, but small on the diamond. |
| `menu` | Three 3-px bars with rounded ends, 2 px gaps (was 2-px bars) |
| `more` | 3×3 dots with 2 px gaps (was 2×2 dots) |
| `plus` / `minus` | A 4-px pen with rounded ends, 12 px long, like a small ± |
| `check` | 4-px runs, rounded vertex, centred |
| `chevron-*` | 4-px diagonal runs, 2-px blunt tip (one drawing, mirrored and transposed) |
| `undo` / `redo` | 2-px hook body with a bigger 4×8 solid head. The hook's counter is 4 px tall, so a 2 px halo papers it cleanly. |
| `reset` | 2-px ring, gap at the top right, 6-wide solid head pointing up |
| `refresh` | 2-px oval with two solid heads, point-symmetric (top-right pointing down, bottom-left pointing up) |
| `rotate` | A 2-px-walled phone with a solid chin and a knocked-out home button. 2-px arc, 6-wide head. 2 px gaps from the phone. |
| `scale` | Shaft goes from 2-px runs to 3-px runs. Heads unchanged. |
| `turn` | Bar 2 → 3 px. Triangle moved 1 px left to keep a 2 px gap. |
| `storm` | 2-px cloud outline and a bigger solid bolt |
| `timer` | 2-px ring, crown, solid wedge |
| `dice` | Solid rounded die with 2×2 paper pips (2-px walls left no room for the pips) |
| `history` | 2-px page, solid fold, 1-px text lines kept as detail |
| `new-game` | Solid front card with a paper plus. The back card is a 2-px "L" behind a 1 px keyline. |
| `dead` | 2-px tombstone, solid cross, tufted ground that extends past the stone |
| `ring` | Band thickened so the diagonals are 2 px too (top and bottom 2 rows). Gem and sparkle kept. |
| `coin` | 2-px face rim, rounded 4×4 boss, solid edge, 1 px seam kept |
| `invert` | 2-px rim on the white half |
| `tickets` | 2-px frame, solid stub with a notch and a toothed perforation, a 5×5 star |
| `monarch` | **Filled** (designer, 2026-11-14: "can be filled, mostly I liked the silhouettes"). Same silhouette and three points. The band line and three jewel slots are paper knockouts. |
| `night` | **Filled** crescent with the same outer and inner curves. The 2×2 star and the dot star stay as separate marks. |

Unchanged on purpose: `poison`, `commander`, `partner`, `life`, `energy`, `experience`, `rads` (as briefed),
and `settings`, `players`, `citys-blessing`, `commander-tax`, `initiative`, `day` (already solid).
`generic-counter` is also unchanged. Its ring is already 2 px. A solid token with a paper "+1" was tried
and dropped, because the "+" turned into a blob at 1×.

## Halo findings (for T41)

- **Use a silhouette halo, not a literal one.** Fill the glyph's enclosed counters first, then dilate. If
  only the ink is dilated, any counter wider than 2r shows the dither through it. On `invert` that is
  plainly wrong (its white half fills with dither). `coin`, `timer`, `reset`, `storm` and `dead` look
  hollow and noisy the same way. `tools.py` `halo_mask(ink, 'f2')` is the reference: dilate the
  hole-filled ink with a 5×5 brush minus its corners. Open inlets such as the inside of the `undo` hook
  or between the `refresh` arrows still show dither. Those are fine.
- **2 px on dither fills.** At 1 px the ink edge sits next to D6's isolated dots and the contour barely
  reads. The thin current X almost disappears. At 2 px every glyph sits on a clean paper shape that
  follows its outline, and the three `more` dots merge into one paper capsule, which reads better than
  three tiny boxes. Use 2 px on the tab too.
- **On the gem's solid black, 1 px is the nicer one.** It gives a crisp sticker keyline around the X and
  the bars. At 2 px the paper blob gets bubbly, especially around close A/B. If the engine wants one
  radius, choose 2 px. If it can take one per fill, use 2 px on dither and 1 px on solid.
- **Shape tweaks made for the halo:** gaps inside a glyph were kept at 1–2 px or 4+ px, never 3 (or 5),
  so a 1 px (or 2 px) halo never leaves a 1-px dither strip. Examples: the undo/redo counter is 4 px, the
  turn bar keeps a 2 px gap, and the rotate head stays 2 px from the phone. With the 2 px silhouette halo,
  `validate --report` finds no slivers except the pointed tips of tapering inlets (the `monarch` crown
  valleys, the arrowhead/shaft corners on `scale`, the tip of `turn`'s triangle). Those are a single
  pixel each and look natural.

## Close X: the pick

**A** (`close.txt`): 4-px runs at 12×12. On the diamond it has the same optical weight as the ± zone
glyphs and the hero numerals, and the four arms keep their notches at 1×. B is closer to the numerals'
weight, but its crossing fills in and reads as a star or blob at 1×. C is heavy enough but small inside
the 41 px diamond.

## Still to redo or check

- The `menu` and `plus` rounded ends look like sausages at 8×. They are fine at 1×, and should be checked
  on device next to the ± glyphs.
- `dice` and `new-game` are now both dark squares with paper marks. They are distinct at 1× (pips vs plus
  and back card), but should be checked side by side in the table-tools menu.
- `chevron-left`/`-right` are 7 px wide, so they sit ½ px off centre.
- The gem mock guesses the gem's geometry (a solid black diamond, |dx|+|dy| ≤ 16, inside a D6 rim).
  Re-check on device in T41.
