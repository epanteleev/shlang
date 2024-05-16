package ir.dominance

import ir.module.BasicBlocks
import ir.module.block.Block


internal object PostDominatorTreeCalculate : DominatorCalculate {
    override fun calculateIncoming(postorder: List<Block>, blockToIndex: Map<Block, Int>): Map<Int, List<Int>> {
        val successors = hashMapOf<Int, List<Int>>()

        for (bb in postorder) {
            val blockSuccessors = bb.successors()
            if (blockSuccessors.isEmpty()) {
                continue
            }

            successors[blockToIndex[bb]!!] = blockSuccessors.map { blockToIndex[it]!! }
        }

        return successors
    }

    override fun blockOrdering(basicBlocks: BasicBlocks): List<Block> {
        return basicBlocks.backwardPostorder().order()
    }

    fun evaluate(basicBlocks: BasicBlocks): PostDominatorTree {
        return PostDominatorTree(calculate(basicBlocks))
    }
}