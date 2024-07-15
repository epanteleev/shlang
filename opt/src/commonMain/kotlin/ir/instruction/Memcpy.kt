package ir.instruction

import common.assertion
import ir.value.Value
import ir.value.UnsignedIntegerConstant
import ir.types.Type
import ir.types.PointerType
import ir.instruction.utils.IRInstructionVisitor
import ir.module.block.Block


class Memcpy private constructor(id: Identity, owner: Block, dst: Value, src: Value, length: UnsignedIntegerConstant):
    Instruction(id, owner, arrayOf(dst, src, length)) {
    override fun <T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun dump(): String {
        return "$NAME ${destination().type()} ${destination()}, ${destination().type()} ${source()}, ${length().type()} ${length()}"
    }

    fun destination(): Value {
        assertion(operands.size == 3) {
            "size should be 2 in $this instruction"
        }

        return operands[0]
    }

    fun source(): Value {
        assertion(operands.size == 3) {
            "size should be 2 in $this instruction"
        }

        return operands[1]
    }

    fun length(): UnsignedIntegerConstant {
        assertion(operands.size == 3) {
            "size should be 2 in $this instruction"
        }

        return operands[2] as UnsignedIntegerConstant
    }

    companion object {
        const val NAME = "memcpy"

        fun make(id: Identity, owner: Block, dst: Value, src: Value, length: UnsignedIntegerConstant): Memcpy {
            require(isAppropriateTypes(dst.type(), src.type())) {
                "inconsistent types: dst=$dst:${dst.type()}, src=$src:${src.type()}"
            }

            return registerUser(Memcpy(id, owner, dst, src, length), dst, src)
        }

        private fun isAppropriateTypes(dstType: Type, srcType: Type): Boolean {
            return dstType == srcType && dstType is PointerType
        }

        fun typeCheck(memcpy: Memcpy): Boolean {
            return isAppropriateTypes(memcpy.destination().type(), memcpy.source().type())
        }
    }
}