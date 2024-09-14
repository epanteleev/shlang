package ir.platform.x64.pass.analysis.regalloc

import ir.value.LocalValue
import asm.x64.Operand
import asm.x64.Register
import asm.x64.GPRegister.rcx
import asm.x64.GPRegister.rdx
import common.assertion
import common.forEachWith
import ir.value.asType
import ir.module.FunctionData
import ir.instruction.Callable
import ir.instruction.Copy
import ir.instruction.matching.*
import ir.module.Sensitivity
import ir.module.block.Block
import ir.pass.analysis.InterferenceGraphFabric
import ir.pass.analysis.intervals.LiveIntervalsFabric
import ir.pass.common.AnalysisType
import ir.pass.common.FunctionAnalysisPass
import ir.pass.common.FunctionAnalysisPassFabric
import ir.types.NonTrivialType
import ir.types.TupleType


class LinearScan internal constructor(private val data: FunctionData): FunctionAnalysisPass<RegisterAllocation>() {
    private val liveRanges = data.analysis(LiveIntervalsFabric)
    private val interferenceGraph = data.analysis(InterferenceGraphFabric)

    private val registerMap = hashMapOf<LocalValue, Operand>()
    private val fixedValues = arrayListOf<LocalValue>() // Debug only purpose
    private val active      = hashMapOf<LocalValue, Operand>()
    private val pool        = VirtualRegistersPool.create(data.arguments())

    init {
        allocFixedRegisters()
        allocRegistersForArgumentValues()
        allocRegistersForLocalVariables()
    }

    override fun run(): RegisterAllocation {
        return RegisterAllocation(pool.stackSize(), registerMap, data.marker())
    }

    private fun allocate(slot: Operand, value: LocalValue) {
        registerMap[value] = slot
        active[value] = slot
    }

    private fun allocRegistersForArgumentValues() {
        for (arg in data.arguments()) {
            fixedValues.add(arg)
            allocate(pool.takeArgument(arg), arg)
        }
    }

    private fun allocFunctionArguments(callable: Callable) {
        val allocation = pool.calleeArgumentAllocate(callable.arguments())
        allocation.forEachWith(callable.arguments()) { operand, arg ->
            if (operand == null) {
                // Nothing to do. UB happens
                return@forEachWith
            }

            fixedValues.add(arg as LocalValue)
            registerMap[arg] = operand
        }
    }

    private fun tryAllocFixedRegisters(bb: Block) {
        for (inst in bb) {
            match(inst) {
                shl(nop(), constant().not()) { shl ->
                    val value = shl.second() as Copy
                    registerMap[value] = rcx
                    fixedValues.add(value)
                }
                shr(nop(), constant().not()) { shr ->
                    val value = shr.second() as Copy
                    registerMap[value] = rcx
                    fixedValues.add(value)
                }
            }
        }
    }

    private fun allocFixedRegisters() {
       for (bb in data) {
           val inst = bb.last()
           if (inst is Callable) {
               allocFunctionArguments(inst)
           }

           tryAllocFixedRegisters(bb)
       }
    }

    private fun allocRegistersForLocalVariables() {
        for ((value, range) in liveRanges) {
            val reg = registerMap[value]
            if (reg != null) {
                assertion(fixedValues.contains(value)
                        || liveRanges.getGroup(value) != null) {
                    "value=$value is already allocated"
                }
                active[value] = reg
                continue
            }

            if (value.type() is TupleType) {
                // Skip tuple instructions
                // Register allocation for tuple instructions will be done for their projections
                continue
            }

            active.entries.retainAll {
                if (liveRanges[it.key].end() <= range.begin()) {
                    val size = it.key.asType<NonTrivialType>().sizeOf()
                    pool.free(it.value, size)
                    return@retainAll false
                } else {
                    return@retainAll true
                }
            }
            pickOperandGroup(value)
        }
    }

    private fun excludeIf(value: LocalValue, reg: Register): Boolean {
        if (reg == rdx) {
            for (use in value.usedIn()) {
                if (tupleDiv(nop(), it_is(value)) (use)) return true
            }
        }

        val neighbors = interferenceGraph.neighbors(value)
        if (neighbors == null) {
            return false
        }

        return neighbors.any { registerMap[it] == reg }
    }

    private fun pickOperandGroup(value: LocalValue) {
        val operand = pool.allocSlot(value) { reg -> excludeIf(value, reg) }
        val group = liveRanges.getGroup(value)
        if (group == null) {
            allocate(operand, value)
            return
        }
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