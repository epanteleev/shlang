package ir.instruction.lir

import ir.Value
import ir.types.*
import ir.instruction.ValueInstruction
import ir.instruction.utils.IRInstructionVisitor


class LeaStack private constructor(name: String, val loadedType: PrimitiveType, origin: Value, index: Value):
    ValueInstruction(name, Type.Ptr, arrayOf(origin, index)) {

    override fun type(): PrimitiveType {
        return tp as PrimitiveType
    }

    override fun dump(): String {
        return "%$identifier = $NAME $loadedType, $tp ${origin()}, ${index().type()} ${index()}"
    }

    fun origin(): Value {
        assert(operands.size == 2) {
            "size should be 1 in $this instruction"
        }

        return operands[0]
    }

    fun index(): Value {
        assert(operands.size == 2) {
            "size should be 2 in $this instruction"
        }

        return operands[1]
    }

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "leastv"

        fun make(name: String, loadedType: PrimitiveType, origin: Value, index: Value): LeaStack {
            val originType = origin.type()
            require(isAppropriateType(originType, index.type())) {
                "should not be $originType, but origin=$origin:$originType"
            }

            return registerUser(LeaStack(name, loadedType, origin, index), origin)
        }

        fun typeCheck(copy: LeaStack): Boolean {
            return isAppropriateType(copy.origin().type(), copy.index().type())
        }

        private fun isAppropriateType(originType: Type, index: Type): Boolean {
            return originType is AggregateType && index is ArithmeticType
        }
    }
}