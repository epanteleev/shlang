package ir.instruction

import ir.Value
import ir.types.*
import ir.instruction.utils.IRInstructionVisitor


class FloatToInt private constructor(name: String, toType: IntegerType, value: Value):
    ValueInstruction(name, toType, arrayOf(value)) {
    override fun dump(): String {
        return "%$identifier = $NAME ${value().type()} ${value()} to ${type()}"
    }

    fun value(): Value {
        assert(operands.size == 1) {
            "size should be 1 in $this instruction"
        }

        return operands[0]
    }

    override fun type(): IntegerType = tp as IntegerType

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "fp2int"

        fun make(name: String, toType: IntegerType, value: Value): FloatToInt {
            val valueType = value.type()
            require(isAppropriateType(valueType)) {
                "inconsistent types in $name: ty=$toType, value=$value:$valueType"
            }

            return registerUser(FloatToInt(name, toType, value), value)
        }

        private fun isAppropriateType(valueType: Type): Boolean {
            return valueType is FloatingPointType
        }

        fun typeCheck(fpext: FloatToInt): Boolean {
            return isAppropriateType(fpext.value().type())
        }
    }
}