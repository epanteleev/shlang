package ir.instruction.lir

import common.assertion
import ir.Definitions.QWORD_SIZE
import ir.value.Value
import ir.types.*
import ir.module.block.Block
import ir.instruction.Identity
import ir.instruction.InstBuilder
import ir.instruction.Instruction
import ir.instruction.utils.IRInstructionVisitor
import ir.value.ArgumentValue


class StoreOnStack private constructor(id: Identity, owner: Block, destination: Value, index: Value, source: Value):
    Instruction(id, owner, arrayOf(destination, index, source)) {

    override fun dump(): String {
        val fromValue = source()
        return "$NAME ${fromValue.type()} ${destination()}: ${index().type()} ${index()} $fromValue"
    }

    fun source(): Value {
        assertion(operands.size == 3) {
            "size should be 2 in $this instruction"
        }

        return operands[2]
    }

    fun destination(): Value {
        assertion(operands.size == 3) {
            "size should be 2 in $this instruction"
        }

        return operands[0]
    }

    fun index(): Value {
        assertion(operands.size == 3) {
            "size should be 2 in $this instruction"
        }

        return operands[1]
    }

    override fun<T> accept(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "movst"

        fun store(dst: Value, index: Value, src: Value): InstBuilder<StoreOnStack> = { id: Identity, owner: Block ->
            make(id, owner, dst, index, src)
        }

        private fun make(id: Identity, owner: Block, dst: Value, index: Value, src: Value): StoreOnStack {
            require(isAppropriateType(dst, index, src)) {
                "inconsistent types: toValue=$dst:${dst.type()}, base=$src:${src.type()}"
            }

            return registerUser(StoreOnStack(id, owner, dst, index, src), dst, index, src)
        }

        fun typeCheck(copy: StoreOnStack): Boolean {
            return isAppropriateType(copy.destination(), copy.index(), copy.source())
        }

        private fun isAppropriateType(toValue: Value, index: Value, fromValue: Value): Boolean {
            if (toValue is Generate || toValue is ArgumentValue) {
                val idxType = index.type()
                return idxType is ArithmeticType && fromValue.type() is PrimitiveType && idxType.sizeOf() == QWORD_SIZE
            }

            return false
        }
    }
}