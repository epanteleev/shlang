package ir.types

class AnyType : TrivialType, NonTrivialType, PrimitiveType {
    override fun size(): Int {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "undef"

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}