package ir.instruction

import common.assertion
import ir.module.block.Block
import ir.instruction.utils.IRInstructionVisitor


class Branch private constructor(id: Identity, owner: Block, target: Block):
    TerminateInstruction(id, owner, arrayOf(), arrayOf(target)) {
    override fun dump(): String {
        return "br label %${target()}"
    }

    fun target(): Block {
        assertion(targets.size == 1) {
            "size should be 1 in $this instruction"
        }

        return targets[0]
    }

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        fun make(id: Identity, owner: Block, target: Block): Branch {
            return Branch(id, owner, target)
        }
    }
}