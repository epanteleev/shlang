package ir.pass.transform.auxiliary

import ir.attributes.ByValue
import ir.global.GlobalValue
import ir.types.*
import ir.instruction.*
import ir.instruction.lir.Generate
import ir.instruction.lir.Lea
import ir.instruction.matching.*
import ir.module.FunctionData
import ir.module.Module
import ir.module.SSAModule
import ir.module.block.Block
import ir.pass.analysis.LivenessAnalysisPassFabric
import ir.value.*
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

    private fun isolateSpecialInstructions() {
        fun transform(bb: Block, inst: Instruction): Instruction {
            inst.match(shl(nop(), constant().not())) { shl: Shl ->
                val copy = bb.putBefore(inst, Copy.copy(shl.rhs()))
                bb.updateDF(inst, Shl.OFFSET, copy)
                isNeed4ArgIsolation = true
                return inst
            }

            inst.match(shr(nop(), constant().not())) { shr: Shr ->
                val copy = bb.putBefore(inst, Copy.copy(shr.rhs()))
                bb.updateDF(inst, Shr.OFFSET, copy)
                isNeed4ArgIsolation = true
                return inst
            }

            inst.match(tupleDiv(nop(), nop())) { tupleDiv: TupleDiv ->
                val rem = tupleDiv.remainder()
                if (rem == null) {
                    bb.putAfter(inst, Projection.proj(tupleDiv, 1))
                } else {
                    bb.updateUsages(rem) { bb.putAfter(rem, Copy.copy(rem)) }
                }
                isNeed3ArgIsolation = true
                return inst
            }

            inst.match(proj(int(), tupleCall(), 1)) {
                isNeed3ArgIsolation = true
                return inst
            }

            inst.match(memcpy(nop(), nop(), nop())) {
                isNeed3ArgIsolation = true
                isNeed4ArgIsolation = true
                return inst
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

        if (arg.attributes.find { it is ByValue } != null) {
            // Argument is in overflow area
            return false
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

            begin.updateUsages(arg) { begin.prepend(Copy.copy(arg)) }
        }
    }

    private fun isolateByValueArgument(bb: Block, call: Instruction, i: ByValue, arg: Value) {
        val gen = bb.putBefore(call, Generate.gen(i.aggregateType))
        val lea = bb.putAfter(gen, Lea.lea(gen))
        bb.putAfter(lea, Memcpy.memcpy(lea, arg, U64Value.of(i.aggregateType.sizeOf().toLong())))
        bb.updateDF(call, i.argumentIndex, gen)
    }

    private fun isolateByValueArguments(bb: Block, call: Instruction) {
        call as Callable
        val byValueAttr = call.attributes().filterIsInstance<ByValue>()
        for (byValue in byValueAttr) {
            val arg = call.arguments()[byValue.argumentIndex]
            isolateByValueArgument(bb, call, byValue, arg)
        }
    }

    private fun insertCopies(bb: Block, call: Instruction) {
        call as Callable
        val byValueAttr = call.attributes().filterIsInstance<ByValue>()
        for ((i, arg) in call.arguments().withIndex()) {
            when (val ty = arg.type()) {
                is FloatingPointType, is IntegerType -> {
                    val copy = bb.putBefore(call, Copy.copy(arg))
                    bb.updateDF(call, i, copy)
                }
                is PtrType -> {
                    val copyOrLea = if (arg is GlobalValue) {
                        bb.putBefore(call, Lea.lea(arg))
                    } else {
                        bb.putBefore(call, Copy.copy(arg))
                    }
                    bb.updateDF(call, i, copyOrLea)
                }
                is AggregateType -> {
                    val byValue = byValueAttr.find { it.argumentIndex == i }
                    if (byValue == null) {
                        throw IllegalStateException("ByValue attribute not found for argument $i")
                    }
                }
                is UndefType -> {}
                else -> throw IllegalArgumentException("Unexpected type: $ty")
            }
        }
    }

    private fun wrapCallInstruction(bb: Block, call: Instruction): Instruction {
        if (call !is Callable) {
            return call
        }
        bb.putBefore(call, DownStackFrame.dsf(call))
        isolateByValueArguments(bb, call)
        insertCopies(bb, call)
        call.target().prepend(UpStackFrame.usf(call))
        return call
    }

    private fun isolateCall() {
        for (bb in cfg) {
            bb.transform { inst -> wrapCallInstruction(bb, inst) }
        }
    }

    fun pass() {
        isolateSpecialInstructions()
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