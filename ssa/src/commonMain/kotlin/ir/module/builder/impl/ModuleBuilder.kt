package ir.module.builder.impl

import ir.*
import ir.module.AnyFunctionPrototype
import ir.module.ExternFunction
import ir.types.Type
import ir.module.Module
import ir.module.SSAModule
import ir.pass.ana.VerifySSA
import ir.module.builder.AnyModuleBuilder
import ir.read.tokens.Vararg


class ModuleBuilder private constructor(): AnyModuleBuilder() {
    private val functions = arrayListOf<FunctionDataBuilder>()
    private val externFunctions = hashMapOf<String, ExternFunction>()

    fun findFunction(name: String): AnyFunctionPrototype {
        val fnBuilder: AnyFunctionPrototype = functions.find { it.prototype().name() == name }?.prototype()
            ?: externFunctions[name] ?: throw RuntimeException("not found name=$name") //TODO O(n)
        return fnBuilder
    }

    fun createFunction(name: String, returnType: Type, argumentTypes: List<Type>, isVararg: Boolean = false): FunctionDataBuilder {
        val data = FunctionDataBuilder.create(name, returnType, argumentTypes, isVararg)
        functions.add(data)
        return data
    }

    fun createFunction(name: String, returnType: Type, argumentTypes: List<Type>, argumentValues: List<ArgumentValue>, isVararg: Boolean = false): FunctionDataBuilder {
        val data = FunctionDataBuilder.create(name, returnType, argumentTypes, argumentValues, isVararg)
        functions.add(data)
        return data
    }

    fun createExternFunction(name: String, returnType: Type, arguments: List<Type>, isVararg: Boolean = false): ExternFunction {
        val extern = ExternFunction(name, returnType, arguments, isVararg)
        externFunctions[name] = extern
        return extern
    }

    override fun build(): Module {
        val fns = functions.mapTo(arrayListOf()) {
            it.build()
        }

        return VerifySSA.run(SSAModule(fns, externFunctions, globals, structs))
    }

    companion object {
        fun create() : ModuleBuilder {
            return ModuleBuilder()
        }
    }
}