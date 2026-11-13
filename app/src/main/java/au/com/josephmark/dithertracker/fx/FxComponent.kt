package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Origin
import kotlin.random.Random

/**
 * Hit / heal / KO / winner effects on the tiles (T28c). Always on (SPEC: no setting). Display only:
 * [Kind.EFFECT], no zones; drawn in the `TILE` layer right after the number, readout and drawer
 * (its slot in `PlayScreen`), clipped to the seat's panel. Wiring notes: `fx/README.md`.
 *
 * - **Trigger**: once per committed pending-delta group ([Events.READOUT_COMMITTED]), never per tap
 *   or hold step (a hold keeps re-arming the readout, so nothing commits while it repeats). The net
 *   life change of the group is mirrored here from the state diffs the readout itself adds (the
 *   seat's own taps; [Origin.ATTRIBUTED] life loss, i.e. commander damage on the target), so a
 *   commander-damage source's group (its dealt damage, no life change) plays nothing, and poison or
 *   counters never do. [FxPolicy] picks the effect.
 * - **KO**: when a seat's life group KOs it, [Events.KO_HOLD] keeps the dead treatment off while the
 *   group is still open; at commit `ko-shatter` (or `fizzle` when compact) plays and
 *   [Events.KO_RELEASE] follows when it ends, is cancelled or can't play. **Last player standing**:
 *   `winner` on the survivor, alongside the KO flourish (or at once for a KO with no flourish).
 * - **Cancel**: a touch-down on the seat ([Events.TOUCH_DOWN]) stops its effect (one clean redraw);
 *   undo/redo/new game/menu actions ([Origin.EXTERNAL]), a layout change, a player-count change and
 *   detach stop all of them; a cover that hides the effect or the seat's content stops it there.
 * - **Commander mode** hides the seat's number but not its effects: a commit there is kept and played
 *   when the number is back (the mode closes), summed if several groups commit meanwhile.
 * - At most [FxPlayer.maxConcurrent] (2) seats animate; a third just stays clean. KO and winner
 *   flourishes make room by stopping a plain hit/heal.
 * - **Invalidation**: once per keyframe, the effect's footprint (the whole panel for tile-scale
 *   effects) clipped to the panel, stored when the effect starts, so the clean step redraws it too.
 * - **Anchor box**: the number's whole footprint (the sticker box, shadow rows included), exactly the
 *   box `render2.py` anchored the designs to — not the cap band.
 *
 * The debug gallery (`--es fx <name|all>`, debuggable builds) drives this same component: see
 * [FxDebug] and [startGallery].
 */
class FxComponent(random: Random = Random(System.nanoTime())) :
    Component(Layer.TILE, Kind.EFFECT, timerCount = P + 1), FxPlayer.Listener {

    val policy = FxPolicy(random, P)

    /** Playback; its timer ids are seat indices, in this component's own timer bank. */
    val player = FxPlayer(P, timers, this)

    /** Draws frames; built from the frame's renderer on first use (tests inject a raster target). */
    internal var renderer: FxRenderer? = null

    private var l: PlayLayout? = null
    private var t: TileSlots? = null
    private var rotations = FxRenderer.DEFAULT_ROTATIONS

    /** Per seat: the screen rect its current effect covers (clipped to the panel). */
    private val area = Array(P) { IRect() }
    private val box = IRect()

    /** Per seat: net life change of the open readout group (mirrors the readout's). */
    private val net = IntArray(P)

    /** Per seat: commits kept while the seat's number is hidden (commander mode), summed. */
    private val deferred = IntArray(P)

    /** Seats whose open life group KO'd them; the dead treatment is held for the flourish. */
    private var koHeld = 0

    /** Seats playing a flourish that holds the dead treatment ([Events.KO_RELEASE] when it ends). */
    private var koPlaying = 0

    private var winnerShown = false
    private var winPending = -1

    /** The last state seen in [onStateChanged]: a commit while another state is being dispatched is that change's doing. */
    private var seen: GameState? = null

    // A stand-in to ask the stage whether a seat's normal content (its number) is visible there.
    private val content = object : Component(Layer.TILE, Kind.CONTENT) {}

    // ---- lifecycle ---------------------------------------------------------------------------------

    override fun onAttach() {
        player.holdScale = FxDebug.holdScale
        resync()
    }

    override fun onDetach() {
        stopGallery()
        stopAll()
    }

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        stopAll()
        l = play
        t = tiles
        rotations = play.rotations
        resync()
        if (FxDebug.gallery != null && !galleryStarted) {
            galleryStarted = true
            timers.schedule(GALLERY_TIMER, GALLERY_START_MS)
        }
    }

    override fun onPlayersChanged() {
        stopAll()
        policy.reset()
        resync()
    }

    override fun prewarm(f: Frame) {
        val r = renderer(f)
        r.prewarm(if (FxDebug.gallery != null) Fx.entries else policy.effects(), rotations)
    }

    private fun renderer(f: Frame): FxRenderer = renderer ?: FxRenderer(f.r).also { renderer = it }

    // ---- drawing -----------------------------------------------------------------------------------

    override fun draw(f: Frame, seat: Int) {
        val fx = player.current(seat) ?: return
        val l = l ?: return
        val t = t ?: return
        if (!f.hit(area[seat])) return
        val panel = l.panels[seat]
        t.numberFootprint(seat, t.compact(seat), f.players[seat].life, box)
        renderer(f).draw(fx.spec, player.frame(seat), box, panel, l.rotation[seat], panel)
    }

    override fun onFrameChanged(seat: Int) {
        invalidate(area[seat])
        if (!player.isPlaying(seat)) {
            val bit = 1 shl seat
            if (koPlaying and bit != 0) {
                koPlaying = koPlaying and bit.inv()
                stage.send(Events.KO_RELEASE, seat)
            }
        }
    }

    // ---- triggers ----------------------------------------------------------------------------------

    override fun onEvent(event: Int, seat: Int) {
        if (event == Events.PAUSE) { // the app went to the background (T20): no keyframes nobody sees
            if (gallery != null) stopGallery()
            stopAll()
            return
        }
        if (seat !in 0 until P) return
        when (event) {
            Events.TOUCH_DOWN -> {
                if (gallery != null) stopGallery()
                deferred[seat] = 0
                player.cancel(seat)
            }
            Events.READOUT_COMMITTED -> onCommit(seat)
        }
    }

    private fun onCommit(seat: Int) {
        val n = net[seat]
        net[seat] = 0
        val state = stage.session.state
        val bit = 1 shl seat
        // Committed by a state change being dispatched right now (undo, a menu action…): no effect;
        // onStateChanged (EXTERNAL) is about to stop everything.
        if (state !== seen) return
        if (n == 0 || seat >= state.players.size) {
            if (koHeld and bit != 0) release(seat)
            return
        }
        if (!visible(seat)) { // effects hidden on this seat (a full overlay): drop
            if (koHeld and bit != 0) release(seat)
            return
        }
        if (!stage.isVisible(content, seat)) {
            // The number is hidden (commander mode): keep it for when it is back. A KO there has no
            // flourish — the dead treatment shows at once.
            if (koHeld and bit != 0) release(seat)
            if (!state.isKo(state.players[seat])) deferred[seat] += n
            return
        }
        fire(seat, n, state)
    }

    /** Plays the effect for [seat]'s committed net change [n]. */
    private fun fire(seat: Int, n: Int, state: GameState) {
        val bit = 1 shl seat
        val ko = koHeld and bit != 0
        if (!ko && state.isKo(state.players[seat])) return // already dead before this group
        val compact = t?.compact(seat) == true
        val fx = policy.choose(seat, n, ko, compact)
        val started = fx != null && start(seat, fx, priority = ko)
        if (ko) {
            koHeld = koHeld and bit.inv()
            if (started) koPlaying = koPlaying or bit else stage.send(Events.KO_RELEASE, seat)
        }
        maybeWinner(state)
    }

    /** Starts [fx] on [seat] (replacing what it shows); [priority] effects make room. */
    private fun start(seat: Int, fx: Fx, priority: Boolean): Boolean {
        val l = l ?: return false
        val t = t ?: return false
        if (seat >= stage.session.state.players.size) return false
        player.cancel(seat) // redraws the old footprint clean first
        val panel = l.panels[seat]
        t.numberFootprint(seat, t.compact(seat), stage.session.state.players[seat].life, box)
        val a = FxLayout.place(fx, box, panel, l.rotation[seat], area[seat])
        a.set(maxOf(a.left, panel.left), maxOf(a.top, panel.top), minOf(a.right, panel.right), minOf(a.bottom, panel.bottom))
        if (player.play(seat, fx)) return true
        if (!priority) return false
        // Make room: stop a plain hit/heal elsewhere.
        for (s in 0 until P) {
            val cur = player.current(s) ?: continue
            if (s == seat || cur.role == FxRole.KO || cur.role == FxRole.WIN) continue
            player.cancel(s)
            break
        }
        return player.play(seat, fx)
    }

    /** One player left: `winner` on the survivor, once (alongside the last KO's flourish). */
    private fun maybeWinner(state: GameState) {
        val ps = state.players
        var alive = 0
        var survivor = -1
        for (s in 0 until minOf(P, ps.size)) if (!state.isKo(ps[s])) { alive++; survivor = s }
        if (alive != 1) {
            if (alive > 1) winnerShown = false
            winPending = -1
            return
        }
        if (winnerShown) return
        if (koHeld != 0) return // a KO flourish is still waiting for its commit: go with it
        if (!visible(survivor) || !stage.isVisible(content, survivor)) {
            winPending = survivor
            return
        }
        winnerShown = true
        winPending = -1
        val fx = policy.choose(survivor, FxPolicy.Trigger.WIN, t?.compact(survivor) == true) ?: return
        start(survivor, fx, priority = true)
    }

    // ---- state ---------------------------------------------------------------------------------------

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        seen = new
        val np = new.players
        val op = old.players
        if (origin == Origin.EXTERNAL) {
            // Undo/redo/new game/menu: the readout has committed every group; nothing plays.
            stopAll()
            resync()
            return
        }
        val n = minOf(P, np.size, op.size)
        for (s in 0 until n) {
            if (np[s] === op[s]) continue
            val d = np[s].life - op[s].life
            if (d != 0 && (s == origin || origin == Origin.ATTRIBUTED)) net[s] += d
            val wasKo = old.isKo(op[s])
            val isKo = new.isKo(np[s])
            val bit = 1 shl s
            if (!wasKo && isKo && net[s] < 0 && d < 0 && visible(s) && stage.isVisible(content, s) && koHeld and bit == 0) {
                // KO'd by this seat's open life group: hold the dead tile for the flourish at commit.
                koHeld = koHeld or bit
                stage.send(Events.KO_HOLD, s)
            } else if (wasKo && !isKo && koHeld and bit != 0) {
                koHeld = koHeld and bit.inv() // healed back inside the group (the KO component released it)
            } else if (isKo && d != 0 && koHeld and bit != 0) {
                stage.send(Events.KO_HOLD, s) // still tapping (or holding) the dead seat: keep the hold armed
            }
        }
        maybeWinner(new)
    }

    override fun onCoversChanged() {
        val state = stage.session.state
        for (s in 0 until P) {
            val shown = visible(s) && stage.isVisible(content, s)
            if (!shown) {
                player.cancel(s)
                if (!visible(s)) deferred[s] = 0
                continue
            }
            val d = deferred[s]
            if (d != 0 && s < state.players.size) {
                deferred[s] = 0
                fire(s, d, state)
            }
        }
        if (winPending >= 0) maybeWinner(state)
    }

    override fun onTimer(id: Int) {
        if (id < P) player.onTimer(id) else galleryStep()
    }

    /** Stops every effect and forgets open groups, holds and deferrals. */
    private fun stopAll() {
        player.cancelAll()
        net.fill(0)
        deferred.fill(0)
        for (s in 0 until P) if ((koHeld or koPlaying) and (1 shl s) != 0) stage.send(Events.KO_RELEASE, s)
        koHeld = 0
        koPlaying = 0
        winPending = -1
    }

    private fun release(seat: Int) {
        koHeld = koHeld and (1 shl seat).inv()
        stage.send(Events.KO_RELEASE, seat)
    }

    private fun resync() {
        val state = stage.session.state
        seen = state
        var alive = 0
        for (p in state.players) if (!state.isKo(p)) alive++
        winnerShown = alive <= 1 // a restored or undone finished game doesn't celebrate again
    }

    // ---- debug gallery ---------------------------------------------------------------------------------

    private var gallery: Array<Fx>? = null
    private var galleryIndex = 0
    private var galleryStarted = false

    /**
     * Debug gallery: sets every seat's life to [life] and plays [effects] on all four seats at once,
     * one after another with [GALLERY_GAP_MS] of clean screen between them, looping until a touch.
     * Uses the real draw/invalidate path ([start] with the cap lifted to every seat).
     */
    fun startGallery(effects: Array<Fx>, life: Int) {
        if (effects.isEmpty()) return
        stopAll()
        val st = stage.session.state
        for (p in st.players) if (p.life != life) stage.session.dispatch(Action.SetLife(p.id, life), System.currentTimeMillis(), null)
        gallery = effects
        galleryIndex = 0
        player.maxConcurrent = P
        galleryStep()
    }

    fun stopGallery() {
        if (gallery == null) return
        gallery = null
        timers.cancel(GALLERY_TIMER)
        player.maxConcurrent = FxPlayer.DEFAULT_MAX_CONCURRENT
    }

    private fun galleryStep() {
        if (gallery == null) {
            // First step of a gallery requested by intent (debuggable builds only).
            val name = FxDebug.gallery ?: return
            val list = if (name == "all") Fx.entries.toTypedArray() else arrayOf(Fx.byName(name) ?: return)
            startGallery(list, FxDebug.life)
            return
        }
        val list = gallery ?: return
        val fx = list[galleryIndex % list.size]
        galleryIndex++
        val n = minOf(P, stage.session.state.players.size)
        for (s in 0 until n) start(s, fx, priority = true)
        var total = 0L
        for (i in 0 until fx.frameCount) total += player.hold(fx, i)
        stage.log?.invoke("fx gallery: ${fx.id} for ${total}ms")
        timers.schedule(GALLERY_TIMER, total + GALLERY_GAP_MS)
    }

    companion object {
        private const val P = PlayLayout.MAX_PLAYERS
        private const val GALLERY_TIMER = P
        const val GALLERY_START_MS = 1_500L
        const val GALLERY_GAP_MS = 2_000L
    }
}

/**
 * Debug hooks set by `MainActivity` from intent extras on debuggable builds only (never from the UI):
 * `--es fx <name|all>` runs the [FxComponent] gallery over life [life] (`--ei fxLife N`, default 37,
 * the total the designs were drawn over); `--ef fxHold <scale>` sets [FxPlayer.holdScale].
 */
object FxDebug {
    @JvmStatic var gallery: String? = null
    @JvmStatic var holdScale: Float = 1f
    @JvmStatic var life: Int = 37
}
