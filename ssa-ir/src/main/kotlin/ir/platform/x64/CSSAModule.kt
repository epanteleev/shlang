package ir.platform.x64

import ir.module.*
import ir.ExternFunction
import ir.module.auxiliary.Copy
import ir.module.auxiliary.DumpModule
import ir.platform.regalloc.LinearScan
import ir.platform.regalloc.RegisterAllocation
import ir.platform.liveness.LiveIntervals

data class CSSAModule(override val functions: List<FunctionData>, override val externFunctions: Set<ExternFunction>):
    Module(functions, externFunctions) {

    private val liveIntervals: Map<FunctionData, LiveIntervals>
    private val registerAllocation: Map<FunctionData, RegisterAllocation>

    init {
        liveIntervals = hashMapOf()
        for (fn in functions) {
            liveIntervals[fn] = fn.liveness()
        }

        registerAllocation = hashMapOf()
        for ((fn, liveIntervals) in liveIntervals) {
            registerAllocation[fn] = LinearScan.alloc(fn, liveIntervals)
        }
    }

    fun regAlloc(data: FunctionData): RegisterAllocation {
        val allocation = registerAllocation[data]
        assert(allocation != null) {
            "cannot find register allocation information for ${data.prototype}"
        }

        return allocation!!
    }

    fun liveInfo(data: FunctionData): LiveIntervals {
        val liveInfo = liveIntervals[data]
        assert(liveInfo != null) {
            "cannot liveness information for ${data.prototype}"
        }

        return liveInfo!!
    }

    override fun copy(): Module {
        return SSAModule(functions.map { Copy.copy(it) }, externFunctions.mapTo(mutableSetOf()) { it })
    }

    override fun toString(): String {
        return DumpModule.dump(this)
    }
}