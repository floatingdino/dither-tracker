# Icons

16×16 1-bit icons, hand-authored as ASCII. The source of truth for every icon in the app.

![contact sheet](contact-sheet.png)

Inverted (dark mode / pressed state): [`contact-sheet-inverted.png`](contact-sheet-inverted.png).

## Format

- One file per icon: `design/icons/<kebab-name>.txt`.
- Exactly 16 lines of exactly 16 characters, ending with a single trailing newline.
- `#` = black (ink), `.` = white. No other characters.
- The outer 1px ring is always empty, so the art lives in the inner 14×14.

## Style

Taken from the three Figma icons (`poison`, `storm`, `rads`):

- **1px outline strokes.** Draw curves as stepped diagonals (1-1, 2-1, 1-2 runs), never as jaggy noise.
- **No solid fills** except small details: eyes, pips, a 2px blade, the inner flame. `invert` is the one deliberate exception, because its half-fill *is* the meaning.
- **Simple UI glyphs use a 2px stroke** (`plus`, `minus`, `menu`, `more`, `close`, `check`, `chevron-*`). On a 16px grid with an even centre, a 1px line can't be centred, and these glyphs need the extra weight to read at S=2.
- **Optically centred.** The ink bounding box is centred on (7.5, 7.5), to within half a pixel where an asymmetric shape forces it. `validate.py --report` prints the offsets.
- **Must read inverted.** Every icon is checked on the inverted sheet, because pressed feedback and dark mode both swap black and white.
- **Original drawings only.** No traced MTG set symbols or mana glyphs.

## Tools (Python 3 stdlib only)

```sh
python3 design/icons/validate.py            # format, margin, duplicate checks (exit 1 on failure)
python3 design/icons/validate.py --report   # also print bbox + centre offset per icon
python3 design/icons/render.py              # writes contact-sheet.png + contact-sheet-inverted.png
python3 design/icons/render.py --scale 4 --cols 8
```

On the contact sheet, each icon is drawn at 8× nearest-neighbour inside a grey frame that marks the 16×16 bounds. A 1× preview and the name sit underneath. The grey frame and labels are only there for review: the icon pixels themselves are pure black and white.

## Icon list

| Icon | Depicts | Used for |
|------|---------|----------|
| `poison` | Skull (from Figma) | Poison counter (drawer) |
| `storm` | Cloud with lightning bolt (from Figma) | Storm counter (drawer) |
| `rads` | Radiation trefoil (from Figma) | Rad counter (drawer) |
| `energy` | Zig-zag lightning bolt | Energy counter (drawer) |
| `experience` | Five-point star | Experience counter (drawer) |
| `commander` | Heater shield with an upright sword | Commander damage (drawer button, damage view) |
| `commander-tax` | Stack of coins, side-on | Commander tax counter, one per commander |
| `tickets` | Ticket stub with side notches and perforation | Tickets counter (drawer) |
| `generic-counter` | Tally marks (four strokes and a slash) | Generic/custom counter (drawer) |
| `life` | Heart outline | Life total, starting-life setting |
| `monarch` | Crown | Monarch toggle (panel badge) |
| `initiative` | Torch (flame over a cup and handle) | Initiative toggle (dungeon), panel badge |
| `citys-blessing` | Castle: spired keep between two towers, with a gate | City's blessing toggle (panel badge) |
| `ring` | Ring with a cut gem | Ring-bearer toggle (panel badge) |
| `day` | Sun with eight rays | Day state (global day/night) |
| `night` | Crescent moon | Night state (global day/night) |
| `dead` | Tombstone with a cross on the ground | Auto-KO marker and toggle |
| `dice` | Die face showing five | Dice tool (d6/d20/custom) |
| `coin` | Coin seen edge-on, with milled rim | Coin-flip tool |
| `timer` | Stopwatch | Turn/game timer |
| `turn` | Play triangle with an end bar (next/skip) | Turn tracker, pass turn |
| `undo` | Hooked arrow pointing left | Undo (menu) |
| `redo` | Hooked arrow pointing right | Redo (menu) |
| `reset` | Single anticlockwise circular arrow | Reset life/counters (with confirm) |
| `new-game` | Card with a plus | New game |
| `settings` | Gear | Settings entry |
| `history` | Page with a folded corner and text lines | Game history log |
| `players` | Two people side by side | Player count & layout setting |
| `menu` | Three bars | Centre diamond menu button |
| `more` | Three dots | Drawer tab, overflow |
| `plus` | Plus | Increment (+) |
| `minus` | Minus | Decrement (−) |
| `close` | X | Close drawer, overlay or dialog |
| `check` | Tick | Confirm, enabled toggle |
| `chevron-left` | Left chevron | Back, previous page, stepper |
| `chevron-right` | Right chevron | Forward, next page, stepper |
| `chevron-up` | Up chevron | Collapse, page up |
| `chevron-down` | Down chevron | Expand, page down |
| `invert` | Half-filled circle | Invert (dark mode) setting |
| `scale` | Diagonal double-headed arrow | Integer scale S setting |
| `rotate` | Phone outline with a curved arrow | Seat/screen orientation |
| `partner` | Two overlapping shields | Partner commanders (two damage sources) |
| `refresh` | Two clockwise circular arrows | Full e-ink refresh (ghosting cleanup) |

## Telling similar icons apart

- `undo` and `redo` are open hooks, while `reset` and `refresh` are closed circles. `reset` has one anticlockwise arrow and `refresh` has two clockwise arrows. `rotate` is the only one with a device in it.
- `day` has rays that stand apart from a small disc. `settings` has teeth joined to the body and a hole in the middle.
- `monarch` (crown) and `commander` (shield and sword) share no outline. `partner` reuses the shield silhouette on purpose, so the two read as related.
- `coin` is a single coin seen edge-on. `commander-tax` is a stack of coins.
- `dead` (tombstone) is the only arched slab, and its ground line extends past the stone.
