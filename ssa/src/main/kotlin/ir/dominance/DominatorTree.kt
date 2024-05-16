package ir.dominance

import ir.module.BasicBlocks
import ir.module.block.Block
import ir.module.block.Label


class DominatorTree(private val idomMap: Map<Block, Block>) {
    private val cachedDominators = hashMapOf<Label, List<Label>>()

    private fun calculateDominators(target: Label): List<Label> {
        val dom = arrayListOf<Label>()
        var current: Label? = target
        while (current != null) {
            dom.add(current)
            current = idomMap[current]
        }

        return dom
    }

    fun dominates(dominator: Label, target: Label): Boolean {
        var current: Label? = target

        while (current != null) {
            if (current == dominator) {
                return true
            }

            current = idomMap[current]
        }

        return false
    }

    fun dominators(target: Label): List<Label> {
        val saved = cachedDominators[target]
        if (saved != null) {
            return saved
        }

        val dom = calculateDominators(target)
        cachedDominators[target] = dom
        return dom
    }

    fun frontiers(): Map<Block, List<Block>> {
        val dominanceFrontiers = hashMapOf<Block, MutableList<Block>>()

        idomMap.forEach { (bb, idom) ->
            val predecessors = bb.predecessors()
            if (predecessors.size < 2) {
                return@forEach
            }

            for (p in predecessors) {
                var runner: Block = p
                while (runner != idom) {
                    (dominanceFrontiers.getOrPut(runner) { arrayListOf() }).add(bb)
                    runner = idomMap[runner]!!
                }
            }
        }

        return dominanceFrontiers
    }

    operator fun iterator(): Iterator<Map.Entry<Block, Block>> {
        return idomMap.iterator()
    }

    companion object {
        fun evaluate(basicBlocks: BasicBlocks): DominatorTree {
            return DominatorTreeCalculate.evaluate(basicBlocks)
        }
    }
}