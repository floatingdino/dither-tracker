package au.com.josephmark.dithertracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View

/** Placeholder root view: fills pure white, draws nothing else. Replaced by the renderer later. */
class BlankView(context: Context) : View(context) {

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
    }
}
