package au.com.josephmark.dithertracker.core

import au.com.josephmark.dithertracker.core.Action.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** T29: per-key coalescing groups with interleaved seats. */
class HistoryInterleavingTest {
    private val s0 = GameState.create(4)

    private fun History.tap(a: Action, t: Long) = dispatch(a, t, a.coalesceKey)
    private fun History.life(id: Int) = state.player(id)!!.life
    private fun fold(s: GameState, vararg actions: Action) = actions.fold(s, ::reduce)
    private fun life(p: Int, delta: Int, value: Int, source: Int? = null, slot: Int? = null) =
        LogEvent.LifeChanged(p, delta, value, source, slot)

    @Test fun interleavedTwoSeatsMakeOneStepAndOneLogEntryEach() {
        val a = ChangeLife(0, -1)
        val b = ChangeLife(1, -1)
        val h = History(s0).tap(a, 0).tap(b, 1).tap(a, 2).tap(a, 3)
        assertEquals(37, h.life(0))
        assertEquals(39, h.life(1))
        assertEquals(listOf("life:1", "life:0"), h.past.map { it.key })
        assertEquals(listOf(life(1, -1, 39), life(0, -3, 37)), h.log.map { it.event })
        assertEquals(3L, h.log.last().timestamp, "merged entry takes the newest timestamp")
    }

    @Test fun fourSeatsTappingAtOnce() {
        var h = History(s0)
        var t = 0L
        repeat(3) { for (seat in 0..3) h = h.tap(ChangeLife(seat, -1), t++) }
        assertEquals(4, h.past.size)
        assertEquals((0..3).map { life(it, -3, 37) }, h.log.map { it.event })
        assertEquals(listOf("life:0", "life:1", "life:2", "life:3"), h.openGroups)
        repeat(4) { h = h.undo() }
        assertEquals(s0, h.state)
        assertTrue(h.log.isEmpty())
    }

    @Test fun undoRemovesTheLastTouchedGroupAndNothingElse() {
        val a = ChangeLife(0, -1)
        val b = ChangeLife(1, -2)
        val c = ChangeLife(2, -3)
        var h = History(s0).tap(a, 0).tap(b, 1).tap(a, 2).tap(c, 3).tap(b, 4)
        assertEquals(listOf("life:0", "life:2", "life:1"), h.past.map { it.key })

        h = h.undo()
        assertEquals(listOf(38, 40, 37, 40), h.state.players.map { it.life }, "B's whole burst, A and C intact")
        assertFalse(h.isGroupOpen("life:0"), "undo closes every group")
        h = h.undo()
        assertEquals(listOf(38, 40, 40, 40), h.state.players.map { it.life })
        h = h.undo()
        assertEquals(s0, h.state)
        assertFalse(h.canUndo)
    }

    @Test fun undoKeepsUnchangedPlayerInstances() {
        val h = History(s0).tap(ChangeLife(0, -1), 0).tap(ChangeLife(1, -1), 1).tap(ChangeLife(0, -1), 2)
        val u = h.undo()
        assertSame(h.state.players[1], u.state.players[1], "B was replayed but is unchanged")
        assertSame(h.state.players[2], u.state.players[2])
        assertSame(u, u.dispatch(ChangeCounter(0, Counter.POISON, -1), 3, "counter:0:POISON"))
    }

    @Test fun undoOfLifeGroupReplaysInterleavedLinkedCommanderDamage() {
        val y = ChangeLife(0, -1)
        val x = ChangeCommanderDamage(0, 1, Commander.PRIMARY, 1)
        val z = ChangeLife(2, -2)
        var h = History(s0).tap(y, 0).tap(x, 1).tap(z, 2).tap(y, 3).tap(x, 4).tap(y, 5)
        assertEquals(35, h.life(0))
        assertEquals(listOf("life:2", "cmd:0:1:0", "life:0"), h.past.map { it.key })
        val full = h

        h = h.undo()
        assertEquals(fold(s0, x, z, x), h.state)
        assertEquals(DamageSlot(2, 2), h.state.player(0)!!.commanderDamage[1]!!.primary)
        assertEquals(
            listOf(life(2, -2, 38), LogEvent.CommanderDamageChanged(0, 1, 0, 2, 2), life(0, -2, 38, 1, 0)),
            h.log.map { it.event },
        )

        h = h.undo()
        assertEquals(fold(s0, z), h.state)
        assertEquals(38, h.life(2))
        assertEquals(full.present, h.redo().redo().present, "redo restores exactly")
    }

    @Test fun lifoRefundSurvivesReplay() {
        val y = { d: Int -> ChangeLife(0, d) }
        val x = { d: Int -> ChangeCommanderDamage(0, 1, Commander.PRIMARY, d) }
        // Damage +3 (takes 3 life), life +5, damage -1 (refunds 1), life +1; then undo the life group.
        var h = History(s0).tap(x(3), 0).tap(y(5), 1).tap(x(-1), 2).tap(y(1), 3)
        assertEquals(44, h.life(0))
        h = h.undo()
        assertEquals(fold(s0, x(3), x(-1)), h.state)
        assertEquals(38, h.life(0))
        assertEquals(DamageSlot(2, 2), h.state.player(0)!!.commanderDamage[1]!!.primary)

        // The other way round: undoing the damage group refunds nothing it didn't take.
        h = History(s0).tap(y(5), 0).tap(x(3), 1).tap(y(1), 2).tap(x(-1), 3)
        assertEquals(listOf("life:0", "cmd:0:1:0"), h.past.map { it.key })
        h = h.undo()
        assertEquals(fold(s0, y(5), y(1)), h.state)
        assertEquals(46, h.life(0))
    }

    @Test fun clampingAtTheLimitsIsReplayedNotInverted() {
        val x = ChangeCommanderDamage(0, 1, Commander.PRIMARY, 3)
        var h = History(s0).dispatch(SetLife(0, -995), 0)
        h = h.tap(ChangeLife(0, -3), 1).tap(x, 2).tap(ChangeLife(0, 5), 3)
        assertEquals(-994, h.life(0)) // -998, clamped at -999 by the damage, then +5
        h = h.undo()
        // Naively inverting the life group's net +2 would give -996; the true game without it is -998.
        assertEquals(fold(s0, SetLife(0, -995), x), h.state)
        assertEquals(-998, h.life(0))

        h = History(s0).dispatch(SetLife(0, 9998), 0)
        h = h.tap(ChangeLife(0, 5), 1).tap(x, 2).tap(ChangeLife(0, 1), 3)
        assertEquals(9997, h.life(0))
        h = h.undo()
        assertEquals(fold(s0, SetLife(0, 9998), x), h.state)
        assertEquals(9995, h.life(0))
    }

    @Test fun koFlipsAreReplayed() {
        // Seat 0 at 2: life +1, linked damage 2 (down to 1), seat 1 taps, life +1. Without the life
        // group the damage is lethal: undo must surface that KO, in the damage group's log block.
        val x = ChangeCommanderDamage(0, 1, Commander.PRIMARY, 2)
        val full = History(s0).dispatch(SetLife(0, 2), 0)
            .tap(ChangeLife(0, 1), 1).tap(x, 2).tap(ChangeLife(1, -1), 3).tap(ChangeLife(0, 1), 4)
        assertFalse(full.state.isKo(0))
        assertTrue(full.log.none { it.event is LogEvent.KnockedOut })

        val h = full.undo()
        assertEquals(fold(s0, SetLife(0, 2), x, ChangeLife(1, -1)), h.state)
        assertTrue(h.state.isKo(0))
        assertEquals(39, h.life(1))
        assertEquals(
            listOf(
                life(0, -38, 2),
                LogEvent.CommanderDamageChanged(0, 1, 0, 2, 2), life(0, -2, 0, 1, 0), LogEvent.KnockedOut(0, KoReason.LIFE),
                life(1, -1, 39),
            ),
            h.log.map { it.event },
        )
        assertEquals(full.present, h.redo().present)

        // And the forward flip: a life group that KOs, undone around another seat's taps.
        var k = History(s0).dispatch(SetLife(0, 2), 0)
        k = k.tap(ChangeLife(0, -1), 1).tap(ChangeLife(1, -1), 2).tap(ChangeLife(0, -1), 3)
        assertEquals(listOf(life(0, -2, 0), LogEvent.KnockedOut(0, KoReason.LIFE)), k.log.takeLast(2).map { it.event })
        k = k.undo()
        assertFalse(k.state.isKo(0))
        assertEquals(fold(s0, SetLife(0, 2), ChangeLife(1, -1)), k.state)
    }

    @Test fun reviveOverrideIsRestoredByUndoReplay() {
        var h = History(s0).dispatch(SetLife(0, 0), 0).dispatch(Revive(0), 1)
        assertEquals(KoOverride.ALIVE, h.state.player(0)!!.koOverride)
        h = h.tap(ChangeLife(0, 1), 2).tap(ChangeLife(1, -1), 3).tap(ChangeLife(0, -1), 4)
        assertEquals(KoOverride.NONE, h.state.player(0)!!.koOverride, "recovering cleared the override")
        assertTrue(h.state.isKo(0))
        h = h.undo()
        assertEquals(fold(s0, SetLife(0, 0), Revive(0), ChangeLife(1, -1)), h.state)
        assertEquals(KoOverride.ALIVE, h.state.player(0)!!.koOverride)
        assertFalse(h.state.isKo(0))
    }

    @Test fun monarchChangesInterleaveAsSingleSteps() {
        val a = ChangeLife(0, -1)
        val b = ChangeLife(1, -1)
        var h = History(s0).tap(a, 0).tap(b, 1).tap(a, 2)
        h = h.tap(SetToggle(2, Toggle.MONARCH, true), 3) // null key: closes both groups
        assertTrue(h.openGroups.isEmpty())
        h = h.tap(a, 4).tap(b, 5).tap(a, 6)
        h = h.tap(SetToggle(3, Toggle.MONARCH, true), 7)
        assertEquals(listOf("life:1", "life:0", null, "life:1", "life:0", null), h.past.map { it.key })
        assertEquals(listOf(false, false, false, true), h.state.players.map { it.toggles[Toggle.MONARCH] })

        h = h.undo()
        assertEquals(listOf(false, false, true, false), h.state.players.map { it.toggles[Toggle.MONARCH] })
        assertEquals(listOf(36, 38), listOf(h.life(0), h.life(1)))
        h = h.undo() // seat 0's second burst
        assertEquals(listOf(38, 38), listOf(h.life(0), h.life(1)))
        assertTrue(h.state.player(2)!!.toggles[Toggle.MONARCH])
        h = h.undo().undo()
        assertEquals(listOf(false, false, false, false), h.state.players.map { it.toggles[Toggle.MONARCH] })
        assertEquals(listOf(38, 39), listOf(h.life(0), h.life(1)))
    }

    @Test fun redoIsSymmetric() {
        var h = History(s0)
        var t = 0L
        for (seat in listOf(0, 1, 0, 2, 1, 0, 3)) h = h.tap(ChangeCommanderDamage(seat, (seat + 1) % 4, 0, 2), t++)
        h = h.tap(ChangeLife(1, -4), t++).tap(ChangeCounter(2, Counter.POISON, 3), t++)
        val snapshots = ArrayList<Game>()
        while (h.canUndo) {
            snapshots += h.present
            h = h.undo()
        }
        assertEquals(s0, h.state)
        for (g in snapshots.asReversed()) {
            h = h.redo()
            assertEquals(g, h.present)
        }
        assertFalse(h.canRedo)
    }

    @Test fun eachKeyExpiresOnItsOwnWindow() {
        val a = ChangeLife(0, -1)
        val b = ChangeLife(1, -1)
        var h = History(s0, coalesceWindowMillis = 1000)
        h = h.tap(a, 0).tap(b, 500).tap(a, 900)
        h = h.tap(b, 1600) // 1100 after B's last: a new group
        h = h.tap(a, 1800) // 900 after A's last: same group
        assertEquals(listOf("life:1", "life:1", "life:0"), h.past.map { it.key })
        assertEquals(listOf(false, true, true), h.past.map { it.isOpen })
        assertEquals(listOf(life(1, -1, 39), life(1, -1, 38), life(0, -3, 37)), h.log.map { it.event })
        h = h.tap(a, 5000)
        assertEquals(4, h.past.size)
        assertEquals(listOf("life:0"), h.openGroups, "B's group was seen to expire too")
    }

    @Test fun endGroupForOneKeyLeavesTheOthersOpen() {
        val a = ChangeLife(0, -1)
        val b = ChangeLife(1, -1)
        var h = History(s0).tap(a, 0).tap(b, 1).endGroup("life:0")
        assertSame(h, h.endGroup("life:0"))
        h = h.tap(a, 2).tap(b, 3)
        assertEquals(listOf("life:0", "life:0", "life:1"), h.past.map { it.key })
        assertEquals("life:1", h.openGroup, "compat: most recently touched open group")
        h = h.endGroup()
        assertTrue(h.openGroups.isEmpty())
        assertEquals(null, h.openGroup)
    }

    @Test fun interleavedGroupsThatNetToZeroDisappear() {
        var h = History(s0).tap(ChangeLife(0, 1), 0).tap(ChangeLife(1, -1), 1).tap(ChangeLife(0, -1), 2)
        assertEquals(listOf("life:1"), h.past.map { it.key })
        assertEquals(listOf(life(1, -1, 39)), h.log.map { it.event })
        assertEquals(40, h.life(0))
        h = h.tap(ChangeLife(0, -1), 3)
        assertEquals(2, h.past.size, "the cancelled group is gone, so this starts a new one")

        // Linked damage dealt and taken back around another seat's taps.
        val x = { d: Int -> ChangeCommanderDamage(0, 1, Commander.PRIMARY, d) }
        h = History(s0).tap(x(2), 0).tap(ChangeLife(2, -1), 1).tap(x(-2), 2)
        assertEquals(listOf("life:2"), h.past.map { it.key })
        assertEquals(fold(s0, ChangeLife(2, -1)), h.state)
        assertEquals(listOf(life(2, -1, 39)), h.log.map { it.event })
    }

    @Test fun depthIsBoundedWithManyOpenGroups() {
        val six = GameState.create(6)
        var h = History(six, maxDepth = 3)
        var t = 0L
        for (seat in 0..5) h = h.tap(ChangeLife(seat, -1), t++)
        assertEquals(listOf("life:3", "life:4", "life:5"), h.past.map { it.key })
        assertEquals(6, h.log.size, "dropped steps stay in the log")
        // A group pushed out by the depth bound is permanent and closed: its seat's next tap
        // starts a new group (and log entry), even inside the window.
        for (seat in 0..5) h = h.tap(ChangeLife(seat, -1), t++)
        assertEquals(listOf("life:3", "life:4", "life:5"), h.past.map { it.key })
        assertEquals(12, h.log.size)
        assertTrue(h.state.players.all { it.life == 38 })
        repeat(3) { h = h.undo() }
        assertFalse(h.canUndo)
        assertEquals(listOf(38, 38, 38, 39, 39, 39), h.state.players.map { it.life })
        assertEquals(9, h.log.size)
        h = h.tap(ChangeLife(0, -1), t)
        assertEquals(1, h.past.size)
    }

    @Test fun replayRecordsStayBoundedWhenGroupsNeverClose() {
        var h = History(s0, maxDepth = 4)
        var t = 0L
        val all = ArrayList<Action>()
        repeat(History.MAX_RECORDS) { for (seat in 0..3) { val a = ChangeLife(seat, if (it % 2 == 0) -1 else 1); all += a; h = h.tap(a, t); t += 50 } }
        assertTrue(h.recordCount <= History.MAX_RECORDS + 1, "records: ${h.recordCount}")
        assertEquals(all.fold(s0, ::reduce), h.state)
        while (h.canUndo) h = h.undo()
        assertTrue(h.past.isEmpty())
    }

    // ---- randomized comparison against a from-scratch reference ---------------------------

    @Test fun randomInterleavingsMatchTheReference() {
        var interleavedUndos = 0
        var cancelledGroups = 0
        for (seed in 1..300) {
            val r = Random(seed)
            val window = 3000L
            var h = History(s0, maxDepth = 100_000, coalesceWindowMillis = window)
            val ref = Reference(s0, window)
            var t = 0L
            repeat(150) { step ->
                val op = r.nextInt(100)
                val what: String
                when {
                    op < 74 -> {
                        val a = randomAction(r)
                        val key = if (r.nextInt(10) == 0) null else a.coalesceKey
                        t += if (r.nextInt(8) == 0) 4000L else r.nextLong(0, 1500)
                        what = "dispatch $a @$t key=$key"
                        val before = h
                        h = h.dispatch(a, t, key)
                        if (h.past.size < before.past.size) cancelledGroups++
                        if (reduce(before.state, a) === before.state) assertSame(before, h, what)
                        ref.dispatch(a, t, key)
                    }
                    op < 84 -> {
                        what = "undo"
                        if (h.past.count { it.isOpen } >= 2) interleavedUndos++
                        h = h.undo(); ref.undo()
                    }
                    op < 92 -> { what = "redo"; h = h.redo(); ref.redo() }
                    op < 96 -> { what = "endGroup"; h = h.endGroup(); ref.endGroup(null) }
                    else -> {
                        val k = ChangeLife(r.nextInt(4), 1).coalesceKey!!
                        what = "endGroup $k"; h = h.endGroup(k); ref.endGroup(k)
                    }
                }
                val ctx = "seed $seed step $step: $what"
                assertEquals(ref.state, h.state, ctx)
                assertEquals(ref.log, h.log, ctx)
                assertEquals(ref.keys, h.past.map { it.key }, ctx)
                assertEquals(ref.open, h.past.map { it.isOpen }, ctx)
                assertEquals(ref.canRedo, h.canRedo, ctx)
            }
        }
        assertTrue(interleavedUndos > 200, "coverage: $interleavedUndos undos with several groups open")
        assertTrue(cancelledGroups > 20, "coverage: $cancelledGroups groups cancelled out")
    }

    @Test fun referenceSanity() {
        // The reference itself: surviving actions replayed in order.
        val ref = Reference(s0, 3000)
        ref.dispatch(ChangeLife(0, -1), 0, "life:0")
        ref.dispatch(ChangeLife(1, -1), 1, "life:1")
        ref.dispatch(ChangeLife(0, -1), 2, "life:0")
        ref.undo()
        assertEquals(fold(s0, ChangeLife(1, -1)), ref.state)
        assertNotEquals(s0, ref.state)
    }

    private fun randomAction(r: Random): Action {
        val p = r.nextInt(4)
        val q = (p + 1 + r.nextInt(3)) % 4
        return when (r.nextInt(100)) {
            in 0..29 -> ChangeLife(p, listOf(-1, -1, 1, -3, 5, -7).random(r))
            in 30..35 -> ChangeLife(p, listOf(-600, 600, 5000, -5000).random(r))
            in 36..45 -> ChangeCounter(p, Counter.POISON, listOf(-1, 1, 3, -4).random(r))
            in 46..51 -> ChangeCommanderTax(p, r.nextInt(2), listOf(2, -2).random(r))
            in 52..77 -> ChangeCommanderDamage(
                p, q, if (r.nextInt(5) == 0) Commander.PARTNER else Commander.PRIMARY,
                listOf(1, 2, 7, -1, -3, -8).random(r), listOf(null, null, true, false).random(r),
            )
            in 78..81 -> SetToggle(p, Toggle.MONARCH, r.nextBoolean())
            in 82..84 -> SetLife(p, listOf(1, 0, -995, 9997).random(r))
            in 85..86 -> Revive(p)
            87 -> MarkDead(p)
            in 88..89 -> NextTurn
            in 90..91 -> SetPartner(p, r.nextBoolean())
            92 -> UpdateSettings(GameSettings(commanderDamageAffectsLife = r.nextBoolean()))
            93 -> NewGame
            else -> ChangeLife(p, -1)
        }
    }
}

/**
 * A deliberately naive model of [History] (no depth bound): the surviving actions in chronological
 * order plus the groups in last-touched order; state and log are recomputed from scratch every time.
 */
private class Reference(private val s0: GameState, private val window: Long) {
    private data class R(val action: Action, val ts: Long, val group: Int)
    private data class G(val id: Int, val key: String?, val last: Long, val open: Boolean, val fresh: Boolean)
    private class Replay(val state: GameState, val entries: List<List<LogEntry>>, val effective: List<Boolean>)

    private var recs = listOf<R>()
    private var groups = listOf<G>()
    private val redoStack = ArrayList<Pair<List<R>, List<G>>>()
    private var nextId = 0

    private fun replay(rs: List<R>): Replay {
        var s = s0
        val entries = ArrayList<List<LogEntry>>()
        val effective = ArrayList<Boolean>()
        for (r in rs) {
            val n = reduce(s, r.action)
            entries += if (n === s) emptyList() else describe(s, n, r.action, r.ts)
            effective += n !== s
            s = n
        }
        return Replay(s, entries, effective)
    }

    private fun block(g: G, rp: Replay): List<LogEntry> {
        var b: List<LogEntry>? = null
        for (i in recs.indices) if (recs[i].group == g.id) b = if (b == null) rp.entries[i] else mergeInto(b, 0, rp.entries[i])
        return b ?: emptyList()
    }

    val state: GameState get() = replay(recs).state
    val log: List<LogEntry>
        get() {
            val rp = replay(recs)
            var out = listOf<LogEntry>()
            for (g in groups) out = if (g.fresh) block(g, rp) else out + block(g, rp)
            return out
        }
    val keys get() = groups.map { it.key }
    val open get() = groups.map { it.open }
    val canRedo get() = redoStack.isNotEmpty()

    private fun closeAll() { groups = groups.map { it.copy(open = false) } }

    private fun dropDead() {
        val rp = replay(recs)
        val alive = recs.indices.filter { rp.effective[it] }.map { recs[it].group }.toSet()
        val dead = groups.map { it.id }.filter { it !in alive }.toSet()
        recs = recs.filter { it.group !in dead }
        groups = groups.filter { it.id !in dead }
    }

    fun dispatch(a: Action, ts: Long, key: String?) {
        val cur = state
        if (reduce(cur, a) === cur) return
        redoStack.clear()
        groups = if (key == null) groups.map { it.copy(open = false) }
        else groups.map { if (it.open && ts - it.last !in 0..window) it.copy(open = false) else it }
        val gi = if (key == null) -1 else groups.indexOfLast { it.open && it.key == key }
        if (gi < 0) {
            val g = G(nextId++, key, ts, key != null, a == NewGame || a == Reset)
            groups = groups + g
            recs = recs + R(a, ts, g.id)
            return
        }
        val g = groups[gi].copy(last = ts)
        groups = groups.filterIndexed { i, _ -> i != gi } + g
        recs = recs + R(a, ts, g.id)
        val rp = replay(recs)
        if (block(g, rp).isEmpty()) {
            val without = recs.filter { it.group != g.id }
            if (replay(without).state == rp.state) {
                recs = without
                groups = groups.filter { it.id != g.id }
                dropDead()
            }
        }
    }

    fun undo() {
        if (groups.isEmpty()) return closeAll()
        closeAll()
        redoStack += recs to groups
        val g = groups.last()
        recs = recs.filter { it.group != g.id }
        groups = groups.dropLast(1)
        dropDead()
    }

    fun redo() {
        if (redoStack.isEmpty()) return closeAll()
        val (r, g) = redoStack.removeAt(redoStack.lastIndex)
        recs = r
        groups = g
    }

    fun endGroup(key: String?) {
        groups = groups.map { if (it.open && (key == null || it.key == key)) it.copy(open = false) else it }
    }
}
