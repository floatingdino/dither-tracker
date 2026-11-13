package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.widgets.WidgetTesting.assertFingertip
import au.com.josephmark.dithertracker.widgets.WidgetTesting.inside
import au.com.josephmark.dithertracker.widgets.WidgetTesting.overlaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory

class WidgetGalleryTest {

    private fun screen(g: DitherGrid) = IRect(0, 0, g.width, g.height)

    private fun gallery(s: Int, rot: Int = 0, w: Int = 1872, h: Int = 1404): Pair<WidgetGallery, DitherGrid> {
        val grid = DitherGrid(w, h, s)
        val g = WidgetGallery(WidgetMetrics(s))
        g.layout(screen(grid), rot)
        return g to grid
    }

    @Test fun fitsTheNomadAtEveryScaleAndOrientation() {
        for (s in WidgetTesting.scales) for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (rot in WidgetTesting.rotations) {
            val (g, grid) = gallery(s, rot, w, h)
            val m = g.metrics
            val what = "S=$s ${w}x$h rot $rot"
            // Landscape fits everywhere; portrait (a 2-column panel on the narrow side) at S = 2..4.
            if (w > h == (rot % 180 == 0) || s <= 4) assertTrue("$what panel cut", g.panel.fits)
            val r = g.region
            val ids = HashSet<Int>()
            for (i in 0 until r.size) {
                assertTrue("$what duplicate ${r.idAt(i)}", ids.add(r.idAt(i)))
                assertTrue(r.idAt(i) in 0 until WidgetGallery.ID_COUNT)
                assertFingertip(m, r.hitAt(i), "$what id ${r.idAt(i)}")
                assertTrue("$what", inside(r.hitAt(i), screen(grid)))
                assertTrue(inside(r.feedbackAt(i), r.hitAt(i)))
                for (j in 0 until i) assertFalse("$what ${r.idAt(i)}/${r.idAt(j)}", overlaps(r.hitAt(i), r.hitAt(j)))
            }
        }
    }

    @Test fun nomadScale4() {
        val (g, _) = gallery(4)
        // 468×351 grid: 460 wide (gap 4 each side), columns 228 | rule | 229 inside the outline.
        assertEquals(460, g.panel.bounds.width)
        assertEquals(228, g.panel.columnWidth(g.metrics, 460, 0))
        assertEquals(229, g.panel.columnWidth(g.metrics, 460, 1))
        assertEquals(1 + 27 + 4 + 7 * 27 + 6 + 4 + 1, g.panel.bounds.height)
        assertTrue(g.panel.fits)
        // Live ids: every widget but the disabled UNDO; stepper both parts; 5 segments; next only; 2 prompt buttons; close.
        assertEquals(3 + 2 + 2 + 2 + 5 + 1 + 2 + 1, g.region.size)
        assertFalse(g.region.contains(WidgetGallery.UNDO))
        assertFalse(g.region.contains(WidgetGallery.LOG + PagedList.PREV))
        assertEquals("1/4", g.log.indicator.toString())
    }

    @Test fun tapsApplyTheStandardBehaviours() {
        val (g, _) = gallery(4)
        assertTrue(g.onTap(WidgetGallery.AUTO_KO)); assertFalse(g.autoKo.checked)
        assertTrue(g.onTap(WidgetGallery.LIFE + Stepper.PLUS)); assertEquals(41, g.life.value)
        assertTrue(g.onRepeat(WidgetGallery.LIFE + Stepper.MINUS)); assertEquals(31, g.life.value)
        assertTrue(g.onTap(WidgetGallery.PLAYERS + 4)); assertEquals(4, g.players.selected)
        assertFalse(g.onTap(WidgetGallery.PLAYERS + 4))
        assertTrue(g.onTap(WidgetGallery.LOG + PagedList.NEXT))
        assertEquals("2/4", g.log.indicator.toString())
        assertTrue(g.region.contains(WidgetGallery.LOG + PagedList.PREV)) // re-collected after the change
        assertFalse(g.onTap(WidgetGallery.NEW_GAME)) // a plain button: the host's business
    }

    @Test fun pressTrackerDrivesTheGallery() {
        val (g, _) = gallery(4, rot = 180)
        val timer = FakeTimer(WidgetGallery.ID_COUNT)
        val log = ArrayList<String>()
        val tracker = PressTracker(object : PressTracker.Sink {
            override fun onPressed(id: Int) { log += "press$id" }
            override fun onReleased(id: Int) { log += "release$id" }
            override fun onTap(id: Int) { g.onTap(id) }
            override fun onRepeat(id: Int) { g.onRepeat(id) }
        }, timer, WidgetGallery.ID_COUNT)
        tracker.targets = g.region
        timer.target = tracker
        val plus = IRect().also { g.region.hitRect(WidgetGallery.LIFE + Stepper.PLUS, it) }
        tracker.down(0, plus.left + 1, plus.top + 1)
        timer.advance(2600) // 500 ms, then every 1000 ms: three +10 steps, no tap
        tracker.up(0)
        assertEquals(70, g.life.value)
        assertEquals(listOf("press9", "release9"), log)
    }

    @Test fun rotationIsExact() {
        val m = WidgetMetrics(4)
        val g = WidgetGallery(m)
        g.layout(IRect(0, 0, 468, 351), 0)
        val p = g.panel
        WidgetTesting.assertRotationExact(p.bounds.width, p.bounds.height) { pt, f -> pt.draw(f, p) }
    }

    @Test fun pressFallbackMatchesInversion() {
        val (g, grid) = gallery(4)
        WidgetTesting.assertFallbackMatchesInvert(grid.width, grid.height, g.region) { g.draw(it) }
    }

    @Test fun renderPreviews() {
        // Device-pixel PNGs of the gallery for eyeballing: app/build/widgets/gallery-*.png.
        for ((s, rot) in listOf(4 to 0, 4 to 180, 2 to 0, 6 to 0, 3 to 90)) {
            val (g, grid) = gallery(s, rot)
            val c = RasterCanvas(grid.width, grid.height)
            g.draw(WidgetPainter(c))
            if (s == 4 && rot == 0) {
                // One pressed widget, as the host would show it.
                WidgetPainter(c).drawPressed(g.region, WidgetGallery.RESET)
            }
            c.writePng(File(WidgetTesting.outDir, "gallery-s$s-r$rot.png"), s)
        }
    }

    @Test fun drawLayoutAndHitPathsDoNotAllocate() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val (g, grid) = gallery(4)
        val c = RasterCanvas(grid.width, grid.height)
        val painter = WidgetPainter(c)
        val tracker = PressTracker(object : PressTracker.Sink {
            override fun onPressed(id: Int) {}
            override fun onReleased(id: Int) {}
            override fun onTap(id: Int) {}
        })
        tracker.targets = g.region
        val tid = Thread.currentThread().id
        val parts = arrayOf<() -> Unit>(
            { g.relayout() },
            { g.draw(painter, tracker) },
            { painter.drawPressed(g.region, WidgetGallery.RESET) },
            {
                var y = 0
                while (y < grid.height) { var x = 0; while (x < grid.width) { g.region.hitTest(x, y); x += 7 }; y += 7 }
            },
            { tracker.down(0, 300, 100); tracker.move(0, 301, 100); tracker.up(0) },
        )
        for (p in parts) repeat(3) { p() } // warm-up: icon masks, glyph scales, class init
        val names = arrayOf("layout+collect", "draw", "pressed", "hitTest", "press")
        // Any allocation per run costs ≥ 16 bytes; one-off JIT/OSR noise stays far below that over many runs.
        val runs = 400
        for (i in parts.indices) {
            val before = bean.getThreadAllocatedBytes(tid)
            for (n in 0 until runs) parts[i]()
            val bytes = bean.getThreadAllocatedBytes(tid) - before
            assertTrue("${names[i]} allocated $bytes bytes in $runs runs", bytes < runs * 16L)
        }
    }
}
