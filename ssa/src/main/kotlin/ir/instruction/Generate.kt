package ir.instruction

import ir.Value
import ir.types.*
import ir.instruction.utils.Visitor


class Generate private constructor(name: String, allocatedType: PrimitiveType):
    ValueInstruction(name, allocatedType, arrayOf()) {
    override fun dump(): String {
        return "%$identifier = $NAME ${type()}"
    }

    override fun type(): PrimitiveType {
        return tp as PrimitiveType
    }

    override fun<T> visit(visitor: Visitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "gen"

        fun make(name: String, ty: PrimitiveType): Generate {
            return Generate(name, ty)
        }

        private fun isAppropriateType(ty: Type): Boolean {
            return ty is PrimitiveType
        }
    }
}