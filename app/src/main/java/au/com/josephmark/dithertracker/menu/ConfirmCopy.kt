package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.widgets.TextFit
import au.com.josephmark.dithertracker.widgets.TextWrap
import kotlin.random.Random

/**
 * The restart confirm's words: a prompt from core's reset table (`Flavour.resetConfirm`'s lines —
 * quips are always on, SPEC) and an independently picked button pair, never the same prompt or pair
 * twice running. [layout] breaks the prompt into ≤ 2 lines: after its question mark when both halves
 * fit ("New game?" / "Shuffle up, we go again", as in the mock), otherwise greedy word wrap
 * ([TextWrap]). Allocation-free after construction.
 */
class ConfirmCopy(private val flavour: Flavour = Flavour.quippy) {
    var prompt: String = flavour.lines(QuipKind.RESET_PROMPT)[0]
        private set
    var confirm: String = flavour.resetButtons[0].confirm
        private set
    var cancel: String = flavour.resetButtons[0].cancel
        private set

    /** Line i is `prompt[start[i], end[i])`. */
    val start = IntArray(MAX_LINES)
    val end = IntArray(MAX_LINES)
    var lines = 0
        private set

    /** Glyphs of [confirm] / [cancel] that fit their buttons ([TextFit]). */
    var confirmChars = 0
        private set
    var cancelChars = 0
        private set

    private var lastPrompt = -1
    private var lastButtons = -1
    private val wrap = TextWrap(MAX_LINES)

    /** Picks a new prompt and button pair. */
    fun pick(random: Random) {
        val prompts = flavour.lines(QuipKind.RESET_PROMPT)
        lastPrompt = pickAvoiding(prompts.size, lastPrompt, random)
        prompt = prompts[lastPrompt]
        val pairs = flavour.resetButtons
        lastButtons = pickAvoiding(pairs.size, lastButtons, random)
        confirm = pairs[lastButtons].confirm
        cancel = pairs[lastButtons].cancel
    }

    /** Breaks [prompt] into lines no wider than [maxW] and fits the button labels into [buttonTextW]. */
    fun layout(font: Font, k: Int, maxW: Int, buttonTextW: Int) {
        val p = prompt
        val q = p.indexOf("? ")
        if (q > 0 && font.widthOf(q + 1, k) <= maxW && font.widthOf(p.length - (q + 2), k) <= maxW) {
            start[0] = 0; end[0] = q + 1
            start[1] = q + 2; end[1] = p.length
            lines = 2
        } else {
            lines = wrap.wrap(p, font, k, maxW)
            for (i in 0 until lines) { start[i] = wrap.start[i]; end[i] = wrap.end[i] }
        }
        confirmChars = TextFit.fit(confirm, font, k, buttonTextW)
        cancelChars = TextFit.fit(cancel, font, k, buttonTextW)
    }

    private fun pickAvoiding(size: Int, avoid: Int, random: Random): Int {
        if (size == 1 || avoid !in 0 until size) return random.nextInt(size)
        val i = random.nextInt(size - 1)
        return if (i >= avoid) i + 1 else i
    }

    companion object {
        const val MAX_LINES = 2
    }
}
