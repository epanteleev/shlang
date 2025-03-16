package types

import ir.Definitions.BYTE_SIZE


sealed class CPrimitive: CompletedType() {
    final override fun alignmentOf(): Int = size()

    fun interfere(type2: CType): CPrimitive? {
        when (this) {
            type2 -> return this
            CHAR -> {
                return when (type2) {
                    BOOL -> CHAR
                    UCHAR -> UCHAR
                    INT -> INT
                    LONG -> LONG
                    ULONG -> ULONG
                    SHORT -> SHORT
                    UINT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    is CEnumType -> INT
                    else -> null
                }
            }

            UCHAR -> {
                return when (type2) {
                    BOOL -> UCHAR
                    INT -> INT
                    LONG -> LONG
                    CHAR -> UCHAR
                    SHORT -> SHORT
                    UINT -> UINT
                    ULONG -> ULONG
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    is CEnumType -> INT
                    else -> null
                }
            }
            SHORT -> {
                return when (type2) {
                    BOOL -> SHORT
                    INT -> INT
                    LONG -> LONG
                    CHAR -> SHORT
                    UCHAR -> SHORT
                    UINT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    is CEnumType -> INT
                    else -> null
                }
            }

            INT -> {
                return when (type2) {
                    BOOL -> INT
                    CHAR -> INT
                    UCHAR -> INT
                    LONG -> LONG
                    ULONG -> ULONG
                    SHORT -> INT
                    USHORT -> INT
                    UINT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    is CEnumType -> INT
                    is CPointer -> type2
                    else -> null
                }
            }

            LONG -> {
                return when (type2) {
                    BOOL -> LONG
                    CHAR -> LONG
                    UCHAR -> LONG
                    INT -> LONG
                    SHORT -> LONG
                    USHORT -> LONG
                    UINT -> LONG
                    ULONG -> ULONG
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    is CEnumType -> LONG
                    else -> null
                }
            }

            FLOAT -> {
                return when (type2) {
                    BOOL -> FLOAT
                    CHAR -> FLOAT
                    INT -> FLOAT
                    SHORT -> FLOAT
                    UINT -> FLOAT
                    DOUBLE -> DOUBLE
                    LONG -> DOUBLE
                    is CEnumType -> FLOAT
                    else -> null
                }
            }

            DOUBLE -> return DOUBLE
            USHORT -> {
                return when (type2) {
                    BOOL -> USHORT
                    INT -> INT
                    SHORT -> USHORT
                    LONG -> LONG
                    ULONG -> ULONG
                    CHAR -> USHORT
                    UINT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    is CEnumType -> INT
                    is CPointer -> type2
                    else -> null
                }
            }

            UINT -> {
                return when (type2) {
                    BOOL -> UINT
                    CHAR -> UINT
                    UCHAR -> UINT
                    LONG -> LONG
                    ULONG -> ULONG
                    SHORT -> UINT
                    USHORT -> UINT
                    INT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    is CEnumType -> UINT
                    else -> null
                }
            }

            ULONG -> {
                return when (type2) {
                    BOOL -> ULONG
                    CHAR -> ULONG
                    UCHAR -> ULONG
                    INT -> ULONG
                    LONG -> ULONG
                    SHORT -> ULONG
                    USHORT -> ULONG
                    UINT -> ULONG
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    is CEnumType -> ULONG
                    is CPointer -> type2
                    else -> null
                }
            }

            is CPointer -> return this
            is CEnumType -> {
                return when (type2) {
                    BOOL -> INT
                    CHAR -> INT
                    UCHAR -> INT
                    SHORT -> INT
                    USHORT -> INT
                    INT -> INT
                    UINT -> UINT
                    LONG -> LONG
                    ULONG -> ULONG
                    FLOAT -> FLOAT
                    DOUBLE -> DOUBLE
                    is CPointer -> type2
                    else -> return null
                }
            }
            is BOOL -> {
                return when (type2) {
                    CHAR -> CHAR
                    UCHAR -> UCHAR
                    SHORT -> SHORT
                    USHORT -> USHORT
                    INT -> INT
                    UINT -> UINT
                    LONG -> LONG
                    ULONG -> ULONG
                    FLOAT -> FLOAT
                    DOUBLE -> DOUBLE
                    is CEnumType -> INT
                    else -> return null
                }
            }
            else -> return null
        }
    }
}

sealed class AnyCInteger: CPrimitive()

data object VOID: CPrimitive() {
    override fun toString(): String = "void"
    override fun size(): Int = BYTE_SIZE
}

data object BOOL: CPrimitive() {
    override fun toString(): String = "_Bool"
    override fun size(): Int = BYTE_SIZE
}