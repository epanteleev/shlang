package examples

import ir.global.StringLiteralGlobalConstant
import ir.types.*
import ir.module.builder.impl.ModuleBuilder
import ir.pass.transform.SSADestructionFabric
import ir.platform.common.CodeGenerationFactory
import ir.platform.common.TargetPlatform
import ir.value.constant.I32Value


fun main() {
    val builder = ModuleBuilder.create()
    val helloStr = builder.addConstant(StringLiteralGlobalConstant("str", ArrayType(Type.I8, 10), "Hello world"))
    val printf = builder.createExternFunction("printf", Type.I32, arrayListOf(PtrType), true)
    builder.createFunction("main", Type.I32, arrayListOf(Type.I32), true).apply {
        val cont = createLabel()
        call(printf, arrayListOf(helloStr, I32Value(0)), cont)
        switchLabel(cont)
        ret(Type.I32, arrayOf(I32Value(0)))
    }

    val module = builder.build()
    println(module.toString())
    val asm = CodeGenerationFactory()
        .setTarget(TargetPlatform.X64)
        .build(SSADestructionFabric.create(module).run())

    println(asm.toString())
}
