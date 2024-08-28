package ir.pass.transform.auxiliary

import ir.global.AggregateConstant
import ir.global.FunctionSymbol
import ir.global.GlobalConstant
import ir.global.StringLiteralConstant
import ir.instruction.Instruction
import ir.module.BasicBlocks
import ir.module.FunctionData
import ir.module.Module
import ir.module.block.Block


internal class ConstantLoading private constructor(private val cfg: FunctionData) {
    private fun pass() {
        fun closure(bb: Block, inst: Instruction): Instruction {
            var inserted: Instruction? = null
            for ((i, use) in inst.operands().withIndex()) {
                if (use is AggregateConstant || use is FunctionSymbol) {
                    val lea = bb.insertBefore(inst) { it.lea(use) }
                    inst.update(i, lea)
                    inserted = lea
                } else if (use is GlobalConstant) {
                    val lea = bb.insertBefore(inst) { it.copy(use) }
                    inst.update(i, lea)
                    inserted = lea
                }
            }
            return inserted?: inst
        }

        for (bb in cfg) {
            bb.transform { inst -> closure(bb, inst) }
        }
    }

    companion object {
        fun run(module: Module): Module {
            for (fn in module.functions) {
                ConstantLoading(fn).pass()
            }
            return module
        }
    }
}