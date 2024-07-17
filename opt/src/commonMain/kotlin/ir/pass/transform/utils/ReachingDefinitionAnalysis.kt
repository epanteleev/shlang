package ir.pass.transform.utils

import ir.value.*
import ir.types.Type
import ir.instruction.*
import ir.module.block.*
import ir.module.BasicBlocks
import ir.pass.isLocalVariable
import ir.dominance.DominatorTree
import ir.pass.transform.Mem2RegException


//TODO Add test for uninitialized values
//TODO this is not a Reaching Definition Analysis
abstract class AbstractReachingDefinitionAnalysis(protected val dominatorTree: DominatorTree) {
    protected fun rename(bb: Block, oldValue: Value): Value {
        return tryRename(bb, oldValue, oldValue.type())?: oldValue
    }

    fun tryRename(bb: Block, oldValue: Value, expectedType: Type): Value? {
        if (oldValue !is LocalValue) {
            return oldValue
        }

        val newValue = findActualValueOrNull(bb, oldValue)
            ?: return null
        return ReachingDefinitionAnalysis.convertOrSkip(expectedType, newValue)
    }

    protected fun findActualValue(bb: Label, value: Value): Value {
        return findActualValueOrNull(bb, value)
            ?: throw Mem2RegException("cannot find: basicBlock=$bb, value=$value")
    }

    protected fun findActualValueOrNull(bb: Label, value: Value): Value? { //TODO Duplicate code
        for (d in dominatorTree.dominators(bb)) {
            val newV = valueMap()[d]!![value]
            if (newV != null) {
                return newV
            }
        }

        return null
    }

    abstract fun valueMap(): Map<Block, Map<Value, Value>>
}

class ReachingDefinitionAnalysis private constructor(cfg: BasicBlocks, dominatorTree: DominatorTree): AbstractReachingDefinitionAnalysis(dominatorTree) {
    private val bbToMapValues = run {
        val bbToMapValues = hashMapOf<Block, MutableMap<Value, Value>>()
        for (bb in cfg) {
            bbToMapValues[bb] = hashMapOf()
        }

        bbToMapValues
    }

    init {
        for (bb in cfg.preorder()) {
            rewriteValuesSetup(bb)
        }
    }

    override fun valueMap(): Map<Block, Map<Value, Value>> {
        return bbToMapValues
    }

    private fun rewriteValuesSetup(bb: Block) {
        val valueMap = bbToMapValues[bb]!!
        for (instruction in bb) {
            if (instruction.operands().isEmpty()) {
                continue
            }

            if (instruction is Store && instruction.isLocalVariable()) {
                val actual = findActualValueOrNull(bb, instruction.value())
                val pointer = instruction.pointer()
                if (actual != null) {
                    valueMap[pointer] = actual
                } else {
                    valueMap[pointer] = instruction.value()
                }

                continue
            }

            if (instruction is Alloc && instruction.isLocalVariable()) {
                valueMap[instruction] = Value.UNDEF
                continue
            }

            if (instruction is Load && instruction.isLocalVariable()) {
                val actual = findActualValue(bb, instruction.operand())
                valueMap[instruction] = actual
                continue
            }

            if (instruction is Phi) {
                // Note: all used values are equal in uncompleted phi instruction.
                // Will take only first value.
                valueMap[instruction.operands().first()] = instruction
                continue
            }

            instruction.update { v -> rename(bb, v) }
        }
    }

    companion object {
        internal fun convertOrSkip(type: Type, value: Value): Value {
            if (value !is Constant) {
                return value
            }

            return Constant.from(type, value)
        }

        fun run(cfg: BasicBlocks, dominatorTree: DominatorTree): ReachingDefinition {
            val ana = ReachingDefinitionAnalysis(cfg, dominatorTree)
            return ReachingDefinition(ana.bbToMapValues, dominatorTree)
        }
    }
}

class ReachingDefinition internal constructor(private val info: Map<Block, Map<Value, Value>>, dominatorTree: DominatorTree): AbstractReachingDefinitionAnalysis(dominatorTree) {
    override fun toString(): String {
        val builder = StringBuilder()
        for ((bb, valueMap) in info) {
            builder.append("----- bb=$bb -----\n")
            for ((from, to) in valueMap) {
                builder.append("$from -> $to\n")
            }
        }

        return builder.toString()
    }

    override fun valueMap(): Map<Block, Map<Value, Value>> = info
}