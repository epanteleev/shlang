package ir.types

class BooleanType : PrimitiveType {
    override fun toString(): String = "u1"
    override fun size(): Int = 1

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}