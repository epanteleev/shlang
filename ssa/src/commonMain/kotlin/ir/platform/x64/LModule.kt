package ir.platform.x64

import common.assertion
import ir.global.GlobalSymbol
import ir.module.*
import ir.types.StructType
import ir.module.auxiliary.*
import ir.platform.x64.regalloc.LinearScan
import ir.platform.x64.regalloc.RegisterAllocation
import ir.liveness.LiveIntervals


class LModule(functions: List<FunctionData>,
                   externFunctions: Map<String, ExternFunction>,
                   globals: Map<String, GlobalSymbol>,
                   types: Map<String, StructType>):
    Module(functions, externFunctions, globals, types) {
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
        assertion(allocation != null) {
            "cannot find register allocation information for ${data.prototype}"
        }

        return allocation!!
    }

    fun liveInfo(data: FunctionData): LiveIntervals {
        val liveInfo = liveIntervals[data]
        assertion(liveInfo != null) {
            "cannot liveness information for ${data.prototype}"
        }

        return liveInfo!!
    }

    override fun copy(): Module {
        return LModule(functions.map { CopyCFG.copy(it) }, externFunctions, globals, types)
    }

    override fun toString(): String {
        return DumpModule.dump(this)
    }
}