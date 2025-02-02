package ir.platform.x64.pass.analysis.regalloc

import ir.types.*
import asm.Operand
import asm.Register
import ir.value.LocalValue
import asm.x64.GPRegister.rcx
import asm.x64.GPRegister.rdx
import common.assertion
import common.forEachWith
import ir.instruction.*
import ir.value.asType
import ir.module.FunctionData
import ir.instruction.lir.Generate
import ir.instruction.lir.Lea
import ir.module.Sensitivity
import ir.pass.analysis.InterferenceGraphFabric
import ir.pass.analysis.intervals.LiveIntervalsFabric
import ir.pass.common.AnalysisType
import ir.pass.common.FunctionAnalysisPass
import ir.pass.common.FunctionAnalysisPassFabric
import ir.platform.x64.pass.analysis.FixedRegisterInstructionsAnalysis


class LinearScan internal constructor(private val data: FunctionData): FunctionAnalysisPass<RegisterAllocation>() {
    private val liveRanges = data.analysis(LiveIntervalsFabric)
    private val interferenceGraph = data.analysis(InterferenceGraphFabric)
    private val fixedRegistersInfo = FixedRegisterInstructionsAnalysis.run(data)

    private val registerMap = hashMapOf<LocalValue, Operand>()
    private val callInfo    = hashMapOf<Callable, List<Operand?>>()
    private val active      = linkedMapOf<LocalValue, Operand>()
    private val pool        = VirtualRegistersPool.create(data.arguments())

    init {
        allocFixedRegisters()
        allocRegistersForArgumentValues()
        allocRegistersForLocalVariables()
    }

    override fun run(): RegisterAllocation {
        return RegisterAllocation(pool.spilledLocalsAreaSize(), registerMap, pool.usedGPCalleeSaveRegisters(), callInfo, data.marker())
    }

    private fun allocate(slot: Operand, value: LocalValue) {
        registerMap[value] = slot
        active[value] = slot
    }

    private fun allocRegistersForArgumentValues() {
        for (arg in data.arguments()) {
            allocate(pool.takeArgument(arg), arg)
        }
    }

    private fun allocFunctionArguments(callable: Callable) {
        val allocation = pool.callerArgumentAllocate(callable.arguments())
        callInfo[callable] = allocation

        allocation.forEachWith(callable.arguments()) { operand, arg ->
            if (operand == null) {
                // Nothing to do. UB happens
                return@forEachWith
            }
            assertion(arg is Copy || arg is Lea || arg is Generate) { "arg=$arg" }

            registerMap[arg as LocalValue] = operand
        }
    }

    private fun allocFixedRegisters() {
        for (bb in data) {
            val inst = bb.last()
            if (inst is Callable) {
                allocFunctionArguments(inst)
            }
        }

        for (value in fixedRegistersInfo.rdxFixedReg) {
            registerMap[value] = rdx
        }

        for (value in fixedRegistersInfo.rcxFixedReg) {
            registerMap[value] = rcx
        }
    }

    private fun allocRegistersForLocalVariables() {
        for ((value, range) in liveRanges) {
            if (value.type() is TupleType) {
                // Skip tuple instructions
                // Register allocation for tuple instructions will be done for their projections
                continue
            }
            if (value.type() is FlagType) {
                // Skip boolean instructions
                continue
            }
            if (value.type() is UndefType) {
                // Skip undefined instructions
                continue
            }
            val reg = registerMap[value]
            if (reg != null) {
                // Found value with fixed register. Skip it
                // Register allocation for such instructions is already done
                active[value] = reg
                continue
            }

            active.entries.retainAll { (local, operand) ->
                if (!liveRanges[local].intersect(range)) {
                    val size = local.asType<NonTrivialType>().sizeOf()
                    pool.free(operand, size)
                    return@retainAll false
                } else {
                    return@retainAll true
                }
            }
            pickOperandGroup(value)
        }
    }

    private fun excludeIf(value: LocalValue, reg: Register): Boolean {
        val neighbors = interferenceGraph.neighbors(value) ?: return false
        return neighbors.any { registerMap[it] == reg }
    }

    private fun pickOperandGroup(value: LocalValue) {
        val operand = pool.allocSlot(value) { reg -> excludeIf(value, reg) }
        val group = liveRanges.getGroup(value) ?: return allocate(operand, value)
        for (v in group) {
            allocate(operand, v)
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("----Liveness----\n")
            .append(liveRanges.toString())
            .append("----Register allocation----\n")

        for ((value , _) in liveRanges) {
            builder.append("$value -> ${registerMap[value]}\n")
        }
        builder.append("----The end----\n")
        return builder.toString()
    }
}

object LinearScanFabric: FunctionAnalysisPassFabric<RegisterAllocation>() {
    override fun type(): AnalysisType {
        return AnalysisType.LINEAR_SCAN
    }

    override fun sensitivity(): Sensitivity {
        return Sensitivity.CONTROL_AND_DATA_FLOW
    }

    override fun create(functionData: FunctionData): RegisterAllocation {
        return LinearScan(functionData).run()
    }
}