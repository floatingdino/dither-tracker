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
- **Monarch / initiative** are exclusive: granting one changes two `Player` objects.
- **Life linking:** adding damage takes life when `linkLife ?: settings.commanderDamageAffectsLife`.
  Removing damage refunds exactly the life it took (LIFO), unless `linkLife = false` is passed.
- **Partners:** partner slot only works when the source has `partner = true`; turning it off zeroes
  that slot's tax and dealt damage without refunding life. Damage from different commanders never sums.
- **KO:** manual DEAD always counts. Revive sets ALIVE only while a lethal rule still matches; ALIVE
  clears itself once the player recovers.
- **Undo grouping:** pass `action.coalesceKey` to `dispatch`; same-key repeats inside the window
  become one undo step and one merged log entry. Call `endGroup()` when the pending-delta readout
  commits. A group netting to zero disappears.
- **Log:** NewGame/Reset start a fresh log (undo restores it). Entries hold ids, not names — the
  history screen needs a fallback for removed players. `turn == 0` means turn tracking not started.
- **Misc:** `startingLife` changes apply on NewGame/Reset. NewGame keeps names/patterns/partners;
  Reset restores defaults. Commander tax is a stored amount (UI steps by 2). Empty `pattern` means
  "UI picks the seat default". Storm is per player.

## Open
- Dice/coin/starting-player results aren't logged (needs a log-only action if T16/T18 want it).
- No seat reordering or remove-specific-player action.
