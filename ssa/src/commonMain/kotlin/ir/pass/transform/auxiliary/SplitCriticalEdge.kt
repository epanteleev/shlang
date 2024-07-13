package ir.pass.transform.auxiliary

import ir.module.Module
import ir.module.BasicBlocks
import ir.module.block.Block


internal class SplitCriticalEdge private constructor(private val cfg: BasicBlocks) {
    private val predecessorMap = hashMapOf<Block, Block>()

    fun pass() {
        val basicBlocks = cfg.blocks()

        for (bbIdx in 0 until basicBlocks.size) {
            val predecessors = basicBlocks[bbIdx].predecessors()
            for (index in predecessors.indices) {
                if (!basicBlocks[bbIdx].hasCriticalEdgeFrom(predecessors[index])) {
                    continue
                }

                insertBasicBlock(basicBlocks[bbIdx], predecessors[index])
            }
        }

        updatePhi(cfg)
    }

    private fun updatePhi(basicBlocks: BasicBlocks) {
        for (bb in basicBlocks) {
            bb.phis { phi ->
                var changed = false
                val validIncoming = phi.incoming().map {
                    val p = predecessorMap[it]
                    if (p != null) {
                        changed = true
                        p
                    } else {
                        it
                    }
                }

                if (!changed) {
                    return@phis
                }

                phi.update(phi.operands().toList(), validIncoming)
            }
        }
    }

    private fun insertBasicBlock(bb: Block, p: Block) {
        val newBlock = cfg.createBlock().apply {
            branch(bb)
        }

        val inst = p.last()
        val targets = inst.targets()
        val newTargets = targets.mapTo(arrayListOf()) {
            if (it == bb) {
                newBlock
            } else {
                it
            }
        }
        inst.updateTargets(newTargets)

        predecessorMap[p] = newBlock
        Block.insertBlock(bb, newBlock, p)
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

fun Block.hasCriticalEdgeFrom(predecessor: Block): Boolean {
    return predecessor.successors().size > 1 && predecessors().size > 1
}