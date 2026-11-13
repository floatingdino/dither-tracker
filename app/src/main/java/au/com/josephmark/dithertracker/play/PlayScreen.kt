package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.chrome.Dividers
import au.com.josephmark.dithertracker.chrome.MenuDiamond
import au.com.josephmark.dithertracker.commander.CommanderClose
import au.com.josephmark.dithertracker.commander.CommanderTile
import au.com.josephmark.dithertracker.drawer.DrawerComponent
import au.com.josephmark.dithertracker.drawer.DrawerTab
import au.com.josephmark.dithertracker.eink.CleanComponent
import au.com.josephmark.dithertracker.fx.FxComponent
import au.com.josephmark.dithertracker.history.HistoryOverlay
import au.com.josephmark.dithertracker.ko.KoComponent
import au.com.josephmark.dithertracker.ko.KoWinner
import au.com.josephmark.dithertracker.life.LifeComponent
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.menu.MenuOverlay
import au.com.josephmark.dithertracker.peeks.PeeksComponent
import au.com.josephmark.dithertracker.readout.ReadoutComponent
import au.com.josephmark.dithertracker.settings.SettingsOverlay
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.tools.DiceOverlay
import au.com.josephmark.dithertracker.tools.FirstPlayer
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
        stage.add(KoWinner(random))               // last player standing: the line replaces the number (Kind.CONTENT); before fx so the flourish plays over it
        stage.add(FxComponent())                  // hit/heal/KO/winner effects, after the number (Kind.EFFECT)
        stage.add(KoComponent(random))            // KO: dead tile, quip, tap-tap revive; covers the seat (Kind.STATUS)

        // ---- TILE_CHROME (per seat, over the tile layer's press inversion) ----------------------------
        stage.add(DrawerTab(drawer))              // closed tab (Kind.CHROME)

        // ---- SCREEN --------------------------------------------------------------------------------
        stage.add(MenuDiamond())                  // centre diamond + menu bars; opens the menu (Kind.CHROME)
        stage.add(CommanderClose(commander))      // the mode's close X on the diamond (Kind.MODE)

        // ---- OVERLAY -------------------------------------------------------------------------------
        val menu = stage.add(MenuOverlay(random)) // table menu, the gem (T09; Kind.OVERLAY)
        menu.destinations.register(MenuItem.LOG, stage.add(HistoryOverlay(random))) // game history card (T18)
        menu.destinations.register(MenuItem.DICE, stage.add(DiceOverlay(random))) // dice / coin tent (T16)
        menu.destinations.register(MenuItem.FIRST, stage.add(FirstPlayer(random))) // first player on their tile (T16)
        menu.destinations.register(MenuItem.SETUP, stage.add(SettingsOverlay())) // settings card (T12)
        stage.add(CleanComponent())               // e-ink cleanup: manual CLEAN, cadence, overlay close (T19b); keep LAST (its ink frame covers everything)
        // slot: settings (T12), history (T18), tools (T16/T17), player setup (T13): Layer.OVERLAY,
        //       Kind.OVERLAY, each with its own Cover if it hides anything. Menu items reach them with
        //       `menu.destinations.register(MenuItem.SETUP, stage.add(SettingsOverlay(...)))`.

        stage.seal()
        return stage
    }
}
