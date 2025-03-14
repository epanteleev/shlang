package ir.platform.common

import ir.module.Module
import ir.pass.CompileContext
import ir.pass.PassPipeline
import ir.pass.PassPipeline.Companion.create
import ir.pass.transform.CSSAConstructionFabric
import ir.pass.transform.Mem2RegFabric
import ir.pass.transform.SSADestructionFabric
import ir.platform.x64.codegen.X64CodeGenerator


enum class TargetPlatform {
    X64
}

interface AnyCodeGenerator {
    fun emit(): CompiledModule
}

class CompilationContext(val picEnabled: Boolean)

class CodeGenerationFactory {
    private var ctx: CompileContext? = null
    private var target: TargetPlatform? = null
    private var picEnabled: Boolean = false

    fun setContext(context: CompileContext): CodeGenerationFactory {
        ctx = context
        return this
    }

    fun setTarget(target: TargetPlatform): CodeGenerationFactory {
        this.target = target
        return this
    }

    fun pic(picEnabled: Boolean): CodeGenerationFactory {
        this.picEnabled = picEnabled
        return this
    }

    fun build(module: Module): CompiledModule {
        val preparedModule = beforeCodegen(ctx as CompileContext)
            .run(module)

        val compiled = when (target as TargetPlatform) {
            TargetPlatform.X64 -> X64CodeGenerator(preparedModule, CompilationContext(picEnabled)).emit()
        }

        return compiled
    }

    companion object {
        private fun beforeCodegen(ctx: CompileContext): PassPipeline = create("before-codegen", arrayListOf(CSSAConstructionFabric, SSADestructionFabric), ctx)
    }
}