package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.History
import au.com.josephmark.dithertracker.core.KoReason
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.MINUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.PLUS
import au.com.josephmark.dithertracker.play.PlayRig
import au.com.josephmark.dithertracker.play.PlayRig.Companion.mid
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The knocked-out tile (T15): layout, cover, revive, quips, effects hook, commander mode, winner. */
class KoTest {

    private val screens = listOf(1872 to 1404, 2560 to 1600, 1280 to 800, 1404 to 1053)

    private class Rig(session: GameSession = GameSession.newGame(4), s: Int = 4) {
        val rig = PlayRig(s = s, session = session)
        val ko = rig.stage.components.filterIsInstance<KoComponent>().single()
        val winner = rig.stage.components.filterIsInstance<KoWinner>().single()
        val l get() = rig.l
        val stage get() = rig.stage
        val session get() = rig.session
        val clock get() = rig.platform.clock
        private var pointer = 0

        fun hit(gx: Int, gy: Int): String {
            val id = stage.hitTest(gx, gy)
            if (id >= 0 && stage.zoneOwner(id) === ko) return "ko:${stage.zoneSeat(id)}"
            return rig.key(id)
        }

        fun hit(r: IRect) = mid(r).let { hit(it[0], it[1]) }

        fun tap(r: IRect) {
            val m = mid(r)
            val p = pointer++
            stage.down(p, m[0], m[1], clock.now)
            stage.up(p)
        }

        fun kill(seat: Int) = session.dispatch(Action.SetLife(rig.id(seat), 0), clock.now)
        fun heal(seat: Int) = session.dispatch(Action.SetLife(rig.id(seat), 40), clock.now)
        fun shown(seat: Int) = (ko.shown ushr seat) and 1 != 0
        fun armed(seat: Int) = (ko.armed ushr seat) and 1 != 0
    }

    // ---- layout -----------------------------------------------------------------------------------

    private fun inside(inner: IRect, outer: IRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun overlaps(a: IRect, b: IRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    @Test fun plateFitsEveryQuipAtEveryScaleForEverySeat() {
        val all = QuipKind.entries.filter { it.name.startsWith("KO_") }
            .flatMap { Flavour.quippy.lines(it) + Flavour.plain.lines(it) }
        for ((w, h) in screens) for (sc in 2..6) {
            val l = PlayLayout.compute(DitherGrid(w, h, sc))
            val k = KoLayout(l)
            val b = KoLayout.Block()
            for (q in all) for (s in 0 until 4) {
                val tag = "$w×$h S=$sc seat $s '$q'"
                k.arrange(s, q.uppercase(), b)
                val p = l.panels[s]
                assertTrue("$tag plate ${b.plate} in $p", inside(b.plate, p))
                assertFalse("$tag plate clear of diamond", overlaps(b.plate, l.diamond))
                if (!l.comfortable) continue // cramped layouts (never chosen): only kept inside the panel
                val sh = b.shadow
                assertTrue("$tag shadow in panel", inside(sh, p))
                assertTrue("$tag revive text", inside(b.reviveText, b.plate))
                assertTrue("$tag lines", b.lines in 1..KoLayout.MAX_LINES)
                assertEquals("$tag whole quip shown", q.trim().length, (0 until b.lines).sumOf { b.end[it] - b.start[it] } + b.lines - 1)
                val num = IRect()
                l.numberFootprint(s, -999, num)
                val o = k.outline
                val ring = IRect(num.left - o, num.top - o, num.right + o, num.bottom + o)
                assertTrue("$tag cut-out in panel", inside(ring, p))
                assertFalse("$tag plate/number", overlaps(b.plate, ring) || overlaps(sh, ring))
                for (i in 0 until b.lines) {
                    val r = b.line[i]
                    assertTrue("$tag line $i", inside(r, b.plate))
                    if (i > 0) assertFalse("$tag lines overlap", overlaps(r, b.line[i - 1]))
                }
                val tomb = IRect(); val ci = IRect(); val cn = IRect()
                for (v in intArrayOf(10, 21, 999)) {
                    k.topRow(s, true, v, tomb, ci, cn)
                    for ((n, r) in listOf("tomb" to tomb, "cause icon" to ci, "cause number" to cn)) {
                        assertTrue("$tag $n $r in panel", inside(r, p))
                        assertFalse("$tag $n/number", overlaps(r, ring))
                        assertFalse("$tag $n/diamond", overlaps(r, l.diamond))
                    }
                    assertFalse("$tag tomb/cause", overlaps(tomb, ci) || overlaps(ci, cn) || overlaps(tomb, cn))
                }
            }
        }
    }

    @Test fun winnerCaptionStaysInTheUnderRowClearOfThePeekCorners() {
        for ((w, h) in screens) for (sc in 2..6) {
            val l = PlayLayout.compute(DitherGrid(w, h, sc))
            val k = KoLayout(l)
            val peek = au.com.josephmark.dithertracker.peeks.PeekLayout(l)
            val b = KoLayout.Block()
            for (line in Flavour.quippy.lines(QuipKind.WINNER) + Flavour.plain.lines(QuipKind.WINNER)) for (s in 0 until 4) {
                val tag = "$w×$h S=$sc seat $s '$line'"
                k.arrangeWinner(s, line.uppercase(), b)
                if (l.underH == 0) { assertEquals(tag, 0, b.lines); continue } // no under row (a layout never chosen)
                assertTrue("$tag shown", b.k >= 1 && b.lines >= 1)
                if (w == 1872 && sc <= 4) assertEquals("$tag whole caption", line.length, (0 until b.lines).sumOf { b.end[it] - b.start[it] } + b.lines - 1)
                for (i in 0 until b.lines) {
                    val r = b.line[i]
                    assertTrue("$tag line $i $r in under row ${l.underRow[s]}", inside(r, l.underRow[s]))
                    if (peek.enabled) for (col in 0..1) {
                        val c = IRect().also { peek.corner(s, col, it) }
                        assertFalse("$tag line $i over peek corner $col", overlaps(r, c))
                    }
                }
            }
        }
    }

    @Test fun nomadNumbers() {
        val k = KoLayout(PlayLayout.compute(DitherGrid(1872, 1404, 4)))
        assertEquals(2, k.iconK)
        assertEquals(2, k.quipKMax)
        assertEquals(1, k.causeK)
        assertEquals(64, k.winnerLeft) // between the peek corners (T38): x 64..169 at S = 4
        assertEquals(169, k.winnerRight)
        val b = KoLayout.Block()
        k.arrange(3, "COULDN'T STOP IT. DIDN'T TRY", b)
        assertEquals(2, b.k)
        assertEquals(2, b.lines)
    }

    @Test fun haloFillsTheTombstoneAndGrowsIt() {
        val icon = au.com.josephmark.dithertracker.dither.Icon.DEAD
        val h = Halo(icon, 2)
        val r = Halo(icon, 2, rotate180 = true)
        assertEquals(20, h.width); assertEquals(20, h.height)
        for (y in 0 until 16) for (x in 0 until 16) if (icon.isInk(x, y)) assertTrue(h.isInk(x + 2, y + 2))
        assertTrue(h.isInk(4 + 2, 9 + 2)) // enclosed paper inside the outline
        assertFalse(h.isInk(0, 0)) // far corner stays outside
        for (y in 0 until 20) for (x in 0 until 20) assertEquals(h.isInk(x, y), r.isInk(19 - x, 19 - y))
    }

    // ---- KO flip, cover, zones ---------------------------------------------------------------------

    @Test fun koFlipInvalidatesOnlyThatTile() {
        val r = Rig()
        r.rig.platform.invalidated.clear()
        r.kill(3)
        assertTrue(r.shown(3))
        val panel = IRect().also { r.l.grid.toDevice(r.l.panels[3], it) }
        assertTrue(r.rig.platform.invalidated.isNotEmpty())
        for (d in r.rig.platform.invalidated) assertTrue("$d in $panel", inside(d, panel))
        // Staying dead with another life change only redraws the cut-out number.
        r.rig.platform.invalidated.clear()
        r.session.dispatch(Action.ChangeLife(r.rig.id(3), -2), 0)
        val o = r.ko.layout!!.outline
        val n = r.l.numberSlot[3]
        val ring = IRect().also { r.l.grid.toDevice(IRect(n.left - o, n.top - o, n.right + o, n.bottom + o), it) }
        assertTrue(r.rig.platform.invalidated.isNotEmpty())
        for (d in r.rig.platform.invalidated) assertTrue("$d in $ring", inside(d, ring))
        // Others' changes leave the dead tile alone.
        r.rig.platform.invalidated.clear()
        r.session.dispatch(Action.ChangeLife(r.rig.id(1), -2), 0)
        for (d in r.rig.platform.invalidated) assertFalse(overlaps(d, panel))
    }

    @Test fun deadTileWinsLifeTabAndHidesContent() {
        val r = Rig()
        r.kill(2)
        val l = r.l
        assertEquals("ko:2", r.hit(l.zones[PlayLayout.zone(2, MINUS)]))
        assertEquals("ko:2", r.hit(l.zones[PlayLayout.zone(2, PLUS)]))
        assertEquals("ko:2", r.hit(l.tab[2].left + 2, l.tab[2].top + 2))
        assertEquals("ko:2", r.hit(l.numberSlot[2]))
        // Others untouched, the diamond still blocks.
        assertEquals("life:3:$MINUS", r.hit(l.zones[PlayLayout.zone(3, MINUS)]))
        assertEquals("tab:1", r.hit(l.tab[1].left + 2, l.tab[1].top + 2))
        assertFalse(r.hit(l.diamondCx, l.diamondCy).startsWith("ko")) // the diamond stays the menu's
        // Life, readout, tab hidden on the dead seat only; the KO component stays visible.
        assertFalse(r.rig.life.visible(2))
        assertFalse(r.rig.readout.visible(2))
        assertFalse(r.rig.tab.visible(2))
        assertTrue(r.rig.life.visible(3))
        assertTrue(r.ko.visible(2))
        assertEquals(Kind.STATUS, r.ko.kind)
        assertEquals(Layer.TILE, r.ko.layer)
        // Press feedback is the plate.
        assertEquals(r.ko.block(2).plate, r.rig.feedback(r.ko, 2, KoComponent.ZONE))
    }

    @Test fun poisonAndCommanderDamageKoWithTheirOwnQuips() {
        val r = Rig()
        r.session.dispatch(Action.SetCounter(r.rig.id(1), Counter.POISON, 10), 0)
        assertTrue(r.shown(1))
        assertTrue(r.ko.quip(1) in Flavour.quippy.lines(QuipKind.KO_POISON).map { it.uppercase() })
        r.session.dispatch(Action.ChangeCommanderDamage(r.rig.id(0), r.rig.id(2), Commander.PRIMARY, 21, linkLife = false), 0)
        assertTrue(r.shown(0))
        assertTrue(r.ko.quip(0) in Flavour.quippy.lines(QuipKind.KO_COMMANDER_DAMAGE).map { it.uppercase() })
    }

    // ---- revive -------------------------------------------------------------------------------------

    @Test fun firstTapArmsSecondTapRevives() {
        val r = Rig()
        r.kill(3)
        val panel = r.l.panels[3]
        r.tap(panel)
        assertTrue(r.armed(3))
        assertTrue(r.session.state.isKo(r.session.state.players[3]))
        r.tap(panel)
        assertFalse(r.armed(3))
        val p = r.session.state.players[3]
        assertFalse(r.session.state.isKo(p))
        assertEquals(0, p.life) // revive keeps the number; the override shows the player alive
        assertFalse(r.shown(3))
        assertEquals("life:3:$MINUS", r.hit(r.l.zones[PlayLayout.zone(3, MINUS)]))
    }

    @Test fun armTimesOut() {
        val r = Rig()
        r.kill(0)
        r.tap(r.l.panels[0])
        assertTrue(r.armed(0))
        r.clock.advance(KoComponent.ARM_MS - 1)
        assertTrue(r.armed(0))
        r.clock.advance(1)
        assertFalse(r.armed(0))
        // The next tap only arms again.
        r.tap(r.l.panels[0])
        assertTrue(r.armed(0))
        assertTrue(r.session.state.isKo(r.session.state.players[0]))
    }

    @Test fun armIsPerSeat() {
        val r = Rig()
        r.kill(0); r.kill(2)
        r.tap(r.l.panels[0])
        r.tap(r.l.panels[2])
        assertTrue(r.armed(0)); assertTrue(r.armed(2))
        r.tap(r.l.panels[2])
        assertTrue(r.shown(0)); assertFalse(r.shown(2))
    }

    // ---- quips ---------------------------------------------------------------------------------------

    @Test fun quipPickedOnceNotRerolledAndNotRepeated() {
        val r = Rig()
        val seen = ArrayList<String>()
        repeat(12) {
            r.kill(3)
            val q = r.ko.quip(3)
            assertTrue(q.isNotEmpty())
            // Redraws, other seats' changes and the dead player's own changes keep it.
            r.session.dispatch(Action.ChangeLife(r.rig.id(1), -1), 0)
            r.session.dispatch(Action.ChangeLife(r.rig.id(3), -1), 0)
            r.tap(r.l.panels[3]) // arm
            r.clock.advance(KoComponent.ARM_MS)
            assertEquals(q, r.ko.quip(3))
            if (seen.isNotEmpty()) assertNotEquals("no repeat of the previous pick", seen.last(), q)
            seen += q
            r.heal(3)
            assertEquals("", r.ko.quip(3))
        }
        assertTrue(seen.toSet().size > 3)
    }

    @Test fun causeChangeRepicks() {
        val r = Rig()
        r.session.dispatch(Action.SetCounter(r.rig.id(3), Counter.POISON, 10), 0)
        assertTrue(r.ko.quip(3) in Flavour.quippy.lines(QuipKind.KO_POISON).map { it.uppercase() })
        r.kill(3) // life wins the reason
        assertTrue(r.ko.quip(3) in Flavour.quippy.lines(QuipKind.KO_LIFE).map { it.uppercase() })
    }

    @Test fun restoredGameShowsDeadAtLayout() {
        val st = GameState.create(4)
        val dead = st.copy(players = st.players.mapIndexed { i, p -> if (i == 1) p.copy(life = -3) else p })
        val r = Rig(GameSession(History(dead)))
        assertTrue(r.shown(1))
        assertEquals("ko:1", r.hit(r.l.panels[1]))
        assertTrue(r.ko.quip(1).isNotEmpty())
    }

    // ---- drawer -------------------------------------------------------------------------------------

    @Test fun openDrawerClosesWhenItsPlayerDies() {
        val r = Rig()
        r.tap(r.l.tab[2])
        assertTrue(r.rig.drawer.state.isOpen(2))
        r.tap(r.l.tab[1])
        assertTrue(r.rig.drawer.state.isOpen(1))
        r.kill(2)
        assertFalse(r.rig.drawer.state.isOpen(2))
        assertTrue(r.rig.drawer.state.isOpen(1))
    }

    // ---- effects hook ----------------------------------------------------------------------------------

    @Test fun holdDefersTheDeadTileUntilReleased() {
        val r = Rig()
        r.stage.send(Events.KO_HOLD, 3)
        r.kill(3)
        assertFalse(r.shown(3))
        assertEquals("life:3:$MINUS", r.hit(r.l.zones[PlayLayout.zone(3, MINUS)]))
        r.rig.platform.invalidated.clear()
        r.stage.send(Events.KO_RELEASE, 3)
        assertTrue(r.shown(3))
        assertTrue(r.rig.platform.invalidated.isNotEmpty())
    }

    @Test fun aHoldRepeatStopsAtTheKoEvenWhileAnEffectHoldsTheTile() {
        val r = Rig()
        r.session.dispatch(Action.SetLife(r.rig.id(3), 10), 0)
        r.stage.send(Events.KO_HOLD, 3)
        val m = mid(r.l.zones[PlayLayout.zone(3, MINUS)])
        r.stage.down(9, m[0], m[1], r.clock.now)
        r.clock.advance(500) // first repeat: 10 -> 0, KO
        assertEquals(0, r.session.state.players[3].life)
        r.clock.advance(3000)
        assertEquals(0, r.session.state.players[3].life)
        r.stage.up(9)
    }

    @Test fun holdSentAfterTheFlipTakesTheTileDownAndTimesOut() {
        val r = Rig()
        r.kill(3)
        assertTrue(r.shown(3))
        r.stage.send(Events.KO_HOLD, 3)
        assertFalse(r.shown(3))
        r.clock.advance(KoComponent.HOLD_MAX_MS)
        assertTrue(r.shown(3))
    }

    @Test fun touchOnAHeldSeatReleases() {
        val r = Rig()
        r.stage.send(Events.KO_HOLD, 0)
        r.kill(0)
        r.tap(r.l.zones[PlayLayout.zone(0, PLUS)])
        assertTrue(r.shown(0))
        assertEquals(0, r.session.state.players[0].life) // the tap was cancelled, not applied
    }

    // ---- commander mode ----------------------------------------------------------------------------

    @Test fun targetDyingInCommanderModeStaysDeadAndModeContinues() {
        val r = Rig()
        r.stage.send(Events.COMMANDER_TARGET, 3)
        assertTrue(r.rig.cmd.mode.active)
        val zone0 = r.l.zones[PlayLayout.zone(0, PLUS)]
        assertTrue(r.hit(zone0).startsWith("cmd:0:"))
        // Seat 0 deals 21 commander damage to seat 3 (hold steps would do the same).
        r.session.dispatch(Action.ChangeCommanderDamage(r.rig.id(3), r.rig.id(0), Commander.PRIMARY, 21, linkLife = null), 0)
        assertTrue(r.shown(3))
        assertTrue(r.rig.cmd.mode.active)
        assertEquals("ko:3", r.hit(r.l.panels[3]))
        assertFalse(r.rig.cmd.visible(3))
        // Seat 0 can still take a point back, which brings the target back to life.
        r.tap(r.l.zones[PlayLayout.zone(0, MINUS)])
        assertFalse(r.shown(3))
        assertTrue(r.hit(r.l.panels[3]).startsWith("cmd:3:"))
    }

    @Test fun deadOpponentStaysDeadInCommanderMode() {
        val r = Rig()
        r.kill(1)
        r.stage.send(Events.COMMANDER_TARGET, 3)
        assertTrue(r.rig.cmd.mode.active)
        assertTrue(r.shown(1))
        assertTrue(r.ko.visible(1))
        assertFalse(r.rig.cmd.visible(1))
        assertEquals("ko:1", r.hit(r.l.zones[PlayLayout.zone(1, PLUS)]))
        assertTrue(r.hit(r.l.zones[PlayLayout.zone(0, PLUS)]).startsWith("cmd:0:"))
        r.rig.cmd.mode.exit()
        assertTrue(r.shown(1))
        assertEquals("ko:1", r.hit(r.l.panels[1]))
    }

    // ---- winner -------------------------------------------------------------------------------------

    @Test fun winnerCaptionAppearsAndDisappears() {
        val r = Rig()
        r.kill(0); r.kill(1)
        assertEquals(-1, r.winner.winner)
        r.rig.platform.invalidated.clear()
        r.kill(2)
        assertEquals(3, r.winner.winner)
        assertTrue(r.winner.caption.isNotEmpty())
        assertEquals(r.winner.caption, r.winner.caption.uppercase())
        assertTrue(r.winner.caption in Flavour.quippy.lines(QuipKind.WINNER).map { it.uppercase() })
        val under = IRect().also { r.l.grid.toDevice(r.l.underRow[3], it) }
        assertTrue(r.rig.platform.invalidated.any { overlaps(it, under) })
        // Revive one: no winner any more.
        r.tap(r.l.panels[2]); r.tap(r.l.panels[2])
        assertEquals(-1, r.winner.winner)
        assertEquals("", r.winner.caption)
        // Everyone dead: no winner either (seat 2 was revived at 0, so it has to be marked out).
        r.session.dispatch(Action.MarkDead(r.rig.id(2)), 0)
        r.kill(3)
        assertEquals(-1, r.winner.winner)
        assertEquals(KoReason.LIFE, r.session.state.koReason(r.session.state.players[3]))
    }

    @Test fun winnerHiddenInCommanderMode() {
        val r = Rig()
        r.kill(0); r.kill(1); r.kill(2)
        assertTrue(r.winner.visible(3))
        r.stage.send(Events.COMMANDER_TARGET, 3)
        assertFalse(r.winner.visible(3))
    }
}
