# Changelog

## 0.1.0 — first release

The first public build of Dither, a 1-bit life tracker for Magic: The Gathering Commander made for
e-ink Android devices. Developed and tested on a Supernote Nomad; see
[docs/compatibility.md](docs/compatibility.md) for other devices (checked on an emulator only).

### Play
- Two to six players around one device, each seat facing its player, with a picker of seating
  layouts per player count (mixed tile sizes and empty seats included).
- Life totals with tap ±1 and hold ±10; the pending change shows as a delta before it commits.
- Commander damage from each opponent, with partners; commander tax.
- Counters: poison, energy, experience, rad, storm, tickets and a generic one, in a drawer per seat,
  with corner peeks when the drawer is closed.
- Trackers: monarch, initiative, city's blessing, ring-bearer and day/night. Any counter or tracker
  can be hidden in settings.
- Knock-outs (life, poison, commander damage, by hand) with a revive, and a winner screen.
- Turn tracker: a tab on the active seat; tap to pass. Optional turn or game times, reported after
  each pass and on the winner screen.
- Dice (d20, d6), a coin, and a d20 roll-off on the tiles for who goes first.
- A game log with undo and redo.

### Look and feel
- Strictly 1-bit: black and white only, shading from dither patterns at a whole-number scale.
- Short keyframed effects for damage, healing, counters, trackers and table moments; quips in the
  spirit of 8- and 16-bit games.
- Dark (inverted) mode.

### E-ink
- A fast display mode on Supernote devices (on by default, switchable in settings).
- Quick taps draw only their result; an automatic screen clean clears ghosting.
- Nothing runs while idle or in the background.

### Known limits
- Only the Supernote Nomad has been tested on real hardware.
- Tabs and icons are sized for a 300 dpi panel, so they come out small on dense phones and large on
  low-density screens.
- Phone-shaped e-ink devices get a cramped layout without tile badges.
- Dither is unofficial Fan Content permitted under the Wizards of the Coast Fan Content Policy. Not
  approved or endorsed by Wizards. Portions of the materials used are property of Wizards of the
  Coast. ©Wizards of the Coast LLC.
