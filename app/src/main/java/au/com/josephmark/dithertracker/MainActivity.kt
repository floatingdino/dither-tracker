package au.com.josephmark.dithertracker

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import au.com.josephmark.dithertracker.data.Persistence
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.fx.FxDebug
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayView
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Once per process: load the save synchronously (a few ms) so the first frame is the restored
        // game, then save changes in the background. No-op on activity recreation.
        Persistence.start(applicationContext)
        // Debug/QA hooks (not reachable from the UI):
        //   --ez inverted true  start in dark mode (overrides the saved setting either way)
        //   --ez demo true      the T04/T05 engine + type demo instead of the Play screen (portrait)
        //   --ez trace true     log Play onDraw clip + duration (tag PlayView)
        //   --es partners 1,3   (debuggable builds only) set `partner` on exactly these seats (T30/T32
        //                       QA until T13 adds a UI for it); an empty string clears every partner
        //   --el seed N         (debuggable builds only) seed the commander-mode quip picker, so
        //                       `tools/capture_scenarios.sh` captures are reproducible
        //   --es fx <name|all>  (debuggable builds only) effects gallery (T28c): sets every life to
        //                       --ei fxLife N (default 37) and plays that effect / cycles them all on
        //                       all four seats, looping until a touch; see fx/README.md
        //   --ef fxHold X       (debuggable builds only) scale every effect hold by X (FxPlayer.holdScale)
        val inverted = if (intent?.hasExtra("inverted") == true) {
            intent.getBooleanExtra("inverted", false)
        } else {
            SettingsHolder.shared.current.inverted
        }
        applyDebugPartners()
        applyDebugFx()
        root = if (intent?.getBooleanExtra("demo", false) == true) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DitherDemoView(this).apply { setInverted(inverted) }
        } else {
            // The game lives in GameSession.shared (process memory, restored from disk by Persistence).
            PlayView(this, GameSession.shared, debugRandom() ?: Random(System.nanoTime())).apply {
                trace = intent?.getBooleanExtra("trace", false) == true
                setInverted(inverted)
            }
        }
        setContentView(root)
    }

    /** Debug-only capture hook: `--el seed N` makes the commander-mode quip picks reproducible. */
    private fun debugRandom(): Random? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        if (intent?.hasExtra("seed") != true) return null
        return Random(intent.getLongExtra("seed", 0L))
    }

    /** Debug-only effects hooks (T28c): `--es fx <name|all>`, `--ei fxLife N`, `--ef fxHold X`. */
    private fun applyDebugFx() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val i = intent ?: return
        FxDebug.gallery = i.getStringExtra("fx")
        FxDebug.life = i.getIntExtra("fxLife", 37)
        FxDebug.holdScale = if (i.hasExtra("fxHold")) i.getFloatExtra("fxHold", 1f).coerceAtLeast(0f) else 1f
    }

    /** Debug-only QA hook: `--es partners 1,3` sets the partner flag on exactly those seats. */
    private fun applyDebugPartners() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val spec = intent?.getStringExtra("partners") ?: return
        val seats = spec.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        val session = GameSession.shared
        for (p in session.state.players) {
            val want = p.seat in seats
            if (p.partner != want) session.dispatch(Action.SetPartner(p.id, want), System.currentTimeMillis())
        }
    }

    override fun onPause() {
        super.onPause()
        // Home, sleep, swipe-away: write anything still waiting on the debounce before we can be killed.
        Persistence.flush()
    }

    override fun onStop() {
        super.onStop()
        Persistence.flush() // no-op unless something changed since onPause
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun enterImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            root.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}
