package ir.instruction

import ir.Value
import ir.instruction.utils.Visitor
import ir.module.block.Block

class ReturnVoid private constructor(): Return(arrayOf()) {
    override fun dump(): String {
        return "ret void"
    }

    override fun copy(usages: List<Value>, newTargets: Array<Block>): ReturnVoid {
        return this
    }

    override fun copy(newUsages: List<Value>): ReturnVoid {
        return this
    }

    override fun visit(visitor: Visitor) {
        visitor.visit(this)
    }

    companion object {
        private val ret = ReturnVoid()

        fun make(): ReturnVoid {
            return ret
        }
    }
}