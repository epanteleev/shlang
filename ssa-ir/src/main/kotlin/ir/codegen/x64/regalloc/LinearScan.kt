package ir.codegen.x64.regalloc

import ir.*
import asm.x64.Operand
import ir.instruction.Callable
import ir.instruction.ValueInstruction

class LinearScan(private val data: FunctionData) {
    private val liveRanges = data.liveness()
    private val registerMap = hashMapOf<LocalValue, Operand>()
    private val active = hashMapOf<Group, Operand>()
    private val pool = VirtualRegistersPool.create(data.arguments())
    private val liveRangesGroup: CoalescedLiveIntervals

    init {
        allocRegistersForArgumentValues()
        handleArguments()

        liveRangesGroup = Coalescing.evaluate(liveRanges, registerMap)

        handleStackAlloc(liveRangesGroup)
        allocRegistersForLocalVariables(liveRangesGroup)
    }

    fun build(): RegisterAllocation {
        return RegisterAllocation(pool.stackSize(), registerMap, liveRanges)
    }

    private fun allocRegistersForArgumentValues() {
        for (arg in data.arguments()) {
            registerMap[arg] = pool.takeArgument(arg)
        }
    }

    private fun handleArguments() {
       for ((value, _) in liveRanges) {
           if (value !is Callable) {
               continue
           }
            val allocation = CalleeArgumentAllocator.alloc(value.arguments().toList())
            for ((operand, arg) in allocation zip value.arguments()) {
                registerMap[arg as ValueInstruction] = operand
            }
       }
    }

    private fun handleStackAlloc(liveRangesGroup: CoalescedLiveIntervals) {
        for ((group, _) in liveRangesGroup) {
            if (!group.stackAllocGroup) {
                continue
            }

            pickOperandGroup(group)
        }
    }

    private fun allocRegistersForLocalVariables(liveRangesGroup: CoalescedLiveIntervals) {
        for ((group, range) in liveRangesGroup) {
            val arg = group.precolored
            if (arg != null || group.stackAllocGroup) {
                continue
            }

            active.entries.removeIf {
                if (liveRangesGroup[it.key].end() < range.begin()) {
                    pool.free(it.value)
                    return@removeIf true
                } else {
                    return@removeIf false
                }
            }
            pickOperandGroup(group)
        }
    }

    private fun pickOperandGroup(group: Group) {
        val operand = pool.allocSlot(group.first())
        active[group] = operand
        for (value in group) {
            registerMap[value] = operand
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("----Liveness----\n")
            .append(liveRanges.toString())
            .append("----Groups----\n")
            .append(liveRangesGroup.toString())
            .append("----Register allocation----\n")

        for ((group, _) in liveRangesGroup) {
            for (value in group) {
                builder.append("$value -> ${registerMap[value]}\n")
            }
        }
        builder.append("----The end----\n")
        return builder.toString()
    }

    companion object {
        fun alloc(data: FunctionData): RegisterAllocation {
            val linearScan = LinearScan(data)
            println(linearScan)
            return linearScan.build()
        }
    }
}