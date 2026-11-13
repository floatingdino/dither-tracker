package au.com.josephmark.dithertracker.readout

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
import au.com.josephmark.dithertracker.stage.Readouts

/**
 * The pending-delta readout (T07): each seat's running change (`-7`, `+20`) as static text above
 * its number, committing 3 s (the core coalesce window) after that seat's last change, or at once
 * when anything else happens ([Origin.EXTERNAL] state changes, cover changes, detach). Display
 * only, no zones. State lives in [PendingDeltas].
 *
 * It is also the stage's [Readouts] service: a seat's own life changes ([Stage.changeLife]) are
 * added automatically; features that attribute their own changes ([Origin.ATTRIBUTED], e.g.
 * commander damage) call [add] / [setGroup] themselves. On commit the seat's core undo group closes
 * ([setGroup]'s key, or the seat's life group).
 *
 * [Kind.READOUT]: commander mode keeps it visible.
 */
class ReadoutComponent : Component(Layer.TILE, Kind.READOUT, timerCount = PlayLayout.MAX_PLAYERS), Readouts, PendingDeltas.Sink {

    private lateinit var pending: PendingDeltas
    private val keys = arrayOfNulls<String>(PlayLayout.MAX_PLAYERS)
    private var l: PlayLayout? = null
    private var t: TileSlots? = null
    private val fp = IRect()

    override fun onAdded() {
        pending = PendingDeltas(PlayLayout.MAX_PLAYERS, timers, this, stage.session.history.coalesceWindowMillis)
        stage.readouts = this
    }

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        t = tiles
    }

    override fun prewarm(f: Frame) {
        val l = l ?: return
        for (rot in l.rotations) for (k in 1..maxOf(l.readoutK, l.tk)) f.text.draw(l.readoutFont, WARM_CHARS, 0, 0, k, rot)
    }

    override fun draw(f: Frame, seat: Int) {
        val l = l ?: return
        val t = t ?: return
        val delta = pending.delta(seat)
        if (delta == 0) return
        val compact = t.compact(seat)
        if (f.hit(t.readoutSlot(seat, compact))) {
            val k = t.readoutFootprint(seat, compact, delta, fp)
            f.text.drawInt(l.readoutFont, delta, fp.left, fp.top, k, l.rotation[seat], signed = true)
        }
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        // A change this screen didn't make (undo, restore, menu…) is "another kind of action".
        if (origin == Origin.EXTERNAL) {
            pending.commitAll()
            return
        }
        val np = new.players
        val op = old.players
        if (origin >= 0 && origin < np.size && np[origin] !== op[origin]) pending.add(origin, np[origin].life - op[origin].life)
    }

    override fun onCoversChanged() = pending.commitAll()

    override fun onDetach() = pending.commitAll()

    override fun onTimer(id: Int) = pending.onTimer(id)

    // ---- PendingDeltas.Sink ----------------------------------------------------------------------

    override fun onReadoutChanged(seat: Int) {
        val t = t ?: return
        invalidate(t.readoutSlot(seat))
    }

    override fun onGroupCommitted(seat: Int) {
        // Core groups undo per coalesce key (T29): close just this seat's group (life, or the
        // commander-damage group it was adjusting in the mode).
        val key = keys[seat]
        if (key != null) {
            keys[seat] = null
            stage.session.endGroup(key)
        } else {
            stage.session.endLifeGroup(seat)
        }
        stage.send(Events.READOUT_COMMITTED, seat)
    }

    // ---- Readouts --------------------------------------------------------------------------------

    override fun delta(seat: Int): Int = pending.delta(seat)
    override fun add(seat: Int, change: Int) = pending.add(seat, change)
    override fun group(seat: Int): String? = keys[seat]
    override fun setGroup(seat: Int, key: String?) { keys[seat] = key }
    override fun commit(seat: Int) = pending.commit(seat)
    override fun commitAll() = pending.commitAll()

    companion object {
        private const val WARM_CHARS = "0123456789-+−"
    }
}
