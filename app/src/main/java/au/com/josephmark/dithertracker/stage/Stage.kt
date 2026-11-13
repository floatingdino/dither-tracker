package au.com.josephmark.dithertracker.stage

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.coalesceKey
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.Scheduler
import au.com.josephmark.dithertracker.play.TileSlots

/**
 * The Play screen's host core (T34): owns no feature logic. Pure Kotlin (the Android view,
 * `play.PlayView`, is a thin shell around it), so it is JVM-tested with fake components.
 *
 * - **Components** are registered once, in order ([add]); their [Layer] decides the draw/hit pass and
 *   the order inside a layer is registration order. [seal] fixes the zone and timer id spaces.
 * - **Zone ids** are allocated here: component `c` gets the block `zoneBase[c] until zoneBase[c] +
 *   (seats × zonesPerSeat)` (tile) or `+ zonesPerSeat` (screen); a global id is
 *   `base + seat × zonesPerSeat + local`. Components only ever see `(seat, local)`.
 * - **Touch** goes through one hit-test over every visible component's declared zones, topmost first
 *   ([hitTest]), into one [PlayInput] (release-to-fire, hold-repeat, multi-pointer).
 * - **Drawing** ([draw]): layer by layer, tile layers seat by seat; after each layer the pressed zones
 *   owned by that layer are inverted.
 * - **Invalidation** from components is unioned and flushed once per entry point (touch, timer, state,
 *   settings, layout, cover change) to the [Platform] in device px.
 * - **Covers** ([Cover]) hide components by [Kind] on seats / the screen: the mode/visibility mechanism.
 *
 * Main thread only; nothing here allocates after [seal] and [layout].
 */
class Stage(
    val session: GameSession,
    val settings: SettingsHolder,
    private val platform: Platform,
    /** Seats the per-seat zone blocks are sized for. */
    val seats: Int = PlayLayout.PLAYERS,
) : PlayInput.ZoneMap, PlayInput.Sink, Scheduler.Target, GameSession.Listener, SettingsHolder.Listener {

    /** What the stage needs from the platform (the Android view in the app, a fake in tests). */
    interface Platform {
        /** Redraw this device-px rect. */
        fun invalidate(left: Int, top: Int, right: Int, bottom: Int)

        /** Redraw everything. */
        fun invalidateAll()

        /** One-shot timer bank, ids `0 until` [Stage.timerCount]; firing calls [Stage.onTimer]. */
        fun schedule(id: Int, delayMillis: Long)
        fun cancel(id: Int)
        fun cancelAllTimers()

        /** Wall-clock ms for core action timestamps. */
        fun now(): Long
    }

    private val list = ArrayList<Component>()
    private val coverList = ArrayList<Cover>()
    private var comps: Array<Component> = emptyArray()
    private var covers: Array<Cover> = emptyArray()
    private var byLayer: Array<Array<Component>> = emptyArray()
    private var zoneBase = IntArray(0)
    private var timerBase = IntArray(0)

    /** Size of the global zone id space (also the first component timer id). */
    var zoneCount = 0
        private set

    /** Size of the global timer id space: hold timers (one per zone), then each component's block. */
    var timerCount = 0
        private set

    // Global zone id -> owner.
    private var idComp = IntArray(0)
    private var idSeat = IntArray(0)
    private var idLocal = IntArray(0)

    /** The press tracker (hold timers are timer ids `0 until zoneCount`). */
    lateinit var input: PlayInput
        private set

    var sealed = false
        private set

    var layout: PlayLayout? = null
        private set

    var tiles: TileSlots? = null
        private set

    /** The readout service (set by the readout component when it is added). */
    var readouts: Readouts = Readouts.NONE

    /** Life per hold-repeat step (`AppSettings.holdRepeatStep`, T12); ≤ 0 disables hold on life. */
    var holdStep: Int = PlayInput.DEFAULT_HOLD_STEP

    /** Debug log (trace builds); null = off. Allocates when on. */
    var log: ((String) -> Unit)? = null

    /**
     * Watches every frame (T19b: the e-ink cleanup cadence, `eink.CleanComponent`). Told of each
     * component invalidation, each pointer-down and the end of each outermost entry point.
     */
    interface Observer {
        /** A component invalidated [rect] (dither px), before the union. */
        fun onInvalidate(rect: IRect)

        /** A pointer went down (after the hit-test, inside the entry point). */
        fun onPointerDown()

        /** An outermost entry point ended (after its flush); [cause] is a [Cause] constant. */
        fun onSettled(cause: Int)
    }

    /** What started the outermost entry point ([Observer.onSettled]). */
    object Cause {
        const val OTHER = 0
        const val TOUCH_DOWN = 1
        const val TOUCH_UP = 2
        const val TOUCH_CANCEL = 3
        /** A hold-repeat tick. */
        const val HOLD = 4
        /** A component timer. */
        const val TIMER = 5
        /** A game change from outside the stage's own entry points (restore, debug hooks). */
        const val STATE = 6
    }

    var observer: Observer? = null

    // ---- setup ---------------------------------------------------------------------------------

    /** Registers [c] (before [seal]). Returns it. */
    fun <C : Component> add(c: C): C {
        check(!sealed) { "add before seal()" }
        check(c.index < 0) { "component added twice" }
        c.stage = this
        c.index = list.size
        list += c
        c.onAdded()
        return c
    }

    /** Registers [cover] (before [seal]). Returns it. */
    fun addCover(cover: Cover): Cover {
        check(!sealed) { "addCover before seal()" }
        coverList += cover
        return cover
    }

    /** Fixes the component list and allocates the zone and timer id spaces. */
    fun seal() {
        check(!sealed)
        sealed = true
        comps = list.toTypedArray()
        covers = coverList.toTypedArray()
        byLayer = Array(Layer.COUNT) { l -> comps.filter { it.layer == l }.toTypedArray() }
        zoneBase = IntArray(comps.size)
        var z = 0
        for (c in comps) {
            zoneBase[c.index] = z
            z += zoneSpan(c)
        }
        zoneCount = z
        idComp = IntArray(z); idSeat = IntArray(z); idLocal = IntArray(z)
        for (c in comps) {
            val n = c.zonesPerSeat
            for (i in 0 until zoneSpan(c)) {
                val id = zoneBase[c.index] + i
                idComp[id] = c.index
                idSeat[id] = if (c.isTile) i / n else SCREEN
                idLocal[id] = if (c.isTile) i % n else i
            }
        }
        timerBase = IntArray(comps.size)
        var t = zoneCount
        for (c in comps) {
            timerBase[c.index] = t
            t += c.timerCount
        }
        timerCount = t
        armed = BooleanArray(t)
        input = PlayInput(this, holdTimers, zoneCount)
        val cap = 2 * zoneCount + EXTRA_RECTS
        zRect = Array(cap) { IRect() }
        zId = IntArray(cap); zComp = IntArray(cap); zSeat = IntArray(cap); zLocal = IntArray(cap)
    }

    private fun zoneSpan(c: Component): Int = if (c.isTile) seats * c.zonesPerSeat else c.zonesPerSeat

    /** All components in registration order. */
    val components: List<Component> get() = comps.asList()

    /** Global zone id of [c]'s [local] on [seat] ([SCREEN] for screen components). */
    fun zoneId(c: Component, seat: Int, local: Int): Int {
        require(local in 0 until c.zonesPerSeat) { "local $local not in 0 until ${c.zonesPerSeat}" }
        return zoneBase[c.index] + (if (c.isTile) seat * c.zonesPerSeat else 0) + local
    }

    /** Owner of global zone [id]: component, seat, local. */
    fun zoneOwner(id: Int): Component = comps[idComp[id]]
    fun zoneSeat(id: Int): Int = idSeat[id]
    fun zoneLocal(id: Int): Int = idLocal[id]

    // ---- lifecycle -----------------------------------------------------------------------------

    fun attach() {
        enter()
        try {
            session.listener = this
            settings.addListener(this)
            for (c in comps) c.onAttach()
        } finally { exit() }
        platform.invalidateAll() // state may have changed while detached
    }

    fun detach() {
        enter()
        try {
            input.cancelAll()
            for (c in comps) c.onDetach()
        } finally { exit() }
        platform.cancelAllTimers()
        armed.fill(false)
        settings.removeListener(this)
        if (session.listener === this) session.listener = null
    }

    /** New geometry: every component lays out, every touch is cancelled. */
    fun layout(l: PlayLayout) {
        enter()
        try {
            layout = l
            val t = TileSlots(l)
            tiles = t
            for (c in comps) c.onLayout(l, t)
            input.zones = this // (re)binding cancels every touch
        } finally { exit() }
    }

    fun prewarm(f: Frame) {
        for (c in comps) c.prewarm(f)
    }

    // ---- services for components -------------------------------------------------------------

    /** Whether [c] is visible on [seat] ([SCREEN] for screen components): no active [Cover] hides it. */
    fun isVisible(c: Component, seat: Int): Boolean {
        if (!anyCover) return true
        val cs = covers
        for (i in cs.indices) {
            val cv = cs[i]
            if (cv.hides and c.kind != 0 && cv.covers(seat)) return false
        }
        return true
    }

    /**
     * Turns [cover] on / changes / turns it off ([seats] mask, [screen] flag; both empty = off).
     * Cancels every touch, notifies every component, redraws the affected panels and screen components.
     */
    fun setCover(cover: Cover, seats: Int, screen: Boolean) {
        enter()
        try {
            val oldSeats = cover.seats
            val oldScreen = cover.screen
            cover.seats = seats
            cover.screen = screen
            anyCover = false
            for (cv in covers) if (cv.active) anyCover = true
            input.cancelAll()
            for (c in comps) c.onCoversChanged()
            val l = layout ?: return
            val affected = oldSeats or seats
            for (s in 0 until minOf(this.seats, l.panels.size)) if ((affected ushr s) and 1 != 0) invalidate(l.panels[s])
            if (oldScreen || screen) {
                for (c in comps) if (!c.isTile && c.bounds(scratch)) invalidate(scratch)
            }
        } finally { exit() }
    }

    /** Whether any active [Cover] hides a [kind] bit on [seat] ([SCREEN] for the screen layers). */
    fun covered(kind: Int, seat: Int): Boolean {
        if (!anyCover) return false
        for (cv in covers) if (cv.hides and kind != 0 && cv.covers(seat)) return true
        return false
    }

    /** Whether any timer (hold or component) is armed, ignoring [except]'s own block. */
    fun timersPending(except: Component? = null): Boolean {
        val skipFrom = if (except != null) timerBase[except.index] else -1
        val skipTo = if (except != null) skipFrom + except.timerCount else -1
        for (i in armed.indices) if (armed[i] && (i < skipFrom || i >= skipTo)) return true
        return false
    }

    /** Redraw this dither rect (unioned and flushed once per entry point). */
    fun invalidate(rect: IRect) {
        if (rect.right <= rect.left || rect.bottom <= rect.top) return
        observer?.onInvalidate(rect)
        if (!dirtyAny) {
            dirty.set(rect.left, rect.top, rect.right, rect.bottom)
            dirtyAny = true
        } else {
            dirty.set(
                minOf(dirty.left, rect.left), minOf(dirty.top, rect.top),
                maxOf(dirty.right, rect.right), maxOf(dirty.bottom, rect.bottom),
            )
        }
        if (depth == 0) flush()
    }

    /** Releases, without applying, every press on [c]'s locals `[from, until)` of [seat]. */
    fun cancelTouches(c: Component, seat: Int, from: Int = 0, until: Int = c.zonesPerSeat) {
        if (until <= from) return
        input.cancelZones(zoneId(c, seat, from), zoneId(c, seat, until - 1) + 1)
    }

    fun cancelAllTouches() = input.cancelAll()

    /** Whether [c]'s [local] on [seat] is pressed. */
    fun isPressed(c: Component, seat: Int, local: Int): Boolean = input.isPressed(zoneId(c, seat, local))

    /** Sends [event] ([Events]) about [seat] to every component, in registration order. */
    fun send(event: Int, seat: Int) {
        enter()
        try {
            for (c in comps) c.onEvent(event, seat)
        } finally { exit() }
    }

    /** The origin of the state change being dispatched now ([Origin] or a seat). */
    var origin: Int = Origin.EXTERNAL
        private set

    /** Dispatches [action] on behalf of [origin] (a seat, or [Origin.ATTRIBUTED]). */
    fun dispatch(origin: Int, action: Action, key: String? = action.coalesceKey) {
        val prev = this.origin
        this.origin = origin
        try {
            session.dispatch(action, platform.now(), key)
        } finally {
            this.origin = prev
        }
    }

    /** ±[delta] life for [seat], attributed to that seat (its readout shows it). */
    fun changeLife(seat: Int, delta: Int) {
        val prev = origin
        origin = seat
        try {
            session.changeLife(seat, delta, platform.now())
        } finally {
            origin = prev
        }
    }

    internal fun scheduleTimer(c: Component, id: Int, delayMillis: Long) {
        require(id in 0 until c.timerCount) { "timer $id not in 0 until ${c.timerCount}" }
        armed[timerBase[c.index] + id] = true
        platform.schedule(timerBase[c.index] + id, delayMillis)
    }

    internal fun cancelTimer(c: Component, id: Int) {
        require(id in 0 until c.timerCount) { "timer $id not in 0 until ${c.timerCount}" }
        armed[timerBase[c.index] + id] = false
        platform.cancel(timerBase[c.index] + id)
    }

    // ---- entry points --------------------------------------------------------------------------

    fun down(pointerId: Int, gx: Int, gy: Int, eventTimeMillis: Long) {
        enter(Cause.TOUCH_DOWN)
        try {
            input.down(pointerId, gx, gy, eventTimeMillis)
            val z = input.zoneOf(pointerId)
            if (z >= 0 && idSeat[z] >= 0) send(Events.TOUCH_DOWN, idSeat[z])
            observer?.onPointerDown()
        } finally { exit() }
    }

    fun up(pointerId: Int) {
        enter(Cause.TOUCH_UP)
        try { input.up(pointerId) } finally { exit() }
    }

    fun cancel() {
        enter(Cause.TOUCH_CANCEL)
        try { input.cancelAll() } finally { exit() }
    }

    override fun onTimer(id: Int) {
        if (id in armed.indices) armed[id] = false
        enter(if (id < zoneCount) Cause.HOLD else Cause.TIMER)
        try {
            if (id < zoneCount) {
                input.onTimer(id)
            } else {
                // Component timer: find the owning block (few components; no allocation).
                for (i in comps.size - 1 downTo 0) {
                    val b = timerBase[i]
                    if (id >= b) {
                        if (id - b < comps[i].timerCount) comps[i].onTimer(id - b)
                        break
                    }
                }
            }
        } finally { exit() }
    }

    override fun onStateChanged(old: GameState, new: GameState) {
        val l = layout ?: return
        enter(Cause.STATE)
        try {
            val np = new.players
            if (np.size != l.playerCount || old.players.size != np.size) {
                for (c in comps) c.onPlayersChanged()
                platform.invalidateAll()
                return
            }
            val o = origin
            for (c in comps) c.onStateChanged(old, new, o)
        } finally { exit() }
    }

    override fun onSettingsChanged(old: AppSettings, new: AppSettings) {
        enter()
        try {
            for (c in comps) c.onSettingsChanged(old, new)
        } finally { exit() }
    }

    // ---- PlayInput.ZoneMap / Sink --------------------------------------------------------------

    /**
     * Global zone id under dither (gx, gy): the topmost visible declared zone ([PlayLayout.NONE] for
     * nothing or a blocking rect). Collects every visible component's zones first.
     */
    override fun hitTest(gx: Int, gy: Int): Int {
        collectZones()
        for (i in zn - 1 downTo 0) {
            if (!zRect[i].contains(gx, gy)) continue
            val c = comps[zComp[i]]
            if (!c.contains(zSeat[i], zLocal[i], gx, gy)) continue
            return zId[i] // NONE for a block
        }
        return PlayLayout.NONE
    }

    override fun repeats(zone: Int): Boolean = comps[idComp[zone]].repeats(idSeat[zone], idLocal[zone])

    override fun onZoneTap(zone: Int) {
        log?.invoke("tap zone $zone (component ${idComp[zone]} seat ${idSeat[zone]} local ${idLocal[zone]})")
        comps[idComp[zone]].onTap(idSeat[zone], idLocal[zone])
    }

    override fun onZoneRepeat(zone: Int) {
        log?.invoke("repeat zone $zone")
        comps[idComp[zone]].onRepeat(idSeat[zone], idLocal[zone])
    }

    override fun onZonePressed(zone: Int) {
        if (feedback(zone, fb)) invalidate(fb)
    }

    override fun onZoneReleased(zone: Int) {
        if (feedback(zone, fb)) invalidate(fb)
    }

    /** The current feedback rect of global [zone]. */
    fun feedback(zone: Int, out: IRect): Boolean = comps[idComp[zone]].feedback(idSeat[zone], idLocal[zone], out)

    // ---- drawing -------------------------------------------------------------------------------

    /**
     * Draws everything in [f]'s clip (the canvas must already be cleared to paper): layer by layer;
     * after each layer, the pressed zones owned by that layer are inverted.
     */
    fun draw(f: Frame) {
        if (layout == null) return
        f.players = session.state.players
        for (layer in 0 until Layer.COUNT) {
            drawLayer(f, layer)
            invertPressed(f, layer)
        }
    }

    /** Draws layers `0..layer` without press feedback (the API < 29 inverted-redraw fallback). */
    fun drawUpTo(f: Frame, layer: Int) {
        f.players = session.state.players
        for (l in 0..layer) drawLayer(f, l)
    }

    private fun drawLayer(f: Frame, layer: Int) {
        val l = layout ?: return
        val cs = byLayer[layer]
        if (cs.isEmpty()) return
        if (Layer.isTile(layer)) {
            val n = minOf(l.playerCount, f.players.size, seats)
            val covered = anyCover
            for (s in 0 until n) {
                if (!f.hit(l.panels[s])) continue
                for (c in cs) if (!covered || isVisible(c, s)) c.draw(f, s)
            }
        } else {
            val covered = anyCover
            for (c in cs) if (!covered || isVisible(c, SCREEN)) c.draw(f, SCREEN)
        }
    }

    private fun invertPressed(f: Frame, layer: Int) {
        if (input.pressedCount == 0) return
        for (c in byLayer[layer]) {
            val base = zoneBase[c.index]
            for (z in base until base + zoneSpan(c)) {
                if (!input.isPressed(z)) continue
                if (!c.feedback(idSeat[z], idLocal[z], fbDraw) || !f.hit(fbDraw)) continue
                f.invert(fbDraw, layer)
            }
        }
    }

    // ---- internals -----------------------------------------------------------------------------

    private val holdTimers = object : Scheduler {
        override fun schedule(id: Int, delayMillis: Long) {
            armed[id] = true
            platform.schedule(id, delayMillis)
        }

        override fun cancel(id: Int) {
            armed[id] = false
            platform.cancel(id)
        }
    }

    /** Armed timers by global id ([timersPending]). */
    private var armed = BooleanArray(0)

    /** What started the outermost entry point in progress. */
    private var cause = Cause.OTHER

    /** True while any cover is active (fast path for [isVisible]). */
    private var anyCover = false

    private var depth = 0
    private val dirty = IRect()
    private var dirtyAny = false
    private val dev = IRect()
    private val fb = IRect()
    private val fbDraw = IRect()
    private val scratch = IRect()

    private fun enter(c: Int = Cause.OTHER) {
        if (depth++ == 0) cause = c
    }

    private fun exit() {
        if (--depth == 0) {
            flush()
            observer?.onSettled(cause)
        }
    }

    private fun flush() {
        if (!dirtyAny) return
        dirtyAny = false
        val l = layout ?: return
        l.grid.toDevice(dirty, dev)
        platform.invalidate(dev.left, dev.top, dev.right, dev.bottom)
    }

    // Collected zones (rebuilt at each touch-down), bottom first.
    private var zRect: Array<IRect> = emptyArray()
    private var zId = IntArray(0)
    private var zComp = IntArray(0)
    private var zSeat = IntArray(0)
    private var zLocal = IntArray(0)
    private var zn = 0

    private val sink = object : ZoneSink {
        var c: Component? = null
        var seat = 0
        override fun add(local: Int, rect: IRect) {
            val comp = c!!
            require(local in 0 until comp.zonesPerSeat) { "${comp.javaClass.simpleName}: local $local not in 0 until ${comp.zonesPerSeat}" }
            put(comp, seat, local, zoneId(comp, seat, local), rect)
        }

        override fun block(rect: IRect) = put(c!!, seat, BLOCK, PlayLayout.NONE, rect)

        private fun put(comp: Component, seat: Int, local: Int, id: Int, rect: IRect) {
            check(zn < zRect.size) { "zone table full (${zRect.size})" }
            zRect[zn].set(rect.left, rect.top, rect.right, rect.bottom)
            zId[zn] = id; zComp[zn] = comp.index; zSeat[zn] = seat; zLocal[zn] = local
            zn++
        }
    }

    private fun collectZones() {
        zn = 0
        val l = layout ?: return
        val n = minOf(l.playerCount, session.state.players.size, seats)
        for (layer in 0 until Layer.COUNT) {
            val cs = byLayer[layer]
            if (Layer.isTile(layer)) {
                for (s in 0 until n) for (c in cs) {
                    if (!isVisible(c, s)) continue
                    sink.c = c; sink.seat = s
                    c.zones(s, sink)
                }
            } else {
                for (c in cs) {
                    if (!isVisible(c, SCREEN)) continue
                    sink.c = c; sink.seat = SCREEN
                    c.zones(SCREEN, sink)
                }
            }
        }
        sink.c = null
    }

    /** Collected zones, for tests and debugging: count and entry accessors. */
    val collectedCount: Int get() = zn
    fun collectedId(i: Int): Int = zId[i]
    fun collectedRect(i: Int): IRect = zRect[i]
    fun collectNow() = collectZones()

    companion object {
        /** The seat argument of screen components. */
        const val SCREEN = -1

        /** Local id of a blocking rect (passed to [Component.contains]). */
        const val BLOCK = -1

        /** Room in the zone table for blocking rects beyond 2 × the zone count. */
        private const val EXTRA_RECTS = 32
    }
}
