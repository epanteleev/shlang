package ir.instruction

import ir.Value
import ir.instruction.utils.IRInstructionVisitor
import ir.module.block.Block
import ir.types.IntegerType
import ir.types.NonTrivialType
import ir.types.PointerType


class Pointer2Int private constructor(name: String, owner: Block, toType: IntegerType, value: Value):
    ValueInstruction(name, owner, toType, arrayOf(value)) {
    override fun dump(): String {
        return "%$id = $NAME ${value().type()} ${value()} to ${type()}"
    }

    fun value(): Value {
        assert(operands.size == 1) {
            "size should be 1 in $this instruction"
        }

        return operands[0]
    }

    override fun type(): IntegerType {
        return tp as IntegerType
    }

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "ptr2int"

        fun make(name: String, owner: Block, toType: IntegerType, value: Value): Pointer2Int {
            val valueType = value.type()
            require(isAppropriateType(valueType)) {
                "inconsistent types in $name: ty=$toType, value=$value:$valueType"
            }

            return registerUser(Pointer2Int(name, owner, toType, value), value)
        }

        private fun isAppropriateType(valueType: NonTrivialType): Boolean {
            return valueType is PointerType
        }

        fun typeCheck(ptr2int: Pointer2Int): Boolean {
            return isAppropriateType(ptr2int.value().type())
        }
    }
}