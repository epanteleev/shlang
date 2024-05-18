package ir.instruction

import ir.Value
import ir.module.block.Block

abstract class TerminateInstruction(owner: Block, usages: Array<Value>, val targets: Array<Block>):
    Instruction(owner, usages) {
    fun targets(): Array<Block> {
        return targets
    }

    override fun hashCode(): Int {
        return targets.hashCode()
    }

    fun updateTargets(newTargets: Collection<Block>) {
        for ((i, v) in newTargets.withIndex()) {
            targets[i] = v
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TerminateInstruction

        return targets.contentEquals(other.targets)
    }
}