package ir.instruction

import ir.Value
import ir.types.*
import ir.instruction.utils.Visitor


class Alloc private constructor(name: String, val allocatedType: Type):
    ValueInstruction(name, allocatedType.ptr(), arrayOf()) {
    override fun dump(): String {
        return "%$identifier = $NAME $allocatedType"
    }

    override fun type(): PointerType {
        return tp as PointerType
    }

    override fun copy(newUsages: List<Value>): Alloc {
        return make(identifier, allocatedType)
    }

    override fun visit(visitor: Visitor) {
        visitor.visit(this)
    }

    companion object {
        const val NAME = "alloc"

        fun make(name: String, ty: Type): Alloc {
            require(isAppropriateType(ty)) {
                "should not be $ty"
            }

            return Alloc(name, ty)
        }

        private fun isAppropriateType(ty: Type): Boolean {
            return ty !is VoidType && ty !is UndefinedType
        }
    }
}