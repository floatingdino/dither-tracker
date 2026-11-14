package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.Fonts
import org.junit.Test
import java.io.File

/**
 * T62: what AUTO picks on the screens of `docs/compatibility.md` — S, grid, tile and numeral in mm,
 * the comfortable arrangements per count (written to `app/build/purity/t62/screen-survey.txt`, with the
 * emulator walk's tap points in `screen-taps.txt`).
 */
class ScreenSurveyTest {

    /** The Nomad (and every 7.8" 300-dpi panel: Boox Nova / Tab Mini) stays at S = 4 at every count. */
    @Test fun nomadIsScale4() {
        for (p in 2..6) {
            org.junit.Assert.assertEquals(4, PlayLayout.chooseScale(1872, 1404, 300, DitherGrid.AUTO, p))
            org.junit.Assert.assertEquals(4, PlayLayout.chooseScale(1404, 1872, 300, DitherGrid.AUTO, p))
        }
    }

    /** Where AUTO finds a comfortable S it uses it; the layout it lands on is comfortable or nothing on that screen is. */
    @Test fun autoLandsOnAComfortableLayoutWhenOneExists() {
        for (d in Screens.ALL) for (p in 2..6) {
            val s = PlayLayout.chooseScale(d.w, d.h, d.dpi, DitherGrid.AUTO, p)
            val any = (DitherGrid.MIN_SCALE..DitherGrid.MAX_SCALE).any { PlayLayout.compute(DitherGrid(d.w, d.h, it), p).comfortable }
            org.junit.Assert.assertEquals("${d.name} ${p}p S=$s", any, PlayLayout.compute(DitherGrid(d.w, d.h, s), p).comfortable)
        }
    }

    @Test fun writeSurvey() {
        val sb = StringBuilder()
        for (d in Screens.ALL) {
            sb.append("${d.name} ${d.w}x${d.h} @${d.dpi}\n")
            for (p in 2..6) {
                val s = PlayLayout.chooseScale(d.w, d.h, d.dpi, DitherGrid.AUTO, p)
                val l = PlayLayout.compute(DitherGrid(d.w, d.h, s), p)
                val mm = { px: Int -> px * s * 25.4 / d.dpi }
                val ok = Arrangements.ALL.filter { it.players == p && PlayLayout.compute(DitherGrid(d.w, d.h, s), p, it.id).comfortable }.map { it.id }
                sb.append(
                    String.format(
                        "  %dp S=%d grid %dx%d tile %dx%d (%.0fx%.0f mm) k=%d cap %.1f mm tabHit %.1f mm tier %d; ok %s\n",
                        p, s, l.grid.width, l.grid.height, l.panelW, l.panelH, mm(l.panelW), mm(l.panelH), l.kMax,
                        mm(Fonts.NUM_L.capHeight * l.kMax), mm(l.chrome.tabH + l.chrome.tabHitPad), l.tier, ok.joinToString(","),
                    ),
                )
            }
        }
        // Device-px tap points for the emulator walk (4 players, default layout): diamond, seat 3's tab.
        val taps = StringBuilder()
        for (d in Screens.ALL) {
            val s = PlayLayout.chooseScale(d.w, d.h, d.dpi)
            val l = PlayLayout.compute(DitherGrid(d.w, d.h, s), 4)
            val g = l.grid
            val t = l.tabHit[3]
            taps.append("${d.name} ${d.w} ${d.h} ${d.dpi} $s ${g.toDeviceX(l.diamondCx) + s / 2} ${g.toDeviceY(l.diamondCy) + s / 2} ")
            taps.append("${g.toDeviceX((t.left + t.right) / 2)} ${g.toDeviceY((t.top + t.bottom) / 2)}\n")
        }
        File("build/purity/t62/screen-taps.txt").apply { parentFile.mkdirs() }.writeText(taps.toString())
        val f = File("build/purity/t62/screen-survey.txt")
        f.parentFile.mkdirs()
        f.writeText(sb.toString())
        println(sb)
    }
}

/** T62: the screen matrix (landscape px, reported density). */
object Screens {
    class Screen(val name: String, val w: Int, val h: Int, val dpi: Int)

    val ALL = listOf(
        Screen("nomad", 1872, 1404, 300),
        Screen("manta", 2560, 1920, 300),
        Screen("boox-6", 1448, 1072, 300),
        Screen("boox-6-lite", 1024, 758, 212),
        Screen("boox-7", 1680, 1264, 300),
        Screen("boox-10.3-c", 2480, 1860, 300),
        Screen("boox-10.3-227", 1872, 1404, 227),
        Screen("boox-13.3", 3200, 2400, 300),
        Screen("boox-tabx", 2200, 1650, 207),
        Screen("palma", 1648, 824, 300),
        Screen("phone-small", 1280, 720, 320),
        Screen("phone", 2400, 1080, 420),
        Screen("phone-tall", 3120, 1440, 560),
        Screen("tab-small-240", 1920, 1200, 240),
        Screen("tab-small-320", 1920, 1200, 320),
        Screen("tab-10", 2560, 1600, 320),
        Screen("tab-10-240", 2000, 1200, 240),
        Screen("tab-12", 2800, 1752, 266),
        Screen("fold", 2208, 1840, 374),
        Screen("tv", 1920, 1080, 160),
    )
}
