package au.com.josephmark.dithertracker.core.serial

/** Thrown inside the decoder only; [SaveCodec.decode] turns it into a [DecodeResult.Failure]. No stack trace. */
internal class CorruptSaveException(message: String) : Exception(message, null, false, false)

internal fun corrupt(message: String): Nothing = throw CorruptSaveException(message)

/** Growable byte buffer with LEB128 varints (signed values zigzag-encoded). */
internal class ByteWriter(initialCapacity: Int = 2048) {
    private var buf = ByteArray(initialCapacity)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
    }

    fun byte(b: Int) {
        ensure(1)
        buf[size++] = b.toByte()
    }

    fun bytes(b: ByteArray, from: Int = 0, to: Int = b.size) {
        ensure(to - from)
        System.arraycopy(b, from, buf, size, to - from)
        size += to - from
    }

    fun append(other: ByteWriter) = bytes(other.buf, 0, other.size)

    fun bool(v: Boolean) = byte(if (v) 1 else 0)

    /** Unsigned varint; [v] is treated as an unsigned 64-bit value. */
    fun uvar(v: Long) {
        var x = v
        while (x and 0x7FL.inv() != 0L) {
            byte(((x and 0x7F) or 0x80).toInt())
            x = x ushr 7
        }
        byte(x.toInt())
    }

    fun uvar(v: Int) {
        require(v >= 0) { "negative unsigned varint $v" }
        uvar(v.toLong())
    }

    fun svar(v: Long) = uvar((v shl 1) xor (v shr 63))
    fun svar(v: Int) = svar(v.toLong())

    /** 0 = null, otherwise 1 followed by the zigzag value. */
    fun optInt(v: Int?) {
        if (v == null) byte(0) else {
            byte(1)
            svar(v)
        }
    }

    fun string(s: String) {
        val b = s.encodeToByteArray()
        uvar(b.size)
        bytes(b)
    }

    fun u16(v: Int) {
        byte(v ushr 8)
        byte(v)
    }

    fun u32(v: Int) {
        byte(v ushr 24)
        byte(v ushr 16)
        byte(v ushr 8)
        byte(v)
    }

    fun reset() {
        size = 0
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

/**
 * Bounds-checked reader over `buf[start until end]`. Every malformed read throws
 * [CorruptSaveException]; element counts are checked against the remaining bytes before anything
 * is allocated, so hostile input can't trigger huge allocations.
 */
internal class ByteReader(private val buf: ByteArray, start: Int, private val end: Int) {
    private var pos = start
    val remaining: Int get() = end - pos

    fun byte(): Int {
        if (pos >= end) corrupt("unexpected end of data")
        return buf[pos++].toInt() and 0xFF
    }

    fun bool(): Boolean = when (byte()) {
        0 -> false
        1 -> true
        else -> corrupt("bad boolean")
    }

    fun uvarLong(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = byte()
            if (shift == 63 && b > 1) corrupt("varint overflow")
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) corrupt("varint too long")
        }
    }

    fun uvar(): Int {
        val v = uvarLong()
        if (v !in 0..Int.MAX_VALUE) corrupt("varint out of range")
        return v.toInt()
    }

    fun svarLong(): Long {
        val v = uvarLong()
        return (v ushr 1) xor -(v and 1)
    }

    fun svar(): Int {
        val v = svarLong()
        if (v !in Int.MIN_VALUE..Int.MAX_VALUE) corrupt("int out of range")
        return v.toInt()
    }

    fun optInt(): Int? = if (bool()) svar() else null

    /** An element count where each element takes at least [minBytesEach] bytes. */
    fun count(minBytesEach: Int = 1): Int {
        val n = uvar()
        if (n.toLong() * minBytesEach > remaining) corrupt("count $n exceeds remaining data")
        return n
    }

    fun string(): String {
        val n = count()
        val s = buf.decodeToString(pos, pos + n) // malformed UTF-8 becomes U+FFFD, never throws
        pos += n
        return s
    }

    fun u16(): Int = (byte() shl 8) or byte()
    fun u32(): Int = (byte() shl 24) or (byte() shl 16) or (byte() shl 8) or byte()

    /** Reads a length-prefixed block and returns a reader over it, advancing past it. */
    fun block(): ByteReader {
        val n = count()
        val r = ByteReader(buf, pos, pos + n)
        pos += n
        return r
    }
}
