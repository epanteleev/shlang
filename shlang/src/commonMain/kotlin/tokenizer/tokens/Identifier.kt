package tokenizer.tokens

import tokenizer.*
import common.assertion


class Identifier(private val data: String, position: Position): CToken(position) {
    init {
        assertion(LexicalElements.keywords.contains(data).not()) {
            "Identifier '$data' is a keyword"
        }

        assertion(LexicalElements.allPunctuators.contains(data).not()) {
            "Identifier '$data' is an operator"
        }
    }

    override fun str(): String = data

    override fun cloneWith(pos: Position): CToken {
        return Identifier(data, pos)
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Identifier

        return data == other.data
    }

    companion object {
        fun unknown(name: String): Identifier {
            return Identifier(name, Position.UNKNOWN)
        }
    }
}