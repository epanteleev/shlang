package ir.pass.transform.utils

import ir.value.*
import ir.types.Type
import ir.instruction.*
import ir.instruction.matching.alloc
import ir.instruction.matching.primitive
import ir.module.block.*
import ir.pass.analysis.dominance.DominatorTree
import ir.module.FunctionData
import ir.pass.analysis.EscapeAnalysisPassFabric
import ir.pass.analysis.traverse.PreOrderFabric
import ir.pass.transform.Mem2RegException
import ir.types.NonTrivialType


//TODO this is not a Reaching Definition Analysis
abstract class AbstractRewritePrimitives(private val dominatorTree: DominatorTree) {
    protected fun rename(bb: Block, oldValue: Value): Value {
        return tryRename(bb, oldValue, oldValue.type())?: oldValue
    }

    fun tryRename(bb: Block, oldValue: Value, expectedType: Type): Value? {
        if (oldValue !is LocalValue) {
            return oldValue
        }

        val newValue = findActualValueOrNull(bb, oldValue)
            ?: return null
        return RewritePrimitivesUtil.convertOrSkip(expectedType, newValue)
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

class RewritePrimitivesUtil private constructor(cfg: FunctionData, dominatorTree: DominatorTree): AbstractRewritePrimitives(dominatorTree) {
    private val escapeState = cfg.analysis(EscapeAnalysisPassFabric)
    private val bbToMapValues = run {
        val bbToMapValues = hashMapOf<Block, MutableMap<Value, Value>>()
        for (bb in cfg) {
            bbToMapValues[bb] = hashMapOf()
        }

        bbToMapValues
    }

    init {
        for (bb in cfg.analysis(PreOrderFabric)) {
            rewriteValuesSetup(bb)
        }
    }

    override fun valueMap(): Map<Block, Map<Value, Value>> {
        return bbToMapValues
    }

    private fun rewriteValuesSetup(bb: Block) {
        val valueMap = bbToMapValues[bb]!!
        for (instruction in bb) {
            if (instruction.emptyOperands()) {
                continue
            }

            if (instruction is Store && escapeState.isNoEscape(instruction.pointer())) {
                val actual = findActualValueOrNull(bb, instruction.value())
                val pointer = instruction.pointer()
                if (actual != null) {
                    valueMap[pointer] = actual
                } else {
                    valueMap[pointer] = instruction.value()
                }

                continue
            }

            if (alloc(primitive()) (instruction) && escapeState.isNoEscape(instruction as Alloc)) {
                valueMap[instruction] = Value.UNDEF
                continue
            }

            if (instruction is Load && escapeState.isNoEscape(instruction.operand())) {
                val actual = findActualValue(bb, instruction.operand())
                valueMap[instruction] = actual
                continue
            }

            if (instruction is Phi) {
                // Note: all used values are equal in uncompleted phi instruction.
                // Will take only first value.
                valueMap[instruction.operand(0)] = instruction
                continue
            }

            bb.updateDF(instruction) { v -> rename(bb, v) }
        }
    }

    companion object {
        internal fun convertOrSkip(type: Type, value: Value): Value {
            if (value !is Constant) {
                return value
            }

            return Constant.from(type as NonTrivialType, value)
        }

        fun run(cfg: FunctionData, dominatorTree: DominatorTree): RewritePrimitives {
            val ana = RewritePrimitivesUtil(cfg, dominatorTree)
            return RewritePrimitives(ana.bbToMapValues, dominatorTree)
        }
    }
}

class RewritePrimitives internal constructor(private val info: Map<Block, Map<Value, Value>>, dominatorTree: DominatorTree): AbstractRewritePrimitives(dominatorTree) {
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