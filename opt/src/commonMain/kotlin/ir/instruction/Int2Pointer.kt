package ir.instruction

import ir.types.*
import ir.value.Value
import common.assertion
import ir.module.block.Block
import ir.instruction.utils.IRInstructionVisitor


class Int2Pointer private constructor(id: Identity, owner: Block, value: Value):
    ValueInstruction(id, owner, arrayOf(value)) {
    override fun dump(): String {
        return "%${name()} = $NAME ${value().type()} ${value()} to ${type()}"
    }

    fun value(): Value {
        assertion(operands.size == 1) {
            "size should be 1 in $this instruction"
        }

        return operands[0]
    }

    override fun type(): PointerType = Type.Ptr

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "int2ptr"

        fun make(id: Identity, owner: Block, value: Value): Int2Pointer {
            val valueType = value.type()
            require(isAppropriateType(valueType)) {
                "inconsistent types in '$id': ty=${Type.Ptr}, value=$value:$valueType"
            }

            return registerUser(Int2Pointer(id, owner, value), value)
        }

        private fun isAppropriateType(valueType: Type): Boolean {
            return valueType is IntegerType
        }

        fun typeCheck(int2ptr: Int2Pointer): Boolean {
            return isAppropriateType(int2ptr.value().type())
        }
    }
}