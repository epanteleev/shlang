package ir.instruction

import ir.Value
import ir.types.*
import ir.instruction.utils.IRInstructionVisitor
import ir.module.block.Block


class Int2Pointer private constructor(name: String, owner: Block, value: Value):
    ValueInstruction(name, owner, Type.Ptr, arrayOf(value)) {
    override fun dump(): String {
        return "%$id = $NAME ${value().type()} ${value()} to ${type()}"
    }

    fun value(): Value {
        assert(operands.size == 1) {
            "size should be 1 in $this instruction"
        }

        return operands[0]
    }

    override fun type(): PointerType {
        return tp as PointerType
    }

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "int2ptr"

        fun make(name: String, owner: Block, value: Value): Int2Pointer {
            val valueType = value.type()
            require(isAppropriateType(valueType)) {
                "inconsistent types in $name: ty=${Type.Ptr}, value=$value:$valueType"
            }

            return registerUser(Int2Pointer(name, owner, value), value)
        }

        private fun isAppropriateType(valueType: NonTrivialType): Boolean {
            return valueType is IntegerType
        }

        fun typeCheck(int2ptr: Int2Pointer): Boolean {
            return isAppropriateType(int2ptr.value().type())
        }
    }
}