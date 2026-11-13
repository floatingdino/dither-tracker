package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerControllerTest {

    private val session = GameSession.newGame(4)
    private val dispatched = ArrayList<Pair<Int, Action>>()
    private val invalidated = ArrayList<IRect>()
    private val cancelled = ArrayList<IntRange>()
    private var counters: List<Counter> = Counter.entries.toList()

    private val host = object : DrawerController.Host {
        override val game: GameState get() = session.state
        override val drawerCounters: List<Counter> get() = counters
        override fun dispatch(seat: Int, action: Action) {
            dispatched += seat to action
            session.dispatch(action, 0)
        }
        override fun invalidate(rect: IRect) { invalidated += IRect(rect.left, rect.top, rect.right, rect.bottom) }
        override fun cancelTouches(from: Int, until: Int) { cancelled += from until until }
    }

    private val layout = DrawerLayout(PlayLayout.compute(DitherGrid(1872, 1404, 4)))
    private val state = DrawerState(4)
    private val c = DrawerController(state, host).also { it.layout = layout }

    init {
        session.listener = GameSession.Listener { old, new -> c.onStateChanged(old, new) }
    }

    private fun id(seat: Int) = session.state.players[seat].id
    private fun tap(seat: Int, local: Int) = c.onTap(DrawerZones.zone(seat, local))
    private fun tapCell(seat: Int, slot: Int, half: Int) = c.onTap(DrawerZones.cellZone(seat, slot, half))
    private fun items(seat: Int) = (0 until state.itemsOnPage(seat)).map { c.itemAt(seat, it) }

    @Test fun tabTogglesAndRedrawsThePanel() {
        assertFalse(state.isOpen(3))
        assertTrue(tap(3, DrawerZones.TAB))
        assertTrue(state.isOpen(3))
        assertEquals(listOf(layout.play.panels[3]), invalidated)
        assertEquals(listOf(DrawerZones.seatFirst(3) until DrawerZones.seatEnd(3)), cancelled)
        tap(3, DrawerZones.TAB)
        assertFalse(state.isOpen(3))
        assertEquals(2, invalidated.size)
        assertTrue(dispatched.isEmpty())
    }

    @Test fun seatsAreIndependent() {
        tap(0, DrawerZones.TAB); tap(2, DrawerZones.TAB)
        assertTrue(state.isOpen(0)); assertTrue(state.isOpen(2))
        assertFalse(state.isOpen(1)); assertFalse(state.isOpen(3))
        tap(0, DrawerZones.HEADER)
        assertEquals(DrawerState.COMMANDER, state.mode(0)); assertEquals(DrawerState.TRAY, state.mode(2))
    }

    @Test fun closedDrawerIgnoresEverythingButTheTab() {
        assertFalse(tap(1, DrawerZones.HEADER))
        assertFalse(tap(1, DrawerZones.NEXT))
        assertFalse(tapCell(1, 0, DrawerZones.PLUS))
        assertTrue(dispatched.isEmpty())
    }

    @Test fun trayItemsAndPaging() {
        tap(3, DrawerZones.TAB)
        // 1 tax + 7 counters = 8 items, 6 per page → 2 pages
        assertEquals(8, c.itemCount(3)); assertEquals(2, state.pageCount(3)); assertEquals(0, state.page(3))
        assertEquals(DrawerItems.item(DrawerItems.TAX, Commander.PRIMARY), c.itemAt(3, 0))
        assertEquals(DrawerItems.item(DrawerItems.COUNTER, Counter.POISON.ordinal), c.itemAt(3, 1))
        assertEquals(6, state.itemsOnPage(3))
        assertFalse(tap(3, DrawerZones.PREV))
        invalidated.clear()
        assertTrue(tap(3, DrawerZones.NEXT))
        assertEquals(1, state.page(3)); assertEquals(2, state.itemsOnPage(3))
        assertEquals(listOf(layout.drawer[3]), invalidated)
        assertEquals(
            listOf(DrawerItems.item(DrawerItems.COUNTER, Counter.TICKETS.ordinal), DrawerItems.item(DrawerItems.COUNTER, Counter.GENERIC.ordinal)),
            items(3),
        )
        assertEquals(-1, c.itemAt(3, 2))
        assertFalse(tap(3, DrawerZones.NEXT))
        assertTrue(tap(3, DrawerZones.PREV))
        assertEquals(0, state.page(3))
    }

    @Test fun trayFollowsTheConfiguredCountersInOrder() {
        counters = listOf(Counter.STORM, Counter.POISON)
        tap(2, DrawerZones.TAB)
        assertEquals(
            listOf(DrawerItems.item(DrawerItems.TAX, 0), DrawerItems.item(DrawerItems.COUNTER, Counter.STORM.ordinal), DrawerItems.item(DrawerItems.COUNTER, Counter.POISON.ordinal)),
            items(2),
        )
        assertEquals(1, state.pageCount(2))
        counters = listOf(Counter.ENERGY)
        c.onCountersChanged()
        assertEquals(listOf(DrawerItems.item(DrawerItems.TAX, 0), DrawerItems.item(DrawerItems.COUNTER, Counter.ENERGY.ordinal)), items(2))
    }

    @Test fun partnerAddsASecondTaxCell() {
        session.dispatch(Action.SetPartner(id(1), true), 0)
        tap(1, DrawerZones.TAB)
        assertEquals(DrawerItems.item(DrawerItems.TAX, Commander.PARTNER), c.itemAt(1, 1))
        assertEquals(9, c.itemCount(1))
    }

    @Test fun counterTapsDispatchChangeCounter() {
        tap(3, DrawerZones.TAB)
        tapCell(3, 1, DrawerZones.PLUS) // poison +1
        tapCell(3, 1, DrawerZones.PLUS)
        tapCell(3, 1, DrawerZones.MINUS)
        assertEquals(Action.ChangeCounter(id(3), Counter.POISON, 1), dispatched[0].second)
        assertEquals(Action.ChangeCounter(id(3), Counter.POISON, -1), dispatched[2].second)
        assertTrue(dispatched.all { it.first == 3 })
        assertEquals(1, session.state.players[3].counters.poison)
        // clamps at 0: further minus is a no-op in core
        tapCell(3, 1, DrawerZones.MINUS); tapCell(3, 1, DrawerZones.MINUS)
        assertEquals(0, session.state.players[3].counters.poison)
    }

    @Test fun taxStepsByTwoAndDoesNotRepeat() {
        tap(0, DrawerZones.TAB)
        tapCell(0, 0, DrawerZones.PLUS)
        assertEquals(Action.ChangeCommanderTax(id(0), Commander.PRIMARY, 2), dispatched.single().second)
        assertEquals(2, session.state.players[0].commanderTax.primary)
        assertFalse(c.repeats(DrawerZones.cellZone(0, 0, DrawerZones.PLUS)))
        assertTrue(c.repeats(DrawerZones.cellZone(0, 1, DrawerZones.PLUS)))
        assertFalse(c.repeats(DrawerZones.zone(0, DrawerZones.HEADER)))
        assertFalse(c.onRepeat(DrawerZones.cellZone(0, 0, DrawerZones.PLUS)))
    }

    @Test fun holdRepeatsCountersByFive() {
        tap(0, DrawerZones.TAB)
        assertTrue(c.onRepeat(DrawerZones.cellZone(0, 2, DrawerZones.PLUS))) // energy
        assertEquals(Action.ChangeCounter(id(0), Counter.ENERGY, 5), dispatched.single().second)
    }

    @Test fun commanderViewMapsOpponentsClockwiseWithPartners() {
        session.dispatch(Action.SetPartner(id(1), true), 0)
        tap(3, DrawerZones.TAB)
        tap(3, DrawerZones.HEADER)
        assertEquals(DrawerState.COMMANDER, state.mode(3))
        fun dmg(src: Int, slot: Int) = DrawerItems.item(DrawerItems.DAMAGE, src * 2 + slot)
        // seat 3 (BL): clockwise 0 TL, 1 TR (+ partner), 2 BR
        assertEquals(listOf(dmg(0, 0), dmg(1, 0), dmg(1, 1), dmg(2, 0)), items(3))
        assertEquals(1, state.pageCount(3))
        // seat 0 (TL): 1, 1P, 2, 3
        tap(0, DrawerZones.TAB); tap(0, DrawerZones.HEADER)
        assertEquals(listOf(dmg(1, 0), dmg(1, 1), dmg(2, 0), dmg(3, 0)), items(0))
        // back to the tray
        tap(3, DrawerZones.HEADER)
        assertEquals(DrawerState.TRAY, state.mode(3))
    }

    @Test fun commanderDamageDispatchesWithGameLifeLinkAndTakesLife() {
        tap(3, DrawerZones.TAB); tap(3, DrawerZones.HEADER)
        val life = session.state.players[3].life
        tapCell(3, 0, DrawerZones.PLUS) // from seat 0
        tapCell(3, 2, DrawerZones.PLUS) // from seat 2
        tapCell(3, 2, DrawerZones.PLUS)
        assertEquals(Action.ChangeCommanderDamage(id(3), id(0), Commander.PRIMARY, 1, linkLife = null), dispatched[0].second)
        assertEquals(Action.ChangeCommanderDamage(id(3), id(2), Commander.PRIMARY, 1, linkLife = null), dispatched[1].second)
        assertEquals(life - 3, session.state.players[3].life)
        assertEquals(1, session.state.players[3].commanderDamageFrom(id(0), 0))
        assertEquals(2, session.state.players[3].commanderDamageFrom(id(2), 0))
        // minus refunds
        tapCell(3, 2, DrawerZones.MINUS)
        assertEquals(life - 2, session.state.players[3].life)
    }

    @Test fun lethalAndValues() {
        tap(3, DrawerZones.TAB); tap(3, DrawerZones.HEADER)
        repeat(4) { c.onRepeat(DrawerZones.cellZone(3, 1, DrawerZones.PLUS)) } // 20
        tapCell(3, 1, DrawerZones.PLUS) // 21
        val item = c.itemAt(3, 1)
        val v = DrawerItems.value(item, session.state.players[3], session.state.players)
        assertEquals(21, v)
        assertTrue(DrawerItems.isLethal(item, v))
        assertFalse(DrawerItems.isLethal(item, 20))
        assertFalse(DrawerItems.isLethal(DrawerItems.item(DrawerItems.COUNTER, 0), 30))
    }

    @Test fun aValueChangeRedrawsJustThatCell() {
        tap(3, DrawerZones.TAB)
        invalidated.clear()
        tapCell(3, 2, DrawerZones.PLUS) // energy
        assertEquals(listOf(layout.cellVis[3 * DrawerZones.MAX_SLOTS + 2]), invalidated)
        invalidated.clear()
        // a change on another (closed) seat redraws nothing of this drawer
        session.changeLife(1, -1, 0)
        assertTrue(invalidated.isEmpty())
    }

    @Test fun partnerChangeRebuildsAnOpenCommanderView() {
        tap(3, DrawerZones.TAB); tap(3, DrawerZones.HEADER)
        assertEquals(3, c.itemCount(3))
        invalidated.clear(); cancelled.clear()
        session.dispatch(Action.SetPartner(id(2), true), 0)
        assertEquals(4, c.itemCount(3))
        assertTrue(invalidated.contains(layout.drawer[3]))
        assertEquals(1, cancelled.size)
    }

    @Test fun drawerActionsUseTheirOwnCoalesceKeys() {
        tap(3, DrawerZones.TAB)
        // a burst on one counter is one undo step
        repeat(3) { tapCell(3, 1, DrawerZones.PLUS) }
        session.undo()
        assertEquals(0, session.state.players[3].counters.poison)
    }

    @Test fun itemsHelpers() {
        val p = session.state.players[0]
        assertNull(DrawerItems.action(DrawerItems.item(9, 0), p, session.state.players, 1))
        assertEquals(2, DrawerItems.tapDelta(DrawerItems.item(DrawerItems.TAX, 0), DrawerZones.PLUS))
        assertEquals(-1, DrawerItems.tapDelta(DrawerItems.item(DrawerItems.COUNTER, 0), DrawerZones.MINUS))
        assertEquals(-5, DrawerItems.holdDelta(DrawerZones.MINUS))
    }
}
