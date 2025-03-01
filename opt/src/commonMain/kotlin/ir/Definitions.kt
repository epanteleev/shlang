package ir

object Definitions {
    const val BYTE_SIZE  = 1
    const val HWORD_SIZE = 2
    const val WORD_SIZE  = 4
    const val QWORD_SIZE = 8
    const val POINTER_SIZE = QWORD_SIZE

    const val FLOAT_SIZE  = 4
    const val DOUBLE_SIZE = 8

    fun alignTo(value: Int, alignment: Int): Int {
        return ((value + alignment - 1) / alignment) * alignment
    }
}