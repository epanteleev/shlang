package ir.instruction

import common.assertion
import ir.value.Value
import ir.types.Type
import ir.instruction.utils.IRInstructionVisitor
import ir.module.block.Block
import ir.types.ArithmeticType
import ir.types.asType


class Neg private constructor(id: Identity, owner: Block, tp: ArithmeticType, value: Value):
    ArithmeticUnary(id, owner, tp, value) {
    override fun dump(): String {
        return "%${name()} = $NAME $tp ${operand()}"
    }

    override fun type(): ArithmeticType {
        return tp as ArithmeticType
    }

    fun operand(): Value {
        assertion(operands.size == 1) {
            "size should be 2 in $this instruction"
        }

        return operands[0]
    }

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "neg"

        fun neg(value: Value): InstBuilder<Neg> = { id: Identity, owner: Block ->
            make(id, owner, value)
        }

        private fun make(id: Identity, owner: Block, value: Value): Neg {
            val valueType = value.type()
            return registerUser(Neg(id, owner, valueType.asType(), value), value)
        }

        fun typeCheck(unary: Neg): Boolean {
            return true
        }
    }
}