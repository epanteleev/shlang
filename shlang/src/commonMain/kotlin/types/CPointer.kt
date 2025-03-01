package types

import typedesc.*
import ir.Definitions.POINTER_SIZE


class CPointer(private val type: CType, private val properties: Set<TypeQualifier> = setOf()) : CPrimitive() {
    override fun size(): Int = POINTER_SIZE

    fun dereference(typeHolder: TypeHolder): CType = when (type) {
        is CFunctionType          -> type.functionType.asType()
        is CUncompletedStructType -> typeHolder.getStructType(type.name)
        is CUncompletedUnionType  -> typeHolder.getUnionType(type.name)
        is CUncompletedEnumType   -> typeHolder.getEnumType(type.name)
        else -> type.asType()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CPointer) return false

        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    override fun toString(): String = buildString {
        properties.forEach { append(it) }
        append(type)
        append("*")
    }
}