package app.mychatibot.eyes

/**
 * Minimal strict JSON reader for flat objects, so validation behaves the same on the device and
 * in JVM tests (Android's org.json accepts duplicate keys, trailing data and non-standard syntax).
 *
 * Accepts exactly one object with string keys and scalar values; rejects nested objects/arrays,
 * duplicate keys, trailing content and anything outside RFC 8259 syntax.
 * Value types: [String], [Long] (integers that fit), [JsonNumber.NonInteger] (fractions,
 * exponents, integers too large for Long), [Boolean], [JsonNull].
 */
internal object StrictJson {

    object JsonNull
    sealed interface JsonNumber { data object NonInteger : JsonNumber }

    class SyntaxError(message: String) : Exception(message)

    fun parseFlatObject(text: String): Map<String, Any> {
        val r = Reader(text)
        r.skipWs()
        r.expect('{')
        val out = LinkedHashMap<String, Any>()
        r.skipWs()
        if (r.peek() == '}') {
            r.pos++
        } else {
            while (true) {
                r.skipWs()
                val key = r.string()
                if (key in out) throw SyntaxError("duplicate key '$key'")
                r.skipWs()
                r.expect(':')
                r.skipWs()
                out[key] = r.value()
                r.skipWs()
                when (r.next()) {
                    ',' -> continue
                    '}' -> break
                    else -> throw SyntaxError("expected ',' or '}'")
                }
            }
        }
        r.skipWs()
        if (!r.atEnd()) throw SyntaxError("trailing content")
        return out
    }

    private class Reader(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length
        fun peek(): Char = if (atEnd()) throw SyntaxError("unexpected end") else s[pos]
        fun next(): Char = peek().also { pos++ }
        fun expect(c: Char) { if (next() != c) throw SyntaxError("expected '$c'") }
        fun skipWs() { while (!atEnd() && s[pos] in " \t\n\r") pos++ }

        fun value(): Any = when (val c = peek()) {
            '"' -> string()
            '{', '[' -> throw SyntaxError("nested values are not allowed")
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", JsonNull)
            else -> if (c == '-' || c in '0'..'9') number() else throw SyntaxError("unexpected '$c'")
        }

        private fun literal(word: String, v: Any): Any {
            if (!s.startsWith(word, pos)) throw SyntaxError("invalid literal")
            pos += word.length
            return v
        }

        private fun number(): Any {
            val start = pos
            if (peek() == '-') pos++
            when {
                !atEnd() && s[pos] == '0' -> pos++
                !atEnd() && s[pos] in '1'..'9' -> while (!atEnd() && s[pos].isAsciiDigit()) pos++
                else -> throw SyntaxError("invalid number")
            }
            var integer = true
            if (!atEnd() && s[pos] == '.') {
                integer = false
                pos++
                digits()
            }
            if (!atEnd() && (s[pos] == 'e' || s[pos] == 'E')) {
                integer = false
                pos++
                if (!atEnd() && (s[pos] == '+' || s[pos] == '-')) pos++
                digits()
            }
            if (!integer) return JsonNumber.NonInteger
            return s.substring(start, pos).toLongOrNull() ?: JsonNumber.NonInteger
        }

        private fun digits() {
            val start = pos
            while (!atEnd() && s[pos].isAsciiDigit()) pos++
            if (pos == start) throw SyntaxError("invalid number")
        }

        fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = next()
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> when (val e = next()) {
                        '"', '\\', '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > s.length) throw SyntaxError("bad unicode escape")
                            val hex = s.substring(pos, pos + 4)
                            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                                throw SyntaxError("bad unicode escape")
                            }
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> throw SyntaxError("bad escape")
                    }
                    c < ' ' -> throw SyntaxError("control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun Char.isAsciiDigit() = this in '0'..'9'
    }
}
