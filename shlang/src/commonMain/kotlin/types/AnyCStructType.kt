package types

import ir.Definitions
import ir.Definitions.QWORD_SIZE
import typedesc.TypeDesc


sealed class AnyCStructType(open val name: String, protected val fields: List<Member>): CAggregateType() {
    override fun toString(): String = name

    abstract fun fieldByIndexOrNull(name: String): FieldDesc?

    fun fieldByIndex(name: String): FieldDesc {
        return fieldByIndexOrNull(name) ?:
            throw RuntimeException("Cannon find field by name: name=$name, { name=$name, $fields }")
    }

    fun fieldByIndexOrNull(index: Int): TypeDesc? {
        if (index < 0 || index >= fields.size) {
            return null
        }

        return fields[index].typeDesc()
    }

    fun fieldByIndex(index: Int): TypeDesc {
        return fieldByIndexOrNull(index) ?:
            throw RuntimeException("Cannon find field by index: index=$index, { name=$name, $fields }")
    }

    fun members(): Collection<Member> {
        return fields
    }

    fun isSmall(): Boolean {
        return size() <= QWORD_SIZE * 2
    }

    abstract fun offset(index: Int): Int

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnyCStructType) return false

        if (name != other.name) return false
        if (fields != other.fields) return false

        return true
    }

    final override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + fields.hashCode()
        return result
    }
}

class CStructType(override val name: String, fields: List<Member>): AnyCStructType(name, fields) {
    private val alignments = alignments()
    private val maxAlignment by lazy { alignments.maxOrNull() ?: 1 }

    override fun alignmentOf(): Int = maxAlignment

    override fun fieldByIndexOrNull(name: String): FieldDesc? {
        var offset = 0
        for ((idx, field) in fields.withIndex()) {
            when (field) {
                is FieldMember -> {
                    if (field.name == name) {
                        return FieldDesc(name, idx + offset, field)
                    }
                }
                is AnonMember -> when (val cType = field.cType()) {
                    is CUnionType -> {
                        val i = cType.fieldByIndexOrNull(name)
                        if (i != null) {
                            return FieldDesc(name, idx + offset, field)
                        }
                    }
                    is CStructType -> {
                        val fieldDesc = cType.fieldByIndexOrNull(name)
                        if (fieldDesc != null) {
                            return FieldDesc(name, idx + fieldDesc.index + offset, field)
                        }
                        offset += cType.members().size
                    }
                }
            }
        }
        return null
    }

    private fun alignments(): IntArray {
        var current = 0
        val result = IntArray(fields.size)
        for (i in fields.indices) {
            val field = fields[i]
            val alignment = field.cType().alignmentOf()
            current = Definitions.alignTo(current + field.cType().size(), alignment)
            result[i] = alignment
        }
        return result
    }

    override fun size(): Int {
        if (fields.isEmpty()) {
            return 1
        }
        var offset = 0
        for (idx in fields.indices) {
            offset = Definitions.alignTo(offset + fields[idx].size(), alignments[idx])
        }
        return Definitions.alignTo(offset, alignmentOf())
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
    override fun fieldByIndexOrNull(name: String): FieldDesc? {
        if (fields.isEmpty()) {
            return null
        }

        for (field in fields) {
            when (field) {
                is FieldMember -> {
                    if (field.name == name) {
                        return FieldDesc(name, 0, field)
                    }
                }
                is AnonMember -> {
                    val fieldDescOrNull = findInsideAnonMember(field, name)
                    if (fieldDescOrNull != null) {
                        return fieldDescOrNull
                    }
                }
            }
        }
        return null
    }

    private fun findInsideAnonMember(field: AnonMember, name: String): FieldDesc? = when (val cType = field.cType()) {
        is CUnionType -> {
            val fieldDesc = cType.fieldByIndexOrNull(name)
            if (fieldDesc != null) {
                FieldDesc(name, 0, field)
            } else {
                null
            }
        }
        is CStructType -> cType.fieldByIndexOrNull(name)
    }

    override fun size(): Int {
        if (fields.isEmpty()) {
            return 1
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

    override fun alignmentOf(): Int {
        return fields.maxOf { it.cType().size() }
    }
}