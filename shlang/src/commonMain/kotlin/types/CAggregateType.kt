package types

import ir.Definitions
import typedesc.TypeDesc


sealed class CAggregateType: CType() {
    fun hasFloatOnly(lo: Int, hi: Int): Boolean {
        return hasFloat(this, lo, hi, 0)
    }

    private fun hasFloat(ty: CType, lo: Int, hi: Int, offset: Int): Boolean {
        if (ty is AnyCStructType) {
            for ((idx, field) in ty.fields().withIndex()) {
                if (!hasFloat(field.cType(), lo, hi, offset + ty.offset(idx))) { //TODO inefficient
                    return false
                }
            }
            return true

        } else if (ty is CArrayType) {
            for (i in 0 until ty.dimension.toInt()) {
                if (!hasFloat(ty.type.cType(), lo, hi, offset + i * ty.type.cType().size())) {
                    return false
                }
            }
            return true
        }

        return offset < lo || hi <= offset || ty is FLOAT || ty is DOUBLE
    }
}

sealed class AnyCStructType(open val name: String, protected val fields: List<Member>): CAggregateType() {
    override fun typename(): String = name

    abstract fun fieldIndex(name: String): Int?

    fun fieldIndex(index: Int): TypeDesc? {
        if (index < 0 || index >= fields.size) {
            return null
        }
        return fields[index].typeDesc()
    }

    fun field(name: String): CType? {
        val field = fields.find { it is FieldMember && it.name == name }
        return field?.cType()
    }

    fun fields(): Collection<Member> {
        return fields
    }

    abstract fun offset(index: Int): Int
    abstract fun maxAlignment(): Int
}

class CStructType(override val name: String, fields: List<Member>): AnyCStructType(name, fields) {
    private val alignments = alignments()
    private var maxAlignment = Int.MIN_VALUE

    override fun fieldIndex(name: String): Int? {
        var offset = 0
        for ((idx, field) in fields.withIndex()) {
            when (field) {
                is FieldMember -> {
                    if (field.name == name) {
                        return idx + offset
                    }
                }
                is AnonMember -> when (val cType = field.cType()) {
                    is CUnionType -> {
                        val i = cType.fieldIndex(name)
                        if (i != null) {
                            return idx + offset
                        }
                        offset += cType.fields().size
                    }
                    is CStructType -> {
                        val i = cType.fieldIndex(name)
                        if (i != null) {
                            return idx + i + offset
                        }
                        offset += cType.fields().size
                    }
                }
            }
        }
        return null
    }

    override fun maxAlignment(): Int {
        if (maxAlignment == Int.MIN_VALUE) {
            maxAlignment = alignments.maxOrNull() ?: 1
        }
        return maxAlignment
    }

    private fun alignments(): IntArray {
        var current = 0
        var alignment = 1
        val result = IntArray(fields.size)
        for (i in fields.indices) {
            val field = fields[i]
            alignment = align(alignment, field.cType())
            current = Definitions.alignTo(current + field.size(), alignment)
            result[i] = alignment
        }
        return result
    }

    override fun size(): Int {
        if (fields.isEmpty()) {
            return 0
        }
        var offset = 0
        for (idx in fields.indices) {
            offset = Definitions.alignTo(offset + fields[idx].typeDesc().size(), alignments[idx])
        }
        return offset
    }

    private fun align(alignment: Int, field: CType): Int = when (field) {
        is AnyCStructType -> maxOf(alignment, field.maxAlignment())
        is CArrayType    -> maxOf(alignment, field.maxAlignment())
        else -> maxOf(alignment, field.size())
    }

    override fun offset(index: Int): Int {
        var current = 0
        for (i in 0 until index) {
            current = Definitions.alignTo(current + fields[i].size(), alignments[i])
        }
        return Definitions.alignTo(current, alignments[index])
    }

    override fun toString(): String = buildString {
        append("struct $name")
        append(" {")
        fields.joinTo(this, separator = "") { field -> field.toString() }
        append("}")
    }
}

class CUnionType(override val name: String, fields: List<Member>): AnyCStructType(name, fields) {
    override fun fieldIndex(name: String): Int? {
        if (fields.isEmpty()) {
            return null
        }
        return 0
    }

    override fun size(): Int {
        if (fields.isEmpty()) {
            return 0
        }
        return fields.maxOf { it.cType().size() }
    }

    override fun toString(): String = buildString {
        append("union $name")
        append(" {")
        fields.joinTo(this, separator = "") { field -> field.toString() }
        append("}")
    }

    override fun offset(index: Int): Int {
        return 0
    }

    override fun maxAlignment(): Int {
        return fields.maxOf { it.cType().size() }
    }
}

sealed class AnyCArrayType(val type: TypeDesc): CAggregateType() {
    fun element(): TypeDesc = type
}

class CArrayType(type: TypeDesc, val dimension: Long) : AnyCArrayType(type) {
    private var maxAlignment = Int.MIN_VALUE

    override fun typename(): String {
        return toString()
    }

    override fun size(): Int {
        return type.size() * dimension.toInt() //TODO
    }

    override fun toString(): String = buildString {
        append("[$dimension]")
        append(type)
    }

    fun offset(index: Int): Int {
        return index * type.size()
    }

    fun asPointer(): CPointer {
        return CPointer(type.cType())
    }

    fun maxAlignment(): Int {
        if (maxAlignment == Int.MIN_VALUE) {
            val cType = type.cType()
            maxAlignment = when (cType) {
                is CArrayType    -> cType.maxAlignment()
                is AnyCStructType -> cType.maxAlignment()
                else -> cType.size()
            }
        }
        return maxAlignment
    }
}