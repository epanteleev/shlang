package ir.pass.transform

import ir.module.Module
import ir.pass.common.TransformPassFabric
import ir.pass.common.TransformPass
import ir.platform.x64.LModule
import ir.pass.transform.auxiliary.*
import ir.platform.x64.auxiliary.Lowering
import ir.platform.x64.pass.transform.*


class SSADestruction(module: Module): TransformPass(module) {
    override fun name(): String = "ssa-destruction"
    override fun run(): Module {
        val transformed = MoveLargeConstants.run(Lowering.run(FunctionsIsolation.run(module)))
        return LModule(transformed.functions, transformed.functionDeclarations, transformed.constantPool, transformed.globals, transformed.types)
    }
}

object SSADestructionFabric: TransformPassFabric() {
    override fun create(module: Module): TransformPass {
        return SSADestruction(module.copy())
    }
}