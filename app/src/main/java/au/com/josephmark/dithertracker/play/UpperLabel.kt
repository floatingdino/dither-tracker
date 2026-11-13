package au.com.josephmark.dithertracker.play

/**
 * A reusable `prefix + name`, upper-cased and truncated to a maximum length, without allocating
 * per draw (the `ui` face has lowercase, but labels on the play surface are caps, like the drawer's).
 */
class UpperLabel : CharSequence {
    private var prefix: String = ""
    private var name: String = ""
    private var n = 0

    /** Shows [prefix] then as much of [name] as fits in [maxNameChars]. */
    fun set(prefix: String, name: String, maxNameChars: Int) {
        this.prefix = prefix
        this.name = name.trim()
        n = prefix.length + this.name.length.coerceIn(0, maxOf(0, maxNameChars))
    }

    override val length: Int get() = n

    override fun get(index: Int): Char =
        (if (index < prefix.length) prefix[index] else name[index - prefix.length]).uppercaseChar()

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = toString().subSequence(startIndex, endIndex)

    // `this@UpperLabel`: inside buildString a bare get(i) is the StringBuilder's own charAt.
    override fun toString(): String = buildString { for (i in 0 until n) append(this@UpperLabel[i]) }
}
