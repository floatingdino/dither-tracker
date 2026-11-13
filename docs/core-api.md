# `:core` API notes (from T03)

Package `au.com.josephmark.dithertracker.core`. Pure Kotlin, no Android, never reads the clock.
Read the sources for signatures; this file records the behaviour a UI author must know.

## Shape
- `GameState(players, settings, dayNight, turn)`; `GameState.create(playerCount = 4, settings)`.
- `Player(id, name, seat, life, pattern, partner, counters, commanderTax, commanderDamage, toggles, koOverride)`.
- `Action` (sealed) + `reduce(state, action)`. `Game(state, log)` with `apply(action, timestamp)`.
- `History(state, maxDepth = 200, coalesceWindowMillis = 3000)`: `dispatch(action, timestamp, coalesceKey)`,
  `endGroup()`, `undo()`, `redo()`, `canUndo`, `canRedo`, `state`, `log`.
- `LogEntry(timestamp, turn, event: LogEvent)`. `TableTools`: `pickStartingPlayer`, `rollDie`, `rollDice`, `flipCoin`.
- `Limits`: life −999..9999, counters 0..999, poison KO 10, commander damage KO 21.

## Behaviour
- **Dirty checks:** a no-op returns the same `GameState`; unchanged players keep the same `Player`
  instance. `old.players[i] !== new.players[i]` ⇒ redraw panel i. `History.dispatch` returns `this` on a no-op.
- **Seats:** `players[i].seat == i` always. Ids are stable; unknown ids / invalid slots are no-ops.
- **Commander damage** is stored on the receiving player, keyed by source id.
- **Turns:** active player lives in `state.turn` — diff it separately for the highlight.
- **Monarch / initiative** are exclusive: granting one changes two `Player` objects. Either can be
  nobody's. A player going KO drops both (T14: in the rules they pass on, but the app can't know to
  whom, so they are left unset; the log gets a `ToggleChanged(…, false)` before the `KnockedOut`).
  Revive does not restore them (undo of the KO does); a KO'd player can't claim one (no-op).
  City's blessing and ring-bearer are independent per player and survive a KO. Day/night is global
  (`UNSET`/`DAY`/`NIGHT`), cleared by NewGame/Reset. All of it has been in the save since v1 (player
  flag bits, `dayNight`), so T14 needed no format bump.
- **Life linking:** adding damage takes life when `linkLife ?: settings.commanderDamageAffectsLife`.
  Removing damage refunds exactly the life it took (LIFO), unless `linkLife = false` is passed.
- **Partners:** partner slot only works when the source has `partner = true`; turning it off zeroes
  that slot's tax and dealt damage without refunding life. Damage from different commanders never sums.
- **KO:** manual DEAD always counts. Revive sets ALIVE only while a lethal rule still matches; ALIVE
  clears itself once the player recovers.
- **Undo grouping (per key, T29):** pass `action.coalesceKey` to `dispatch`; each key's repeats
  inside the window (measured from that key's own last action) become one undo step and one merged
  log entry, however other keys interleave — four seats tapping at once give one step per seat.
  Undo removes the *last-touched* step as a whole and nothing else: other seats' interleaved
  changes stay, re-applied through `reduce` on the snapshot from before the undone group (so
  clamping, linked-life refunds, KO flips and overrides come out exactly as if the group never
  happened). Redo restores exactly what undo removed. A group's merged log entry sits where it was
  last touched (the log reads in undo order); its values are those after its latest action.
  `endGroup(key)` closes one group (call it when that seat's readout commits); `endGroup()`,
  undo, redo and any null-key action close all of them. `openGroups` / `isGroupOpen(key)` list
  them; `openGroup` is the most recently touched one (compat). A group netting to zero
  disappears. A step pushed out by `maxDepth` becomes permanent and closed (its seat's next tap
  starts a new group). `past` is now `List<UndoStep>` (key, merged entries, open flag).
- **Log:** NewGame/Reset start a fresh log (undo restores it). Entries hold ids, not names — the
  history screen needs a fallback for removed players. `turn == 0` means turn tracking not started.
- **Misc:** `startingLife` changes apply on NewGame/Reset. NewGame keeps names/patterns/partners;
  Reset restores defaults. Commander tax is a stored amount (UI steps by 2). Empty `pattern` means
  "UI picks the seat default". Storm is per player.

## Open
- Dice/coin/starting-player results aren't logged (needs a log-only action if T16/T18 want it).
- No seat reordering or remove-specific-player action.
