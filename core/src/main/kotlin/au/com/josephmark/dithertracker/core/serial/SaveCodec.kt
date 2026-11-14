package au.com.josephmark.dithertracker.core.serial

import au.com.josephmark.dithertracker.core.CommanderDamage
import au.com.josephmark.dithertracker.core.CommanderTax
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.Counters
import au.com.josephmark.dithertracker.core.DamageSlot
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.Game
import au.com.josephmark.dithertracker.core.GameSettings
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.KoOverride
import au.com.josephmark.dithertracker.core.LogEntry
import au.com.josephmark.dithertracker.core.LogEvent
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.core.PlayerId
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.core.Toggles
import au.com.josephmark.dithertracker.core.TurnState
import java.util.zip.CRC32

/**
 * Everything persisted across process death: the current [Game] (state + log) and the UI settings.
 *
 * Undo/redo [au.com.josephmark.dithertracker.core.History] is deliberately **not** persisted: it
 * would multiply the file size by up to `maxDepth` snapshots for little value. After a restore the
 * app starts a fresh `History(save.game)` — the game and its log survive, undo resets.
 */
data class SaveFile(val game: Game, val settings: AppSettings = AppSettings())

sealed interface DecodeResult {
    data class Success(
        val save: SaveFile,
        /** Format version the data was written with (≤ [SaveCodec.CURRENT_VERSION]). */
        val version: Int,
        /** Log entries skipped because they used an unknown event tag or enum code. */
        val skippedLogEntries: Int = 0,
    ) : DecodeResult

    data class Failure(val reason: FailureReason, val detail: String) : DecodeResult
}

enum class FailureReason {
    /** Shorter than the fixed header + checksum. */
    TOO_SHORT,

    /** Not a Dither Tracker save. */
    BAD_MAGIC,

    /** Written by a newer app (or version 0). Don't overwrite it blindly. */
    UNSUPPORTED_VERSION,

    /** Checksum mismatch: truncated or bit-rotted file. */
    CHECKSUM_MISMATCH,

    /** Checksum matched but the content didn't parse or failed validation. */
    CORRUPT,
}

/**
 * Compact versioned binary save format (dependency-free, no Android).
 *
 * ```
 * magic   4 bytes  "DTSV"
 * version u16 BE   CURRENT_VERSION
 * body    ...      version-specific (v1 below)
 * crc32   u32 BE   CRC-32 of every preceding byte (magic + version + body)
 * ```
 * v1 body: AppSettings, then the Game (GameSettings, dayNight, turn, players, log). v2 (T11) appends
 * `AppSettings.playerCount` to the settings; a v1 save takes it from the saved game. v3 (T17) appends
 * the turn settings (`turnTracker`, `turnTimer`) after it and the turn clock (`TurnState.startedAt`,
 * `turnStartedAt`: wall-clock ms, each an optional varint) after the turn's numbers; older saves get
 * the defaults (tracker on, timer off) and an unstamped clock. v4 (T53) appends the hidden trackers
 * (`AppSettings.hiddenTrackers`: a count, then [Codes.tracker] codes) after the turn settings; older
 * saves show every tracker. v5 (T53) appends the remembered card facings (`settingsFacing`,
 * `historyFacing`: one varint each, 0 = never turned, else 1 + rotation / 90); older saves open the
 * cards facing the side that asks. (`AppSettings.layout` has been in the body since v1: an opaque string, so
 * T53's per-count layout choices needed no format change.) Integers are
 * LEB128 varints, signed ones zigzag-encoded; strings are varint length + UTF-8; enums use the
 * explicit codes in [Codes]; booleans of a player/settings are packed into flag bytes. Log entries
 * are length-prefixed (so an entry with an unknown tag or enum code is skipped, not fatal) and
 * timestamps are stored as deltas from the previous entry.
 *
 * Why binary rather than a text format: the file is rewritten after every committed change on a
 * slow CPU, so a single pass over primitives into one byte array (no tokenising, no number
 * parsing, no intermediate strings) is the cheapest option, and varints keep a typical save around
 * 2 KB. Debuggability comes from the unit tests and data-class `toString`, not from the file.
 *
 * **Migrations:** bump [CURRENT_VERSION] when the body layout changes, keep the old reader
 * (e.g. `readBodyV1`) and add a branch in [readBody] that reads the old layout and maps it into
 * the current model. Older readers reject newer versions with [FailureReason.UNSUPPORTED_VERSION].
 */
object SaveCodec {
    const val CURRENT_VERSION = 5

    /** Log entries kept by default when encoding (the most recent ones). */
    const val DEFAULT_MAX_LOG_ENTRIES = 500

    private val MAGIC = byteArrayOf('D'.code.toByte(), 'T'.code.toByte(), 'S'.code.toByte(), 'V'.code.toByte())
    private const val HEADER_SIZE = 6
    private const val CRC_SIZE = 4

    /**
     * Encodes [save], keeping only the most recent [maxLogEntries] log entries (negative = 0).
     * [save] is immutable, so this is safe to call off the main thread.
     */
    fun encode(save: SaveFile, maxLogEntries: Int = DEFAULT_MAX_LOG_ENTRIES): ByteArray =
        encodeAs(CURRENT_VERSION, save, maxLogEntries)

    /**
     * [encode] in an older layout ([version] 1..[CURRENT_VERSION]), dropping what that version could not
     * hold: what an older app wrote, so the tests can prove every old save still loads.
     */
    internal fun encodeAs(version: Int, save: SaveFile, maxLogEntries: Int = DEFAULT_MAX_LOG_ENTRIES): ByteArray {
        require(version in 1..CURRENT_VERSION) { "version $version" }
        val w = ByteWriter()
        w.bytes(MAGIC)
        w.u16(version)
        writeSettings(w, save.settings)
        if (version >= 2) w.uvar(save.settings.playerCount)
        if (version >= 3) writeTurnSettings(w, save.settings)
        if (version >= 4) writeTrackers(w, save.settings)
        if (version >= 5) writeFacings(w, save.settings)
        writeGame(w, save.game, maxLogEntries.coerceAtLeast(0), clock = version >= 3)
        val crc = CRC32().apply { update(w.toByteArray()) }.value.toInt()
        w.u32(crc)
        return w.toByteArray()
    }

    /** Never throws (barring JVM errors such as OOM on the caller's own huge array). */
    fun decode(bytes: ByteArray): DecodeResult {
        try {
            if (bytes.size < HEADER_SIZE + CRC_SIZE) {
                return DecodeResult.Failure(FailureReason.TOO_SHORT, "${bytes.size} bytes")
            }
            for (i in MAGIC.indices) {
                if (bytes[i] != MAGIC[i]) return DecodeResult.Failure(FailureReason.BAD_MAGIC, "not a save file")
            }
            val header = ByteReader(bytes, MAGIC.size, HEADER_SIZE)
            val version = header.u16()
            if (version < 1 || version > CURRENT_VERSION) {
                return DecodeResult.Failure(FailureReason.UNSUPPORTED_VERSION, "version $version")
            }
            val bodyEnd = bytes.size - CRC_SIZE
            val stored = ByteReader(bytes, bodyEnd, bytes.size).u32()
            val actual = CRC32().apply { update(bytes, 0, bodyEnd) }.value.toInt()
            if (stored != actual) return DecodeResult.Failure(FailureReason.CHECKSUM_MISMATCH, "crc")

            val r = ByteReader(bytes, HEADER_SIZE, bodyEnd)
            val ctx = DecodeContext()
            val save = readBody(version, r, ctx)
            if (r.remaining != 0) corrupt("${r.remaining} trailing bytes")
            return DecodeResult.Success(save, version, ctx.skippedLogEntries)
        } catch (e: Exception) {
            // CorruptSaveException, or a model constructor's require() rejecting decoded values.
            return DecodeResult.Failure(FailureReason.CORRUPT, e.message ?: e.javaClass.simpleName)
        }
    }

    /** [decode] for callers that only care whether it worked. */
    fun decodeOrNull(bytes: ByteArray): SaveFile? = (decode(bytes) as? DecodeResult.Success)?.save

    private class DecodeContext {
        var skippedLogEntries = 0
    }

    private fun readBody(version: Int, r: ByteReader, ctx: DecodeContext): SaveFile = when (version) {
        1 -> readBodyV1(r, ctx)
        2 -> readBodyV2(r, ctx)
        3 -> readBodyV3(r, ctx)
        4 -> readBodyV4(r, ctx)
        5 -> readBodyV5(r, ctx)
        // 6 -> readBodyV6(r, ctx); the older readers stay and map their layouts into the current model.
        else -> corrupt("no reader for version $version")
    }

    // ---- v1 ---------------------------------------------------------------------------------

    private fun readBodyV1(r: ByteReader, ctx: DecodeContext): SaveFile {
        val settings = readSettings(r)
        val game = readGame(r, ctx, clock = false)
        // v1 had no pending player count: the next new game keeps the saved game's.
        return SaveFile(game, settings.copy(playerCount = game.state.players.size))
    }

    // ---- v2 (T11): v1 + AppSettings.playerCount ------------------------------------------------

    private fun readBodyV2(r: ByteReader, ctx: DecodeContext): SaveFile {
        val settings = readSettingsV2(r)
        val game = readGame(r, ctx, clock = false)
        return SaveFile(game, settings)
    }

    private fun readSettingsV2(r: ByteReader): AppSettings {
        val v1 = readSettings(r)
        val count = r.uvar()
        return v1.copy(playerCount = if (count in AppSettings.PLAYER_COUNT_RANGE) count else AppSettings.DEFAULT_PLAYER_COUNT)
    }

    // ---- v3 (T17): v2 + turn settings, and the turn clock in the game ---------------------------------

    private fun readBodyV3(r: ByteReader, ctx: DecodeContext): SaveFile {
        val settings = readTurnSettings(r, readSettingsV2(r))
        val game = readGame(r, ctx, clock = true)
        return SaveFile(game, settings)
    }

    // ---- v4 (T53): v3 + the hidden trackers ---------------------------------------------------------

    private fun readBodyV4(r: ByteReader, ctx: DecodeContext): SaveFile {
        val settings = readTrackers(r, readTurnSettings(r, readSettingsV2(r)))
        val game = readGame(r, ctx, clock = true)
        return SaveFile(game, settings)
    }

    // ---- v5 (T53): v4 + the cards' remembered facings -------------------------------------------------

    private fun readBodyV5(r: ByteReader, ctx: DecodeContext): SaveFile {
        val settings = readFacings(r, readTrackers(r, readTurnSettings(r, readSettingsV2(r))))
        val game = readGame(r, ctx, clock = true)
        return SaveFile(game, settings)
    }

    private fun facingCode(f: Int): Int = if (f < 0) 0 else 1 + f / 90

    /** Unknown codes read as "never turned". */
    private fun facingOf(code: Int): Int = if (code in 1..4) (code - 1) * 90 else AppSettings.FACING_NONE

    private fun writeFacings(w: ByteWriter, s: AppSettings) {
        w.uvar(facingCode(s.settingsFacing))
        w.uvar(facingCode(s.historyFacing))
    }

    private fun readFacings(r: ByteReader, s: AppSettings): AppSettings {
        val settings = facingOf(r.uvar())
        val history = facingOf(r.uvar())
        return s.copy(settingsFacing = settings, historyFacing = history)
    }

    private fun writeTrackers(w: ByteWriter, s: AppSettings) {
        w.uvar(s.hiddenTrackers.size)
        for (t in Tracker.entries) if (t in s.hiddenTrackers) w.uvar(Codes.tracker(t))
    }

    /** Unknown codes (a newer app's trackers) are dropped: they come back shown. */
    private fun readTrackers(r: ByteReader, s: AppSettings): AppSettings =
        s.copy(hiddenTrackers = List(r.count()) { r.uvar() }.mapNotNull(Codes::tracker).toSet())

    private const val T_TRACKER = 1

    private fun writeTurnSettings(w: ByteWriter, s: AppSettings) {
        w.uvar((if (s.turnTracker) T_TRACKER else 0) or (Codes.turnTimer(s.turnTimer) shl 1))
    }

    private fun readTurnSettings(r: ByteReader, s: AppSettings): AppSettings {
        val v = r.uvar()
        return s.copy(turnTracker = v and T_TRACKER != 0, turnTimer = Codes.turnTimer(v ushr 1) ?: TurnTimer.OFF)
    }

    /** An optional wall-clock time: absent ([TurnState.NO_TIME]) or a zigzag varint. */
    private fun writeTime(w: ByteWriter, t: Long) {
        w.bool(t != TurnState.NO_TIME)
        if (t != TurnState.NO_TIME) w.svar(t)
    }

    private fun readTime(r: ByteReader): Long = if (r.bool()) r.svarLong() else TurnState.NO_TIME

    // AppSettings flags
    private const val S_INVERTED = 1
    private const val S_KEEP_AWAKE = 2
    private const val S_HAPTICS = 4

    private fun writeSettings(w: ByteWriter, s: AppSettings) {
        w.svar(s.scale)
        w.uvar((if (s.inverted) S_INVERTED else 0) or (if (s.keepScreenAwake) S_KEEP_AWAKE else 0) or (if (s.haptics) S_HAPTICS else 0))
        w.svar(s.holdRepeatStep)
        w.svar(s.fullRefreshEvery)
        w.uvar(s.drawerCounters.size)
        for (c in s.drawerCounters) w.uvar(Codes.counter(c))
        w.uvar(Codes.orientation(s.orientation))
        w.string(s.layout)
    }

    /** Sanitises rather than fails: out-of-range values fall back to defaults/clamp. */
    private fun readSettings(r: ByteReader): AppSettings {
        val d = AppSettings()
        val scale = r.svar()
        val flags = r.uvar()
        val holdStep = r.svar()
        val fullRefresh = r.svar()
        val counters = List(r.count()) { r.uvar() }.mapNotNull(Codes::counter).distinct()
        val orientation = Codes.orientation(r.uvar()) ?: d.orientation
        val layout = r.string()
        return AppSettings(
            scale = if (scale == AppSettings.SCALE_AUTO || scale in AppSettings.SCALE_MIN..AppSettings.SCALE_MAX) scale else d.scale,
            inverted = flags and S_INVERTED != 0,
            holdRepeatStep = holdStep.coerceIn(AppSettings.HOLD_STEP_RANGE),
            keepScreenAwake = flags and S_KEEP_AWAKE != 0,
            drawerCounters = counters,
            fullRefreshEvery = fullRefresh.coerceIn(AppSettings.FULL_REFRESH_RANGE),
            haptics = flags and S_HAPTICS != 0,
            orientation = orientation,
            layout = layout,
        )
    }

    // GameSettings flags
    private const val G_CMD_AFFECTS_LIFE = 1
    private const val G_AUTO_KO = 2

    private fun writeGameSettings(w: ByteWriter, s: GameSettings) {
        w.svar(s.startingLife)
        w.uvar((if (s.commanderDamageAffectsLife) G_CMD_AFFECTS_LIFE else 0) or (if (s.autoKo) G_AUTO_KO else 0))
    }

    private fun readGameSettings(r: ByteReader): GameSettings {
        val startingLife = r.svar()
        val flags = r.uvar()
        return GameSettings(startingLife, flags and G_CMD_AFFECTS_LIFE != 0, flags and G_AUTO_KO != 0)
    }

    private fun writeGame(w: ByteWriter, game: Game, maxLogEntries: Int, clock: Boolean) {
        val s = game.state
        writeGameSettings(w, s.settings)
        w.uvar(Codes.dayNight(s.dayNight))
        w.optInt(s.turn.activePlayer)
        w.optInt(s.turn.startingPlayer)
        w.svar(s.turn.number)
        w.svar(s.turn.round)
        if (clock) { // v3
            writeTime(w, s.turn.startedAt)
            writeTime(w, s.turn.turnStartedAt)
        }
        w.uvar(s.players.size)
        for (p in s.players) writePlayer(w, p)

        val log = if (game.log.size > maxLogEntries) game.log.takeLast(maxLogEntries) else game.log
        w.uvar(log.size)
        val entry = ByteWriter(64)
        var prevTimestamp = 0L
        for (e in log) {
            entry.reset()
            entry.svar(e.timestamp - prevTimestamp)
            prevTimestamp = e.timestamp
            entry.svar(e.turn)
            writeEvent(entry, e.event)
            w.uvar(entry.size)
            w.append(entry)
        }
    }

    /** [clock]: the save has the turn clock (v3+). */
    private fun readGame(r: ByteReader, ctx: DecodeContext, clock: Boolean): Game {
        val settings = readGameSettings(r)
        val dayNight = Codes.dayNight(r.uvar()) ?: DayNight.UNSET
        val numbers = TurnState(activePlayer = r.optInt(), startingPlayer = r.optInt(), number = r.svar(), round = r.svar())
        val turn = if (clock) numbers.copy(startedAt = readTime(r), turnStartedAt = readTime(r)) else numbers
        val playerCount = r.count()
        if (playerCount !in GameState.MIN_PLAYERS..GameState.MAX_PLAYERS) corrupt("player count $playerCount")
        val players = List(playerCount) { seat -> readPlayer(r, seat) }
        if (players.any { it.id < 0 } || players.map { it.id }.toSet().size != players.size) corrupt("bad player ids")
        val state = GameState(players, settings, dayNight, turn)

        val logSize = r.count()
        val log = ArrayList<LogEntry>(logSize)
        var timestamp = 0L
        repeat(logSize) {
            val er = r.block()
            timestamp += er.svarLong()
            val turnNo = er.svar()
            // Trailing bytes inside an entry are tolerated (room for appended fields).
            val event = readEvent(er)
            if (event == null) ctx.skippedLogEntries++ else log += LogEntry(timestamp, turnNo, event)
        }
        return Game(state, log)
    }

    private const val P_PARTNER = 1

    private fun writePlayer(w: ByteWriter, p: Player) {
        w.svar(p.id)
        w.string(p.name)
        w.svar(p.life)
        w.string(p.pattern)
        var flags = if (p.partner) P_PARTNER else 0
        for (t in Toggle.entries) if (p.toggles[t]) flags = flags or Codes.toggleBit(t)
        w.uvar(flags)
        w.uvar(Codes.koOverride(p.koOverride))

        val nonZero = Counter.entries.filter { p.counters[it] != 0 }
        w.uvar(nonZero.size)
        for (c in nonZero) {
            w.uvar(Codes.counter(c))
            w.svar(p.counters[c])
        }
        w.svar(p.commanderTax.primary)
        w.svar(p.commanderTax.partner)

        w.uvar(p.commanderDamage.size)
        for ((source, d) in p.commanderDamage.entries.sortedBy { it.key }) {
            w.svar(source)
            w.svar(d.primary.damage)
            w.svar(d.primary.lifeLinked)
            w.svar(d.partner.damage)
            w.svar(d.partner.lifeLinked)
        }
    }

    private fun readPlayer(r: ByteReader, seat: Int): Player {
        val id = r.svar()
        val name = r.string()
        val life = r.svar()
        val pattern = r.string()
        val flags = r.uvar()
        val koOverride = Codes.koOverride(r.uvar()) ?: KoOverride.NONE

        var counters = Counters()
        repeat(r.count(minBytesEach = 2)) {
            val counter = Codes.counter(r.uvar())
            val value = r.svar()
            if (counter != null) counters = counters.with(counter, value) // unknown counter: dropped
        }
        val tax = CommanderTax(primary = r.svar(), partner = r.svar())

        val damage = LinkedHashMap<PlayerId, CommanderDamage>()
        repeat(r.count(minBytesEach = 5)) {
            val source = r.svar()
            val d = CommanderDamage(DamageSlot(r.svar(), r.svar()), DamageSlot(r.svar(), r.svar()))
            if (!d.isZero) damage[source] = d // keep the model's "no zero entries" normal form
        }

        var toggles = Toggles()
        for (t in Toggle.entries) if (flags and Codes.toggleBit(t) != 0) toggles = toggles.with(t, true)
        return Player(
            id = id,
            name = name,
            seat = seat,
            life = life,
            pattern = pattern,
            partner = flags and P_PARTNER != 0,
            counters = counters,
            commanderTax = tax,
            commanderDamage = damage,
            toggles = toggles,
            koOverride = koOverride,
        )
    }

    // LogEvent tags. Never reuse or renumber.
    private const val E_LIFE = 1
    private const val E_COUNTER = 2
    private const val E_TAX = 3
    private const val E_CMD_DAMAGE = 4
    private const val E_TOGGLE = 5
    private const val E_KNOCKED_OUT = 6
    private const val E_REVIVED = 7
    private const val E_RENAMED = 8
    private const val E_PATTERN = 9
    private const val E_PARTNER = 10
    private const val E_DAY_NIGHT = 11
    private const val E_TURN = 12
    private const val E_SETTINGS = 13
    private const val E_PLAYER_COUNT = 14
    private const val E_NEW_GAME = 15
    private const val E_RESET = 16

    /** Exhaustive over [LogEvent]: a new subtype without a tag here fails to compile. */
    private fun writeEvent(w: ByteWriter, e: LogEvent) {
        when (e) {
            is LogEvent.LifeChanged -> {
                w.uvar(E_LIFE); w.svar(e.player); w.svar(e.delta); w.svar(e.value); w.optInt(e.source); w.optInt(e.slot)
            }
            is LogEvent.CounterChanged -> {
                w.uvar(E_COUNTER); w.svar(e.player); w.uvar(Codes.counter(e.counter)); w.svar(e.delta); w.svar(e.value)
            }
            is LogEvent.CommanderTaxChanged -> {
                w.uvar(E_TAX); w.svar(e.player); w.svar(e.slot); w.svar(e.delta); w.svar(e.value)
            }
            is LogEvent.CommanderDamageChanged -> {
                w.uvar(E_CMD_DAMAGE); w.svar(e.player); w.svar(e.source); w.svar(e.slot); w.svar(e.delta); w.svar(e.value)
            }
            is LogEvent.ToggleChanged -> {
                w.uvar(E_TOGGLE); w.svar(e.player); w.uvar(Codes.toggle(e.toggle)); w.bool(e.on)
            }
            is LogEvent.KnockedOut -> {
                w.uvar(E_KNOCKED_OUT); w.svar(e.player); w.uvar(Codes.koReason(e.reason))
            }
            is LogEvent.Revived -> {
                w.uvar(E_REVIVED); w.svar(e.player)
            }
            is LogEvent.Renamed -> {
                w.uvar(E_RENAMED); w.svar(e.player); w.string(e.from); w.string(e.to)
            }
            is LogEvent.PatternChanged -> {
                w.uvar(E_PATTERN); w.svar(e.player); w.string(e.pattern)
            }
            is LogEvent.PartnerChanged -> {
                w.uvar(E_PARTNER); w.svar(e.player); w.bool(e.partner)
            }
            is LogEvent.DayNightChanged -> {
                w.uvar(E_DAY_NIGHT); w.uvar(Codes.dayNight(e.value))
            }
            is LogEvent.TurnChanged -> {
                w.uvar(E_TURN); w.optInt(e.activePlayer); w.svar(e.number); w.svar(e.round)
            }
            is LogEvent.SettingsChanged -> {
                w.uvar(E_SETTINGS); writeGameSettings(w, e.settings)
            }
            is LogEvent.PlayerCountChanged -> {
                w.uvar(E_PLAYER_COUNT); w.svar(e.count)
            }
            LogEvent.NewGameStarted -> w.uvar(E_NEW_GAME)
            LogEvent.GameReset -> w.uvar(E_RESET)
        }
    }

    /** Null for an unknown tag or enum code (the entry is skipped). Malformed data still throws. */
    private fun readEvent(r: ByteReader): LogEvent? = when (r.uvar()) {
        E_LIFE -> LogEvent.LifeChanged(r.svar(), r.svar(), r.svar(), r.optInt(), r.optInt())
        E_COUNTER -> {
            val player = r.svar()
            val counter = Codes.counter(r.uvar())
            val delta = r.svar()
            val value = r.svar()
            counter?.let { LogEvent.CounterChanged(player, it, delta, value) }
        }
        E_TAX -> LogEvent.CommanderTaxChanged(r.svar(), r.svar(), r.svar(), r.svar())
        E_CMD_DAMAGE -> LogEvent.CommanderDamageChanged(r.svar(), r.svar(), r.svar(), r.svar(), r.svar())
        E_TOGGLE -> {
            val player = r.svar()
            val toggle = Codes.toggle(r.uvar())
            val on = r.bool()
            toggle?.let { LogEvent.ToggleChanged(player, it, on) }
        }
        E_KNOCKED_OUT -> {
            val player = r.svar()
            Codes.koReason(r.uvar())?.let { LogEvent.KnockedOut(player, it) }
        }
        E_REVIVED -> LogEvent.Revived(r.svar())
        E_RENAMED -> LogEvent.Renamed(r.svar(), r.string(), r.string())
        E_PATTERN -> LogEvent.PatternChanged(r.svar(), r.string())
        E_PARTNER -> LogEvent.PartnerChanged(r.svar(), r.bool())
        E_DAY_NIGHT -> Codes.dayNight(r.uvar())?.let { LogEvent.DayNightChanged(it) }
        E_TURN -> LogEvent.TurnChanged(r.optInt(), r.svar(), r.svar())
        E_SETTINGS -> LogEvent.SettingsChanged(readGameSettings(r))
        E_PLAYER_COUNT -> LogEvent.PlayerCountChanged(r.svar())
        E_NEW_GAME -> LogEvent.NewGameStarted
        E_RESET -> LogEvent.GameReset
        else -> null
    }
}
