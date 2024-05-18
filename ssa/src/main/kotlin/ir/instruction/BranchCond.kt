package ir.instruction

import ir.Value
import ir.types.*
import ir.module.block.Block
import ir.instruction.utils.IRInstructionVisitor


class BranchCond private constructor(owner: Block, value: Value, onTrue: Block, onFalse: Block) :
    TerminateInstruction(owner, arrayOf(value), arrayOf(onTrue, onFalse)) {
    override fun dump(): String {
        return "br u1 ${condition()} label %${onTrue()}, label %${onFalse()} "
    }

    fun condition(): Value {
        assert(operands.size == 1) {
            "size should be 1 in $this instruction"
        }

        return operands[0]
    }

    fun onTrue(): Block {
        assert(targets.size == 2) {
            "size should be 2 in $this instruction"
        }

        return targets[0]
    }

    fun onFalse(): Block {
        assert(targets.size == 2) {
            "size should be 2 in $this instruction"
        }

        return targets[1]
    }

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        fun make(owner: Block, value: Value, onTrue: Block, onFalse: Block): BranchCond {
            val valueType = value.type()
            require(isAppropriateType(valueType)) {
                "should be boolean type, but value=$value:$valueType"
            }

            return registerUser(BranchCond(owner, value, onTrue, onFalse), value)
        }

        private fun isAppropriateType(valueType: Type): Boolean {
            return valueType is BooleanType
        }

        fun typeCheck(branchCond: BranchCond): Boolean {
            return isAppropriateType(branchCond.condition().type())
        }
    }
}