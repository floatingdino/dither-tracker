package au.com.josephmark.dithertracker

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayView

class MainActivity : Activity() {

    private lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug/QA hooks (not reachable from the UI):
        //   --ez inverted true  start in dark mode
        //   --ez demo true      the T04/T05 engine + type demo instead of the Play screen (portrait)
        //   --ez trace true     log Play onDraw clip + duration (tag PlayView)
        val inverted = intent?.getBooleanExtra("inverted", false) == true
        root = if (intent?.getBooleanExtra("demo", false) == true) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DitherDemoView(this).apply { setInverted(inverted) }
        } else {
            // The game lives in GameSession.shared (process memory), so recreation keeps it.
            PlayView(this, GameSession.shared).apply {
                trace = intent?.getBooleanExtra("trace", false) == true
                setInverted(inverted)
            }
        }
        setContentView(root)
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
