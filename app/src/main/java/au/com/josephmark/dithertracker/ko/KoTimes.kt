package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.TurnState
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.turns.TimeFormat
import au.com.josephmark.dithertracker.widgets.CharBuf

/**
 * The winner screen's time report (T66, Sam: "with these on, let's report them on the winner screen:
 * total turn time against each seat or total game time on each seat"). Pure; shared by the dead
 * tiles ([KoComponent]) and the winner's tile ([KoWinner]).
 *
 * - **When**: there is a winner (core stamped the game's end, `TurnState.endedAt`), the tracker is on
 *   with a timer mode (GAME / TURN), and the game was clocked (tracking started under the active
 *   clock). Never mid-game: a KO tile before the end shows no time (it would be a running figure).
 * - **What**: TURN — each seat's own total time on turn (`TurnState.timeOnTurn`, the turn in progress
 *   counted to the end); GAME — the game's total (`gameMillis`), the same figure on every seat, each
 *   facing its player. Active time only. Static: everything is fixed at the end (`endedAt`).
 */
object KoTimes {

    fun on(state: GameState, settings: AppSettings): Boolean {
        val t = state.turn
        return settings.turnTracker && settings.turnTimer != TurnTimer.OFF && t.started && t.stamped && t.endedAt != TurnState.NO_TIME
    }

    /** The figure for [seat] ([on] must hold), in ms. */
    fun millis(state: GameState, settings: AppSettings, seat: Int): Long {
        val t = state.turn
        return if (settings.turnTimer == TurnTimer.GAME) t.gameMillis(t.endedAt)
        else t.timeOnTurn(state.players[seat].id, t.endedAt)
    }

    /** [millis] as text into [out]. */
    fun format(state: GameState, settings: AppSettings, seat: Int, out: CharBuf): CharBuf =
        TimeFormat.format(millis(state, settings, seat), out)
}
