package examples

import ir.value.I32Value
import ir.platform.common.CodeGenerationFactory
import ir.platform.common.TargetPlatform
import ir.types.Type


fun main() {
    val builder = ir.module.builder.impl.ModuleBuilder.create()
    builder.createFunction("func", ir.types.Type.I32, arrayListOf(ir.types.Type.I32)).apply {
        val divRes = tupleDiv(I32Value(10), I32Value(2))
        val reminder = proj(divRes, 1)
        ret(Type.U32, arrayOf(reminder))
    }

    val module = builder.build()
    println(module.toString())
    val asm = CodeGenerationFactory()
        .setTarget(TargetPlatform.X64)
        .build(ir.pass.transform.SSADestructionFabric.create(module).run())

    println(asm.toString())
}
