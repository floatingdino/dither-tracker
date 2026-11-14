package au.com.josephmark.dithertracker.eink

/**
 * How many frames the display can show for a moment of animation (T59): [FAST] or [SLOW]. The one
 * place features ask before choosing how many keyframes to play (SPEC rule 4):
 *
 * - **SLOW** — an e-ink panel on its normal waveform: 2–4 held keyframes, each ≥ ~250 ms.
 * - **FAST** — the Supernote's fast view mode is actually in effect (the Display setting is FAST *and*
 *   [EinkViewMode]'s guard set the DU waveform on our root view and has not disabled itself), **or** the
 *   screen is not an e-ink panel at all (the emulator, an LCD tablet: it shows any number of frames). A
 *   few more held poses are allowed — still poses, not tweens; still skippable; still cheap.
 *
 * Read it with [current] wherever the choice is made (at the start of a sequence, never mid-way). It
 * asks the live waveform state, which `MainActivity` updates on every Display setting change (and the
 * `--ei einkMode N` pin), so a change applies to the next sequence with no listener. Allocation-free.
 * The device facts ([einkPanel], [fastMode]) are set once by `MainActivity`; JVM tests and the
 * emulator keep the defaults (not e-ink: FAST). The debug extra `--es displaySpeed fast|slow` sets
 * [override] (captures, tests).
 */
object DisplaySpeed {
    const val FAST = 0
    const val SLOW = 1
    const val NO_OVERRIDE = -1

    /** Whether the screen is an e-ink panel ([isEinkDevice]: a Supernote or another known e-ink vendor, T62). */
    @JvmField var einkPanel: Boolean = false

    /**
     * Whether a device is (very likely) an e-ink panel, from its `Build.MANUFACTURER`, `Build.BRAND` and
     * `Build.MODEL` (T62; `docs/compatibility.md`). Vendors that only make e-ink devices match by name:
     * Supernote / Ratta, Onyx (Boox), Bigme, Boyue (Likebook, Meebook), Meebook. Hisense also sells LCD
     * phones, so only its e-ink models (A5, A7, A9 families) match. A miss costs extra keyframes on a slow
     * panel; a false match only fewer poses on a fast screen — so the list errs towards matching. Only
     * the Supernote's fast view mode can make an e-ink panel [FAST] (no other vendor call is used).
     */
    fun isEinkDevice(manufacturer: String?, brand: String?, model: String?): Boolean {
        for (name in arrayOf(manufacturer, brand)) {
            val n = name?.trim()?.lowercase() ?: continue
            if (n in EINK_VENDORS) return true
            if (n == "hisense") {
                val m = model?.trim()?.uppercase() ?: return false
                return HISENSE_EINK.any { m.startsWith(it) && (m.length == it.length || !m[it.length].isDigit()) }
            }
        }
        return false
    }

    private val EINK_VENDORS = setOf("supernote", "ratta", "onyx", "boox", "bigme", "boyue", "likebook", "meebook")
    private val HISENSE_EINK = arrayOf("A5", "A7", "A9")

    /**
     * Whether a fast waveform (DU, A2, DUX) is what our root view asks for and T58's guard still works
     * ([EinkDisplay.mode], [EinkViewMode.available]).
     */
    fun interface FastMode {
        fun inEffect(): Boolean
    }

    /** Where a component asks for the speed (tests inject a fixed one); the default is [current]. */
    fun interface Source {
        fun current(): Int
    }

    /** Set by `MainActivity` on a Supernote; the default answers "no fast waveform". */
    @JvmField var fastMode: FastMode = FastMode { false }

    /** [FAST] / [SLOW] forced (debug extra, tests), or [NO_OVERRIDE]. */
    @JvmField var override: Int = NO_OVERRIDE

    /** The speed now. */
    fun current(): Int = when {
        override != NO_OVERRIDE -> override
        !einkPanel -> FAST
        fastMode.inEffect() -> FAST
        else -> SLOW
    }

    val isFast: Boolean get() = current() == FAST

    /** Whether e-ink view mode [mode] ([EinkViewMode]) is a fast (1-bit, short) waveform. */
    fun isFastWaveform(mode: Int): Boolean =
        mode == EinkViewMode.DU || mode == EinkViewMode.A2 || mode == EinkViewMode.DUX

    /** `--es displaySpeed` values: "fast" / "slow"; anything else clears the override. */
    fun parse(value: String?): Int = when (value?.lowercase()) {
        "fast" -> FAST
        "slow" -> SLOW
        else -> NO_OVERRIDE
    }

    fun name(speed: Int): String = if (speed == FAST) "fast" else "slow"
}
