package ir.pass.transform

import ir.*
import ir.block.Block
import ir.instruction.Branch
import ir.instruction.BranchCond

class SplitCriticalEdge private constructor(private val cfg: BasicBlocks) {
    private var maxIndex = cfg.maxBlockIndex()

    private fun hasCriticalEdge(bb: Block, predecessor: Block): Boolean {
        return predecessor.successors().size > 1 && bb.predecessors().size > 1
    }

    fun pass() {
        val basicBlocks = cfg.blocks()
        for (bbIdx in 0 until basicBlocks.size) {
            val predecessors = basicBlocks[bbIdx].predecessors()
            for (index in predecessors.indices) {
                if (!hasCriticalEdge(basicBlocks[bbIdx], predecessors[index])) {
                    continue
                }

                insertBasicBlock(basicBlocks[bbIdx], predecessors[index])
            }
        }
    }

    private fun insertBasicBlock(bb: Block, p: Block) {
        maxIndex += 1
        val newBlock = Block.empty(maxIndex).apply {
            branch(bb)
        }

        when (val flow = p.last()) {
            is Branch -> p.updateFlowInstruction(Branch(newBlock))
            is BranchCond -> {
                val newFlowInst = when (bb) {
                    flow.onTrue() -> {
                        BranchCond(flow.condition(), newBlock, flow.onFalse())
                    }
                    flow.onFalse() -> {
                        BranchCond(flow.condition(), flow.onTrue(), newBlock)
                    }
                    else -> {
                        throw RuntimeException("internal error: p=$p, bb=$bb")
                    }
                }
                p.updateFlowInstruction(newFlowInst)
            }
            else -> {
                throw RuntimeException("unsupported terminate instruction: inst=$flow p=$p, bb=$bb")
            }
        }

        p.updateSuccessor(bb, newBlock)
        bb.removePredecessors(p)

        cfg.putBlock(newBlock)
    }

    companion object {
        fun run(module: Module): Module {
            module.functions.forEach { fnData ->
                val cfg = fnData.blocks
                SplitCriticalEdge(cfg).pass()
            }

            return module
        }
    }
}