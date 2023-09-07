package ir.read

import java.lang.StringBuilder

sealed class Token(open val line: Int, open val pos: Int) {
    abstract fun message(): String
}

data class Identifier(val string: String, override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos identifier '$string'"
    }
}

data class IntValue(val int: Long, override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos int value '$int'"
    }
}

data class FloatValue(val fp: Double, override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos float value '$fp'"
    }
}

data class ValueToken(val name: String, override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos value '%$name'"
    }
}

data class TypeToken(val type: String, val indirection: Int, override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        val stars = "*".repeat(indirection)
        return "$line:$pos type '$type$stars'"
    }
}

data class OpenParen(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos '('"
    }
}

data class CloseParen(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos ')'"
    }
}

data class OpenBrace(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos '{'"
    }
}

data class CloseBrace(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos '}'"
    }
}

data class Equal(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos '='"
    }
}

data class Comma(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos ','"
    }
}

data class Dot(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos '.'"
    }
}

data class Define(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos 'define'"
    }
}

data class Colon(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos ':'"
    }
}

data class Extern(override val line: Int, override val pos: Int): Token(line, pos) {
    override fun message(): String {
        return "$line:$pos 'extern'"
    }
}