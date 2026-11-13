package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.core.GameSettings
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.widgets.CharBuf
import au.com.josephmark.dithertracker.widgets.ListRow
import au.com.josephmark.dithertracker.widgets.Segmented
import au.com.josephmark.dithertracker.widgets.Toggle
import au.com.josephmark.dithertracker.widgets.Widget

/**
 * The two settings stores a row reads and writes: the UI settings ([AppSettings], held by
 * `data.SettingsHolder`, persisted by it) and the rule settings inside the game ([GameSettings],
 * changed through the session with `Action.UpdateSettings`, persisted with the game). The overlay
 * implements it over the stage; tests use a plain fake.
 */
interface SettingsAccess {
    val app: AppSettings
    val game: GameSettings

    /** Seats at the table now (the running game's player count; `app.playerCount` is the next game's). */
    val players: Int get() = app.playerCount
    fun updateApp(new: AppSettings)
    fun updateGame(new: GameSettings)
}

/** Reads an int choice from the settings (a `fun interface`, so no boxing on the touch path). */
fun interface IntRead {
    fun read(s: SettingsAccess): Int
}

/** Writes an int choice back. */
fun interface IntWrite {
    fun write(s: SettingsAccess, value: Int)
}

fun interface BoolRead {
    fun read(s: SettingsAccess): Boolean
}

fun interface BoolWrite {
    fun write(s: SettingsAccess, value: Boolean)
}

/**
 * One row of the settings card: owns its widget (built once with [create]), reads the settings into
 * it ([bind]) and turns a tap on one of its parts into a settings write or a navigation ([tap]).
 * Layout, paging, drawing and hit-testing are the card's job ([SettingsCard]); a row never knows
 * where it is.
 *
 * **Extension point (T11 players/layout/orientation, T13 names/patterns):** add a row to
 * [SettingsTable.main] (or a [LinkRow] to a new [SettingsPage]) — nothing else changes; the card
 * sizes itself for the longest page and pages rows that do not fit.
 */
abstract class SettingsRow {

    /** The row's widget (after [create]). */
    lateinit var widget: Widget
        private set

    /** Ids this row may own (its widget's id count can change on [onScreen], never above this). */
    open val maxIds: Int get() = 1

    /** Builds the widget with first id [id] (once, when the overlay is built). */
    fun create(id: Int): Widget = build(id).also { widget = it }

    protected abstract fun build(id: Int): Widget

    /** Replaces the widget (rows whose options depend on the screen, see [ScaleRow]). */
    protected fun replace(w: Widget) {
        widget = w
    }

    /** The screen changed (first layout, size or scale): rows whose choices depend on it rebuild. */
    open fun onScreen(grid: DitherGrid) {}

    /** The card was opened: rows that compare with "how it was when you came in" remember it. */
    open fun onOpen(s: SettingsAccess) {}

    /** Whether the row is listed now (hints come and go). */
    open fun visible(s: SettingsAccess): Boolean = true

    /** Reads the settings into the widget. */
    abstract fun bind(s: SettingsAccess)

    /** Applies a tap on [part] of the widget: one of the [Tap] results. */
    abstract fun tap(part: Int, s: SettingsAccess): Int

    /** [tap] results. */
    object Tap {
        const val NONE = 0
        const val CHANGED = 1

        /** Open [LinkRow.page]. */
        const val OPEN = 2

        /** Back to the previous page. */
        const val BACK = 3
    }
}

/**
 * A choice among a few int [values] shown as a [Segmented] with [options] (selected = inverted).
 * Numeric options use the heavy small numerals. A stored value that is not offered selects nothing.
 */
open class ChoiceRow(
    val label: String,
    val options: Array<String>,
    val values: IntArray,
    private val read: IntRead,
    private val write: IntWrite,
) : SettingsRow() {
    init {
        require(options.size == values.size) { "options/values mismatch" }
    }

    override val maxIds: Int get() = Segmented.MAX_OPTIONS

    val segmented: Segmented get() = widget as Segmented

    override fun build(id: Int): Widget = Segmented(id, label, options).also { it.numerals = true }

    /** Index of the stored value among [currentValues], or -1. */
    fun selectedIndex(s: SettingsAccess): Int {
        val v = read.read(s)
        val vs = currentValues
        for (i in vs.indices) if (vs[i] == v) return i
        return -1
    }

    /** The values currently offered (a [ScaleRow] filters them by screen). */
    open val currentValues: IntArray get() = values

    override fun bind(s: SettingsAccess) {
        segmented.selected = selectedIndex(s)
    }

    override fun tap(part: Int, s: SettingsAccess): Int {
        val vs = currentValues
        if (part !in vs.indices) return Tap.NONE
        if (read.read(s) == vs[part]) return Tap.NONE
        write.write(s, vs[part])
        return Tap.CHANGED
    }
}

/** An on/off setting as a [Toggle] (solid box = on). */
class ToggleRow(
    val label: String,
    private val read: BoolRead,
    private val write: BoolWrite,
    private val icon: Icon? = null,
) : SettingsRow() {
    val toggle: Toggle get() = widget as Toggle

    override fun build(id: Int): Widget = Toggle(id, label, icon = icon)

    override fun bind(s: SettingsAccess) {
        toggle.checked = read.read(s)
    }

    override fun tap(part: Int, s: SettingsAccess): Int {
        write.write(s, !read.read(s))
        return Tap.CHANGED
    }
}

/** A row that opens a sub-[page] (chevron ›). */
class LinkRow(val label: String, val page: SettingsPage, private val icon: Icon? = null) : SettingsRow() {
    override fun build(id: Int): Widget = ListRow(id, label, icon = icon, chevron = true)
    override fun bind(s: SettingsAccess) {}
    override fun tap(part: Int, s: SettingsAccess): Int = Tap.OPEN
}

/** The first row of a sub-page: ‹ back to where it was opened from. */
class BackRow(val label: String) : SettingsRow() {
    override fun build(id: Int): Widget = ListRow(id, label, icon = Icon.CHEVRON_LEFT)
    override fun bind(s: SettingsAccess) {}
    override fun tap(part: Int, s: SettingsAccess): Int = Tap.BACK
}

/**
 * A quiet, untappable line of text (no id), right-aligned, listed only while [shown]. The card draws
 * it without the dotted divider above it, so it reads as a footnote. Its [text] is rewritten on every
 * bind (into one reused buffer). Put hints **last** on their page: one that appears mid-list would
 * shift the rows under a finger that is about to tap again.
 */
class HintRow(private val text: Text, private val shown: Shown) : SettingsRow() {
    fun interface Shown {
        fun shown(s: SettingsAccess, opened: GameSettings?): Boolean
    }

    fun interface Text {
        /** [opened]: the game settings when the card opened (as [Shown] gets them). */
        fun write(s: SettingsAccess, opened: GameSettings?, out: CharBuf)
    }

    private var opened: GameSettings? = null
    val buf = CharBuf(MAX_CHARS)

    override val maxIds: Int get() = 0

    override fun build(id: Int): Widget = ListRow(Widget.NO_ID, "", value = buf)
    override fun onOpen(s: SettingsAccess) {
        opened = s.game
    }

    override fun visible(s: SettingsAccess): Boolean = shown.shown(s, opened)
    override fun bind(s: SettingsAccess) {
        text.write(s, opened, buf.clear())
    }

    override fun tap(part: Int, s: SettingsAccess): Int = Tap.NONE

    companion object {
        const val MAX_CHARS = 48
    }
}

/**
 * Scale S: AUTO plus every S in 2..6 at which the Play layout is `comfortable` on this screen
 * ([SettingsTable.scaleValues]); rebuilt when the screen changes (the Nomad's set until then).
 */
class ScaleRow(read: IntRead, write: IntWrite) :
    ChoiceRow(LABEL, arrayOf("AUTO", "2", "3", "4"), intArrayOf(AppSettings.SCALE_AUTO, 2, 3, 4), read, write) {

    private var offered: IntArray = values
    private var builtFor: DitherGrid? = null
    private var id = Widget.NO_ID

    override val currentValues: IntArray get() = offered

    override fun build(id: Int): Widget {
        this.id = id
        return segmentedFor(offered)
    }

    override fun onScreen(grid: DitherGrid) {
        val same = builtFor
        if (same != null && same.deviceWidth == grid.deviceWidth && same.deviceHeight == grid.deviceHeight) return
        builtFor = grid
        val vs = SettingsTable.scaleValues(grid.deviceWidth, grid.deviceHeight)
        if (vs.contentEquals(offered)) return
        offered = vs
        replace(segmentedFor(vs))
    }

    private fun segmentedFor(vs: IntArray): Segmented =
        Segmented(id, LABEL, Array(vs.size) { if (vs[it] == AppSettings.SCALE_AUTO) "AUTO" else vs[it].toString() }).also { it.numerals = true }

    companion object {
        const val LABEL = "Scale"
    }
}

/** A titled list of rows: the main settings, or a sub-page reached through a [LinkRow]. */
class SettingsPage(val title: String, val rows: Array<SettingsRow>)
