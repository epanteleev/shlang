package ir.codegen

import asm.*
import ir.*

class LinearScan(data: FunctionData) {
    private val liveRanges = data.liveness()
    private val registerMap = hashMapOf<LocalValue, Operand>()

    fun build(): RegisterAllocation {
        val pool = RegisterPool()
        allocRegistersForLocalVariables(pool)
        val frameSize = pool.stackSize()
        return RegisterAllocation(frameSize, registerMap, liveRanges)
    }

    private fun allocRegistersForLocalVariables(pool: RegisterPool) {
        val active = hashMapOf<LocalValue, Operand>()
        for ((variable, range) in liveRanges) {
            active.entries.removeIf {
                if (liveRanges[it.key].isDiedHere(range.begin())) {
                    pool.free(it.value)
                    return@removeIf true
                } else {
                    return@removeIf false
                }
            }

            val operand = pool.allocSlot(variable)
            active[variable] = operand
            registerMap[variable] = operand
        }

        pool.finalize()
    }

    companion object {
        fun alloc(data: FunctionData): RegisterAllocation {
            return LinearScan(data).build()
        }
    }
}
