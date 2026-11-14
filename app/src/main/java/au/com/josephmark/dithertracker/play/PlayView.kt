package au.com.josephmark.dithertracker.play

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.CanvasDitherRenderer
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.IconPainter
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.type.TextRenderer
import kotlin.random.Random

/**
 * The Play screen's Android host (T34): one opaque custom View around a [Stage], which owns the
 * components ([PlayScreen]), zones, presses, layering and invalidation. This class only adapts
 * Android to it — size → [PlayLayout] + renderer grid, `MotionEvent` → dither-px pointers, `onDraw`
 * → [Stage.draw] with a [Frame], `Handler` timers, `invalidate(rect)`. It owns no feature logic; see
 * `docs/architecture.md`.
 *
 * Drawing goes through [DitherRenderer] in root coordinates (this View fills the window at (0,0));
 * rotated seats use blit rotation, never `Canvas.rotate`. `onDraw` culls by the canvas clip, so under
 * software rendering a dirty rect costs only its own pixels; under HWUI (the default) the whole
 * display list is re-recorded. One frame per action: every entry point's invalidations are unioned.
 */
@SuppressLint("ViewConstructor")
class PlayView(
    context: Context,
    session: GameSession,
    /** Picks the commander-mode quip; seedable for the capture harness (`--el seed N`, debug builds). */
    random: Random = Random(System.nanoTime()),
) : View(context) {

    private val r = CanvasDitherRenderer()
    private val text = TextRenderer(r)
    private val icons = IconPainter(r)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val platform = object : Stage.Platform {
        override fun invalidate(left: Int, top: Int, right: Int, bottom: Int) {
            if (trace) Log.d(TAG, "inval $left $top $right $bottom")
            if (stopped) { dirtyWhileStopped = true; return }
            @Suppress("DEPRECATION")
            this@PlayView.invalidate(left, top, right, bottom)
        }

        override fun invalidateAll() {
            if (trace) Log.d(TAG, "inval all")
            if (stopped) { dirtyWhileStopped = true; return }
            this@PlayView.invalidate()
        }
        override fun schedule(id: Int, delayMillis: Long) = timers.schedule(id, delayMillis)
        override fun cancel(id: Int) = timers.cancel(id)
        override fun cancelAllTimers() = timers.cancelAll()
        override fun now(): Long = System.currentTimeMillis()

        override fun relayout(players: Int) {
            if (width > 0 && height > 0) relayout(width, height)
        }
    }

    private val stage: Stage = PlayScreen.build(Stage(session, SettingsHolder.shared, platform), random)
    private val timers: HandlerScheduler = HandlerScheduler(mainHandler, stage.timerCount).also { it.target = stage }

    // Scratch, reused: no allocation in onDraw / touch paths.
    private val clipDev = Rect()
    private val clipDevI = IRect()
    private val dev = IRect()
    private val savedClip = IRect()
    private var canvas: Canvas? = null

    private val frame = object : Frame() {
        override fun invert(rect: IRect, layer: Int) {
            if (DitherRenderer.canInvertInPlace) {
                r.invertRect(rect)
                return
            }
            // API < 29: redraw everything up to the zone's layer inverted, clipped to the rect.
            val c = canvas ?: return
            val l = stage.layout ?: return
            l.grid.toDevice(rect, dev)
            savedClip.set(clip.left, clip.top, clip.right, clip.bottom)
            clip.set(maxOf(clip.left, rect.left), maxOf(clip.top, rect.top), minOf(clip.right, rect.right), minOf(clip.bottom, rect.bottom))
            c.save()
            c.clipRect(dev.left, dev.top, dev.right, dev.bottom)
            r.withInverted {
                r.clear()
                stage.drawUpTo(this, layer)
            }
            c.restore()
            clip.set(savedClip.left, savedClip.top, savedClip.right, savedClip.bottom)
        }
    }.also {
        it.r = r
        it.text = text
        it.icons = icons
    }

    /** Life per hold-repeat step (`AppSettings.holdRepeatStep`, T12); ≤ 0 disables hold on life. */
    var holdStep: Int
        get() = stage.holdStep
        set(value) { stage.holdStep = value }

    /** How long a touch must be down before its zone inverts (T57; debug builds: `--ei pressDelay <ms>`, 0 = on touch-down). */
    var pressDelayMillis: Long
        get() = stage.pressDelayMillis
        set(value) { stage.pressDelayMillis = value }

    /** Debug (`--ez trace true`): logs every onDraw's clip, duration and allocation count, touches (with their allocation count), taps, layout. Allocates; off by default. */
    var trace = false
        set(value) {
            field = value
            stage.log = if (value) { msg -> Log.d(TAG, msg) } else null
            @Suppress("DEPRECATION")
            if (value) Debug.startAllocCounting() else Debug.stopAllocCounting()
        }

    init {
        setWillNotDraw(false)
        isClickable = false
    }

    /**
     * Live settings (T12): invert and the hold step apply at once; a scale change re-lays the whole
     * screen out (posted, so it never runs inside the tap that changed it — layout cancels every touch).
     */
    private val settingsListener = SettingsHolder.Listener { old, new ->
        if (old.inverted != new.inverted) setInverted(new.inverted)
        if (old.holdRepeatStep != new.holdRepeatStep) stage.holdStep = new.holdRepeatStep
        if (old.scale != new.scale || old.layout != new.layout) {
            mainHandler.removeCallbacks(relayoutTask)
            mainHandler.post(relayoutTask)
        }
    }

    private val relayoutTask = Runnable {
        if (width > 0 && height > 0) {
            relayout(width, height)
            invalidate()
        }
    }

    fun setInverted(on: Boolean) {
        if (r.inverted == on) return
        r.inverted = on
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stage.holdStep = stage.settings.current.holdRepeatStep
        stage.settings.addListener(settingsListener)
        stage.attach()
    }

    override fun onDetachedFromWindow() {
        stage.detach()
        stage.settings.removeListener(settingsListener)
        mainHandler.removeCallbacks(relayoutTask)
        mainHandler.removeCallbacks(prewarmLater)
        super.onDetachedFromWindow()
    }

    /** The activity paused / resumed: components drop or re-arm optional timers (the e-ink cleanup's quiet timer). */
    fun onHostPause() = stage.send(Events.PAUSE, Stage.SCREEN)

    fun onHostResume() = stage.send(Events.RESUME, Stage.SCREEN)

    // Not visible (T20): a timer that still fires in the background (a menu or mode timing out) only
    // notes that a redraw is due; the one redraw happens when the window is visible again. On the
    // Nomad, such frames were drawn in the background (up to 20 ms each).
    private var stopped = false
    private var dirtyWhileStopped = false

    /** The activity stopped (not visible): hold redraws until [onHostStart]. */
    fun onHostStop() {
        stopped = true
    }

    /** The activity is visible again: one redraw if anything changed while stopped. */
    fun onHostStart() {
        stopped = false
        if (dirtyWhileStopped) {
            dirtyWhileStopped = false
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = relayout(w, h)

    /** Grid + layout for a [w]×[h] view at the scale the settings ask for (AUTO or a fixed S), for the game's player count. */
    private fun relayout(w: Int, h: Int) {
        val app = stage.settings.current
        val players = stage.session.state.players.size
        val s = PlayLayout.chooseScale(w, h, resources.displayMetrics.densityDpi, app.scale, players, app.layout)
        val t0 = SystemClock.elapsedRealtimeNanos()
        val grid = DitherGrid(w, h, s)
        r.setGrid(grid)
        r.prewarm()
        val ta = SystemClock.elapsedRealtimeNanos()
        val l = PlayLayout.compute(grid, players, app.layout)
        stage.layout(l)
        val t1 = SystemClock.elapsedRealtimeNanos()
        prewarm()
        if (trace) {
            val t2 = SystemClock.elapsedRealtimeNanos()
            Log.d(TAG, "layout $l")
            Log.d(TAG, "layout took ${(t1 - t0) / 1000}us (tile shaders ${(ta - t0) / 1000}us), prewarm ${(t2 - t1) / 1000}us; masks=${r.maskCount} bytes=${r.maskBytes()}")
        }
    }

    /**
     * Builds the renderer's scaled glyph/icon masks for everything the components can show, by
     * drawing them once into a throwaway 1×1 canvas — so nothing is built or allocated in onDraw.
     */
    private fun prewarm() {
        r.begin(scratch)
        stage.prewarm(frame, Stage.Prewarm.FIRST_FRAME)
        mainHandler.removeCallbacks(prewarmLater)
        mainHandler.post(prewarmLater)
    }

    private val scratch by lazy { Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)) }

    /**
     * Effects and overlays (T20): built just after the first frame (posted from the layout pass, so it
     * runs once that traversal has drawn), not before it. Nothing can show them earlier; if something
     * did, the renderer would build the missing masks on first use.
     */
    private val prewarmLater = Runnable {
        if (stage.layout == null) return@Runnable
        val t0 = if (trace) SystemClock.elapsedRealtimeNanos() else 0L
        r.begin(scratch)
        stage.prewarm(frame, Stage.Prewarm.LATER)
        if (trace) Log.d(TAG, "prewarm later took ${(SystemClock.elapsedRealtimeNanos() - t0) / 1000}us; masks=${r.maskCount} bytes=${r.maskBytes()}")
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val l = stage.layout ?: return true
        if (trace && event.actionMasked != MotionEvent.ACTION_MOVE) Log.d(TAG, "touch $event")
        val a0 = if (trace) allocs() else 0
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                stage.down(
                    event.getPointerId(i),
                    l.grid.toDitherX(event.getX(i).toInt()),
                    l.grid.toDitherY(event.getY(i).toInt()),
                    event.eventTime,
                )
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> stage.up(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> stage.cancel()
        }
        if (trace) Log.d(TAG, "touch handled allocs=${allocs() - a0}")
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val l = stage.layout ?: return
        val a0 = if (trace) allocs() else 0 // outside the timed window
        val t0 = if (trace) SystemClock.elapsedRealtimeNanos() else 0L
        r.begin(canvas)
        if (!canvas.getClipBounds(clipDev)) return
        clipDevI.set(clipDev.left, clipDev.top, clipDev.right, clipDev.bottom)
        l.grid.toDitherCovering(clipDevI, frame.clip)
        this.canvas = canvas
        r.clear()
        stage.draw(frame)
        this.canvas = null
        if (trace && !firstDrawLogged) {
            firstDrawLogged = true
            Log.d(TAG, "first onDraw ${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()}ms after process start")
        }
        if (trace) {
            val us = (SystemClock.elapsedRealtimeNanos() - t0) / 1000
            val n = allocs() - a0
            Log.d(TAG, "onDraw hw=${canvas.isHardwareAccelerated} clip=$clipDev allocs=$n took ${us}us masks=${r.maskCount}")
        }
    }

    private var firstDrawLogged = false

    /** Objects allocated on this thread so far (trace only: counting starts when [trace] is set). */
    @Suppress("DEPRECATION")
    private fun allocs(): Int = Debug.getThreadAllocCount()

    companion object {
        private const val TAG = "PlayView"
    }
}
