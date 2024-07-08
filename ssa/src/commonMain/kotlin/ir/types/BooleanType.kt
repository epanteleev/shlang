package ir.types


object BooleanType : PrimitiveType {
    override fun toString(): String = "u1"
    override fun sizeOf(): Int = 1

    override fun hashCode(): Int {
        return this::class.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return true
    }
}