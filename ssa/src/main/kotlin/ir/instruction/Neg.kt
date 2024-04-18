package ir.instruction

import ir.Value
import ir.types.Type
import ir.instruction.utils.IRInstructionVisitor
import ir.types.ArithmeticType


class Neg private constructor(name: String, tp: ArithmeticType, value: Value):
    ArithmeticUnary(name, tp, value) {
    override fun dump(): String {
        return "%$identifier = $NAME $tp ${operand()}"
    }

    override fun type(): ArithmeticType {
        return tp as ArithmeticType
    }

    fun operand(): Value {
        assert(operands.size == 1) {
            "size should be 2 in $this instruction"
        }

        return operands[0]
    }

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "neg"

        fun make(name: String, type: ArithmeticType, value: Value): Neg {
            val valueType = value.type()
            require(isAppropriateTypes(type, valueType)) {
                "should be the same type, but type=$type, value=$value:$valueType"
            }

            return registerUser(Neg(name, type, value), value)
        }

        private fun isAppropriateTypes(tp: ArithmeticType, argType: Type): Boolean {
            return tp == argType
        }

        fun typeCheck(unary: Neg): Boolean {
            return isAppropriateTypes(unary.type(), unary.operand().type())
        }
    }
}