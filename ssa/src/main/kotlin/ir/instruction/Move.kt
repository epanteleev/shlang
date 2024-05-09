package ir.instruction

import ir.Constant
import ir.Value
import ir.instruction.utils.IRInstructionVisitor
import ir.types.Type


class Move private constructor(destination: Value, source: Value, index: Value):
    Instruction(arrayOf(destination, source, index)) {

    override fun dump(): String {
        val fromValue = source()
        return "$NAME ${fromValue.type()} ${destination()}:${index()} $fromValue"
    }

    fun source(): Value {
        assert(operands.size == 3) {
            "size should be 2 in $this instruction"
        }

        return operands[1]
    }

    fun destination(): Value {
        assert(operands.size == 3) {
            "size should be 2 in $this instruction"
        }

        return operands[0]
    }

    fun index(): Value {
        assert(operands.size == 3) {
            "size should be 2 in $this instruction"
        }

        return operands[2]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Move
        return source() == other.source() && destination() == other.destination()
    }

    override fun hashCode(): Int {
        return source().type().hashCode() xor destination().type().hashCode()
    }

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "move"

        fun make(dst: Value, src: Value): Move {
            require(isAppropriateType(dst, src)) {
                "inconsistent types: toValue=$dst:${dst.type()}, fromValue=$src:${src.type()}"
            }

            return registerUser(Move(dst, src, Value.UNDEF), dst, src) //TODO: fix index
        }

        fun make(dst: Value, src: Value, index: Value): Move {
            require(isAppropriateType(dst, src)) {
                "inconsistent types: toValue=$dst:${dst.type()}, base=$src:${src.type()}"
            }

            return registerUser(Move(dst, src, index), dst, src, index)
        }

        fun typeCheck(copy: Move): Boolean {
            return isAppropriateType(copy.destination(), copy.source())
        }

        private fun isAppropriateType(toValue: Value, fromValue: Value): Boolean {
            return true
        }
    }
}