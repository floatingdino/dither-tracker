package au.com.josephmark.dithertracker.core.flavour

import au.com.josephmark.dithertracker.core.CoinFace
import au.com.josephmark.dithertracker.core.KoReason
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class FlavourTest {
    private val both = listOf(Flavour.quippy, Flavour.plain)

    // --- Budgets -------------------------------------------------------------------------------

    @Test fun everyLineFitsCharsetAndBudget() {
        for (f in both) for (kind in QuipKind.entries) for (line in f.lines(kind)) {
            assertStyled(line, "$kind")
            if (kind == QuipKind.RESET_PROMPT) {
                val wrapped = wrap(line, LINE_MAX)
                assertTrue(wrapped.size <= 2, "prompt wraps to ${wrapped.size} lines: $wrapped")
            } else {
                assertTrue(line.length <= LINE_MAX, "$kind longer than $LINE_MAX: '$line' (${line.length})")
                assertFalse(line.endsWith("?") && kind != QuipKind.WINNER, "$kind caption is a question: '$line'")
            }
        }
    }

    @Test fun buttonsAndNamesFitBudget() {
        for (f in both) {
            for (b in f.resetButtons) for (label in listOf(b.confirm, b.cancel)) {
                assertStyled(label, "button")
                assertTrue(label.length <= LABEL_MAX, "button too long: '$label'")
            }
            for (set in f.nameSets) {
                assertEquals(6, set.names.size)
                assertEquals(set.names.size, set.names.toSet().size, "duplicate name in ${set.title}")
                for (name in set.names) {
                    assertStyled(name, "name")
                    assertTrue(name.length <= LABEL_MAX, "name too long: '$name'")
                }
            }
        }
    }

    @Test fun verbatimQuotesAreAllInTheTablesAndOnlyWhereTheyFit() {
        val hero = listOf(QuipKind.KO_LIFE, QuipKind.KO_POISON, QuipKind.KO_COMMANDER_DAMAGE, QuipKind.KO_MANUAL, QuipKind.WINNER)
        val lines = hero.flatMap { Flavour.quippy.lines(it) }
        for (q in QuipTables.verbatim) assertTrue(q in lines, "verbatim line not in a KO/winner table: '$q'")
        // Each table lists its own lines first, then its verbatim quotes (the doc mirrors that split).
        for (kind in hero) {
            val l = Flavour.quippy.lines(kind)
            val first = l.indexOfFirst { it in QuipTables.verbatim }
            if (first >= 0) assertTrue(l.drop(first).all { it in QuipTables.verbatim }, "$kind mixes verbatim and own lines")
        }
    }

    @Test fun noDuplicatesWithinOrAcrossCategories() {
        val all = QuipKind.entries.flatMap { Flavour.quippy.lines(it) } +
            Flavour.quippy.resetButtons.flatMap { listOf(it.confirm, it.cancel) }
        val seen = HashSet<String>()
        for (line in all) assertTrue(seen.add(line.lowercase()), "duplicate line: '$line'")
        val names = Flavour.quippy.nameSets.flatMap { it.names }
        assertEquals(names.size, names.map { it.lowercase() }.toSet().size, "a name appears in two sets")
    }

    @Test fun categoriesHaveRoughlyTheAgreedCounts() {
        val min = mapOf(
            QuipKind.KO_LIFE to 20, QuipKind.KO_POISON to 10, QuipKind.KO_COMMANDER_DAMAGE to 10,
            QuipKind.KO_MANUAL to 6, QuipKind.STARTING_PLAYER to 6, QuipKind.D20_NATURAL_1 to 4,
            QuipKind.D20_NATURAL_20 to 4, QuipKind.DICE_ROLL to 4, QuipKind.COIN_HEADS to 2,
            QuipKind.COIN_TAILS to 2, QuipKind.RESET_PROMPT to 4, QuipKind.EMPTY_HISTORY to 3,
            QuipKind.WINNER to 6, QuipKind.COMMANDER_TARGET to 10,
        )
        for (kind in QuipKind.entries) {
            val n = Flavour.quippy.lines(kind).size
            assertTrue(n >= min.getValue(kind), "$kind has only $n lines")
        }
        assertTrue(Flavour.quippy.resetButtons.size >= 3)
        assertTrue(Flavour.quippy.nameSets.size >= 4)
    }

    // --- Coverage ------------------------------------------------------------------------------

    @Test fun everyKoReasonHasLines() {
        for (f in both) for (reason in KoReason.entries) {
            val kind = QuipKind.ko(reason)
            assertTrue(f.lines(kind).isNotEmpty())
            assertTrue(f.ko(reason, Random(1)) in f.lines(kind))
        }
        assertEquals(KoReason.entries.size, KoReason.entries.map(QuipKind::ko).toSet().size)
    }

    @Test fun plainSetIsCompleteAndNeutral() {
        val p = Flavour.plain
        assertFalse(p.quips)
        for (kind in QuipKind.entries) assertEquals(1, p.lines(kind).size, "$kind")
        assertEquals("Knocked out", p.ko(KoReason.LIFE, Random(0)))
        assertEquals(ResetConfirm("Reset game?", ConfirmButtons("Yes", "No")), p.resetConfirm(Random(0)))
        assertEquals("No history yet", p.emptyHistory(Random(0)))
        assertEquals(listOf(p.defaultNames), p.nameSets)
        assertEquals(List(6) { "Player ${it + 1}" }, p.defaultNames.names)
        assertEquals(Flavour.plain, Flavour.of(false))
        assertEquals(Flavour.quippy, Flavour.of(true))
    }

    @Test fun commanderTargetLinesAreShortEnoughToDrawLarge() {
        for (f in both) for (line in f.lines(QuipKind.COMMANDER_TARGET)) {
            assertTrue(line.length <= QuipTables.TARGET_MAX, "target line too long: '$line'")
        }
        assertEquals("Target", Flavour.plain.commanderTarget(Random(0)))
        var recent = RecentPicks.EMPTY
        var last: String? = null
        val r = Random(3)
        repeat(50) {
            val p = Flavour.quippy.commanderTarget(r, recent)
            assertNotEquals(last, p.value)
            assertTrue(p.value in Flavour.quippy.lines(QuipKind.COMMANDER_TARGET))
            last = p.value; recent = p.recent
        }
    }

    @Test fun defaultNamesArePlayerNumbersInBothSets() {
        assertEquals(Flavour.plain.defaultNames, Flavour.quippy.defaultNames)
        assertEquals("Player 3", Flavour.quippy.defaultNames.name(2))
    }

    @Test fun diceRouting() {
        assertEquals(QuipKind.D20_NATURAL_1, QuipKind.dice(20, 1))
        assertEquals(QuipKind.D20_NATURAL_20, QuipKind.dice(20, 20))
        assertEquals(QuipKind.DICE_ROLL, QuipKind.dice(20, 7))
        assertEquals(QuipKind.DICE_ROLL, QuipKind.dice(6, 1))
        assertEquals(QuipKind.DICE_ROLL, QuipKind.dice(6, 6))
        assertEquals(QuipKind.COIN_HEADS, QuipKind.coin(CoinFace.HEADS))
        assertEquals(QuipKind.COIN_TAILS, QuipKind.coin(CoinFace.TAILS))
        assertTrue(Flavour.quippy.dice(20, 20, Random(3)) in Flavour.quippy.lines(QuipKind.D20_NATURAL_20))
        assertTrue(Flavour.quippy.coin(CoinFace.TAILS, Random(3)) in Flavour.quippy.lines(QuipKind.COIN_TAILS))
    }

    // --- Picker --------------------------------------------------------------------------------

    @Test fun picksAreDeterministicForASeed() {
        for (kind in QuipKind.entries) {
            val a = List(20) { Flavour.quippy.line(kind, Random(it)) }
            val b = List(20) { Flavour.quippy.line(kind, Random(it)) }
            assertEquals(a, b)
        }
        val r1 = Random(42); val r2 = Random(42)
        assertEquals(List(10) { Flavour.quippy.resetConfirm(r1) }, List(10) { Flavour.quippy.resetConfirm(r2) })
    }

    @Test fun picksReachEveryLine() {
        val r = Random(7)
        for (kind in QuipKind.entries) {
            val lines = Flavour.quippy.lines(kind)
            val seen = List(lines.size * 50) { Flavour.quippy.line(kind, r) }.toSet()
            assertEquals(lines.toSet(), seen, "$kind")
        }
    }

    @Test fun recentVariantNeverRepeatsTheLastPick() {
        val r = Random(99)
        for (kind in QuipKind.entries) {
            val lines = Flavour.quippy.lines(kind)
            if (lines.size < 2) continue
            var recent = RecentPicks.EMPTY
            var last: String? = null
            val seen = HashSet<String>()
            repeat(500) {
                val p = Flavour.quippy.line(kind, r, recent)
                assertNotEquals(last, p.value, "$kind repeated")
                assertEquals(lines.indexOf(p.value), p.recent.lastIndex(kind))
                last = p.value; recent = p.recent; seen += p.value
            }
            assertEquals(lines.toSet(), seen, "$kind no-repeat picker skips lines")
        }
    }

    @Test fun recentIsPerCategory() {
        val r = Random(5)
        var recent = RecentPicks.EMPTY
        recent = Flavour.quippy.ko(KoReason.LIFE, r, recent).recent
        assertEquals(RecentPicks.NONE, recent.lastIndex(QuipKind.KO_POISON))
        assertTrue(recent.lastIndex(QuipKind.KO_LIFE) >= 0)
    }

    @Test fun recentResetConfirmRepeatsNeitherPromptNorButtons() {
        val r = Random(11)
        var recent = RecentPicks.EMPTY
        var last: ResetConfirm? = null
        repeat(300) {
            val p = Flavour.quippy.resetConfirm(r, recent)
            last?.let {
                assertNotEquals(it.prompt, p.value.prompt)
                assertNotEquals(it.buttons, p.value.buttons)
            }
            last = p.value; recent = p.recent
        }
    }

    @Test fun singleLineCategoriesStillPickWithRecent() {
        var recent = RecentPicks.EMPTY
        repeat(3) {
            val p = Flavour.plain.winner(Random(it), recent)
            assertEquals("Wins", p.value)
            recent = p.recent
        }
    }

    @Test fun pickIndexIgnoresOutOfRangeAvoid() {
        repeat(50) {
            assertTrue(Flavour.pickIndex(3, Random(it), avoid = 7) in 0..2)
            assertEquals(0, Flavour.pickIndex(1, Random(it), avoid = 0))
        }
    }

    // --- Helpers -------------------------------------------------------------------------------

    private fun assertStyled(s: String, what: String) {
        for (c in s) if (c !in CHARSET) fail("$what '$s' has a character the ui face can't draw: '$c'")
        assertEquals(s.trim(), s, "$what has outer whitespace: '$s'")
        assertFalse("  " in s, "$what has a double space: '$s'")
        // Verbatim game quotes (T42) keep their own punctuation.
        if (s in QuipTables.verbatim) return
        assertFalse('!' in s, "$what has an exclamation mark: '$s'")
        assertFalse(s.endsWith(".") && !s.endsWith("..."), "$what ends with a full stop: '$s'")
        assertTrue(s.first().isUpperCase() || s.first().isDigit(), "$what is not sentence case: '$s'")
    }

    companion object {
        /** ui face budget: 6 px advance × scale 4 on a 233 px panel, less margins. */
        const val LINE_MAX = 32
        const val LABEL_MAX = 10

        /** ASCII subset of the `ui` face (design/font/README.md); the face's `×`/`−` are left out on purpose. */
        val CHARSET: Set<Char> =
            (('A'..'Z') + ('a'..'z') + ('0'..'9') + " +-/:;.,!?()[]%#&'\"<>=_|*".toList()).toSet()

        /** Greedy word wrap, as the UI is expected to do it. */
        fun wrap(s: String, width: Int): List<String> {
            val out = mutableListOf<String>()
            var cur = ""
            for (word in s.split(' ')) {
                require(word.length <= width) { "word longer than a line: $word" }
                cur = when {
                    cur.isEmpty() -> word
                    cur.length + 1 + word.length <= width -> "$cur $word"
                    else -> { out += cur; word }
                }
            }
            if (cur.isNotEmpty()) out += cur
            return out
        }
    }
}
