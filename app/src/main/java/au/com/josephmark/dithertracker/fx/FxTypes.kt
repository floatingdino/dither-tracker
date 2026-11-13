package au.com.josephmark.dithertracker.fx

/** What an effect is drawn for, from its `meta.txt` `role` (see the generator in app/build.gradle.kts). */
enum class FxRole(val id: String) {
    DAMAGE("damage"),
    HEAL("heal"),
    BIG_HIT("big-hit"),
    KO("ko"),
}

/**
 * The reference point of the life total's ink box that an effect's frame centre is anchored to
 * (`anchor <ref> dx dy` in `meta.txt`). [rx]/[ry] are -1 (left/top), 0 (centre) or +1 (right/bottom).
 */
enum class FxAnchorRef(val id: String, val rx: Int, val ry: Int) {
    CENTRE("centre", 0, 0),
    TL("tl", -1, -1),
    TR("tr", 1, -1),
    BL("bl", -1, 1),
    BR("br", 1, 1),
    TOP("top", 0, -1),
    BOTTOM("bottom", 0, 1),
    LEFT("left", -1, 0),
    RIGHT("right", 1, 0),
}
