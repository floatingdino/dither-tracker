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

    /** Whether the screen is an e-ink panel (on a Supernote; another vendor's panel is not recognised). */
    @JvmField var einkPanel: Boolean = false

    /**
     * Whether a fast waveform (DU, A2, DUX) is what our root view asks for and T58's guard still works
     * ([EinkDisplay.mode], [EinkViewMode.available]).
     */
    fun interface FastMode {
        fun inEffect(): Boolean
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
