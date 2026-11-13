package au.com.josephmark.dithertracker.core.serial

import au.com.josephmark.dithertracker.core.Action.*
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.Game
import au.com.josephmark.dithertracker.core.GameSettings
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.History
import au.com.josephmark.dithertracker.core.KoOverride
import au.com.josephmark.dithertracker.core.KoReason
import au.com.josephmark.dithertracker.core.LogEntry
import au.com.josephmark.dithertracker.core.LogEvent
import au.com.josephmark.dithertracker.core.LogEvent.*
import au.com.josephmark.dithertracker.core.Toggle
import java.util.zip.CRC32
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SaveCodecTest {

    // ---- fixtures ----------------------------------------------------------------------------

    /** A 6-player game driven through the real reducer, touching every part of the state. */
    private fun richGame(): Game {
        var h = History(GameState.create(4, GameSettings(startingLife = 30, commanderDamageAffectsLife = true, autoKo = true)))
        var t = 1_700_000_000_000L
        fun d(a: au.com.josephmark.dithertracker.core.Action) {
            t += 7_919
            h = h.dispatch(a, t)
        }
        d(SetPlayerCount(6))
        d(Rename(0, "Ærin — ✦ 日本"))
        d(Rename(5, ""))
        d(SetPattern(2, "DIAG"))
        d(SetPartner(1, true))
        d(SetPartner(4, true))
        d(SetStartingPlayer(3))
        d(NextTurn)
        d(NextTurn)
        d(ChangeCommanderDamage(target = 0, source = 1, slot = Commander.PARTNER, delta = 7))
        d(ChangeCommanderDamage(target = 0, source = 1, slot = Commander.PRIMARY, delta = 5, linkLife = false))
        d(ChangeCommanderDamage(target = 2, source = 4, slot = Commander.PRIMARY, delta = 21))
        d(ChangeCommanderTax(1, Commander.PRIMARY, 4))
        d(ChangeCommanderTax(1, Commander.PARTNER, 2))
        Counter.entries.forEachIndexed { i, c -> d(ChangeCounter(3, c, i + 1)) }
        d(SetCounter(4, Counter.POISON, 999))
        Toggle.entries.forEach { d(SetToggle(2, it, true)) }
        d(SetToggle(5, Toggle.RING_BEARER, true))
        d(ChangeLife(5, -45)) // KO by life
        d(Revive(5)) // ALIVE override while still at <= 0
        d(MarkDead(3)) // DEAD override
        d(SetDayNight(DayNight.NIGHT))
        d(UpdateSettings(GameSettings(startingLife = 30, commanderDamageAffectsLife = false, autoKo = true)))
        d(SetLife(0, -999))
        return h.present
    }

    /** One entry for every [LogEvent] subtype, with edge-case values. Exhaustive `when` guards new subtypes. */
    private fun everyEvent(): List<LogEvent> {
        val events = listOf(
            LifeChanged(0, -5, 35),
            LifeChanged(1, -7, 33, source = 2, slot = Commander.PARTNER),
            CounterChanged(2, Counter.GENERIC, 999, 999),
            CommanderTaxChanged(3, Commander.PARTNER, 2, 4),
            CommanderDamageChanged(4, 5, Commander.PRIMARY, -21, 0),
            ToggleChanged(5, Toggle.CITYS_BLESSING, true),
            KnockedOut(0, KoReason.COMMANDER_DAMAGE),
            Revived(Int.MAX_VALUE),
            Renamed(1, "Player 2", "🐉 Long name ".repeat(20)),
            PatternChanged(2, ""),
            PartnerChanged(3, false),
            DayNightChanged(DayNight.DAY),
            TurnChanged(null, 0, 0),
            TurnChanged(4, Int.MAX_VALUE, Int.MIN_VALUE),
            SettingsChanged(GameSettings(1, false, false)),
            PlayerCountChanged(6),
            NewGameStarted,
            GameReset,
        )
        // Compile-time guard: adding a LogEvent subtype makes this `when` non-exhaustive.
        for (e in events) {
            when (e) {
                is LifeChanged, is CounterChanged, is CommanderTaxChanged, is CommanderDamageChanged,
                is ToggleChanged, is KnockedOut, is Revived, is Renamed, is PatternChanged, is PartnerChanged,
                is DayNightChanged, is TurnChanged, is SettingsChanged, is PlayerCountChanged,
                NewGameStarted, GameReset -> Unit
            }
        }
        return events
    }

    private fun richSettings() = AppSettings(
        scale = 6,
        inverted = true,
        holdRepeatStep = 5,
        keepScreenAwake = false,
        drawerCounters = listOf(Counter.STORM, Counter.POISON, Counter.RAD),
        fullRefreshEvery = 0,
        haptics = true,
        orientation = Orientation.PORTRAIT,
        layout = "6-ring",
    )

    private fun roundTrip(save: SaveFile, max: Int = SaveCodec.DEFAULT_MAX_LOG_ENTRIES): SaveFile {
        val result = SaveCodec.decode(SaveCodec.encode(save, max))
        assertIs<DecodeResult.Success>(result, "decode failed: $result")
        assertEquals(SaveCodec.CURRENT_VERSION, result.version)
        assertEquals(0, result.skippedLogEntries)
        return result.save
    }

    private fun fixCrc(bytes: ByteArray) {
        val end = bytes.size - 4
        val crc = CRC32().apply { update(bytes, 0, end) }.value.toInt()
        for (i in 0 until 4) bytes[end + i] = (crc ushr (24 - 8 * i)).toByte()
    }

    // ---- round trips -------------------------------------------------------------------------

    @Test fun richGameRoundTrips() {
        val game = richGame()
        val s = game.state
        // Sanity: the fixture really covers what it claims.
        assertEquals(6, s.players.size)
        assertTrue(s.players[1].partner && s.players[4].partner)
        assertEquals(KoOverride.ALIVE, s.players[5].koOverride)
        assertEquals(KoOverride.DEAD, s.players[3].koOverride)
        assertTrue(s.players[0].commanderDamage.getValue(1).partner.lifeLinked == 7)
        assertTrue(s.players[0].commanderDamage.getValue(1).primary.let { it.damage == 5 && it.lifeLinked == 0 })
        assertTrue(Counter.entries.all { s.players[3].counters[it] > 0 })
        assertTrue(Toggle.entries.all { s.players[2].toggles[it] })
        assertTrue(s.turn.started)
        assertTrue(game.log.size > 20)

        val save = SaveFile(game, richSettings())
        assertEquals(save, roundTrip(save))
    }

    @Test fun everyLogEventSubtypeRoundTrips() {
        val events = everyEvent()
        // Runtime guard too: every nested LogEvent subtype must appear in the fixture.
        val declared = LogEvent::class.java.declaredClasses.filter { LogEvent::class.java.isAssignableFrom(it) }.toSet()
        assertEquals(declared, events.map { it.javaClass }.toSet(), "LogEvent subtypes missing from the fixture")

        // Timestamps going backwards (merged entries) and extreme values must survive the delta encoding.
        val stamps = listOf(0L, Long.MAX_VALUE, Long.MIN_VALUE, -1L, 1_700_000_000_000L, 1_699_999_999_000L)
        val log = events.mapIndexed { i, e -> LogEntry(stamps[i % stamps.size], i * 3 - 5, e) }
        val save = SaveFile(Game(GameState.create(2), log))
        assertEquals(save, roundTrip(save))
    }

    @Test fun defaultsRoundTrip() {
        val save = SaveFile(Game(GameState.create()))
        assertEquals(save, roundTrip(save))
        assertEquals(AppSettings(), roundTrip(save).settings)
    }

    @Test fun everyPlayerCountAndScaleRoundTrips() {
        for (n in GameState.MIN_PLAYERS..GameState.MAX_PLAYERS) for (scale in listOf(0, 2, 3, 4, 5, 6)) {
            val save = SaveFile(Game(GameState.create(n)), AppSettings(scale = scale, drawerCounters = emptyList()))
            assertEquals(save, roundTrip(save))
        }
    }

    @Test fun restoredGameKeepsWorkingInHistory() {
        val restored = roundTrip(SaveFile(richGame())).game
        val h = History(restored).dispatch(ChangeLife(0, 1), Long.MAX_VALUE)
        assertEquals(restored.state.players[0].life + 1, h.state.players[0].life)
        assertEquals(restored.log.size + 1, h.log.size)
        assertTrue(!History(restored).canUndo)
    }

    // ---- log truncation ----------------------------------------------------------------------

    @Test fun logIsTruncatedToMostRecentEntries() {
        val log = (0 until 50).map { LogEntry(1000L + it, it, LifeChanged(0, -1, 39 - it)) }
        val game = Game(GameState.create(), log)
        assertEquals(log.takeLast(10), roundTrip(SaveFile(game), max = 10).game.log)
        assertEquals(log, roundTrip(SaveFile(game), max = 50).game.log)
        assertEquals(log, roundTrip(SaveFile(game), max = 500).game.log)
        assertEquals(emptyList(), roundTrip(SaveFile(game), max = 0).game.log)
        assertEquals(emptyList(), roundTrip(SaveFile(game), max = -3).game.log)
        // Truncation never touches the state.
        assertEquals(game.state, roundTrip(SaveFile(game), max = 0).game.state)
    }

    @Test fun typicalFourPlayerGameIsSmall() {
        val rnd = Random(42)
        var h = History(GameState.create(4))
        var t = 1_731_456_000_000L
        h = h.dispatch(SetStartingPlayer(0), t)
        while (h.log.size < 200) {
            t += rnd.nextLong(2_000, 90_000)
            val p = rnd.nextInt(4)
            h = when (rnd.nextInt(10)) {
                0 -> h.dispatch(NextTurn, t)
                1 -> h.dispatch(ChangeCommanderDamage(p, (p + 1) % 4, 0, rnd.nextInt(1, 6)), t)
                2 -> h.dispatch(ChangeCounter(p, Counter.entries[rnd.nextInt(3)], 1), t)
                else -> h.dispatch(ChangeLife(p, rnd.nextInt(-6, 4).takeIf { it != 0 } ?: 1), t)
            }
        }
        val game = Game(h.state, h.log.takeLast(200))
        val bytes = SaveCodec.encode(SaveFile(game))
        println("4-player game, ${game.log.size} log entries: ${bytes.size} bytes")
        assertTrue(bytes.size < 4096, "save is ${bytes.size} bytes")
        assertEquals(SaveFile(game), SaveCodec.decodeOrNull(bytes))
    }

    // ---- header failures ---------------------------------------------------------------------

    @Test fun emptyAndShortInputFail() {
        assertEquals(FailureReason.TOO_SHORT, (SaveCodec.decode(ByteArray(0)) as DecodeResult.Failure).reason)
        assertEquals(FailureReason.TOO_SHORT, (SaveCodec.decode("DTSV".encodeToByteArray()) as DecodeResult.Failure).reason)
        assertNull(SaveCodec.decodeOrNull(ByteArray(0)))
    }

    @Test fun wrongMagicFails() {
        val bytes = SaveCodec.encode(SaveFile(richGame()))
        bytes[0] = 'X'.code.toByte()
        assertEquals(FailureReason.BAD_MAGIC, (SaveCodec.decode(bytes) as DecodeResult.Failure).reason)
        val json = """{"players":[{"life":40},{"life":40}]}""".encodeToByteArray()
        assertEquals(FailureReason.BAD_MAGIC, (SaveCodec.decode(json) as DecodeResult.Failure).reason)
    }

    @Test fun newerAndZeroVersionsAreRejected() {
        for (v in listOf(SaveCodec.CURRENT_VERSION + 1, 0xFFFF, 0)) {
            val bytes = SaveCodec.encode(SaveFile(richGame()))
            bytes[4] = (v ushr 8).toByte()
            bytes[5] = v.toByte()
            fixCrc(bytes)
            assertEquals(FailureReason.UNSUPPORTED_VERSION, (SaveCodec.decode(bytes) as DecodeResult.Failure).reason)
        }
    }

    @Test fun checksumMismatchIsDetected() {
        val bytes = SaveCodec.encode(SaveFile(richGame()))
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        assertEquals(FailureReason.CHECKSUM_MISMATCH, (SaveCodec.decode(bytes) as DecodeResult.Failure).reason)
    }

    // ---- unknown codes -----------------------------------------------------------------------

    @Test fun unknownLogEventTagIsSkipped() {
        val log = listOf(LogEntry(5, 0, NewGameStarted), LogEntry(6, 0, Revived(3)))
        val bytes = SaveCodec.encode(SaveFile(Game(GameState.create(), log)))
        // Last entry is [len][tsDelta][turn][tag=7][player=3]; its tag sits 2 bytes before the CRC.
        val tagAt = bytes.size - 4 - 2
        assertEquals(7, bytes[tagAt].toInt())
        bytes[tagAt] = 99
        fixCrc(bytes)
        val result = assertIs<DecodeResult.Success>(SaveCodec.decode(bytes))
        assertEquals(listOf(LogEntry(5, 0, NewGameStarted)), result.save.game.log)
        assertEquals(1, result.skippedLogEntries)
    }

    @Test fun unknownCounterCodeInSettingsIsDropped() {
        val settings = AppSettings(drawerCounters = listOf(Counter.POISON, Counter.RAD))
        val bytes = SaveCodec.encode(SaveFile(Game(GameState.create()), settings))
        // Settings start right after the 6-byte header: scale, flags, holdStep, fullRefresh, count, codes...
        val firstCode = 6 + 5
        assertEquals(1, bytes[firstCode].toInt())
        bytes[firstCode] = 99
        fixCrc(bytes)
        assertEquals(listOf(Counter.RAD), SaveCodec.decodeOrNull(bytes)?.settings?.drawerCounters)
    }

    @Test fun enumCodesAreStable() {
        // Changing any of these corrupts existing saves: add new codes, never renumber.
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), listOf(Counter.POISON, Counter.ENERGY, Counter.EXPERIENCE, Counter.RAD, Counter.STORM, Counter.TICKETS, Counter.GENERIC).map(Codes::counter))
        assertEquals(listOf(1, 2, 3, 4), listOf(Toggle.MONARCH, Toggle.INITIATIVE, Toggle.CITYS_BLESSING, Toggle.RING_BEARER).map(Codes::toggle))
        assertEquals(listOf(0, 1, 2), listOf(DayNight.UNSET, DayNight.DAY, DayNight.NIGHT).map(Codes::dayNight))
        assertEquals(listOf(0, 1, 2), listOf(KoOverride.NONE, KoOverride.ALIVE, KoOverride.DEAD).map(Codes::koOverride))
        assertEquals(listOf(1, 2, 3, 4), listOf(KoReason.LIFE, KoReason.POISON, KoReason.COMMANDER_DAMAGE, KoReason.MANUAL).map(Codes::koReason))
        assertEquals(listOf(0, 1, 2), listOf(Orientation.AUTO, Orientation.LANDSCAPE, Orientation.PORTRAIT).map(Codes::orientation))
        // Every code maps back, and all codes are distinct per enum.
        Counter.entries.forEach { assertEquals(it, Codes.counter(Codes.counter(it))) }
        Toggle.entries.forEach { assertEquals(it, Codes.toggle(Codes.toggle(it))) }
        assertNull(Codes.counter(0))
        assertNull(Codes.koReason(99))
    }

    // ---- fuzz-ish ----------------------------------------------------------------------------

    private fun decodeNoThrow(bytes: ByteArray): DecodeResult = try {
        SaveCodec.decode(bytes)
    } catch (e: Throwable) {
        fail("decode threw ${e::class.simpleName}: ${e.message}")
    }

    @Test fun everyTruncationFailsCleanly() {
        val bytes = SaveCodec.encode(SaveFile(richGame(), richSettings()))
        for (len in 0 until bytes.size) {
            val r = decodeNoThrow(bytes.copyOf(len))
            assertIs<DecodeResult.Failure>(r, "truncated to $len decoded")
        }
    }

    @Test fun everyTruncationWithFixedChecksumNeverThrows() {
        // Bypass the CRC so the parser itself sees every cut-off point.
        val bytes = SaveCodec.encode(SaveFile(richGame(), richSettings()))
        val body = bytes.copyOf(bytes.size - 4)
        for (len in 6..body.size) {
            val cut = body.copyOf(len) + ByteArray(4)
            fixCrc(cut)
            val r = decodeNoThrow(cut)
            if (len < body.size) assertIs<DecodeResult.Failure>(r, "truncated body $len decoded")
            else assertIs<DecodeResult.Success>(r)
        }
    }

    @Test fun randomByteFlipsNeverThrow() {
        val original = SaveCodec.encode(SaveFile(richGame(), richSettings()))
        val rnd = Random(1234)
        var parsed = 0
        repeat(20_000) { iteration ->
            val bytes = original.copyOf()
            repeat(rnd.nextInt(1, 6)) { bytes[rnd.nextInt(bytes.size)] = rnd.nextInt(256).toByte() }
            // Half the time fix the checksum so the flips reach the parser.
            val fixed = iteration % 2 == 0
            if (fixed) fixCrc(bytes)
            when (val r = decodeNoThrow(bytes)) {
                is DecodeResult.Success -> parsed++
                is DecodeResult.Failure -> if (!fixed && !bytes.contentEquals(original)) {
                    assertTrue(r.reason != FailureReason.CORRUPT || bytes.size < 10)
                }
            }
        }
        println("random flips: $parsed of 20000 still parsed (expected for value bytes with fixed CRC)")
    }

    @Test fun randomGarbageNeverThrows() {
        val rnd = Random(99)
        repeat(5_000) {
            val bytes = rnd.nextBytes(rnd.nextInt(0, 300))
            if (it % 2 == 0 && bytes.size >= 10) {
                "DTSV".encodeToByteArray().copyInto(bytes)
                bytes[4] = 0; bytes[5] = 1
                fixCrc(bytes)
            }
            decodeNoThrow(bytes)
        }
    }

    @Test fun hugeCountsDoNotAllocate() {
        // A body claiming 2^31-1 log entries / huge string length must fail fast, not OOM.
        val w = ByteWriter()
        w.bytes("DTSV".encodeToByteArray())
        w.u16(1)
        w.svar(0); w.uvar(0); w.svar(10); w.svar(20)
        w.uvar(Int.MAX_VALUE) // drawer counter count
        w.u32(0)
        val bytes = w.toByteArray()
        fixCrc(bytes)
        assertEquals(FailureReason.CORRUPT, (decodeNoThrow(bytes) as DecodeResult.Failure).reason)
    }

    // ---- settings sanitising -----------------------------------------------------------------

    @Test fun outOfRangeSettingsAreSanitisedOnLoad() {
        val w = ByteWriter()
        w.bytes("DTSV".encodeToByteArray())
        w.u16(1)
        w.svar(9) // scale: invalid -> auto
        w.uvar(0xFF) // all flags plus unknown bits
        w.svar(-5) // hold step -> 1
        w.svar(1_000_000) // refresh -> 1000
        w.uvar(3); w.uvar(1); w.uvar(1); w.uvar(42) // duplicate + unknown counter
        w.uvar(77) // unknown orientation -> default
        w.string("x")
        // minimal game
        w.svar(40); w.uvar(3); w.uvar(0); w.optInt(null); w.optInt(null); w.svar(0); w.svar(0)
        w.uvar(2)
        for (id in 0..1) {
            w.svar(id); w.string("P"); w.svar(40); w.string(""); w.uvar(0); w.uvar(9) // unknown KO override -> NONE
            w.uvar(0); w.svar(0); w.svar(0); w.uvar(0)
        }
        w.uvar(0)
        w.u32(0)
        val bytes = w.toByteArray()
        fixCrc(bytes)
        val save = SaveCodec.decodeOrNull(bytes) ?: fail("did not decode: ${SaveCodec.decode(bytes)}")
        assertEquals(
            AppSettings(
                scale = AppSettings.SCALE_AUTO, inverted = true, holdRepeatStep = 1, keepScreenAwake = true,
                drawerCounters = listOf(Counter.POISON), fullRefreshEvery = 1000, haptics = true,
                orientation = AppSettings().orientation, layout = "x",
                playerCount = 2, // a v1 save has no pending count: the saved game's (T11)
            ),
            save.settings,
        )
        assertTrue(save.game.state.players.all { it.koOverride == KoOverride.NONE })
    }

    // ---- v2 (T11): the player count for the next game -------------------------------------------------

    @Test fun pendingPlayerCountRoundTrips() {
        for (n in AppSettings.PLAYER_COUNT_RANGE) for (seats in 2..6) {
            val save = SaveFile(Game(GameState.create(seats)), richSettings().copy(playerCount = n))
            assertEquals(save, roundTrip(save))
        }
    }

    @Test fun version1SavesStillLoadAndKeepTheirGamesCount() {
        // The v1 body is the v2 body without the count after the settings: rebuild one from a v2 encoding.
        for (seats in listOf(2, 4, 6)) {
            val game = Game(GameState.create(seats))
            val settings = richSettings()
            val v2 = SaveCodec.encode(SaveFile(game, settings.copy(playerCount = 3)))
            val settingsOnly = ByteWriter().also { it.bytes("DTSV".encodeToByteArray()); it.u16(2) }.toByteArray().size +
                settingsSize(settings)
            val v1 = v2.copyOfRange(0, settingsOnly) + v2.copyOfRange(settingsOnly + 1, v2.size) // drop the 1-byte count
            v1[5] = 1 // version 1
            fixCrc(v1)
            val r = SaveCodec.decode(v1)
            assertIs<DecodeResult.Success>(r, "v1 decode failed: $r")
            assertEquals(1, r.version)
            assertEquals(game, r.save.game)
            assertEquals(settings.copy(playerCount = seats), r.save.settings)
        }
    }

    @Test fun outOfRangePendingPlayerCountFallsBackToTheDefault() {
        val bytes = SaveCodec.encode(SaveFile(Game(GameState.create(4)), richSettings().copy(playerCount = 6)))
        val at = 6 + settingsSize(richSettings())
        assertEquals(6, bytes[at].toInt())
        bytes[at] = 9
        fixCrc(bytes)
        val save = SaveCodec.decodeOrNull(bytes) ?: fail("did not decode")
        assertEquals(AppSettings.DEFAULT_PLAYER_COUNT, save.settings.playerCount)
    }

    /** Encoded size of the v1 settings block (the v2 count follows it). */
    private fun settingsSize(s: AppSettings): Int {
        val a = SaveCodec.encode(SaveFile(Game(GameState.create(4)), s)).size
        val b = SaveCodec.encode(SaveFile(Game(GameState.create(4)), AppSettings())).size
        val d = AppSettings()
        // Default settings: scale 0, flags 2, hold 10, refresh 20, 7 counters, orientation, empty layout.
        val defaultSize = 1 + 1 + 1 + 1 + 1 + d.drawerCounters.size + 1 + 1
        return defaultSize + (a - b)
    }

    @Test fun duplicatePlayerIdsAreCorrupt() {
        val good = SaveCodec.encode(SaveFile(Game(GameState.create(2))))
        // Player 1's id byte: find the second player's record by re-encoding with a known layout.
        val w = ByteWriter()
        w.bytes(good.copyOf(good.size - 4))
        val bytes = w.toByteArray()
        // Second player's id (zigzag 1 = 0x02) is the first 0x02 after the first player's name "Player 1".
        val name2 = "Player 2".encodeToByteArray()
        val at = (0..bytes.size - name2.size).first { i -> name2.indices.all { bytes[i + it] == name2[it] } }
        // Layout: ... [id][nameLen][name]; id sits 2 bytes before the name.
        assertEquals(2, bytes[at - 2].toInt())
        bytes[at - 2] = 0
        val withCrc = bytes + ByteArray(4)
        fixCrc(withCrc)
        assertEquals(FailureReason.CORRUPT, (SaveCodec.decode(withCrc) as DecodeResult.Failure).reason)
    }
}
