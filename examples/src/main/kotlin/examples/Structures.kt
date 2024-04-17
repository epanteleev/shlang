package examples

import ir.*
import ir.types.*
import ir.module.builder.impl.ModuleBuilder
import ir.pass.transform.SSADestructionFabric
import ir.platform.common.CodeGenerationFactory
import ir.platform.common.Target


fun main() {
    val builder = ModuleBuilder.create()
    val pointStruct = StructType("point", arrayListOf(Type.I32, Type.I32))
    builder.addStructType(pointStruct)

    builder.createFunction("main", Type.I32, arrayListOf()).apply {
        val first = alloc(pointStruct)
        val firstField = gep(first, Type.I32, I32Value(0))
        store(firstField, I32Value(4))
        ret(I32Value(0))
    }

    val module = builder.build()
    println(module.toString())
    val des = SSADestructionFabric.create(module).run()
    println(des)
    val asm = CodeGenerationFactory()
        .setTarget(Target.X64)
        .build(SSADestructionFabric.create(module).run())

    println(asm.toString())
}