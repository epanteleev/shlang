package ir.types

import common.ArrayWrapper


class TupleType(private val tuple: Array<PrimitiveType>): TrivialType {
    override fun toString(): String {
        return "|${tuple.joinToString()}|"
    }

    fun innerTypes(): List<PrimitiveType> {
        return ArrayWrapper(tuple)
    }

    fun innerType(index: Int): PrimitiveType {
        if (index < 0 || index >= tuple.size) {
            throw RuntimeException("index out of bounds: $index, type='${tuple.joinToString { it.toString() }}'")
        }
        return tuple[index]
    }

    inline fun<reified T: NonTrivialType> asInnerType(index: Int): T {
        val type = innerType(index)
        if (type !is T) {
            throw RuntimeException("unexpected type: '$type'")
        }

        return type
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TupleType

        return tuple.contentEquals(other.tuple)
    }

    override fun hashCode(): Int {
        return tuple.contentHashCode()
    }
}