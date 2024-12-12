package ir.types


sealed interface IntegerType: ArithmeticType

data class SignedIntType(private val size: Int) : IntegerType {
    override fun sizeOf(): Int {
        return size
    }

    override fun toString(): String {
        return when (size) {
            1 -> "i8"
            2 -> "i16"
            4 -> "i32"
            8 -> "i64"
            else -> throw TypeErrorException("unsupported size=$size")
        }
    }
}