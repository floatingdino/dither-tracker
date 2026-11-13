package au.com.josephmark.dithertracker

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import au.com.josephmark.dithertracker.data.Persistence
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayView

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
        val inverted = if (intent?.hasExtra("inverted") == true) {
            intent.getBooleanExtra("inverted", false)
        } else {
            SettingsHolder.shared.current.inverted
        }
        root = if (intent?.getBooleanExtra("demo", false) == true) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DitherDemoView(this).apply { setInverted(inverted) }
        } else {
            // The game lives in GameSession.shared (process memory, restored from disk by Persistence).
            PlayView(this, GameSession.shared).apply {
                trace = intent?.getBooleanExtra("trace", false) == true
                setInverted(inverted)
            }
        }
        setContentView(root)
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
