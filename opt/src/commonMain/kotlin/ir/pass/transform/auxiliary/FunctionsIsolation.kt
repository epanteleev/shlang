package ir.pass.transform.auxiliary

import ir.types.*
import ir.instruction.*
import ir.instruction.matching.*
import ir.module.FunctionData
import ir.module.Module
import ir.module.SSAModule
import ir.module.block.Block
import ir.pass.analysis.LivenessAnalysisPassFabric
import ir.value.ArgumentValue
import ir.value.constant.U64Value


internal class FunctionsIsolation private constructor(private val cfg: FunctionData) {
    private val liveness = cfg.analysis(LivenessAnalysisPassFabric)
    private val allCalls = run { //TODO separate analysis pass
        val calls = arrayListOf<Instruction>()
        for (bb in cfg) {
            for (inst in bb) {
                if (inst is Callable) {
                    calls.add(inst)
                }
            }
        }
        calls
    }

    private var isNeed3ArgIsolation: Boolean = false
    private var isNeed4ArgIsolation: Boolean = false

    private fun isolateBinaryOp() {
        fun transform(bb: Block, inst: Instruction): Instruction {
            match(inst) {
                shl(nop(), constant().not()) { shl ->
                    val copy = bb.insertBefore(inst) { it.copy(shl.rhs()) }
                    bb.updateDF(inst, Shl.OFFSET, copy)
                    isNeed4ArgIsolation = true
                }
                shr(nop(), constant().not()) { shr ->
                    val copy = bb.insertBefore(inst) { it.copy(shr.rhs()) }
                    bb.updateDF(inst, Shr.OFFSET, copy)
                    isNeed4ArgIsolation = true
                }
                tupleDiv(nop(), nop()) { tupleDiv ->
                    val rem = tupleDiv.remainder()
                    if (rem == null) {
                        bb.insertAfter(inst) { it.proj(tupleDiv, 1) }
                    } else {
                        bb.updateUsages(rem) { bb.insertAfter(rem) { it.copy(rem) } }
                    }
                    isNeed3ArgIsolation = true
                }
            }
            return inst
        }

        for (bb in cfg)  {
            bb.transform { inst -> transform(bb, inst) }
        }
    }

    private fun mustBeIsolated(arg: ArgumentValue, index: Int): Boolean {
        if (index == 2 && isNeed3ArgIsolation) {
            return true
        }
        if (index == 3 && isNeed4ArgIsolation) {
            return true
        }

        for (call in allCalls) {
            call as Callable
            if (liveness.liveOut(call).contains(arg)) {
                // Argument is live out of the call
                return true
            }
            if (call.operands().contains(arg)) {
                // Argument is used in the call
                return true
            }
        }

        return false
    }

    private fun isolateArgumentValues() {
        val begin = cfg.begin()
        for ((idx, arg) in cfg.arguments().withIndex()) {
            if (!mustBeIsolated(arg, idx)) {
                continue
            }

            begin.updateUsages(arg) { begin.prepend { it.copy(arg) } }
        }
    }

    private fun isolateCall() {
        fun insertCopies(bb: Block, call: Instruction): Instruction {
            if (call !is Callable) {
                return call
            }
            bb.insertBefore(call) { it.downStackFrame(call) }

            for ((i, arg) in call.arguments().withIndex()) {
                when (val argType = arg.type()) {
                    is PrimitiveType -> {
                        val copy = bb.insertBefore(call) { it.copy(arg) }
                        bb.updateDF(call, i, copy)
                    }
                    is AggregateType -> {
                        val gen = bb.insertBefore(call) { it.gen(argType) }
                        bb.insertAfter(gen) { it.memcpy(gen, arg, U64Value(argType.sizeOf().toLong())) }
                        bb.updateDF(call, i, gen)
                    }
                    else -> throw IllegalStateException("Unexpected type: $argType")
                }
            }

            call.target().prepend { it.upStackFrame(call) }
            return call
        }

        for (bb in cfg) {
            bb.transform { inst -> insertCopies(bb, inst) }
        }
    }

    fun pass() {
        isolateBinaryOp()
        isolateArgumentValues()
        isolateCall()
    }

    companion object {
        fun run(module: Module): Module {
            module.functions().forEach { FunctionsIsolation(it).pass() }
            return SSAModule(module.functions, module.externFunctions, module.constantPool, module.globals, module.types)
        }
    }
}