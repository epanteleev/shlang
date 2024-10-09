package examples

import ir.types.Type
import ir.module.builder.impl.ModuleBuilder
import ir.pass.transform.SSADestructionFabric
import ir.platform.common.CodeGenerationFactory
import ir.platform.common.TargetPlatform
import ir.value.constant.F32Value
import I32Value


fun main() {
    val builder = ModuleBuilder.create()
    val printFloat = builder.createExternFunction("printFloat", Type.Void, arrayListOf(Type.F32))
    builder.createFunction("main", Type.I32, arrayListOf()).apply {
        val first = alloc(Type.F32)
        store(first, F32Value(4f))
        val second = alloc(Type.F32)
        store(second, F32Value(8f))

        load(Type.F32, first)
        val s = load(Type.F32, second)
        val res = add(s, s)
        val cont = createLabel()
        vcall(printFloat, arrayListOf(res), cont)
        switchLabel(cont)
        ret(Type.U32, arrayOf(I32Value(0)))
    }

    val module = builder.build()
    println(module.toString())
    val des = SSADestructionFabric.create(module).run()
    println(des)
    val asm = CodeGenerationFactory()
        .setTarget(TargetPlatform.X64)
        .build(des)

    println(asm.toString())
}