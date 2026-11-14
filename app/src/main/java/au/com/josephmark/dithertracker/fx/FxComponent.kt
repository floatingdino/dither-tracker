package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.core.serial.Tracker
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Origin
import au.com.josephmark.dithertracker.stage.PlayInput
import kotlin.random.Random

/**
 * Hit / heal / KO / winner effects on the tiles (T28c). Always on (SPEC: no setting). Display only:
 * [Kind.EFFECT], no zones; drawn in the `TILE` layer right after the number, readout and drawer
 * (its slot in `PlayScreen`), clipped to the seat's panel. Wiring notes: `fx/README.md`.
 *
 * - **Trigger** (T55): [QUIET_MS] after a seat's last life change (one timer per seat, restarted by
 *   each change), not at the readout's commit: the effect plays alongside the readout, which keeps its
 *   own schedule. The band comes from the group's running total at that moment; a group plays each
 *   band once and plays again only when its total escalates into a bigger band ([FxPolicy.escalates]),
 *   so taps or a hold that stay in a band never replay. The net is mirrored from the state diffs the
 *   readout itself adds (the seat's own taps; [Origin.ATTRIBUTED] life loss, i.e. commander damage on
 *   the target). A group that commits before it went quiet plays at the commit. [FxPolicy] picks.
 *   **Never under a finger** (T55, the owner): while a pointer is down on any of the seat's zones
 *   (a hold, a held drawer cell…) its quiet timer only looks again every [HELD_POLL_MS]; the full
 *   quiet starts from the release, so a hold plays once, after it, sized by the whole group.
 * - **KO**: when a seat's life group KOs it, [Events.KO_HOLD] keeps the dead treatment off while the
 *   group is still open; once it is quiet `ko-shatter` (or `fizzle` when compact) plays and
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
    Component(Layer.TILE, Kind.EFFECT, timerCount = TIMERS), FxPlayer.Listener {

    val policy = FxPolicy(random, P)

    /** Playback; its timer ids are seat indices, in this component's own timer bank. */
    val player = FxPlayer(P, timers, this)

    /** Draws frames; built from the frame's renderer on first use (tests inject a raster target). */
    internal var renderer: FxRenderer? = null

    private var l: PlayLayout? = null
    private var t: TileSlots? = null
    private var rotations = FxRenderer.DEFAULT_ROTATIONS

    /** Per seat: the screen rect its current effect covers (clipped to [clipR]). */
    private val area = Array(P) { IRect() }

    /** Tests: [seat]'s stored footprint. */
    internal fun areaOf(seat: Int): IRect = area[seat]

    /** Per seat: what its current effect is clipped to — the panel, or on a compact seat the area above the drawer. */
    private val clipR = Array(P) { IRect() }
    private val box = IRect()
    private val half = IRect()

    /** Per seat: the group had commander damage taken (an [Origin.ATTRIBUTED] loss with commander damage). */
    private var cmdr = 0

    /** Per seat: commander damage among the [deferred] commits. */
    private var deferredCmdr = 0

    /**
     * Per seat: the non-life trigger waiting for its quiet (T55: a counter gained, a toggle on,
     * day/night, a revive), or −1; [base] is the seat's player before the burst, so a gain undone
     * inside the burst plays nothing.
     */
    private val pending = IntArray(P) { -1 }
    private val base = arrayOfNulls<Player>(P)
    private var baseDayNight = DayNight.UNSET

    /** Per seat: non-life triggers played in the current burst (until [BURST_MS] without one), and when the last change came. */
    private val burstFired = IntArray(P)
    private val burstAt = LongArray(P)

    /** Per seat: net life change of the open readout group (mirrors the readout's). */
    private val net = IntArray(P)

    /** Per seat: the triggers already played for the open readout group (bit `1 shl Trigger.ordinal`). */
    private val fired = IntArray(P)

    /** How long a seat's life must be quiet before its effect starts (T55: not the readout's commit). */
    var quietMs: Long = QUIET_MS

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
        renderer(f).draw(fx.spec, player.frame(seat), box, panel, l.rotation[seat], clipR[seat])
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
            paused = true // later PAUSE listeners (a first-player pick landing) start nothing
            return
        }
        if (event == Events.RESUME) { paused = false; return }
        if (event == Events.TURN_PASSED) return onTurnPassed()
        if (seat !in 0 until P) return
        when (event) {
            Events.FIRST_PLAYER -> fireNow(seat, FxPolicy.Trigger.FIRST_PLAYER)
            Events.TOUCH_DOWN -> {
                if (gallery != null) stopGallery()
                deferred[seat] = 0
                player.cancel(seat)
            }
            Events.READOUT_COMMITTED -> onCommit(seat)
        }
    }

    /** A life change on [seat]: (re)start its quiet timer; the effect starts when it fires. */
    private fun armQuiet(seat: Int) {
        heldSeats = heldSeats and (1 shl seat).inv() // a full quiet from now (held: it looks again)
        if (!paused) timers.schedule(QUIET_TIMER + seat, quietMs)
    }

    /** Tests: [seat]'s quiet timer as a local timer id. */
    internal fun quietTimer(seat: Int): Int = QUIET_TIMER + seat

    /** Between [Events.PAUSE] and [Events.RESUME]: nothing starts, nothing is armed. */
    private var paused = false

    /**
     * [seat]'s life has been quiet for [quietMs] (T55): play for the open group's running total now,
     * alongside the readout (which stays up on its own schedule). Once per trigger per group: taps that
     * keep the total in the same band play nothing more; escalating into a bigger band plays again.
     */
    private fun onQuiet(seat: Int) {
        // A finger still down on this tile (a ±10 hold, a held drawer cell…): nothing starts until
        // it lifts (T55, the owner). Look again shortly; the full quiet then runs from the release.
        if (held(seat)) {
            heldSeats = heldSeats or (1 shl seat)
            timers.schedule(QUIET_TIMER + seat, HELD_POLL_MS)
            return
        }
        if (heldSeats and (1 shl seat) != 0) {
            heldSeats = heldSeats and (1 shl seat).inv()
            armQuiet(seat) // released since the last look: the quiet starts now
            return
        }
        val state = stage.session.state
        val p = pending[seat]
        val b = base[seat]
        pending[seat] = -1
        base[seat] = null
        if (state !== seen || seat >= state.players.size) return
        if (!visible(seat)) return // a full overlay: the commit drops it
        if (!stage.isVisible(content, seat)) return // the number is hidden: kept for the commit / the mode closing
        val n = net[seat]
        if (n != 0 && fire(seat, n, state)) return
        val d = deferred[seat]
        if (n == 0 && d != 0) { // committed while the finger was still down
            deferred[seat] = 0
            val c = deferredCmdr and (1 shl seat) != 0
            deferredCmdr = deferredCmdr and (1 shl seat).inv()
            fire(seat, d, state, commander = c)
            fired[seat] = 0
            return
        }
        if (p >= 0) firePending(seat, TRIGGERS[p], b, state)
    }

    /** Seats whose quiet timer found a finger down at its last look (it re-arms in full on release). */
    private var heldSeats = 0

    /**
     * Whether a pointer is down on one of [seat]'s zones — any component's (life halves, commander
     * quarters, drawer cells, the turn tab…), pressed or not yet (T57's `PlayInput.isDown` per zone).
     * No allocation: the stage's pointer table.
     */
    private fun held(seat: Int): Boolean {
        val input = stage.input
        if (input.downCount == 0) return false
        for (pointer in 0 until PlayInput.MAX_POINTERS) {
            val z = input.zoneOf(pointer)
            if (z >= 0 && stage.zoneSeat(z) == seat) return true
        }
        return false
    }

    /**
     * A non-life trigger after its burst went quiet: only if it is still a gain against the player
     * before the burst ([b]) — a +1 −1 burst plays nothing — once per trigger per burst.
     */
    private fun firePending(seat: Int, trigger: FxPolicy.Trigger, b: Player?, state: GameState) {
        val now = state.players[seat]
        val gained = when (trigger) {
            FxPolicy.Trigger.POISON -> b == null || now.counters.poison > b.counters.poison
            FxPolicy.Trigger.ENERGY -> b == null || now.counters.energy > b.counters.energy
            FxPolicy.Trigger.EXPERIENCE -> b == null || now.counters.experience > b.counters.experience
            FxPolicy.Trigger.RAD -> b == null || now.counters.rad > b.counters.rad
            FxPolicy.Trigger.STORM -> b == null || now.counters.storm > b.counters.storm
            FxPolicy.Trigger.MONARCH -> now.toggles.monarch && (b == null || !b.toggles.monarch)
            FxPolicy.Trigger.INITIATIVE -> now.toggles.initiative && (b == null || !b.toggles.initiative)
            FxPolicy.Trigger.RING -> now.toggles.ringBearer && (b == null || !b.toggles.ringBearer)
            FxPolicy.Trigger.NIGHT -> state.dayNight == DayNight.NIGHT && baseDayNight != DayNight.NIGHT
            FxPolicy.Trigger.DAY -> state.dayNight == DayNight.DAY && baseDayNight != DayNight.DAY
            FxPolicy.Trigger.REVIVE -> !state.isKo(now)
            else -> true
        }
        if (!gained) return
        val bit = 1 shl trigger.ordinal
        if (burstFired[seat] and bit != 0) return // already played in this burst (taps or a hold)
        burstFired[seat] = burstFired[seat] or bit
        if (trigger.allSeats) playAll(trigger, state) else fireNow(seat, trigger)
    }

    /** Plays [trigger] on [seat] now, if its number shows and it is alive. */
    private fun fireNow(seat: Int, trigger: FxPolicy.Trigger) {
        val state = stage.session.state
        if (seat !in 0 until minOf(P, state.players.size)) return
        if (!shows(seat, state) || held(seat)) return
        val fx = policy.choose(seat, trigger, t?.compact(seat) == true, narrowTier()) ?: return
        start(seat, fx, priority = false)
    }

    /** [seat] can show an effect now: alive, effects not covered, its number visible. */
    private fun shows(seat: Int, state: GameState): Boolean =
        !state.isKo(state.players[seat]) && visible(seat) && stage.isVisible(content, seat)

    /** Day/night (T55): one effect on every seat that [shows] it, stepped together. */
    private fun playAll(trigger: FxPolicy.Trigger, state: GameState) {
        if (paused) return
        val fx0 = policy.choose(-1, trigger, false, narrowTier()) ?: return // one effect for the table
        var mask = 0
        for (s in 0 until minOf(P, state.players.size)) {
            if (!shows(s, state) || held(s)) continue // not over a tile a finger is holding
            // A KO or winner flourish keeps its seat; a compact seat plays it above its drawer (place).
            if (player.current(s)?.let { it.role == FxRole.KO || it.role == FxRole.WIN } == true) continue
            place(s, fx0)
            mask = mask or (1 shl s)
        }
        if (mask != 0) player.playAll(mask, fx0)
    }

    /** The turn passed (T55, sent by the turn tab after its pass): `your-turn` on the new active seat. */
    private fun onTurnPassed() {
        val state = stage.session.state
        val id = state.turn.activePlayer ?: return
        val seat = state.indexOf(id)
        if (seat >= 0) fireNow(seat, FxPolicy.Trigger.TURN)
    }

    private fun narrowTier(): Boolean = l?.tier == PlayLayout.TIER_NARROW

    private fun onCommit(seat: Int) {
        val n = net[seat]
        val played = fired[seat] != 0
        val bit = 1 shl seat
        val wasCmdr = cmdr and bit != 0
        if (pending[seat] < 0) timers.cancel(QUIET_TIMER + seat)
        net[seat] = 0
        fired[seat] = 0
        cmdr = cmdr and bit.inv()
        val state = stage.session.state
        // Committed by a state change being dispatched right now (undo, a menu action…): no effect;
        // onStateChanged (EXTERNAL) is about to stop everything.
        if (state !== seen) return
        if (n == 0 || seat >= state.players.size || played) {
            if (koHeld and bit != 0) release(seat)
            return
        }
        if (held(seat) && visible(seat) && stage.isVisible(content, seat) && koHeld and bit == 0) {
            // Committed under a finger still down on the tile: play it a quiet after the release.
            deferred[seat] += n
            if (wasCmdr) deferredCmdr = deferredCmdr or bit
            armQuiet(seat)
            return
        }
        // Nothing played yet for this group (committed before it went quiet, or hidden meanwhile).
        if (!visible(seat)) { // effects hidden on this seat (a full overlay): drop
            if (koHeld and bit != 0) release(seat)
            return
        }
        if (!stage.isVisible(content, seat)) {
            // The number is hidden (commander mode): keep it for when it is back. A KO there has no
            // flourish — the dead treatment shows at once.
            if (koHeld and bit != 0) release(seat)
            if (!state.isKo(state.players[seat])) {
                deferred[seat] += n
                if (wasCmdr) deferredCmdr = deferredCmdr or bit
            }
            return
        }
        if (wasCmdr) cmdr = cmdr or bit
        fire(seat, n, state)
        fired[seat] = 0
        cmdr = cmdr and bit.inv()
    }

    /** Plays the effect for [seat]'s net change [n] (running or committed), once per trigger per group. */
    private fun fire(seat: Int, n: Int, state: GameState, commander: Boolean = cmdr and (1 shl seat) != 0): Boolean {
        val bit = 1 shl seat
        val ko = koHeld and bit != 0
        if (!ko && state.isKo(state.players[seat])) return false // already dead before this group
        val trigger = policy.trigger(n, ko, commander) ?: return false
        if (!policy.escalates(trigger, fired[seat])) return false // this band already played for the group
        fired[seat] = fired[seat] or (1 shl trigger.ordinal)
        val compact = t?.compact(seat) == true
        val fx = policy.choose(seat, trigger, compact, narrowTier())
        val started = fx != null && start(seat, fx, priority = ko)
        if (ko) {
            koHeld = koHeld and bit.inv()
            if (started) koPlaying = koPlaying or bit else stage.send(Events.KO_RELEASE, seat)
        }
        maybeWinner(state)
        return true
    }

    /** Starts [fx] on [seat] (replacing what it shows); [priority] effects make room. */
    private fun start(seat: Int, fx: Fx, priority: Boolean): Boolean {
        if (l == null || t == null || paused) return false
        if (seat >= stage.session.state.players.size) return false
        player.cancel(seat) // redraws the old footprint clean first
        place(seat, fx)
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

    /**
     * Stores [seat]'s footprint for [fx] ([area]) and its clip ([clipR]): the panel, or on a compact
     * seat only the area above the drawer (the two life halves there), so an effect never draws over
     * the drawer the player is using.
     */
    private fun place(seat: Int, fx: Fx) {
        val l = l ?: return
        val t = t ?: return
        val panel = l.panels[seat]
        val compact = t.compact(seat)
        val c = clipR[seat]
        if (compact) {
            val a = t.lifeFeedback(seat, PlayLayout.MINUS)
            val b = t.lifeFeedback(seat, PlayLayout.PLUS)
            c.set(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
            c.set(maxOf(c.left, panel.left), maxOf(c.top, panel.top), minOf(c.right, panel.right), minOf(c.bottom, panel.bottom))
        } else {
            c.set(panel.left, panel.top, panel.right, panel.bottom)
        }
        t.numberFootprint(seat, compact, stage.session.state.players[seat].life, box)
        val a = FxLayout.place(fx, box, panel, l.rotation[seat], area[seat])
        a.set(maxOf(a.left, c.left), maxOf(a.top, c.top), minOf(a.right, c.right), minOf(a.bottom, c.bottom))
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
        val fx = policy.choose(survivor, FxPolicy.Trigger.WIN, t?.compact(survivor) == true, narrowTier()) ?: return
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
            if (d != 0 && (s == origin || origin == Origin.ATTRIBUTED)) {
                net[s] += d
                // Commander damage taken (T55): the target's group plays the imp instead of a hit.
                if (origin == Origin.ATTRIBUTED && d < 0 && np[s].commanderDamage !== op[s].commanderDamage) cmdr = cmdr or (1 shl s)
                armQuiet(s)
            }
            if (s == origin) observe(s, op[s], np[s], old, new)
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
        if (origin in 0 until n && new.dayNight != old.dayNight) {
            // Day/night is the table's; the seat whose drawer set it carries the burst.
            val t = when (new.dayNight) {
                DayNight.NIGHT -> FxPolicy.Trigger.NIGHT
                DayNight.DAY -> FxPolicy.Trigger.DAY
                DayNight.UNSET -> null
            }
            if (pending[origin] < 0) baseDayNight = old.dayNight
            if (t != null && stage.settings.current.shows(Tracker.DAY_NIGHT)) pend(origin, t, op[origin])
        }
        maybeWinner(new)
    }

    /**
     * A change [origin] made to its own player (T55): a counter gained, a toggle turned on, a revive.
     * Effects play on the gain only (never a decrement, an undo or a restore: those are EXTERNAL),
     * and only for trackers the settings show.
     */
    private fun observe(s: Int, o: Player, p: Player, old: GameState, new: GameState) {
        if (p.counters !== o.counters) {
            val shown = stage.settings.current.drawerCounters
            for (i in COUNTERS.indices) {
                val c = COUNTERS[i]
                if (p.counters[c] <= o.counters[c] || !shown.contains(c)) continue
                FxPolicy.counterTrigger(c)?.let { pend(s, it, o) }
            }
        }
        if (p.toggles !== o.toggles) {
            val st = stage.settings.current
            for (i in TOGGLES.indices) {
                val tg = TOGGLES[i]
                if (!p.toggles[tg] || o.toggles[tg] || !st.shows(TRACKERS[i])) continue
                FxPolicy.toggleTrigger(tg)?.let { pend(s, it, o) }
            }
        }
        if (old.isKo(o) && !new.isKo(p) && p.life == o.life) pend(s, FxPolicy.Trigger.REVIVE, o) // the KO tile's tap-tap revive
    }

    /** Queues [trigger] for [seat]'s quiet ([onQuiet]); [before] is the player before the burst. */
    private fun pend(seat: Int, trigger: FxPolicy.Trigger, before: Player) {
        val now = stage.now()
        if (now - burstAt[seat] > BURST_MS) burstFired[seat] = 0
        burstAt[seat] = now
        if (pending[seat] < 0) base[seat] = before
        pending[seat] = trigger.ordinal
        armQuiet(seat)
    }

    override fun onCoversChanged() {
        val state = stage.session.state
        for (s in 0 until P) {
            val shown = visible(s) && stage.isVisible(content, s)
            if (!shown) {
                player.cancel(s)
                if (!visible(s)) {
                    deferred[s] = 0
                    deferredCmdr = deferredCmdr and (1 shl s).inv()
                }
                continue
            }
            val d = deferred[s]
            if (d != 0 && s < state.players.size) {
                deferred[s] = 0
                val c = deferredCmdr and (1 shl s) != 0
                deferredCmdr = deferredCmdr and (1 shl s).inv()
                fire(s, d, state, commander = c)
                if (net[s] == 0) fired[s] = 0
            }
        }
        if (winPending >= 0) maybeWinner(state)
    }

    override fun onTimer(id: Int) {
        when {
            id <= P -> player.onTimer(id) // seats, and the all-seat group's timer (P)
            id >= QUIET_TIMER -> onQuiet(id - QUIET_TIMER)
            else -> galleryStep()
        }
    }

    /** Stops every effect and forgets open groups, holds and deferrals. */
    private fun stopAll() {
        player.cancelAll()
        for (s in 0 until P) timers.cancel(QUIET_TIMER + s)
        net.fill(0)
        fired.fill(0)
        deferred.fill(0)
        pending.fill(-1)
        base.fill(null)
        burstFired.fill(0)
        cmdr = 0
        deferredCmdr = 0
        heldSeats = 0
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
        // Timer ids: seats 0 until P (FxPlayer), P the all-seat group (FxPlayer.groupTimer), then the gallery.
        private const val GALLERY_TIMER = P + 1
        /** Per-seat quiet timers ([onQuiet]): `QUIET_TIMER + seat`. */
        private const val QUIET_TIMER = P + 2
        private const val TIMERS = QUIET_TIMER + P

        /**
         * Quiet after a seat's last life change before its effect starts (T55, Sam: "waiting until the
         * delta disappears is too slow"). Deliberate taps come 150–250 ms apart, so a burst stays one
         * group; a hold repeats every second (its first step at 500 ms) and plays once per band.
         */
        const val QUIET_MS = 300L

        /** A counter / toggle burst plays each trigger once until this long passes without a change (the readout's window). */
        const val BURST_MS = 3_000L

        /** While a finger is down on a seat with an effect waiting, how often its quiet timer looks again. */
        const val HELD_POLL_MS = 100L

        private val TRIGGERS = FxPolicy.Trigger.values()
        private val COUNTERS = Counter.values()
        private val TOGGLES = Toggle.values()
        /** Parallel to [TOGGLES]: the settings tracker that hides each (T53). */
        private val TRACKERS = TOGGLES.map {
            when (it) {
                Toggle.MONARCH -> Tracker.MONARCH
                Toggle.INITIATIVE -> Tracker.INITIATIVE
                Toggle.CITYS_BLESSING -> Tracker.CITYS_BLESSING
                Toggle.RING_BEARER -> Tracker.RING_BEARER
            }
        }.toTypedArray()
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
