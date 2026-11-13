package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.chrome.Dividers
import au.com.josephmark.dithertracker.chrome.MenuDiamond
import au.com.josephmark.dithertracker.commander.CommanderClose
import au.com.josephmark.dithertracker.commander.CommanderTile
import au.com.josephmark.dithertracker.drawer.DrawerComponent
import au.com.josephmark.dithertracker.drawer.DrawerTab
import au.com.josephmark.dithertracker.life.LifeComponent
import au.com.josephmark.dithertracker.peeks.PeeksComponent
import au.com.josephmark.dithertracker.readout.ReadoutComponent
import au.com.josephmark.dithertracker.stage.Stage
import kotlin.random.Random

/**
 * The Play screen's component list — the one place features are plugged in (T34). Order matters
 * only inside a layer (draw order bottom → top, and for overlapping zones the later wins); layers
 * themselves are ordered by `stage.Layer`. Adding a feature = one `stage.add(...)` line in its slot.
 * See `docs/architecture.md`.
 */
object PlayScreen {

    fun build(stage: Stage, random: Random = Random(System.nanoTime())): Stage {
        // ---- BACKDROP ------------------------------------------------------------------------------
        stage.add(Dividers())

        // ---- TILE (per seat, in the player's frame; drawn in this order) -------------------------------
        stage.add(LifeComponent())                // number + ±, life halves (Kind.CONTENT)
        val commander = stage.add(CommanderTile(random)) // commander-damage mode content (Kind.MODE)
        stage.add(ReadoutComponent())             // pending delta; the Readouts service (Kind.READOUT)
        stage.add(PeeksComponent())               // counter chips under the number (Kind.CONTENT)
        val drawer = stage.add(DrawerComponent()) // open drawer; the TileSlots lower occupant (Kind.CONTENT)
        // slot: effects (T28c) — an `fx` component, Layer.TILE, Kind.EFFECT, drawn here: after the number,
        //       readout and drawer, before the layer's press inversion (see fx/README.md, "Draw").
        // slot: KO overlay (T15) — a `ko` component, Layer.TILE, Kind.CONTENT (dead dither over the panel,
        //       tap to revive: a whole-panel zone, so it must come after life here to win its touches).

        // ---- TILE_CHROME (per seat, over the tile layer's press inversion) ----------------------------
        stage.add(DrawerTab(drawer))              // closed tab (Kind.CHROME)

        // ---- SCREEN --------------------------------------------------------------------------------
        stage.add(MenuDiamond())                  // centre diamond + menu bars; a block until T09 (Kind.CHROME)
        stage.add(CommanderClose(commander))      // the mode's close X on the diamond (Kind.MODE)

        // ---- OVERLAY -------------------------------------------------------------------------------
        // slot: table menu (T09), settings (T12), history (T18), tools (T16/T17), player setup (T13):
        //       Layer.OVERLAY, Kind.OVERLAY, each with its own Cover (Kind.ALL_BELOW_OVERLAY, or just
        //       the seats it sits on).

        stage.seal()
        return stage
    }
}
