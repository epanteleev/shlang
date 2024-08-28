package ir.pass.transform.auxiliary

import ir.module.*
import ir.instruction.*
import ir.module.block.Block
import ir.pass.isLocalVariable
import ir.types.PrimitiveType


class RemoveDeadMemoryInstructions private constructor(private val cfg: FunctionData) {
    private fun removeMemoryInstructions(bb: Block) {
        fun filter(instruction: Instruction): Boolean {
            return when (instruction) {
                is Alloc -> instruction.allocatedType is PrimitiveType && instruction.isLocalVariable()
                is Store -> instruction.isLocalVariable()
                is Load  -> instruction.isLocalVariable()
                else -> false
            }
        }

        bb.removeIf { filter(it) }
    }

    fun pass() {
        for (bb in cfg) {
            removeMemoryInstructions(bb)
        }
    }

    companion object {
        fun run(module: Module): Module {
            module.functions.forEach { fnData ->
                RemoveDeadMemoryInstructions(fnData).pass()
            }

            return module
        }
    }
}