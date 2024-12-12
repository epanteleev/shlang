package ir.types

import ir.Definitions.BYTE_SIZE
import ir.Definitions.HWORD_SIZE
import ir.Definitions.QWORD_SIZE
import ir.Definitions.WORD_SIZE


sealed class UnsignedIntType(private val size: Int) : IntegerType {
    override fun sizeOf(): Int {
        return size
    }

    override fun toString(): String {
        return when (size) {
            1 -> "u8"
            2 -> "u16"
            4 -> "u32"
            8 -> "u64"
            else -> throw TypeErrorException("unsupported size=$size")
        }
    }
}

object U8Type : UnsignedIntType(BYTE_SIZE)
object U16Type : UnsignedIntType(HWORD_SIZE)
object U32Type : UnsignedIntType(WORD_SIZE)
object U64Type : UnsignedIntType(QWORD_SIZE)