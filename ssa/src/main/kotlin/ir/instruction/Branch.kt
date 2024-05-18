package ir.instruction

import ir.module.block.Block
import ir.instruction.utils.IRInstructionVisitor


class Branch private constructor(owner: Block, target: Block): TerminateInstruction(owner, arrayOf(), arrayOf(target)) {
    override fun dump(): String {
        return "br label %${target()}"
    }

    fun target(): Block {
        assert(targets.size == 1) {
            "size should be 1 in $this instruction"
        }

        return targets[0]
    }

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        fun make(owner: Block, target: Block): Branch {
            return Branch(owner, target)
        }
    }
}