package ir.global

import ir.types.ArrayType
import ir.types.NonTrivialType
import ir.types.Type


abstract class GlobalConstant(protected open val name: String): GlobalSymbol {
    override fun dump(): String {
        return "@$name = constant ${type()} ${data()}"
    }

    override fun toString(): String = "@$name"

    override fun name(): String {
        return name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GlobalConstant

        return name == other.name
    }

    abstract fun contentType(): NonTrivialType

    /*** Returns the internal representation of data. */
    abstract fun data(): String

    abstract fun content(): String

    companion object {
        fun<T: Number> of(name: String, kind: Type, value: T): GlobalConstant {
            return when (kind) {
                Type.I8  -> I8ConstantValue(name, value.toByte())
                Type.U8  -> U8ConstantValue(name, value.toByte().toUByte())
                Type.I16 -> I16ConstantValue(name, value.toShort())
                Type.U16 -> U16ConstantValue(name, value.toShort().toUShort())
                Type.I32 -> I32ConstantValue(name, value.toInt())
                Type.U32 -> U32ConstantValue(name, value.toInt().toUInt())
                Type.I64 -> I64ConstantValue(name, value.toLong())
                Type.U64 -> U64ConstantValue(name, value.toLong().toULong())
                Type.F32 -> F32ConstantValue(name, value.toFloat())
                Type.F64 -> F64ConstantValue(name, value.toDouble())
                else -> throw RuntimeException("Cannot create constant: kind=$kind, value=$value")
            }
        }
    }
}


class U8ConstantValue(override val name: String, val u8: UByte): GlobalConstant(name) {
    override fun data(): String {
        return u8.toString()
    }
    override fun type(): NonTrivialType = Type.U8

    override fun content(): String = data()

    override fun contentType(): NonTrivialType = Type.U8
}

class I8ConstantValue(override val name: String, val i8: Byte): GlobalConstant(name) {
    override fun data(): String {
        return i8.toString()
    }

    override fun type(): NonTrivialType = Type.I8

    override fun content(): String = data()

    override fun contentType(): NonTrivialType = Type.I8
}

class U16ConstantValue(override val name: String, val u16: UShort): GlobalConstant(name) {
    override fun data(): String {
        return u16.toString()
    }

    override fun type(): NonTrivialType = Type.U16

    override fun content(): String = data()

    override fun contentType(): NonTrivialType = Type.U16
}

class I16ConstantValue(override val name: String, val i16: Short): GlobalConstant(name) {

    override fun data(): String {
        return i16.toString()
    }

    override fun type(): NonTrivialType = Type.I16

    override fun content(): String = data()

    override fun contentType(): NonTrivialType = Type.I16
}

class U32ConstantValue(override val name: String, val u32: UInt): GlobalConstant(name) {
    override fun data(): String {
        return u32.toString()
    }

    override fun type(): NonTrivialType = Type.U32

    override fun content(): String = data()

    override fun contentType(): NonTrivialType = Type.U32
}

class I32ConstantValue(override val name: String, val i32: Int): GlobalConstant(name) {
    override fun data(): String {
        return i32.toString()
    }

    override fun type(): NonTrivialType = Type.I32

    override fun content(): String = data()

    override fun contentType(): NonTrivialType = Type.I32
}

class U64ConstantValue(override val name: String, val u64: ULong): GlobalConstant(name) {
    override fun data(): String {
        return u64.toString()
    }

    override fun type(): NonTrivialType = Type.U64

    override fun content(): String = data()

    override fun contentType(): NonTrivialType = Type.U64
}

class I64ConstantValue(override val name: String, val i64: Long): GlobalConstant(name) {
    override fun data(): String {
        return i64.toString()
    }

    override fun type(): NonTrivialType = Type.I64

    override fun content(): String = data()

    override fun contentType(): NonTrivialType = Type.I64
}

class F32ConstantValue(override val name: String, val f32: Float): GlobalConstant(name) {
    override fun data(): String {
        return f32.toBits().toString()
    }

    override fun type(): NonTrivialType = Type.F32

    override fun content(): String = f32.toString()

    override fun contentType(): NonTrivialType = Type.F32
}

class F64ConstantValue(override val name: String, val f64: Double): GlobalConstant(name) {
    override fun data(): String {
        return f64.toBits().toString()
    }

    override fun type(): NonTrivialType = Type.F64

    override fun content(): String = f64.toString()

    override fun contentType(): NonTrivialType = Type.F64
}

class StringLiteralConstant(override val name: String, val tp: ArrayType, val string: String?): GlobalConstant(name) {
    override fun data(): String {
        return "\"$string\""
    }

    override fun type(): NonTrivialType = Type.Ptr

    override fun content(): String = data()

    override fun contentType(): NonTrivialType = Type.Ptr
}