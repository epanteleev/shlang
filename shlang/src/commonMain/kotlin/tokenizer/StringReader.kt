package tokenizer

import kotlin.math.*


class StringReader(val str: String, var pos: Int = 0) {
    private val size
        get() = str.length
    val eof
        get() = pos >= size
    private val available
        get() = size - pos

    private fun eof(offset: Int): Boolean {
        return pos + offset >= size
    }

    fun peek(): Char {
        if (eof) {
            throw IllegalStateException("EOF")
        }
        return str[pos]
    }

    fun isSpace(): Boolean {
        return isSpace(0)
    }

    fun isLetter(): Boolean {
        if (eof) {
            return false
        }
        val ch = peek()
        return ch.isLetter() || ch == '_'
    }

    fun isOneFrom(vararg char: Char): Boolean {
        if (eof) {
            return false
        }
        val ch = peek()
        return char.contains(ch)
    }

    fun isOneFrom(offset: Int, vararg char: Char): Boolean {
        if (eof(offset)) {
            return false
        }
        val ch = peekOffset(offset)
        return char.contains(ch)
    }

    fun isHexDigit(): Boolean {
        if (eof) {
            return false
        }
        val ch = peek()
        return ch.isDigit() || (ch in 'a'..'f') || (ch in 'A'..'F')
    }

    fun isDigit(offset: Int = 0): Boolean {
        if (eof(offset)) {
            return false
        }

        val ch = peekOffset(offset)
        return ch.isDigit()
    }

    fun isBinary(offset: Int = 0): Boolean {
        if (eof(offset)) {
            return false
        }

        val ch = peekOffset(offset)
        return ch == '0' || ch == '1'
    }

    fun isOctal(offset: Int = 0): Boolean {
        if (eof(offset)) {
            return false
        }

        val ch = peekOffset(offset)
        return ch in '0'..'7'
    }

    private fun isSpace(offset: Int): Boolean {
        if (eof(offset)) {
            return false
        }
        val ch = peekOffset(offset)
        return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\u000C' || ch == '\u000B'
    }

    fun check(char: Char): Boolean {
        if (eof) {
            return false
        }
        return str[pos] == char
    }

    fun check(expect: String): Boolean {
        return str.startsWith(expect, pos)
    }

    fun inRange(offset: Int): Boolean = pos + offset < size

    fun peekOffset(offset: Int): Char {
        return if (pos + offset >= 0 && pos + offset < str.length) {
            str[pos + offset]
        } else {
            throw IllegalStateException("EOF")
        }
    }

    fun read(): Char {
        val p = pos++;
        return if (p >= 0 && p < str.length) {
            str[p]
        } else {
            throw IllegalStateException("EOF")
        }
    }

    fun peek(count: Int): String {
        return str.substring(pos, pos + min(available, count))
    }

    fun read(count: Int) {
        pos += count
    }

    companion object {
        private val punctuation = arrayOf(
            '!', '"', '#', '$', '%', '&', '(', ')', '*', '+', ',', '-', '.', '/', ':', ';', '<', '=',
            '>','?','@','[','\\',']','^','`','{','|','}','~',' ','\t','\n'
        )

        fun tryPunct(ch: Char): Boolean {
            return punctuation.contains(ch)
        }

        fun isSeparator(char: Char): Boolean {
            return punctuation.contains(char)
        }
    }
}