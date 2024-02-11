package ir.types


data class ArrayType(private val type: Type, val size: Int) : AggregateType {
    fun elementType(): Type = type

    override fun size(): Int {
        return size * type.size()
    }

    override fun offset(index: Int): Int {
        return index * type.size()
    }

    override fun toString(): String {
        return "<$type x $size>"
    }
}