package ir.instruction

import ir.Value
import ir.types.*
import ir.instruction.utils.IRInstructionVisitor
import ir.module.block.Block


class Flag2Int private constructor(name: String, owner: Block, toType: IntegerType, value: Value):
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
        const val NAME = "flag2int"

        fun make(name: String, owner: Block, toType: IntegerType, value: Value): Flag2Int {
            require(isAppropriateType(value.type())) {
                "inconsistent types in $name: type=${value.type()}"
            }

            return registerUser(Flag2Int(name, owner, toType, value), value)
        }

        fun isAppropriateType(valueType: Type): Boolean {
            return valueType is BooleanType
        }

        fun typeCheck(bitcast: Flag2Int): Boolean {
            return isAppropriateType(bitcast.value().type())
        }
    }
}