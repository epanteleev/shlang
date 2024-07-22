package gen

import ir.types.Type
import ir.Definitions.BYTE_SIZE
import ir.Definitions.HWORD_SIZE
import ir.Definitions.QWORD_SIZE
import ir.Definitions.WORD_SIZE
import ir.types.NonTrivialType
import ir.types.StructType


object CallConvention {
    fun coerceArgumentTypes(cType: StructType): List<NonTrivialType>? = when (cType.sizeOf()) {
        BYTE_SIZE  -> arrayListOf(Type.I8)
        HWORD_SIZE -> arrayListOf(Type.I16)
        WORD_SIZE  -> arrayListOf(Type.I32)
        QWORD_SIZE -> arrayListOf(Type.I64)
        QWORD_SIZE + BYTE_SIZE  -> arrayListOf(Type.I64, Type.I8)
        QWORD_SIZE + HWORD_SIZE -> arrayListOf(Type.I64, Type.I16)
        QWORD_SIZE + WORD_SIZE  -> arrayListOf(Type.I64, Type.I32)
        QWORD_SIZE * 2          -> arrayListOf(Type.I64, Type.I64)
        else -> null
    }
}