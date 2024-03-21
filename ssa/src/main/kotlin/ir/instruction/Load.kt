package ir.instruction

import ir.Value
import ir.types.*
import ir.instruction.utils.Visitor


class Load private constructor(name: String, private val loadedType: PrimitiveType, ptr: Value):
    ValueInstruction(name, Type.Ptr, arrayOf(ptr)) {
    override fun dump(): String {
        return "%$identifier = $NAME $loadedType ${operand()}"
    }

    fun operand(): Value {
        assert(operands.size == 1) {
            "size should be 2 in $this instruction"
        }

        return operands[0]
    }

    override fun type(): PrimitiveType {
        return loadedType
    }

    override fun<T> visit(visitor: Visitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "load"

        fun make(name: String, loadedType: PrimitiveType, operand: Value): Load {
            val type = operand.type()
            require(isAppropriateTypes(type)) {
                "should be pointer to primitive type, but operand.type=$type"
            }

            return registerUser(Load(name, loadedType, operand), operand)
        }

        private fun isAppropriateTypes(tp: Type): Boolean {
            return tp is PointerType
        }

        fun isCorrect(load: Load): Boolean {
            return isAppropriateTypes(load.operand().type())
        }
    }
}