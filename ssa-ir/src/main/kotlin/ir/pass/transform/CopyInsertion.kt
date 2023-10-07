package ir.pass.transform

import ir.*
import ir.block.Block

class CopyInsertion private constructor(private val cfg: BasicBlocks) {
    private fun hasCriticalEdge(bb: Block, predecessor: Block): Boolean {
        return predecessor.successors().size > 1 && bb.predecessors().size > 1
    }

    private fun modifyPhis(bb: Block, phi: Phi) {
        val newValues = hashMapOf<Value, Value>()
        for ((incoming, operand) in phi.zip()) {
            assert(!hasCriticalEdge(bb, incoming)) {
                "Flow graph has critical edge from $incoming to $bb"
            }

            val copy = incoming.insert(incoming.last()) {
                it.copy(operand)
            }

            newValues[operand] = copy
        }

        phi.updateUsagesInPhi { v, _ -> newValues[v]!! }
    }

    fun pass() {
        for (bb in cfg) {
            bb.phis().forEach { modifyPhis(bb, it) }
        }
    }

    companion object {
        fun run(module: Module): Module {
            module.functions.forEach { CopyInsertion(it.blocks).pass() }
            return module
        }
    }
}