package ir.pass.transform

import ir.module.Module
import ir.module.SSAModule
import ir.pass.PassFabric
import ir.pass.TransformPass
import ir.pass.transform.auxiliary.CopyInsertion
import ir.pass.transform.auxiliary.SplitCriticalEdge


class CSSAConstruction internal constructor(module: Module): TransformPass(module) {
    override fun name(): String = "cssa-construction"

    override fun run(): Module {
        val transformed = CopyInsertion.run(SplitCriticalEdge.run(module))
        return SSAModule(transformed.functions, transformed.externFunctions, transformed.globals, transformed.types)
    }
}

object CSSAConstructionFabric: PassFabric {
    override fun create(module: Module): TransformPass {
        return CSSAConstruction(module.copy())
    }
}