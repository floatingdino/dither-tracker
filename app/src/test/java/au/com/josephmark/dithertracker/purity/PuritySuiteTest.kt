package au.com.josephmark.dithertracker.purity

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.commander.CommanderZones
import au.com.josephmark.dithertracker.drawer.DrawerItems
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.fx.Fx
import au.com.josephmark.dithertracker.ko.KoComponent
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.menu.MenuOverlay
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.settings.BackRow
import au.com.josephmark.dithertracker.settings.LinkRow
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.tools.DiceLayout
import au.com.josephmark.dithertracker.tools.DiceOverlay
import au.com.josephmark.dithertracker.tools.DiceState
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.tools.FirstPlayer
import au.com.josephmark.dithertracker.tools.Outcome
import au.com.josephmark.dithertracker.tools.Presentation
import au.com.josephmark.dithertracker.turns.TurnTab
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Full pixel-purity regression suite, JVM half (T22): every Play-screen state a script can reach —
 * play, presses, drawers, peeks, toggles (T14), commander mode, KO and winner, the gem, settings, history, dice and
 * coin, first player, every effect keyframe — drawn by the real components onto a [RasterRenderer]
 * at S = 2..6 on a landscape and a portrait screen, each in both colour modes, and checked with
 * [PurityChecks]. The device half is `tools/check_purity.sh` (see `docs/architecture.md`, "Verification harness").
 *
 * One test per (screen, S) × script; a script calls [Shots.shot] at each state it wants checked and
 * every failure is collected, so one run lists them all. Failing renders are written to
 * `app/build/purity/<screen>/<shot>{,-inv}.png`.
 */
@RunWith(Parameterized::class)
class PuritySuiteTest(private val w: Int, private val h: Int, private val s: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}x{1} S={2}")
        fun configs(): List<Array<Any>> {
            val out = ArrayList<Array<Any>>()
            for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (s in 2..6) out += arrayOf<Any>(w, h, s)
            return out
        }

        /**
         * Known, reported violations the suite tolerates (each is a finding to fix at its source, then
         * delete here); their frames skip the phase check only. Empty: `fizzle` (T48) was the last,
         * its dissolve now a tile layer sampled at global phase.
         */
        val KNOWN_OFF_PHASE = emptySet<String>()

        /** `PURITY_DUMP=<dir>` in the test JVM's environment: also write every shot there, for review. */
        private val dump: String? = System.getenv("PURITY_DUMP")
    }

    /** Collects every check failure of one script. */
    private inner class Shots(val rig: ScreenRig) {
        val failures = ArrayList<String>()
        var count = 0

        /**
         * Renders the current state normally and inverted and checks both. [panelsOnlyAgainst]: a
         * baseline render (normal mode) that may differ from this one only inside the seat panels.
         * [phase] false skips the phase scan (a [KNOWN_OFF_PHASE] effect).
         */
        fun shot(name: String, panelsOnlyAgainst: ByteArray? = null, phase: Boolean = true): ByteArray {
            count++
            val r = rig.render(false)
            val normal = r.px.copyOf()
            val errs = ArrayList<String>()
            check(r, errs, "", phase)
            if (panelsOnlyAgainst != null) PurityChecks.diffOutside(panelsOnlyAgainst, normal, r.width, rig.l.panels)?.let { errs += it }
            val inv = rig.render(true)
            check(inv, errs, " (inverted)", phase)
            PurityChecks.complementError(normal, inv.px, inv.width)?.let { errs += it }
            if (dump != null) {
                val dir = File(dump, "${w}x$h-S$s")
                rig.render(false).writePng(File(dir, "$name.png"))
            }
            if (errs.isNotEmpty()) {
                failures += "${rig.tag} $name:\n    " + errs.joinToString("\n    ")
                val dir = File(PurityChecks.outDir, "${w}x$h-S$s")
                inv.writePng(File(dir, "$name-inv.png"))
                rig.render(false).writePng(File(dir, "$name.png"))
            }
            return normal
        }

        /**
         * Out-of-grid draws fail only on [PlayLayout.comfortable] layouts: the others (k = 1 forced on a
         * panel narrower than four digits, e.g. portrait S = 6) are never chosen by AUTO nor offered
         * in Settings (`SettingsTable.scaleValues`), and a 4-digit total legitimately overflows there.
         */
        private fun check(r: RasterRenderer, errs: MutableList<String>, mode: String, phase: Boolean) {
            if (r.outside > 0 && rig.l.comfortable) errs += "${r.outside} px drawn outside the grid$mode: " + r.outsideNotes.joinToString("; ")
            if (!r.clipBalanced) errs += "clipTo without unclip$mode"
            if (phase) PurityChecks.phaseErrors(r).let { if (it.isNotEmpty()) errs += "off-phase pattern$mode: " + it.joinToString("; ") }
        }

        fun assertClean() {
            assertTrue("no shots taken", count > 0)
            assertTrue("${failures.size} of $count shots failed:\n" + failures.joinToString("\n"), failures.isEmpty())
        }
    }

    /** [prewarmed] false: the script draws effects outside `FxPolicy`'s pools (the gallery), which only a debug gallery run prewarms. */
    private fun script(prewarmed: Boolean = true, body: Shots.(ScreenRig) -> Unit) {
        val shots = Shots(ScreenRig(w, h, s))
        shots.body(shots.rig)
        shots.assertClean()
        // T20: every image / sparse tile paint a shot drew was built by the host's prewarm, so the
        // device never builds a scaled bitmap inside onDraw (a stall, and an allocation per first use).
        val cold = shots.rig.r.cold
        if (prewarmed) assertTrue("${shots.rig.tag}: ${cold.size} masks first needed in a draw, not prewarmed: " + cold.entries.joinToString { "${it.value} ${it.key.javaClass.simpleName} ${(it.key as? MonoImage)?.let { m -> "${m.width}x${m.height}" } ?: ""}" }, cold.isEmpty())
    }

    // ---- play: idle, readouts, presses, wide numbers ------------------------------------------------

    @Test fun play() = script { rig ->
        shot("01_fresh")
        rig.tap(rig.life, 1, PlayLayout.MINUS)
        rig.tap(rig.life, 1, PlayLayout.MINUS)
        rig.tap(rig.life, 3, PlayLayout.PLUS)
        shot("02_readouts")
        rig.press(rig.life, 3, PlayLayout.PLUS)
        shot("03_press_life")
        rig.cancel()
        rig.advance(4_000) // commit: hit/heal effects start
        shot("04_committed")
        rig.advance(10_000)
        rig.dispatch(Action.SetLife(rig.id(0), 999))
        rig.dispatch(Action.SetLife(rig.id(1), 9999))
        rig.dispatch(Action.SetLife(rig.id(2), 7))
        rig.dispatch(Action.SetLife(rig.id(3), 100))
        rig.advance(10_000)
        shot("05_wide_numbers")
        rig.press(rig.tab, 0, 0)
        shot("06_press_tab")
        rig.cancel()
    }

    // ---- drawers and peeks ---------------------------------------------------------------------------

    @Test fun drawers() = script { rig ->
        rig.dispatch(Action.SetPartner(rig.id(1), true))
        rig.tap(rig.tab, 3, 0)
        shot("10_drawer3_zero")
        // Bump every cell the page shows (how many depends on S), some twice.
        for (slot in 0 until DrawerZones.MAX_SLOTS) {
            if (rig.pointOf(rig.drawer, 3, DrawerZones.cell(slot, DrawerZones.PLUS)) == null) continue
            repeat(1 + slot % 2) { rig.tap(rig.drawer, 3, DrawerZones.cell(slot, DrawerZones.PLUS)) }
        }
        rig.tap(rig.tab, 1, 0)
        for (i in 0 until 8) rig.tap(rig.drawer, 1, DrawerZones.cell(1, DrawerZones.PLUS)) // poison 8: warning
        rig.tap(rig.drawer, 1, DrawerZones.cell(0, DrawerZones.PLUS))
        shot("11_drawers_nonzero")
        rig.press(rig.drawer, 3, DrawerZones.cell(0, DrawerZones.MINUS))
        shot("12_press_cell")
        rig.cancel()
        rig.press(rig.drawer, 3, DrawerZones.BUTTON)
        shot("13_press_drawer_button")
        rig.cancel()
        rig.tap(rig.drawer, 1, DrawerZones.NEXT)
        shot("14_drawer1_page2")
        rig.tap(rig.drawer, 1, DrawerZones.cell(0, DrawerZones.PLUS))
        rig.advance(4_000)
        rig.drawer.controller.closeAll()
        rig.dispatch(Action.SetToggle(rig.id(2), Toggle.MONARCH, true))
        rig.dispatch(Action.SetToggle(rig.id(0), Toggle.INITIATIVE, true))
        rig.dispatch(Action.SetToggle(rig.id(0), Toggle.RING_BEARER, true))
        rig.dispatch(Action.ChangeCounter(rig.id(2), Counter.POISON, 10 - 1)) // one short of KO
        rig.dispatch(Action.ChangeCounter(rig.id(0), Counter.EXPERIENCE, 12))
        rig.dispatch(Action.ChangeCommanderTax(rig.id(0), Commander.PRIMARY, 3))
        rig.advance(10_000)
        shot("15_peeks")
    }

    // ---- T14: state toggles (badges, toggle cells, day/night on the diamond) ------------------------------

    @Test fun toggles() = script { rig ->
        rig.dispatch(Action.SetToggle(rig.id(0), Toggle.MONARCH, true))
        rig.dispatch(Action.SetToggle(rig.id(0), Toggle.RING_BEARER, true))
        rig.dispatch(Action.SetToggle(rig.id(1), Toggle.INITIATIVE, true))
        for (seat in 0 until 4) rig.dispatch(Action.SetToggle(rig.id(seat), Toggle.CITYS_BLESSING, seat % 2 == 1))
        rig.dispatch(Action.ChangeCounter(rig.id(1), Counter.POISON, 9)) // warning peek beside the badges
        rig.dispatch(Action.ChangeCommanderTax(rig.id(3), Commander.PRIMARY, 2))
        rig.advance(10_000)
        shot("16_badges")
        rig.dispatch(Action.SetDayNight(DayNight.DAY))
        shot("17_day")
        rig.pressAt(rig.l.diamondCx, rig.l.diamondCy)
        shot("18_day_pressed")
        rig.cancel()
        rig.dispatch(Action.SetDayNight(DayNight.NIGHT))
        shot("19_night")
        // The toggle page of two drawers (the cells follow the counters), one off-and-on mix each.
        for (seat in intArrayOf(0, 3)) {
            rig.tap(rig.tab, seat, 0)
            var guard = 0
            while (rig.pointOf(rig.drawer, seat, DrawerZones.NEXT) != null &&
                (0 until rig.drawer.state.itemsOnPage(seat)).none { DrawerItems.isToggle(rig.drawer.controller.itemAt(seat, it)) } && guard++ < 10
            ) rig.tap(rig.drawer, seat, DrawerZones.NEXT)
        }
        shot("1a_toggle_cells")
        val slot = (0 until rig.drawer.state.itemsOnPage(3)).first { DrawerItems.isToggle(rig.drawer.controller.itemAt(3, it)) }
        rig.press(rig.drawer, 3, DrawerZones.cell(slot, DrawerZones.MINUS))
        shot("1b_press_toggle_cell")
        rig.cancel()
        rig.dispatch(Action.SetDayNight(DayNight.UNSET))
        shot("1c_toggle_cells_unset")
    }

    // ---- commander-damage mode -------------------------------------------------------------------------

    @Test fun commander() = script { rig ->
        rig.dispatch(Action.SetPartner(rig.id(1), true))
        rig.tap(rig.tab, 3, 0)
        rig.tap(rig.drawer, 3, DrawerZones.BUTTON)
        shot("20_mode_enter")
        for (i in 0 until 3) rig.tap(rig.cmd, 2, CommanderZones.A_PLUS)
        rig.advance(5_000)
        for (i in 0 until 20) rig.tap(rig.cmd, 1, CommanderZones.A_PLUS) // 20: one short of lethal, so the target stays up
        rig.tap(rig.cmd, 1, CommanderZones.B_PLUS)
        shot("21_mode_damage")
        rig.advance(4_000)
        shot("22_mode_committed")
        rig.press(rig.cmd, 1, CommanderZones.B_MINUS)
        shot("23_press_partner_quarter")
        rig.cancel()
        rig.press(rig.cmd, 3, CommanderZones.TARGET)
        shot("24_press_target")
        rig.cancel()
        // T52: the PARTNER tabs — on and off, pressed, and the HOLD hint (seat 1's partner has damage).
        rig.tap(rig.cmd, 2, CommanderZones.PARTNER)
        rig.tap(rig.cmd, 1, CommanderZones.PARTNER)
        shot("24a_partner_on_and_hold_hint")
        rig.press(rig.cmd, 0, CommanderZones.PARTNER)
        shot("24b_partner_pressed")
        rig.cancel()
        rig.cmd.mode.exit()
        rig.advance(10_000)
        shot("25_after_mode")
    }

    // ---- KO and winner (read-only use of ko/) ------------------------------------------------------------

    @Test fun koAndWinner() = script { rig ->
        val ko = rig.one<KoComponent>()
        rig.dispatch(Action.SetLife(rig.id(2), 0))
        rig.advance(6_000)
        shot("30_ko_one")
        rig.press(ko, 2, 0)
        shot("31_press_ko")
        rig.cancel()
        rig.dispatch(Action.ChangeCounter(rig.id(0), Counter.POISON, 10))
        rig.advance(6_000)
        shot("32_ko_two")
        // The last KO through a life tap, so the flourish and the winner line play.
        rig.dispatch(Action.SetLife(rig.id(3), 1))
        rig.advance(6_000)
        rig.tap(rig.life, 3, PlayLayout.MINUS)
        rig.advance(3_100)
        var i = 0
        while (rig.fx.player.isPlaying(1) || rig.fx.player.isPlaying(3)) {
            shot("33_last_ko_frame$i")
            rig.advance(250)
            if (++i > 40) break
        }
        rig.advance(6_000)
        shot("34_winner")
    }

    /** T51: every KO cause (life and marked dead without a chip, commander damage with one) and the winner at 6 seats, sideways included. */
    @Test fun koCausesAndWinnerAtSixSeats() {
        val shots = Shots(ScreenRig(w, h, s, players = 6))
        val rig = shots.rig
        rig.dispatch(Action.SetLife(rig.id(0), -3))
        rig.dispatch(Action.MarkDead(rig.id(1)))
        rig.dispatch(Action.ChangeCounter(rig.id(2), Counter.POISON, 10))
        rig.dispatch(Action.ChangeCommanderDamage(rig.id(3), rig.id(4), Commander.PRIMARY, 21, linkLife = false))
        rig.advance(6_000)
        shots.shot("35_ko_causes_6p")
        rig.press(rig.one<KoComponent>(), 3, 0)
        shots.shot("36_press_ko_6p")
        rig.cancel()
        rig.tap(rig.one<KoComponent>(), 0, 0) // armed: REVIVE?
        shots.shot("37_revive_armed_6p")
        rig.dispatch(Action.SetLife(rig.id(4), 0))
        rig.advance(10_000)
        shots.shot("38_winner_6p")
        shots.assertClean()
    }

    // ---- the gem (table menu) ----------------------------------------------------------------------------

    @Test fun gem() = script { rig ->
        rig.openMenu()
        shot("40_gem_fresh") // undo and redo disabled
        rig.tapAt(rig.l.diamondCx, rig.l.diamondCy) // the centre X closes it
        rig.tap(rig.life, 2, PlayLayout.MINUS)
        rig.advance(10_000)
        rig.openMenu()
        shot("41_gem_undo")
        for (item in MenuItem.entries) {
            val p = rig.gemPoint(item)
            if (!rig.menu.enabled[au.com.josephmark.dithertracker.menu.GemLayout.slotOf(item)]) continue
            rig.pressAt(p[0], p[1])
            shot("42_press_${item.name.lowercase()}")
            rig.cancel()
        }
        rig.pressAt(rig.l.diamondCx, rig.l.diamondCy)
        shot("43_press_centre")
        rig.cancel()
        rig.gemPoint(MenuItem.NEW).let { rig.tapAt(it[0], it[1]) }
        shot("44_new_confirm")
        rig.tap(rig.menu, Stage.SCREEN, MenuOverlay.CONFIRM)
        rig.advance(10_000)
        shot("45_after_new")
        rig.openFromMenu(MenuItem.CLEAN)
        var i = 0
        while (rig.stage.timersPending() && i < 8) {
            shot("46_clean_$i")
            rig.advance(200)
            i++
        }
        rig.advance(60_000)
        shot("47_after_clean")
    }

    // ---- settings card -----------------------------------------------------------------------------------

    @Test fun settings() = script { rig ->
        val o = rig.settings
        rig.openFromMenu(MenuItem.SETUP)
        assertTrue(o.isOpen)
        shot("50_settings")
        val box = IRect()
        for (row in o.main.rows.filterIsInstance<LinkRow>()) {
            if (!o.card.region.hitRect(row.widget.id, box)) continue
            rig.tapAt((box.left + box.right) / 2, (box.top + box.bottom) / 2)
            shot("51_page_${row.label.lowercase().replace(' ', '_')}")
            val back = o.page.rows.filterIsInstance<BackRow>().firstOrNull() ?: continue
            if (o.card.region.hitRect(back.widget.id, box)) rig.tapAt((box.left + box.right) / 2, (box.top + box.bottom) / 2)
        }
        val first = o.main.rows.first()
        if (o.card.region.hitRect(first.widget.id, box)) {
            rig.pressAt((box.left + box.right) / 2, (box.top + box.bottom) / 2)
            shot("52_press_row")
            rig.cancel()
        }
        for (k in 1..3) {
            o.rotate()
            shot("53_rotated_${k * 90}")
        }
        o.close()
        shot("54_closed")
    }

    // ---- history card -------------------------------------------------------------------------------------

    @Test fun history() = script { rig ->
        rig.openFromMenu(MenuItem.LOG)
        shot("60_history_empty")
        rig.history.close()
        for (seat in 0 until 4) {
            rig.tap(rig.life, seat, PlayLayout.MINUS)
            rig.advance(4_000)
        }
        rig.dispatch(Action.ChangeCounter(rig.id(1), Counter.POISON, 3))
        rig.dispatch(Action.SetLife(rig.id(2), 0))
        rig.advance(10_000)
        rig.openFromMenu(MenuItem.LOG)
        shot("61_history_entries")
        for (k in 1..3) {
            rig.history.rotateNext()
            shot("62_rotated_${k * 90}")
        }
        rig.history.close()
    }

    // ---- dice / coin panel and first player ------------------------------------------------------------------

    @Test fun diceAndFirstPlayer() = script { rig ->
        val dice = rig.dice
        rig.openFromMenu(MenuItem.DICE)
        shot("70_dice_rolling")
        rig.advance(DiceState.PRESENT_MAX_MS)
        shot("71_dice_d20")
        // T52: every keyframe of every outcome's presentation (the result is fixed at the tap: roll
        // until the outcome comes up, skipping the others' presentations with a tap).
        // T59: in both display speeds (the fast one has more poses).
        for (speed in intArrayOf(DisplaySpeed.SLOW, DisplaySpeed.FAST)) try {
        DisplaySpeed.override = speed
        val sp = DisplaySpeed.name(speed)
        val p = Presentation.tumble(speed)
        for (o in 0 until Outcome.COUNT) {
            val button = when (o) {
                Outcome.D6 -> DiceLayout.D6
                Outcome.HEADS, Outcome.TAILS -> DiceLayout.COIN
                else -> DiceLayout.D20
            }
            var tries = 0
            while (true) {
                rig.tap(dice, Stage.SCREEN, button)
                if (dice.state.outcome == o) break
                rig.tap(dice, Stage.SCREEN, button) // skip to the result
                check(++tries < 500) { "outcome $o never came up" }
            }
            for (beat in 0 until p.beats(o)) {
                check(dice.state.presentation === p)
                shot("72_present_${sp}_${p.scripts[o].name.replace(' ', '_')}_$beat")
                rig.advance(p.holdMs(o, beat))
            }
            shot("73_result_${sp}_${p.scripts[o].name.replace(' ', '_')}")
        }
        } finally {
            DisplaySpeed.override = DisplaySpeed.NO_OVERRIDE
        }
        rig.tap(dice, Stage.SCREEN, DiceOverlay.AGAIN)
        rig.advance(DiceState.PRESENT_MAX_MS)
        shot("74_dice_again")
        rig.press(dice, Stage.SCREEN, DiceOverlay.AGAIN)
        shot("75_press_again")
        rig.cancel()
        rig.tap(dice, Stage.SCREEN, DiceLayout.CLOSE)
        rig.advance(1_000)
        rig.openFromMenu(MenuItem.FIRST)
        shot("76_first_player_asking")
        rig.advance(FirstPlayer.WHO_MS)
        shot("76_first_player")
        rig.advance(10_000)
        shot("77_first_player_gone")
    }

    // ---- T17: turn tracker and timer ---------------------------------------------------------------------------

    @Test fun turns() = script { rig ->
        val turn = rig.stage.components.filterIsInstance<TurnTab>().single()
        rig.dispatch(Action.SetToggle(rig.id(0), Toggle.MONARCH, true))
        rig.dispatch(Action.ChangeCommanderTax(rig.id(3), Commander.PRIMARY, 2))
        rig.dispatch(Action.SetStartingPlayer(rig.id(0)))
        rig.advance(10_000)
        shot("80_turn_tab")
        if (turn.shownSeat < 0) return@script // S = 5, 6 on the Nomad: not comfortable, no far strip (never offered)
        rig.press(turn, 0, TurnTab.PASS)
        shot("81_turn_tab_pressed")
        rig.cancel()
        rig.tap(turn, 0, TurnTab.PASS)
        rig.tap(rig.life, 1, PlayLayout.MINUS) // the active seat's readout under its tab
        shot("82_passed_with_readout")
        rig.advance(10_000)
        rig.stage.settings.update(rig.stage.settings.current.copy(turnTimer = TurnTimer.GAME))
        rig.advance(7 * 60_000L)
        shot("83_game_clock_7")
        rig.advance(100 * 60_000L)
        shot("84_game_clock_107")
        rig.stage.settings.update(rig.stage.settings.current.copy(turnTimer = TurnTimer.TURN))
        rig.tap(turn, 1, TurnTab.PASS)
        shot("85_turn_timer_0")
        rig.dispatch(Action.SetLife(rig.id(2), 0)) // the active seat goes out on its own turn
        rig.advance(10_000)
        shot("86_ko_active_near_edge")
        rig.tap(turn, 2, TurnTab.PASS)
        shot("87_passed_on_from_ko")
        // (2, 3, 5 and 6 seats, the narrow tier and sideways seats: turns/TurnTabTest on CountRig.)
    }

    // ---- effect keyframes ----------------------------------------------------------------------------------------

    @Test fun effects() = script(prewarmed = false) { rig ->
        rig.fx.startGallery(arrayOf(Fx.entries.first()), 37)
        rig.fx.stopGallery()
        rig.fx.player.cancelAll()
        rig.advance(10_000)
        val clean = shot("80_clean_37")
        for (fx in Fx.entries) {
            rig.fx.startGallery(arrayOf(fx), 37)
            rig.fx.stopGallery()
            for (i in 0 until fx.frameCount) {
                shot("81_${fx.id}_$i", panelsOnlyAgainst = clean, phase = fx.id !in KNOWN_OFF_PHASE)
                rig.advance(rig.fx.player.hold(fx, i))
            }
            rig.fx.player.cancelAll()
            rig.advance(10_000)
        }
    }
}
