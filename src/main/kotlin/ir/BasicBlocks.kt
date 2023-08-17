package ir

import ir.iterator.BasicBlocksIterator
import ir.iterator.BfsTraversalIterator
import ir.iterator.PostorderIterator
import ir.iterator.PreorderIterator
import ir.utils.DefUseInfo
import ir.utils.LiveIntervals

class BasicBlocks private constructor(private val blocks: MutableList<BasicBlock>) {
    fun findBlock(label: Label): BasicBlock {
        return blocks.find { it.index() == label.index() }
            ?: throw IllegalArgumentException("Cannot find correspond block: $label")
    }

    fun begin(): BasicBlock {
        return blocks[0]
    }

    fun putBlock(block: BasicBlock) {
        blocks.add(block)
    }
    
    fun preorder(): BasicBlocksIterator {
        return PreorderIterator(blocks[0], blocks.size)
    }

    fun postorder(): BasicBlocksIterator {
        return PostorderIterator(blocks[0], blocks.size)
    }

    fun bfsTraversal(): BfsTraversalIterator {
        return BfsTraversalIterator(blocks[0], blocks.size)
    }

    fun dominatorTree(): DominatorTree {
        return DominatorTree.evaluate(this)
    }

    fun defUseInfo(): DefUseInfo {
        return DefUseInfo.create(this)
    }

    operator fun iterator(): Iterator<BasicBlock> {
        return blocks.iterator()
    }

    companion object {
        fun create(): BasicBlocks {
            return BasicBlocks(arrayListOf())
        }
    }
}